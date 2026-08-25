package com.bbc.sms.staff;

import com.bbc.sms.media.PhotoService;
import com.bbc.sms.media.PhotoUpload;
import com.bbc.sms.media.ProfilePhoto;
import com.bbc.sms.staff.dto.StaffDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService service;
    private final PhotoService photos;
    private final StaffDocumentService documents;

    public StaffController(StaffService service, PhotoService photos, StaffDocumentService documents) {
        this.service = service;
        this.photos = photos;
        this.documents = documents;
    }

    // ---- Classes d'un enseignant --------------------------------------------

    @GetMapping("/{id}/classes")
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public List<TeacherClassView> classes(@PathVariable UUID id) {
        return service.classesOf(id);
    }

    /** Remplace la totalité des classes de l'enseignant ; une liste vide le détache de toutes. */
    @PutMapping("/{id}/classes")
    // The endpoint is an HR edit operation; the service evaluates the
    // resource-scoped teaching action for every submitted class.
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public List<TeacherClassView> setClasses(@PathVariable UUID id, @RequestBody SetTeacherClasses in) {
        return service.setClasses(id, in.classIds());
    }

    // ---- Photo de profil ----------------------------------------------------

    @GetMapping("/{id}/photo")
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        service.get(id);   // vérifie l'appartenance à l'établissement
        ProfilePhoto p = photos.find(PhotoService.EMPLOYEE, id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.noCache())
                .eTag(String.valueOf(p.getUpdatedAt().toInstant().toEpochMilli()))
                .body(p.getBytes());
    }

    @PutMapping("/{id}/photo")
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public void savePhoto(@PathVariable UUID id, @RequestBody PhotoUpload in) {
        service.get(id);
        photos.save(PhotoService.EMPLOYEE, id, in.dataUrl());
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public void deletePhoto(@PathVariable UUID id) {
        service.get(id);
        photos.delete(PhotoService.EMPLOYEE, id);
    }

    // ---- Documents RH privés -----------------------------------------------

    @GetMapping("/{employeeId}/documents")
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public List<StaffDocumentView> documents(@PathVariable UUID employeeId) {
        return documents.list(employeeId);
    }

    @PostMapping(value = "/{employeeId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public StaffDocumentView uploadDocument(@PathVariable UUID employeeId,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(name = "documentType", defaultValue = "other") String documentType,
                                            @RequestParam(name = "label", required = false) String label) {
        return documents.upload(employeeId, file, documentType, label);
    }

    @GetMapping("/{employeeId}/documents/{documentId}")
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable UUID employeeId,
                                                                 @PathVariable UUID documentId) {
        StaffDocumentService.Download download = documents.download(employeeId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.byteSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.stream()));
    }

    @DeleteMapping("/{employeeId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public void deleteDocument(@PathVariable UUID employeeId, @PathVariable UUID documentId) {
        documents.delete(employeeId, documentId);
    }

    @GetMapping
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public List<EmployeeView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@policy.canAction('HR_VIEW')")
    public EmployeeView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public EmployeeView create(@Valid @RequestBody EmployeeUpsert in) {
        return service.create(in);
    }

    @PostMapping("/import")
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public StaffImportResult importStaff(@Valid @RequestBody StaffImportRequest in) {
        return service.importStaff(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public EmployeeView update(@PathVariable UUID id, @Valid @RequestBody EmployeeUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Suppression groupée depuis l'annuaire ; rend le détail des fiches refusées. */
    @PostMapping("/bulk-delete")
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public BulkDeleteResult bulkDelete(@Valid @RequestBody BulkDeleteRequest in) {
        return service.deleteAll(in.ids());
    }

    /**
     * Create the employee's login account if missing, otherwise regenerate its
     * password; the new credentials are e-mailed to the employee. Doubles as the
     * admin "reset credentials" action.
     */
    @PostMapping("/{id}/reset-credentials")
    @PreAuthorize("@policy.canAction('HR_MANAGE')")
    public AccountResult resetCredentials(@PathVariable UUID id) {
        return service.resetCredentials(id);
    }
}
