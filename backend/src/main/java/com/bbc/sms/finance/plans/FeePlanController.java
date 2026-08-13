package com.bbc.sms.finance.plans;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.plans.FeePlanDtos.*;

@RestController
@RequestMapping("/api/finance/v2/plans")
public class FeePlanController {
    private final FeePlanService service;

    public FeePlanController(FeePlanService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<PlanView> list(@RequestParam(required = false) UUID sessionId,
                               @RequestParam(required = false) String lifecycle) {
        return service.list(sessionId, lifecycle);
    }

    @GetMapping("/context")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public PlanContext context(@RequestParam(required = false) UUID sessionId) {
        return service.context(sessionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public PlanView detail(@PathVariable UUID id) { return service.detail(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView create(@Valid @RequestBody PlanCreateRequest request) { return service.createDraft(request); }

    @PutMapping("/{id}/draft")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView update(@PathVariable UUID id, @Valid @RequestBody PlanUpdateRequest request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView addLine(@PathVariable UUID id, @Valid @RequestBody PlanLineRequest request) {
        return service.addLine(id, request);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView updateLine(@PathVariable UUID id, @PathVariable UUID lineId,
                               @Valid @RequestBody PlanLineRequest request) {
        return service.updateLine(id, lineId, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView removeLine(@PathVariable UUID id, @PathVariable UUID lineId,
                               @Valid @RequestBody PlanActionRequest request) {
        return service.removeLine(id, lineId, request);
    }

    @GetMapping("/templates")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<TemplateView> templates() { return service.listTemplates(); }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public TemplateView createTemplate(@Valid @RequestBody TemplateRequest request) {
        return service.createTemplate(request);
    }

    @GetMapping("/templates/{templateId}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public TemplateView template(@PathVariable UUID templateId) { return service.template(templateId); }

    @PutMapping("/templates/{templateId}")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public TemplateView updateTemplate(@PathVariable UUID templateId, @Valid @RequestBody TemplateRequest request) {
        return service.updateTemplate(templateId, request);
    }

    @DeleteMapping("/templates/{templateId}")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable UUID templateId, @Valid @RequestBody PlanActionRequest request) {
        service.deleteTemplate(templateId, request);
    }

    @GetMapping("/{id}/installments-preview")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public InstallmentPreview installmentsPreview(@PathVariable UUID id, @RequestParam UUID lineId) {
        return service.installmentPreview(id, lineId);
    }

    @PostMapping("/{id}/activation-preview")
    @PreAuthorize("@perm.canAction('FEE_PLAN_ACTIVATE')")
    public ActivationPreview activationPreview(@PathVariable UUID id) { return service.activationPreview(id); }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@perm.canAction('FEE_PLAN_ACTIVATE')")
    public PlanView activate(@PathVariable UUID id, @Valid @RequestBody PlanActionRequest request) {
        return service.activate(id, request);
    }

    @PostMapping("/copy/preview")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public CopyPreview copyPreview(@Valid @RequestBody CopyPreviewRequest request) {
        return service.copyPreview(request);
    }

    @PostMapping("/copy")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public PlanView copy(@Valid @RequestBody CopyApplyRequest request) { return service.copy(request); }

    @GetMapping("/resolve")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public ResolutionView resolve(@RequestParam UUID enrollmentId) { return service.resolve(enrollmentId); }

    @GetMapping("/student-context")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<StudentContextView> studentContext(@RequestParam(required = false) String query,
                                                   @RequestParam(required = false) UUID sessionId) {
        return service.studentContext(query, sessionId);
    }

    @PostMapping("/{id}/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public OverrideView requestOverride(@PathVariable UUID id, @Valid @RequestBody OverrideRequest request) {
        return service.requestOverride(id, request);
    }

    @GetMapping("/{id}/overrides")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<OverrideView> overrides(@PathVariable UUID id, @RequestParam UUID enrollmentId) {
        return service.overrides(id, enrollmentId);
    }

    @PostMapping("/overrides/{overrideId}/decision")
    @PreAuthorize("@perm.canAction('FEE_PLAN_ACTIVATE')")
    public OverrideView decideOverride(@PathVariable UUID overrideId,
                                       @Valid @RequestBody OverrideDecisionRequest request) {
        return service.decideOverride(overrideId, request);
    }

    @GetMapping("/{id}/overrides/impact-preview")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public ImpactPreview impact(@PathVariable UUID id, @RequestParam UUID enrollmentId,
                                @RequestParam UUID lineId) {
        return service.impact(id, enrollmentId, lineId);
    }

    @GetMapping("/{id}/elections")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<ElectionView> elections(@PathVariable UUID id, @RequestParam UUID enrollmentId) {
        return service.elections(id, enrollmentId);
    }

    @PostMapping("/{id}/elections/{lineId}")
    @PreAuthorize("@perm.canAction('FEE_PLAN_DRAFT')")
    public ElectionView election(@PathVariable UUID id, @PathVariable UUID lineId,
                                 @RequestParam UUID enrollmentId,
                                 @Valid @RequestBody ElectionRequest request) {
        return service.saveElection(lineId, enrollmentId, request);
    }
}
