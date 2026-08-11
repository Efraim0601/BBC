package com.bbc.sms.media;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Photos de profil (élève / employé).
 *
 * <p>Le navigateur envoie une image déjà recadrée et compressée sous forme de
 * data URL ({@code data:image/jpeg;base64,…}) : le serveur ne fait pas de
 * traitement d'image, il valide le type et la taille puis stocke les octets.
 * La limite volontairement basse (512 Ko) tient compte de ce redimensionnement
 * côté client — au-delà, c'est que l'image n'est pas passée par la capture.
 */
@Service
public class PhotoService {

    public static final String STUDENT = "student";
    public static final String EMPLOYEE = "employee";

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_BYTES = 512 * 1024;

    private final ProfilePhotoRepository repo;
    private final JdbcTemplate jdbc;

    public PhotoService(ProfilePhotoRepository repo, JdbcTemplate jdbc) { this.repo = repo; this.jdbc = jdbc; }

    /** Enregistre (ou remplace) la photo à partir d'une data URL. */
    @Transactional
    public void save(String ownerType, UUID ownerId, String dataUrl) {
        UUID schoolId = TenantContext.get();
        Decoded img = decode(dataUrl);

        ProfilePhoto p = repo.findByOwnerTypeAndOwnerIdAndSchoolId(ownerType, ownerId, schoolId)
                .orElseGet(() -> {
                    ProfilePhoto fresh = new ProfilePhoto();
                    fresh.setOwnerType(ownerType);
                    fresh.setOwnerId(ownerId);
                    fresh.setSchoolId(schoolId);
                    return fresh;
                });
        p.setContentType(img.contentType());
        p.setBytes(img.bytes());
        p.setByteSize(img.bytes().length);
        p.setUpdatedAt(OffsetDateTime.now());
        repo.save(p);
        jdbc.update("""
                INSERT INTO profile_photo_version
                    (school_id,owner_type,owner_id,content_type,bytes,byte_size,sha256,captured_at)
                VALUES (?,?,?,?,?,?,encode(digest(?, 'sha256'),'hex'),now())
                ON CONFLICT (school_id,owner_type,owner_id,sha256) DO NOTHING
                """, schoolId, ownerType, ownerId, img.contentType(), img.bytes(), img.bytes().length,
                img.bytes());
    }

    /** La photo, ou null quand la personne n'en a pas. */
    @Transactional(readOnly = true)
    public ProfilePhoto find(String ownerType, UUID ownerId) {
        return repo.findByOwnerTypeAndOwnerIdAndSchoolId(ownerType, ownerId, TenantContext.get()).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean exists(String ownerType, UUID ownerId) {
        return repo.existsByOwnerTypeAndOwnerIdAndSchoolId(ownerType, ownerId, TenantContext.get());
    }

    @Transactional
    public void delete(String ownerType, UUID ownerId) {
        repo.deleteByOwnerTypeAndOwnerIdAndSchoolId(ownerType, ownerId, TenantContext.get());
    }

    private record Decoded(String contentType, byte[] bytes) {}

    /** Découpe et valide {@code data:<type>;base64,<données>}. */
    private static Decoded decode(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw ApiException.badRequest("Aucune image reçue");
        }
        if (!dataUrl.startsWith("data:")) {
            throw ApiException.badRequest("Format d'image invalide");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) throw ApiException.badRequest("Format d'image invalide");

        String header = dataUrl.substring(5, comma);          // image/jpeg;base64
        if (!header.endsWith(";base64")) throw ApiException.badRequest("Image non encodée en base64");
        String contentType = header.substring(0, header.length() - ";base64".length()).toLowerCase();
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("Format accepté : JPEG, PNG ou WebP");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Image illisible");
        }
        if (bytes.length == 0) throw ApiException.badRequest("Image vide");
        if (bytes.length > MAX_BYTES) {
            throw ApiException.badRequest("Image trop lourde (max " + (MAX_BYTES / 1024) + " Ko après compression)");
        }
        return new Decoded(contentType, bytes);
    }
}
