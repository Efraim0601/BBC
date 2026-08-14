package com.bbc.sms.documents;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.bbc.sms.documents.DocumentDesignDtos.*;

@RestController
@RequestMapping("/api/settings/document-design")
public class DocumentDesignController {
    private final DocumentDesignService service;

    public DocumentDesignController(DocumentDesignService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('DOCUMENT_DESIGN_PUBLISH') and @perm.staffOnly()")
    public DocumentDesignView current() { return service.current(); }

    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("@perm.canAction('DOCUMENT_DESIGN_PUBLISH') and @perm.staffOnly()")
    public TemplateVersionView publishTemplate(@PathVariable UUID id, @Valid @RequestBody PublishRequest in) {
        return service.publishTemplate(id, in.reason());
    }

    @PostMapping("/branding/publish")
    @PreAuthorize("@perm.canAction('DOCUMENT_DESIGN_PUBLISH') and @perm.staffOnly()")
    public BrandingVersionView publishBranding(@Valid @RequestBody PublishRequest in) {
        return service.publishBranding(in);
    }
}
