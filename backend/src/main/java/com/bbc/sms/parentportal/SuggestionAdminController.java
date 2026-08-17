package com.bbc.sms.parentportal;

import com.bbc.sms.parentportal.dto.ParentDtos.SuggestionView;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionAdminController {

    private final ParentService service;

    public SuggestionAdminController(ParentService service) {
        this.service = service;
    }

    @GetMapping("")
    @PreAuthorize("@perm.canAction('DASHBOARD_VIEW') and @perm.staffOnly()")
    public List<SuggestionView> allSuggestions() {
        return service.allSuggestions(TenantContext.get());
    }
}
