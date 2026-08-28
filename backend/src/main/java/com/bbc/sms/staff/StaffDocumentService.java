package com.bbc.sms.staff;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.storage.ObjectStorage;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.dto.StaffDtos.StaffDocumentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** HR-only document workflow for employee records. */
@Service
public class StaffDocumentService {

    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("cv", "diploma", "certificate", "identity", "contract", "other");
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("txt", "text/plain"));

    private final StaffDocumentRepository repo;
    private final EmployeeRepository employees;
    private final ObjectStorage storage;
    private final TeacherScopeService teacherScope;

    public StaffDocumentService(StaffDocumentRepository repo,
                                 EmployeeRepository employees,
                                 ObjectStorage storage,
                                 TeacherScopeService teacherScope) {
        this.repo = repo;
        this.employees = employees;
        this.storage = storage;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public List<StaffDocumentView> list(UUID employeeId) {
        requireEmployee(employeeId);
        UUID schoolId = TenantContext.get();
        return repo.findBySchoolIdAndEmployeeIdOrderByCreatedAtDesc(schoolId, employeeId)
                .stream().map(StaffDocumentService::view).toList();
    }

    @Transactional
    public StaffDocumentView upload(UUID employeeId, MultipartFile file, String documentType, String label) {
        requireEmployee(employeeId);
        UUID schoolId = TenantContext.get();
        String type = normalizeType(documentType);
        String fileName = cleanFileName(file == null ? null : file.getOriginalFilename());
        String contentType = validate(file, fileName);
        String displayLabel = label == null || label.isBlank() ? fileName : label.trim();
        if (displayLabel.length() > 200) {
            displayLabel = displayLabel.substring(0, 200);
        }

        StaffDocument document = new StaffDocument();
        document.setSchoolId(schoolId);
        document.setEmployeeId(employeeId);
        document.setDocumentType(type);
        document.setLabel(displayLabel);
        document.setObjectKey(storage.newKey(schoolId, "staff/" + employeeId, fileName));
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setByteSize(file.getSize());
        AppUserPrincipal principal = principal();
        if (principal != null) {
            document.setUploadedBy(principal.userId());
            document.setUploadedByName(principal.displayName());
        }
        try (InputStream data = file.getInputStream()) {
            storage.put(document.getObjectKey(), data, file.getSize(), contentType);
        } catch (IOException e) {
            throw ApiException.badRequest("Le fichier n'a pas pu être lu");
        }
        try {
            return view(repo.save(document));
        } catch (RuntimeException e) {
            storage.delete(document.getObjectKey());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Download download(UUID employeeId, UUID documentId) {
        requireEmployee(employeeId);
        StaffDocument document = requireDocument(employeeId, documentId);
        InputStream stream = storage.get(document.getObjectKey());
        if (stream == null) throw ApiException.notFound("Le fichier");
        return new Download(stream, document.getFileName(), document.getContentType(), document.getByteSize());
    }

    @Transactional
    public void delete(UUID employeeId, UUID documentId) {
        requireEmployee(employeeId);
        StaffDocument document = requireDocument(employeeId, documentId);
        repo.delete(document);
        storage.delete(document.getObjectKey());
    }

    public record Download(InputStream stream, String fileName, String contentType, long byteSize) {}

    private Employee requireEmployee(UUID id) {
        Employee employee = employees.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("L'employé"));
        teacherScope.assertEmployee(employee.getId());
        return employee;
    }

    private StaffDocument requireDocument(UUID employeeId, UUID documentId) {
        return repo.findByIdAndSchoolIdAndEmployeeId(documentId, TenantContext.get(), employeeId)
                .orElseThrow(() -> ApiException.notFound("Le document"));
    }

    private static StaffDocumentView view(StaffDocument d) {
        return new StaffDocumentView(d.getId(), d.getEmployeeId(), d.getDocumentType(), d.getLabel(),
                d.getFileName(), d.getContentType(), d.getByteSize(), d.getUploadedByName(), d.getCreatedAt());
    }

    private static String normalizeType(String input) {
        String value = input == null ? "other" : input.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(value)) throw ApiException.badRequest("Type de document inconnu");
        return value;
    }

    private static String validate(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Aucun fichier reçu");
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Fichier trop lourd (max " + (MAX_BYTES / 1024 / 1024) + " Mo)");
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw ApiException.badRequest("Format non accepté. PDF, Word, image ou texte.");
        }
        return contentType;
    }

    private static String cleanFileName(String original) {
        if (original == null || original.isBlank()) throw ApiException.badRequest("Fichier sans nom");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw ApiException.badRequest("Nom de fichier invalide");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object p = auth == null ? null : auth.getPrincipal();
        return p instanceof AppUserPrincipal aup ? aup : null;
    }
}
