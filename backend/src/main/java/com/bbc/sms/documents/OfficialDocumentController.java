package com.bbc.sms.documents;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.documents.OfficialDocumentDtos.*;

@RestController
@RequestMapping("/api/official-documents")
public class OfficialDocumentController {
    private final OfficialDocumentService service;
    public OfficialDocumentController(OfficialDocumentService service) { this.service = service; }

    @GetMapping("/templates") @PreAuthorize("@perm.canAction('DOCUMENT_VIEW')")
    public List<TemplateView> templates() { return service.templates(); }

    @GetMapping @PreAuthorize("@perm.canAction('DOCUMENT_VIEW')")
    public List<GeneratedDocumentView> list(@RequestParam String aggregateType, @RequestParam String aggregateId) {
        return service.list(aggregateType, aggregateId);
    }

    @PostMapping("/generate") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('DOCUMENT_GENERATE')")
    public GeneratedDocumentView generate(@Valid @RequestBody GenerateRequest in,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.generate(in, key);
    }

    @GetMapping("/{id}/content") @PreAuthorize("@perm.canAction('DOCUMENT_VIEW')")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=official-document.pdf")
                .cacheControl(CacheControl.noStore()).body(service.content(id));
    }

    @PostMapping("/{id}/revoke") @PreAuthorize("@perm.canAction('DOCUMENT_REVOKE')")
    public GeneratedDocumentView revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest in) { return service.revoke(id, in); }

    @GetMapping("/verify/{number}")
    public VerificationView verify(@PathVariable String number) { return service.verify(number); }
}
