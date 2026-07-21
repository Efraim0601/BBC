package com.bbc.sms.parentportal;

import com.bbc.sms.classkit.dto.ClassKitDtos.ClassResourceView;
import com.bbc.sms.parentportal.dto.ParentDtos.*;
import com.bbc.sms.platform.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parent")
public class ParentController {

    private final ParentService service;

    public ParentController(ParentService service) {
        this.service = service;
    }

    @GetMapping("/children")
    @PreAuthorize("@perm.isParent()")
    public List<ChildView> children(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.children(principal);
    }

    @GetMapping("/children/{studentId}/grades")
    @PreAuthorize("@perm.isParent()")
    public List<GradeView> grades(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID studentId) {
        return service.grades(principal, studentId);
    }

    @GetMapping("/children/{studentId}/resources/{kind}")
    @PreAuthorize("@perm.isParent()")
    public ClassResourceView resources(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID studentId, @PathVariable String kind) {
        return service.resources(principal, studentId, kind);
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.isParent()")
    public SuggestionView createSuggestion(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @Valid @RequestBody SuggestionRequest req) {
        return service.createSuggestion(principal, req);
    }

    @GetMapping("/suggestions")
    @PreAuthorize("@perm.isParent()")
    public List<SuggestionView> mySuggestions(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.mySuggestions(principal);
    }
}
