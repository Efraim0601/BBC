package com.bbc.sms.foundation.session;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.*;

@RestController
@RequestMapping("/api/settings/academic-sessions")
public class AcademicSessionController {
    private final AcademicSessionService service;
    public AcademicSessionController(AcademicSessionService service) { this.service = service; }

    @GetMapping @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public List<SessionView> list() { return service.list(); }

    @GetMapping("/current") @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public SessionView current() { return service.current(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public SessionView create(@Valid @RequestBody SessionUpsert in) { return service.create(in); }

    @PutMapping("/{id}") @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public SessionView update(@PathVariable UUID id, @Valid @RequestBody SessionUpsert in) { return service.update(id, in); }

    @PostMapping("/{id}/state") @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public SessionView state(@PathVariable UUID id, @Valid @RequestBody SessionStateRequest in) { return service.changeState(id, in); }

    @PostMapping("/{sessionId}/terms") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public TermView addTerm(@PathVariable UUID sessionId, @Valid @RequestBody TermUpsert in) { return service.addTerm(sessionId, in); }

    @PutMapping("/terms/{id}") @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public TermView updateTerm(@PathVariable UUID id, @Valid @RequestBody TermUpsert in) { return service.updateTerm(id, in); }

    @DeleteMapping("/terms/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public void deleteTerm(@PathVariable UUID id, @RequestParam(required = false) String reason) { service.deleteTerm(id, reason); }

    @GetMapping("/{sessionId}/reporting-periods")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public List<ReportingPeriodView> reportingPeriods(@PathVariable UUID sessionId) {
        return service.reportingPeriods(sessionId);
    }

    @PostMapping("/{sessionId}/reporting-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public ReportingPeriodView createReportingPeriod(@PathVariable UUID sessionId,
                                                       @Valid @RequestBody ReportingPeriodUpsert in) {
        return service.upsertReportingPeriod(sessionId, null, in);
    }

    @PutMapping("/{sessionId}/reporting-periods/{id}")
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public ReportingPeriodView updateReportingPeriod(@PathVariable UUID sessionId, @PathVariable UUID id,
                                                      @Valid @RequestBody ReportingPeriodUpsert in) {
        return service.upsertReportingPeriod(sessionId, id, in);
    }

    @DeleteMapping("/reporting-periods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public void deleteReportingPeriod(@PathVariable UUID id, @RequestParam(required = false) String reason) {
        service.deleteReportingPeriod(id, reason);
    }

    @PostMapping("/{sessionId}/reporting-periods/standard/preview")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public StandardStructureView previewStandardStructure(@PathVariable UUID sessionId) {
        return service.previewStandardStructure(sessionId);
    }

    @PostMapping("/{sessionId}/reporting-periods/standard/apply")
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public StandardStructureView applyStandardStructure(@PathVariable UUID sessionId,
                                                         @RequestParam(required = false) String reason) {
        return service.applyStandardStructure(sessionId, reason);
    }
}
