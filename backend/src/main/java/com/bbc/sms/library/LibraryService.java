package com.bbc.sms.library;

import com.bbc.sms.library.dto.LibraryDtos.ResourceUpsert;
import com.bbc.sms.library.dto.LibraryDtos.ResourceView;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.PermissionService;
import com.bbc.sms.platform.security.SectionRoles;
import com.bbc.sms.platform.storage.ObjectStorage;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bibliothèque de ressources : ce que la direction met à disposition, et pour qui.
 *
 * <p>Deux axes se croisent, et ce service est le seul endroit où ils se croisent :
 * <ul>
 *   <li><b>le destinataire</b> — {@code all} (personnel + parents), {@code staff},
 *       {@code parents} ;</li>
 *   <li><b>le périmètre</b> — toute l'école ({@code section} nulle) ou un cycle.</li>
 * </ul>
 *
 * <p>Le cloisonnement n'est jamais demandé au client : il se déduit du compte.
 * Un administrateur de cycle publie pour son cycle et pour lui seul ; il lit les
 * documents école-entière — ils le concernent — sans pouvoir les modifier. Un
 * enseignant ne voit que ce qui est publié et qui lui est adressé. Un parent
 * passe par le portail parent, où le périmètre se déduit des cycles de ses
 * enfants.
 *
 * <p>Le contrôle est refait à chaque téléchargement. L'identifiant d'une
 * ressource ne vaut pas droit d'y accéder : la liste et le fichier appliquent
 * la même règle.
 */
@Service
public class LibraryService {

    /** Destinataires reconnus — la contrainte CHECK de la table dit la même chose. */
    private static final Set<String> AUDIENCES = Set.of("all", "staff", "parents");

    /** Rubriques, pour trier et colorer la liste. */
    private static final Set<String> CATEGORIES = Set.of("circular", "pedagogy", "admin", "form", "other");

    /** 25 Mio — une circulaire scannée passe, une vidéo non. */
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    /**
     * Extensions acceptées et leur type MIME de référence.
     *
     * <p>C'est l'extension qui fait foi, pas l'en-tête envoyé par le navigateur :
     * celui-ci annonce volontiers {@code application/octet-stream} pour un .docx
     * selon le poste, et un client malveillant annonce ce qu'il veut. On garde
     * donc le type déduit du nom, seul cohérent d'un poste à l'autre.
     */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("pdf",  "application/pdf"),
            Map.entry("doc",  "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt",  "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf",  "application/rtf"),
            Map.entry("xls",  "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ods",  "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("ppt",  "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("odp",  "application/vnd.oasis.opendocument.presentation"),
            Map.entry("txt",  "text/plain"),
            Map.entry("csv",  "text/csv"),
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png",  "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif",  "image/gif"));

    private final SharedResourceRepository repo;
    private final ObjectStorage storage;
    private final TeacherScopeService teacherScope;
    private final PermissionService perm;
    private final JdbcTemplate jdbc;

    public LibraryService(SharedResourceRepository repo,
                          ObjectStorage storage,
                          TeacherScopeService teacherScope,
                          PermissionService perm,
                          JdbcTemplate jdbc) {
        this.repo = repo;
        this.storage = storage;
        this.teacherScope = teacherScope;
        this.perm = perm;
        this.jdbc = jdbc;
    }

    /** Le fichier servi au téléchargement — le flux est à fermer par l'appelant. */
    public record Download(InputStream stream, String fileName, String contentType, long byteSize) {}

    // ---- Lecture personnel ---------------------------------------------------

    /**
     * Les ressources visibles par le compte courant.
     *
     * <p>Qui publie voit aussi ses brouillons ; qui ne fait que consulter ne voit
     * que le publié qui lui est adressé — un document réservé aux parents
     * n'apparaît jamais dans la liste d'un enseignant.
     */
    @Transactional(readOnly = true)
    public List<ResourceView> list() {
        boolean canWrite = perm.can("library", "write");
        String viewerSection = teacherScope.section();
        return repo.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(r -> inScope(r, viewerSection))
                .filter(r -> canWrite || (r.isPublished() && !"parents".equals(r.getAudience())))
                .map(r -> view(r, canWrite && editable(r, viewerSection)))
                .toList();
    }

    /** Le fichier d'une ressource, après le même contrôle que la liste. */
    @Transactional(readOnly = true)
    public Download download(UUID id) {
        SharedResource r = require(id);
        boolean canWrite = perm.can("library", "write");
        String viewerSection = teacherScope.section();
        if (!inScope(r, viewerSection)) throw denied();
        if (!canWrite && (!r.isPublished() || "parents".equals(r.getAudience()))) throw denied();
        return open(r);
    }

    // ---- Lecture portail parent ---------------------------------------------

    /**
     * Ce qu'un parent peut consulter : le publié qui lui est adressé, borné aux
     * cycles où il a un enfant. Un document école-entière les concerne tous.
     */
    @Transactional(readOnly = true)
    public List<ResourceView> listForParent(UUID parentUserId) {
        Set<String> sections = childSections(parentUserId);
        return repo.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(r -> visibleToParent(r, sections))
                .map(r -> view(r, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Download downloadForParent(UUID parentUserId, UUID id) {
        SharedResource r = require(id);
        if (!visibleToParent(r, childSections(parentUserId))) throw denied();
        return open(r);
    }

    // ---- Écriture ------------------------------------------------------------

    /** Dépose un fichier et sa fiche. La ressource naît en brouillon si on le demande. */
    @Transactional
    public ResourceView create(MultipartFile file, ResourceUpsert in) {
        UUID schoolId = TenantContext.get();
        String section = resolveSection(in.section());
        String fileName = cleanFileName(file.getOriginalFilename());
        String contentType = validate(file, fileName);

        SharedResource r = new SharedResource();
        r.setSchoolId(schoolId);
        r.setObjectKey(storage.newKey(schoolId, "library", fileName));
        r.setFileName(fileName);
        r.setContentType(contentType);
        r.setByteSize(file.getSize());
        AppUserPrincipal p = principal();
        if (p != null) {
            r.setUploadedBy(p.userId());
            r.setUploadedByName(p.displayName());
        }
        apply(r, in, section);

        // Le fichier part AVANT le commit : si le stockage refuse, la transaction
        // n'a rien laissé derrière elle. L'inverse — une fiche sans fichier —
        // donnerait une ligne intéressante à l'écran et vide au téléchargement.
        try (InputStream data = file.getInputStream()) {
            storage.put(r.getObjectKey(), data, file.getSize(), contentType);
        } catch (IOException e) {
            throw ApiException.badRequest("Le fichier n'a pas pu être lu");
        }
        return view(repo.save(r), true);
    }

    /**
     * Modifie la fiche — titre, rubrique, destinataire, périmètre, publication.
     * Le fichier, lui, ne se remplace pas : on dépose une nouvelle ressource,
     * de sorte qu'un document déjà diffusé ne change jamais de contenu dans le
     * dos de ceux qui l'ont lu.
     */
    @Transactional
    public ResourceView update(UUID id, ResourceUpsert in) {
        SharedResource r = require(id);
        assertEditable(r);
        apply(r, in, resolveSection(in.section()));
        return view(repo.save(r), true);
    }

    @Transactional
    public void delete(UUID id) {
        SharedResource r = require(id);
        assertEditable(r);
        repo.delete(r);
        storage.delete(r.getObjectKey());
    }

    // ---- Règles --------------------------------------------------------------

    /** Recopie la fiche sur l'entité, en datant la première publication. */
    private void apply(SharedResource r, ResourceUpsert in, String section) {
        String audience = in.audience() == null ? "" : in.audience().toLowerCase(Locale.ROOT);
        if (!AUDIENCES.contains(audience)) {
            throw ApiException.badRequest("Destinataire inconnu : " + in.audience());
        }
        String category = in.category() == null ? "other" : in.category().toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) category = "other";

        r.setTitle(in.title().trim());
        r.setDescription(in.description() == null || in.description().isBlank() ? null : in.description().trim());
        r.setCategory(category);
        r.setAudience(audience);
        r.setSection(section);
        if (in.published() && !r.isPublished()) r.setPublishedAt(Instant.now());
        if (!in.published()) r.setPublishedAt(null);
        r.setPublished(in.published());
        r.setUpdatedAt(Instant.now());
    }

    /**
     * Le périmètre effectif du dépôt.
     *
     * <p>Un administrateur de cycle n'a pas le choix : quoi qu'annonce le client,
     * c'est son cycle. Seul l'admin principal peut viser toute l'école, ou
     * désigner un cycle qui n'est pas le sien.
     */
    private String resolveSection(String requested) {
        String locked = teacherScope.adminSection();
        if (locked != null) return locked;
        if (requested == null || requested.isBlank()) return null;   // toute l'école
        String section = requested.trim().toLowerCase(Locale.ROOT);
        if (!SectionRoles.SECTIONS.contains(section)) {
            throw ApiException.badRequest("Cycle inconnu : " + requested);
        }
        return section;
    }

    /** La ressource est-elle dans le périmètre de lecture d'un compte cloisonné ? */
    private static boolean inScope(SharedResource r, String viewerSection) {
        return viewerSection == null || r.getSection() == null || viewerSection.equals(r.getSection());
    }

    /** Modifiable : école entière pour qui n'est pas cloisonné, son cycle sinon. */
    private static boolean editable(SharedResource r, String viewerSection) {
        return viewerSection == null || viewerSection.equals(r.getSection());
    }

    private void assertEditable(SharedResource r) {
        if (!editable(r, teacherScope.section())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Cette ressource ne relève pas de votre section");
        }
    }

    private static boolean visibleToParent(SharedResource r, Set<String> childSections) {
        if (!r.isPublished()) return false;
        if (!"all".equals(r.getAudience()) && !"parents".equals(r.getAudience())) return false;
        return r.getSection() == null || childSections.contains(r.getSection());
    }

    /** Les cycles où ce parent a un enfant — le périmètre de ce qu'il peut lire. */
    private Set<String> childSections(UUID parentUserId) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT s.level FROM student s
                  JOIN parent_student ps ON ps.student_id = s.id
                 WHERE ps.parent_user_id = ? AND s.school_id = ? AND s.level IS NOT NULL
                """, String.class, parentUserId, TenantContext.get()));
    }

    // ---- Fichier -------------------------------------------------------------

    /** Type MIME de référence du fichier, après contrôle de l'extension et du poids. */
    private static String validate(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Aucun fichier reçu");
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Fichier trop lourd (max " + (MAX_BYTES / 1024 / 1024) + " Mo)");
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw ApiException.badRequest(
                    "Format non accepté. PDF, Word, Excel, PowerPoint, texte ou image.");
        }
        return contentType;
    }

    /**
     * Le nom d'affichage du fichier, débarrassé de tout chemin.
     *
     * <p>Il ne sert pas de clé de stockage (cf. {@link ObjectStorage#newKey}) mais
     * il revient dans l'en-tête {@code Content-Disposition} : un nom porteur de
     * séparateurs ou de retours à la ligne n'a rien à y faire.
     */
    private static String cleanFileName(String original) {
        if (original == null || original.isBlank()) throw ApiException.badRequest("Fichier sans nom");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw ApiException.badRequest("Nom de fichier invalide");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private Download open(SharedResource r) {
        InputStream stream = storage.get(r.getObjectKey());
        if (stream == null) throw ApiException.notFound("Le fichier");
        return new Download(stream, r.getFileName(), r.getContentType(), r.getByteSize());
    }

    // ---- Utilitaires ---------------------------------------------------------

    private SharedResource require(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("La ressource"));
    }

    private static ResourceView view(SharedResource r, boolean canEdit) {
        return new ResourceView(r.getId(), r.getTitle(), r.getDescription(), r.getCategory(),
                r.getAudience(), r.getSection(), r.getFileName(), r.getContentType(), r.getByteSize(),
                r.isPublished(), r.getPublishedAt(), r.getUploadedByName(), r.getCreatedAt(), canEdit);
    }

    private static AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object p = auth == null ? null : auth.getPrincipal();
        return p instanceof AppUserPrincipal aup ? aup : null;
    }

    private static ApiException denied() {
        return new ApiException(HttpStatus.FORBIDDEN, "Accès refusé");
    }
}
