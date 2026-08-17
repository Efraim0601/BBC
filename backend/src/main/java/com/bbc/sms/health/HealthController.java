package com.bbc.sms.health;

import com.bbc.sms.health.dto.HealthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService service;

    public HealthController(HealthService service) { this.service = service; }

    @GetMapping("/students/{studentId}")
    // The student-scoped V2 action is evaluated by HealthService after the
    // path student has been resolved. A context-free legacy action check here
    // rejects valid contextual grants before the service can evaluate them.
    @PreAuthorize("@perm.staffOnly()")
    public StudentHealth forStudent(@PathVariable UUID studentId) {
        return service.forStudent(studentId);
    }

    @PutMapping("/students/{studentId}/record")
    @PreAuthorize("@perm.staffOnly()")
    public HealthRecordView upsertRecord(@PathVariable UUID studentId,
                                         @Valid @RequestBody HealthRecordUpsert in) {
        return service.upsertRecord(studentId, in);
    }

    @PostMapping("/students/{studentId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public VisitView addVisit(@PathVariable UUID studentId, @Valid @RequestBody VisitUpsert in) {
        return service.addVisit(studentId, in);
    }

    @DeleteMapping("/visits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOnly()")
    public void deleteVisit(@PathVariable UUID id) {
        service.deleteVisit(id);
    }

    @PostMapping("/students/{studentId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public ActivityView addActivity(@PathVariable UUID studentId, @Valid @RequestBody ActivityUpsert in) {
        return service.addActivity(studentId, in);
    }

    @DeleteMapping("/activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOnly()")
    public void deleteActivity(@PathVariable UUID id) {
        service.deleteActivity(id);
    }
}
