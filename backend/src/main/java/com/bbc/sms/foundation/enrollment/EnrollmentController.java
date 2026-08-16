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

    // EnrollmentService resolves the student/class resource and performs the
    // V2 action check; this controller must not use a context-free gate for a
    // STUDENT-scoped action.
    @GetMapping("/students/{studentId}") @PreAuthorize("@perm.staffOnly()")
    public List<EnrollmentView> history(@PathVariable UUID studentId) { return service.history(studentId); }

    @GetMapping("/roster") @PreAuthorize("@perm.staffOnly()")
    public List<EnrollmentView> roster(@RequestParam UUID sessionId, @RequestParam UUID classId) { return service.roster(sessionId, classId); }

    @PostMapping("/students/{studentId}") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public EnrollmentView enroll(@PathVariable UUID studentId, @Valid @RequestBody EnrollmentRequest in) { return service.enroll(studentId, in); }

    @PostMapping("/students/{studentId}/transfer")
    @PreAuthorize("@perm.staffOnly()")
    public EnrollmentView transfer(@PathVariable UUID studentId, @Valid @RequestBody TransferRequest in) { return service.transfer(studentId, in); }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@perm.staffOnly()")
    public EnrollmentView withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest in) { return service.withdraw(id, in); }
}
