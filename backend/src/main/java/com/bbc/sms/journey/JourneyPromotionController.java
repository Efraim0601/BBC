package com.bbc.sms.journey;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.journey.dto.JourneyPromotionDtos.*;

@RestController
@RequestMapping("/api/journey/progression")
public class JourneyPromotionController {
    private final JourneyPromotionService service;
    public JourneyPromotionController(JourneyPromotionService service) { this.service = service; }

    @GetMapping("/paths")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<ProgressionPathView> paths(@RequestParam UUID sourceSessionId,
                                           @RequestParam UUID targetSessionId) {
        return service.paths(sourceSessionId, targetSessionId);
    }

    @PostMapping("/paths")
    @PreAuthorize("@perm.canAction('PROGRESSION_CONFIGURE')")
    public ProgressionPathView savePath(@Valid @RequestBody ProgressionPathUpsert input) {
        return service.savePath(input);
    }

    @DeleteMapping("/paths/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('PROGRESSION_CONFIGURE')")
    public void deletePath(@PathVariable UUID id) { service.deletePath(id); }

    @GetMapping("/graphs")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<ProgressionGraphView> graphVersions(@RequestParam UUID sourceSessionId,
                                                    @RequestParam UUID targetSessionId) {
        return service.graphVersions(sourceSessionId, targetSessionId);
    }

    @PostMapping("/graphs/copy")
    @PreAuthorize("@perm.canAction('PROGRESSION_CONFIGURE')")
    public ProgressionGraphView copyGraph(@Valid @RequestBody ProgressionGraphCopyRequest input) {
        return service.copyGraph(input);
    }

    @PostMapping("/graphs/copy/preview")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public ProgressionGraphPreviewView previewGraphCopy(@Valid @RequestBody ProgressionGraphCopyRequest input) {
        return service.previewGraphCopy(input);
    }

    @PostMapping("/graphs/{id}/publish")
    @PreAuthorize("@perm.canAction('PROMOTION_CONFIGURE')")
    public ProgressionGraphView publishGraph(@PathVariable UUID id,
                                             @RequestParam(required = false) Long expectedVersion) {
        return service.publishGraph(id, expectedVersion);
    }

    @PostMapping("/graphs/{id}/archive")
    @PreAuthorize("@perm.canAction('PROMOTION_CONFIGURE')")
    public ProgressionGraphView archiveGraph(@PathVariable UUID id,
                                             @RequestParam(required = false) Long expectedVersion) {
        return service.archiveGraph(id, expectedVersion);
    }

    @GetMapping("/rules")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<PromotionRuleView> rules(@RequestParam UUID sessionId) { return service.rules(sessionId); }

    @PostMapping("/rules")
    @PreAuthorize("@perm.canAction('PROGRESSION_CONFIGURE')")
    public PromotionRuleView saveRule(@Valid @RequestBody PromotionRuleUpsert input) {
        return service.saveRule(input);
    }

    @GetMapping("/rule-sets")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<PromotionRuleSetView> ruleSets(@RequestParam UUID sessionId) {
        return service.ruleSets(sessionId);
    }

    @PostMapping("/rule-sets")
    @PreAuthorize("@perm.canAction('PROMOTION_CONFIGURE')")
    public PromotionRuleSetView saveRuleSet(@Valid @RequestBody PromotionRuleSetUpsert input) {
        return service.saveRuleSet(input);
    }

    @PostMapping("/rule-sets/{id}/publish")
    @PreAuthorize("@perm.canAction('PROMOTION_CONFIGURE')")
    public PromotionRuleSetView publishRuleSet(@PathVariable UUID id,
                                               @RequestParam(required = false) Long expectedVersion) {
        return service.publishRuleSet(id, expectedVersion);
    }

    @PostMapping("/promotion-previews")
    @PreAuthorize("@perm.canAction('PROMOTION_RECOMMEND')")
    public PromotionPreviewView readOnlyPreview(@Valid @RequestBody PromotionPreviewRequest input) {
        return service.previewReadOnly(input);
    }

    /** Compatibility mutation endpoint; the user-facing preview route above is read-only. */
    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('PROMOTION_REVIEW')")
    public PromotionBatchView createBatch(@Valid @RequestBody PromotionPreviewRequest input) {
        return service.createReviewedBatch(input);
    }

    @PostMapping("/batches/preview")
    @PreAuthorize("@perm.canAction('PROMOTION_RECOMMEND')")
    public PromotionPreviewView preview(@Valid @RequestBody PromotionPreviewRequest input) {
        return service.previewReadOnly(input);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public PromotionBatchView batch(@PathVariable UUID id) { return service.batch(id); }

    @GetMapping("/batches/{id}/register")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public PromotionRegisterView register(@PathVariable UUID id) { return service.register(id); }

    @GetMapping("/batches")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<PromotionBatchListItem> batches(@RequestParam(required = false) UUID sourceSessionId,
                                                @RequestParam(required = false) UUID targetSessionId,
                                                @RequestParam(required = false) String status) {
        return service.batches(sourceSessionId, targetSessionId, status);
    }

    @PostMapping("/batches/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('PROMOTION_REVIEW')")
    public void cancelBatch(@PathVariable UUID id, @Valid @RequestBody PromotionCancelRequest input) {
        service.cancelBatch(id, input);
    }

    @PostMapping("/batches/{id}/commit/preview")
    @PreAuthorize("@perm.canAction('PROMOTION_COMMIT')")
    public PromotionCommitPreviewView commitPreview(@PathVariable UUID id) {
        return service.commitPreview(id);
    }

    @PatchMapping("/decisions/{id}")
    @PreAuthorize("@perm.canAction('PROMOTION_OVERRIDE')")
    public PromotionCandidateView override(@PathVariable UUID id,
                                            @Valid @RequestBody PromotionOverrideRequest input) {
        return service.override(id, input);
    }

    @GetMapping("/decisions/{id}/history")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<PromotionDecisionHistoryView> decisionHistory(@PathVariable UUID id) {
        return service.decisionHistory(id);
    }

    @PostMapping("/batches/{id}/commit")
    @PreAuthorize("@perm.canAction('PROMOTION_COMMIT')")
    public PromotionBatchView commit(@PathVariable UUID id,
                                     @Valid @RequestBody PromotionCommitRequest input) {
        return service.commit(id, input);
    }

    @PostMapping("/enrollments/{enrollmentId}/activate")
    @PreAuthorize("@perm.canAction('PROMOTION_COMMIT')")
    public PromotionActivationView activate(@PathVariable UUID enrollmentId,
                                            @Valid @RequestBody PromotionActivationRequest input) {
        return service.activatePlanned(enrollmentId, input);
    }
}
