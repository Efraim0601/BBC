package com.bbc.sms.finance.charges;

import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.security.PermissionActions;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClassRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.charges.ChargeDtos.*;

/** Minimal, explicit BAY-46 HTTP boundary. Every read and write is tenant scoped. */
@RestController
@RequestMapping("/api/finance/v2/charges")
public class ChargeController {
    private final ChargeGenerationPreviewService preview;
    private final ChargeGenerationService generation;
    private final ChargeQueryService queries;
    private final ChargeAdjustmentService adjustments;
    private final AcademicSessionRepository sessions;
    private final SchoolClassRepository classes;

    public ChargeController(ChargeGenerationPreviewService preview,
                            ChargeGenerationService generation,
                            ChargeQueryService queries,
                            ChargeAdjustmentService adjustments,
                            AcademicSessionRepository sessions,
                            SchoolClassRepository classes) {
        this.preview = preview;
        this.generation = generation;
        this.queries = queries;
        this.adjustments = adjustments;
        this.sessions = sessions;
        this.classes = classes;
    }

    @GetMapping("/context")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public ContextView context() {
        UUID schoolId = TenantContext.get();
        return new ContextView(
                sessions.findBySchoolIdOrderByStartDateDesc(schoolId).stream()
                        .map(s -> new SessionOption(s.getId(), s.getCode(), s.getLabel(), s.getStartDate(), s.getEndDate(), s.getStatus())).toList(),
                classes.findBySchoolIdOrderByName(schoolId).stream()
                        .map(c -> new ClassOption(c.getId(), c.getSectionId(), c.getName(), c.getLevel(), c.getSubsystem())).toList());
    }

    @GetMapping("/student-options")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<ChargeQueryService.StudentContextOption> studentOptions(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID sessionId) {
        return queries.studentOptions(query, sessionId);
    }

    @PostMapping("/generation-preview")
    @PreAuthorize("@perm.canAction('CHARGE_PREVIEW')")
    public GenerationPreview generationPreview(@Valid @RequestBody GenerationRequest request) {
        return preview.preview(request);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@perm.canAction('CHARGE_GENERATE')")
    public GenerationJobView generate(@Valid @RequestBody GenerationRequest request,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return generation.generate(request, idempotencyKey);
    }

    @GetMapping("/jobs")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<GenerationJobView> jobs() { return generation.jobs(); }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public GenerationJobView job(@PathVariable UUID id) { return generation.job(id); }

    @GetMapping("/jobs/{id}/results")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<GenerationResultView> results(@PathVariable UUID id) { return generation.results(id); }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("@perm.canAction('CHARGE_GENERATE')")
    public GenerationJobView retry(@PathVariable UUID id,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return generation.retry(id, idempotencyKey);
    }

    @GetMapping
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<ChargeView> list(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) UUID academicSessionId,
                                 @RequestParam(required = false) UUID schoolClassId,
                                 @RequestParam(required = false) UUID studentId,
                                 @RequestParam(required = false) String feeTypeCode,
                                 @RequestParam(required = false) LocalDate dueFrom,
                                 @RequestParam(required = false) LocalDate dueTo,
                                 @RequestParam(required = false) Long minAmountMinor,
                                 @RequestParam(required = false) Long maxAmountMinor,
                                 @RequestParam(required = false) String query) {
        return queries.list(new ChargeListFilters(status, academicSessionId, schoolClassId, studentId,
                feeTypeCode, dueFrom, dueTo, minAmountMinor, maxAmountMinor, query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public ChargeView detail(@PathVariable UUID id) { return queries.detail(id); }

    @GetMapping("/accounts/{enrollmentId}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public StudentAccountView account(@PathVariable UUID enrollmentId) { return queries.account(enrollmentId); }

    @GetMapping("/ageing")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public AgeingView ageing(@RequestParam(required = false) LocalDate asOfDate,
                             @RequestParam(required = false) UUID academicSessionId,
                             @RequestParam(required = false) UUID schoolClassId) {
        return queries.ageing(asOfDate, academicSessionId, schoolClassId);
    }

    @PostMapping("/{id}/adjustment-impact-preview")
    @PreAuthorize("@perm.canAction('CHARGE_ADJUST')")
    public AdjustmentImpact adjustmentImpact(@PathVariable UUID id,
                                             @Valid @RequestBody AdjustmentRequest request) {
        return adjustments.impact(id, request);
    }

    @PostMapping("/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_WAIVE_REQUEST')")
    public AdjustmentView requestAdjustment(@PathVariable UUID id,
                                            @Valid @RequestBody AdjustmentRequest request) {
        return adjustments.request(id, request);
    }

    @GetMapping("/{id}/adjustments")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<AdjustmentView> adjustments(@PathVariable UUID id) { return adjustments.list(id); }

    @PostMapping("/adjustments/{adjustmentId}/decision")
    @PreAuthorize("@perm.canAction('FEE_WAIVE_APPROVE')")
    public AdjustmentView decision(@PathVariable UUID adjustmentId,
                                   @Valid @RequestBody AdjustmentDecisionRequest request) {
        return adjustments.decide(adjustmentId, request);
    }
}
