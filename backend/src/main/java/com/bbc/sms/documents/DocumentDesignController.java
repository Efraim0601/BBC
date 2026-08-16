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
    // DocumentDesignService performs the resource-aware V2 action check after
    // resolving the tenant. Keeping only the staff envelope here allows the
    // narrow bootstrap-user exception to reach that check without widening
    // the ordinary role matrix.
    @PreAuthorize("@perm.staffOnly()")
    public DocumentDesignView current() { return service.current(); }

    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("@perm.staffOnly()")
    public TemplateVersionView publishTemplate(@PathVariable UUID id, @Valid @RequestBody PublishRequest in) {
        return service.publishTemplate(id, in.reason());
    }

    @PostMapping("/branding/publish")
    @PreAuthorize("@perm.staffOnly()")
    public BrandingVersionView publishBranding(@Valid @RequestBody PublishRequest in) {
        return service.publishBranding(in);
    }
}
