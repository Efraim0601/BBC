package com.bbc.sms.academic.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.bbc.sms.academic.security.AcademicAccessDtos.MyScopeView;

/** Teacher-facing, already-filtered academic scope read model. */
@RestController
@RequestMapping("/api/academic/me")
public class AcademicScopeController {
    private final AcademicAccessPolicyService policy;
    private final AcademicAccessDelegationService delegations;

    public AcademicScopeController(AcademicAccessPolicyService policy,
                                   AcademicAccessDelegationService delegations) {
        this.policy = policy;
        this.delegations = delegations;
    }

    @GetMapping("/scope")
    @PreAuthorize("@perm.canAction('ACADEMIC_ROSTER_VIEW') and @perm.staffOnly()")
    public MyScopeView scope(@RequestParam(required = false) UUID sessionId,
                             @RequestParam(required = false) UUID periodId) {
        UUID resolvedSession = sessionId == null ? policy.currentSessionId() : sessionId;
        return delegations.myScope(resolvedSession, periodId);
    }
}
