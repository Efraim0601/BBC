package com.bbc.sms.foundation.cohort;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.cohort.CohortDtos.*;

@RestController
@RequestMapping("/api/setup/cohorts")
public class AcademicCohortController {
    private final AcademicCohortService service;

    public AcademicCohortController(AcademicCohortService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.staffOnly()")
    public List<CohortView> list(@RequestParam UUID sessionId) { return service.list(sessionId); }

    @GetMapping("/class-options")
    @PreAuthorize("@perm.staffOnly()")
    public List<ClassOption> classOptions(@RequestParam UUID sessionId) { return service.classOptions(sessionId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@parcours.allows() and @perm.staffOnly()")
    public CohortView create(@Valid @RequestBody CohortUpsert in) { return service.upsert(null, in); }

    @PutMapping("/{id}")
    @PreAuthorize("@parcours.allows() and @perm.staffOnly()")
    public CohortView update(@PathVariable UUID id, @Valid @RequestBody CohortUpsert in) { return service.upsert(id, in); }

    @GetMapping("/pathway-preview")
    @PreAuthorize("@perm.staffOnly()")
    public PathwayPreview pathwayPreview(@RequestParam UUID sourceSessionId,
                                         @RequestParam UUID targetSessionId,
                                         @RequestParam UUID sourceCohortId) {
        return service.pathwayPreview(sourceSessionId, targetSessionId, sourceCohortId);
    }

    @PostMapping("/pathway-choices")
    @PreAuthorize("@perm.staffOnly()")
    public PathwayApplyResult applyPathway(@Valid @RequestBody PathwayApply in) {
        return service.applyPathway(in);
    }
}
