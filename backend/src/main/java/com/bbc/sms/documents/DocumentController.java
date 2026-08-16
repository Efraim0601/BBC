package com.bbc.sms.documents;

import com.bbc.sms.documents.dto.DocumentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping("/students/{studentId}")
    // Student-scoped V2 authorization is enforced by DocumentService after
    // the path student is known; the controller supplies only the staff envelope.
    @PreAuthorize("@perm.staffOnly()")
    public StudentDossier forStudent(@PathVariable UUID studentId) {
        return service.forStudent(studentId);
    }

    @PostMapping("/students/{studentId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public DocumentView addDocument(@PathVariable UUID studentId, @Valid @RequestBody DocumentUpsert in) {
        return service.addDocument(studentId, in);
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOnly()")
    public void deleteDocument(@PathVariable UUID id) {
        service.deleteDocument(id);
    }

    @PostMapping("/students/{studentId}/orientations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public OrientationView addOrientation(@PathVariable UUID studentId, @Valid @RequestBody OrientationUpsert in) {
        return service.addOrientation(studentId, in);
    }

    @DeleteMapping("/orientations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOnly()")
    public void deleteOrientation(@PathVariable UUID id) {
        service.deleteOrientation(id);
    }
}
