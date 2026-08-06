package com.bbc.sms.foundation.enrollment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.enrollment.EnrollmentDtos.*;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    private final EnrollmentService service;
    public EnrollmentController(EnrollmentService service) { this.service = service; }

    @GetMapping("/students/{studentId}") @PreAuthorize("@perm.canAction('ENROLLMENT_VIEW') and @perm.staffOnly()")
    public List<EnrollmentView> history(@PathVariable UUID studentId) { return service.history(studentId); }

    @GetMapping("/roster") @PreAuthorize("@perm.canAction('ENROLLMENT_VIEW') and @perm.staffOnly()")
    public List<EnrollmentView> roster(@RequestParam UUID sessionId, @RequestParam UUID classId) { return service.roster(sessionId, classId); }

    @PostMapping("/students/{studentId}") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('ENROLLMENT_MANAGE') and @perm.staffOnly()")
    public EnrollmentView enroll(@PathVariable UUID studentId, @Valid @RequestBody EnrollmentRequest in) { return service.enroll(studentId, in); }

    @PostMapping("/students/{studentId}/transfer")
    @PreAuthorize("@perm.canAction('ENROLLMENT_MANAGE') and @perm.staffOnly()")
    public EnrollmentView transfer(@PathVariable UUID studentId, @Valid @RequestBody TransferRequest in) { return service.transfer(studentId, in); }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@perm.canAction('ENROLLMENT_MANAGE') and @perm.staffOnly()")
    public EnrollmentView withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest in) { return service.withdraw(id, in); }
}
