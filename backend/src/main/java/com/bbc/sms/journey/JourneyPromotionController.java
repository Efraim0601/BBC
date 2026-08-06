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

    @GetMapping("/rules")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public List<PromotionRuleView> rules(@RequestParam UUID sessionId) { return service.rules(sessionId); }

    @PostMapping("/rules")
    @PreAuthorize("@perm.canAction('PROGRESSION_CONFIGURE')")
    public PromotionRuleView saveRule(@Valid @RequestBody PromotionRuleUpsert input) {
        return service.saveRule(input);
    }

    @PostMapping("/batches/preview")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('PROMOTION_REVIEW')")
    public PromotionBatchView preview(@Valid @RequestBody PromotionPreviewRequest input) {
        return service.preview(input);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("@perm.canAction('PROGRESSION_VIEW')")
    public PromotionBatchView batch(@PathVariable UUID id) { return service.batch(id); }

    @PatchMapping("/decisions/{id}")
    @PreAuthorize("@perm.canAction('PROMOTION_REVIEW')")
    public PromotionCandidateView override(@PathVariable UUID id,
                                            @Valid @RequestBody PromotionOverrideRequest input) {
        return service.override(id, input);
    }

    @PostMapping("/batches/{id}/commit")
    @PreAuthorize("@perm.canAction('PROMOTION_COMMIT')")
    public PromotionBatchView commit(@PathVariable UUID id,
                                     @Valid @RequestBody PromotionCommitRequest input) {
        return service.commit(id, input);
    }
}
