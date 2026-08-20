package com.bbc.sms.academic.secondary;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.academic.secondary.SecondaryCompetencyDtos.*;

@RestController
@RequestMapping("/api/academic/secondary-competencies")
public class SecondaryCompetencyController {
    private final SecondaryCompetencyService service;
    public SecondaryCompetencyController(SecondaryCompetencyService service) { this.service = service; }

    @GetMapping @PreAuthorize("@perm.canAction('ACADEMIC_ASSESSMENT_VIEW') and @perm.staffOnly()")
    public List<ModelView> list(@RequestParam UUID reportingPeriodId, @RequestParam UUID classId,
                                @RequestParam UUID subjectId, @RequestParam(required = false) String locale) {
        return service.list(reportingPeriodId, classId, subjectId, locale);
    }

    @GetMapping("/{id}") @PreAuthorize("@perm.canAction('ACADEMIC_ASSESSMENT_VIEW') and @perm.staffOnly()")
    public ModelView get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/models") @PreAuthorize("@perm.canAction('ACADEMIC_ASSESSMENT_MANAGE') and @perm.staffOnly()")
    public ModelView create(@Valid @RequestBody ModelRequest request) { return service.create(request); }

    @PostMapping("/models/{id}/copy") @PreAuthorize("@perm.canAction('ACADEMIC_ASSESSMENT_MANAGE') and @perm.staffOnly()")
    public ModelView copy(@PathVariable UUID id, @RequestParam String reason) { return service.copy(id, reason); }

    @PostMapping("/models/{id}/publish") @PreAuthorize("@perm.canAction('ACADEMIC_ASSESSMENT_MANAGE') and @perm.staffOnly()")
    public ModelView publish(@PathVariable UUID id) { return service.publish(id); }

    @GetMapping("/{modelId}/marks") @PreAuthorize("@perm.canAction('ACADEMIC_SUBJECT_GRADE_VIEW') and @perm.staffOnly()")
    public List<MarkView> marks(@PathVariable UUID modelId, @RequestParam UUID reportingPeriodId,
                                @RequestParam(required = false) UUID studentId) {
        return service.marks(modelId, reportingPeriodId, studentId);
    }

    @PutMapping("/marks") @PreAuthorize("@perm.canAction('ACADEMIC_SUBJECT_GRADE_EDIT') and @perm.staffOnly()")
    public MarkView saveMark(@Valid @RequestBody MarkRequest request) { return service.saveMark(request); }

    @PostMapping("/marks/import") @PreAuthorize("@perm.canAction('ACADEMIC_SUBJECT_GRADE_EDIT') and @perm.staffOnly()")
    public List<MarkView> importMarks(@Valid @RequestBody ImportRequest request) { return service.importMarks(request); }
}
