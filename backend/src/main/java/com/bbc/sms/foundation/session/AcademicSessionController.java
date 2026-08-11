package com.bbc.sms.foundation.session;

import jakarta.validation.Valid;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
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
    private final AcademicWindowOverrideService overrides;
    private final AcademicWindowPolicyService windows;
    private final AcademicWindowRuleService windowRules;
    private final AcademicConfigurationCopyService configurationCopy;
    private final IdempotencyService idempotency;
    public AcademicSessionController(AcademicSessionService service, AcademicWindowOverrideService overrides,
                                     AcademicWindowPolicyService windows, AcademicWindowRuleService windowRules,
                                     AcademicConfigurationCopyService configurationCopy, IdempotencyService idempotency) {
        this.service = service; this.overrides = overrides; this.windows = windows; this.windowRules = windowRules;
        this.configurationCopy = configurationCopy; this.idempotency = idempotency;
    }

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

    @GetMapping("/{sessionId}/readiness")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public SessionReadinessView readiness(@PathVariable UUID sessionId) {
        return service.readiness(sessionId);
    }

    @GetMapping("/{sessionId}/reporting-periods/dependencies")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public List<StructureDependencyView> dependencies(@PathVariable UUID sessionId) {
        return service.dependencies(sessionId);
    }

    @GetMapping("/{sessionId}/window-rules")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public List<WorkflowWindowRuleView> windowRules(@PathVariable UUID sessionId) {
        return windowRules.list(sessionId);
    }

    @PutMapping("/{sessionId}/window-rules")
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public WorkflowWindowRuleView saveWindowRule(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody WorkflowWindowRuleUpsert in) {
        return windowRules.upsert(sessionId, in);
    }

    @PostMapping("/{targetSessionId}/configuration-copy/preview")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public ConfigurationCopyPreview previewConfigurationCopy(@PathVariable UUID targetSessionId,
                                                              @Valid @RequestBody ConfigurationCopyPreviewRequest in) {
        return configurationCopy.preview(targetSessionId, in);
    }

    @PostMapping("/{targetSessionId}/configuration-copy/apply")
    @PreAuthorize("@perm.canAction('SESSION_MANAGE')")
    public ConfigurationCopyPreview applyConfigurationCopy(@PathVariable UUID targetSessionId,
                                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                            @Valid @RequestBody ConfigurationCopyApplyRequest in) {
        return idempotency.execute("academic-session-configuration-copy:" + targetSessionId,
                idempotencyKey, in, ConfigurationCopyPreview.class,
                () -> configurationCopy.apply(targetSessionId, in, idempotencyKey));
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
                                                         @RequestParam(required = false) String reason,
                                                         @RequestParam(required = false) String fingerprint,
                                                         @RequestBody(required = false) StandardStructureApplyRequest request) {
        String requestedReason = request != null && request.reason() != null ? request.reason() : reason;
        String requestedFingerprint = request != null && request.fingerprint() != null ? request.fingerprint() : fingerprint;
        return service.applyStandardStructure(sessionId, requestedReason, requestedFingerprint,
                request == null ? null : request.periods(), request == null ? null : request.dependencies());
    }

    @GetMapping("/{sessionId}/window-overrides")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public List<WindowOverrideView> windowOverrides(@PathVariable UUID sessionId,
                                                     @RequestParam(required = false) UUID reportingPeriodId) {
        return overrides.list(sessionId, reportingPeriodId);
    }

    @GetMapping("/{sessionId}/reporting-periods/{periodId}/effective-window")
    @PreAuthorize("@perm.canAction('SESSION_VIEW')")
    public AcademicWindowPolicyService.WindowView effectiveWindow(@PathVariable UUID sessionId,
                                                                    @PathVariable UUID periodId,
                                                                    @RequestParam String action) {
        AcademicWindowPolicyService.Action parsed;
        try {
            parsed = AcademicWindowPolicyService.Action.valueOf(action.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw com.bbc.sms.platform.common.ApiException.field(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "WINDOW_ACTION_INVALID",
                    "L'action de fenêtre est invalide.", "action", "Choose a valid workflow action.");
        }
        return windows.effective(sessionId, periodId, parsed);
    }

    @PostMapping("/{sessionId}/window-overrides")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('ACADEMIC_WINDOW_OVERRIDE')")
    public WindowOverrideView createWindowOverride(@PathVariable UUID sessionId,
                                                    @Valid @RequestBody WindowOverrideUpsert in) {
        return overrides.create(sessionId, in);
    }

    @PostMapping("/window-overrides/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('ACADEMIC_WINDOW_OVERRIDE')")
    public void revokeWindowOverride(@PathVariable UUID id, @RequestParam String reason) {
        overrides.revoke(id, reason);
    }
}
