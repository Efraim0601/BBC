package com.bbc.sms.academic.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.academic.security.AcademicAccessDtos.*;

/** Administrative readiness, delegation, and access-impact APIs. */
@RestController
@RequestMapping("/api/academic-access")
public class AcademicAccessController {
    // These actions are CLASS_SUBJECT scoped. The delegation service must
    // resolve the request resource before applying the V2 policy; a
    // context-free controller action check rejects valid management requests.
    private static final String ADMIN_READ = "@perm.staffOnly()";
    private static final String ADMIN_WRITE = "@perm.staffOnly()";

    private final AcademicAccessDelegationService delegations;

    public AcademicAccessController(AcademicAccessDelegationService delegations) {
        this.delegations = delegations;
    }

    @GetMapping("/readiness")
    @PreAuthorize(ADMIN_READ)
    public ReadinessView readiness(@RequestParam(required = false) UUID sessionId) {
        return delegations.readiness(sessionId);
    }

    @GetMapping("/delegations")
    @PreAuthorize(ADMIN_READ)
    public List<DelegationView> list(@RequestParam(required = false) UUID sessionId,
                                    @RequestParam(required = false) UUID classId,
                                    @RequestParam(required = false) UUID employeeId,
                                    @RequestParam(required = false) String status) {
        return delegations.list(sessionId, classId, employeeId, status);
    }

    @PostMapping("/delegations/preview")
    @PreAuthorize(ADMIN_READ)
    public DelegationPreview preview(@Valid @RequestBody DelegationRequest request) {
        return delegations.previewRequest(request);
    }

    @PostMapping("/delegations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN_WRITE)
    public DelegationView create(@Valid @RequestBody DelegationRequest request) {
        return delegations.create(request);
    }

    @PostMapping("/delegations/{id}/revoke")
    @PreAuthorize(ADMIN_WRITE)
    public DelegationView revoke(@PathVariable UUID id,
                                 @Valid @RequestBody DelegationRevokeRequest request) {
        return delegations.revoke(id, request);
    }

    @GetMapping("/teachers/{employeeId}/preview")
    @PreAuthorize(ADMIN_READ)
    public List<ScopeSubject> teacherPreview(@PathVariable UUID employeeId,
                                             @RequestParam UUID sessionId,
                                             @RequestParam(required = false) LocalDate date) {
        return delegations.teacherPreview(employeeId, sessionId, date);
    }
}
