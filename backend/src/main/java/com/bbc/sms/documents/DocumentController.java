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
    @PreAuthorize("@perm.can('documents','read')")
    public StudentDossier forStudent(@PathVariable UUID studentId) {
        return service.forStudent(studentId);
    }

    @PostMapping("/students/{studentId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('documents','write')")
    public DocumentView addDocument(@PathVariable UUID studentId, @Valid @RequestBody DocumentUpsert in) {
        return service.addDocument(studentId, in);
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('documents','write')")
    public void deleteDocument(@PathVariable UUID id) {
        service.deleteDocument(id);
    }

    @PostMapping("/students/{studentId}/orientations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('documents','write')")
    public OrientationView addOrientation(@PathVariable UUID studentId, @Valid @RequestBody OrientationUpsert in) {
        return service.addOrientation(studentId, in);
    }

    @DeleteMapping("/orientations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('documents','write')")
    public void deleteOrientation(@PathVariable UUID id) {
        service.deleteOrientation(id);
    }
}
