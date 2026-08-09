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
    @PreAuthorize("@perm.can('settings','read') or @perm.can('documents','read')")
    public DocumentDesignView current() { return service.current(); }

    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("@perm.can('settings','write') or @perm.canAction('DOCUMENT_DESIGN_PUBLISH')")
    public TemplateVersionView publishTemplate(@PathVariable UUID id, @Valid @RequestBody PublishRequest in) {
        return service.publishTemplate(id, in.reason());
    }

    @PostMapping("/branding/publish")
    @PreAuthorize("@perm.can('settings','write') or @perm.canAction('DOCUMENT_DESIGN_PUBLISH')")
    public BrandingVersionView publishBranding(@Valid @RequestBody PublishRequest in) {
        return service.publishBranding(in);
    }
}
