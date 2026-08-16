package com.bbc.sms.setup;

import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.setup.dto.SetupDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Academic Setup admin API. Mutations require Settings write (admin); reads are
 * available to anyone who can touch students, academics or the timetable, since
 * those forms need the class/subject reference lists.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    /**
     * Keep reads behind the staff envelope so the service can evaluate the V2
     * user/role rules with the resource context (class, subject and session)
     * where required.  A missing parcours is valid for an all-parcours read;
     * writes still require an explicitly selected parcours in addition to the
     * staff envelope.  Calling a legacy @perm.canAction gate here would reject
     * a V2 user exception before the service check could run.
     */
    private static final String READ = "@perm.staffOnly()";
    private static final String WRITE = "@parcours.allows() and @perm.staffOnly()";
    private static final String CLASS_WRITE = WRITE;
    private static final String CLASS_ASSIGNMENT_WRITE = WRITE;
    private static final String SUBJECT_WRITE = WRITE;
    private static final String CURRICULUM_WRITE = WRITE;
    private static final String CURRICULUM_CLASS_WRITE = WRITE;
    private static final String CURRICULUM_CATALOG_WRITE = WRITE;
    private static final String ASSIGNMENT_WRITE = WRITE;

    private final SetupService service;
    private final CurriculumCopyService curriculumCopy;
    private final IdempotencyService idempotency;

    public SetupController(SetupService service, CurriculumCopyService curriculumCopy, IdempotencyService idempotency) {
        this.service = service; this.curriculumCopy = curriculumCopy; this.idempotency = idempotency;
    }

    // ---- Sections -----------------------------------------------------------
    @GetMapping("/sections")
    @PreAuthorize(READ)
    public List<SectionView> sections() { return service.listSections(); }

    @PostMapping("/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CLASS_WRITE)
    public SectionView createSection(@Valid @RequestBody SectionUpsert in) { return service.createSection(in); }

    @PutMapping("/sections/{id}")
    @PreAuthorize(CLASS_WRITE)
    public SectionView updateSection(@PathVariable String id, @Valid @RequestBody SectionUpsert in) {
        return service.updateSection(id, in);
    }

    @DeleteMapping("/sections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CLASS_WRITE)
    public void deleteSection(@PathVariable String id) { service.deleteSection(id); }

    // ---- Classes ------------------------------------------------------------
    @GetMapping("/classes")
    @PreAuthorize(READ)
    public List<ClassView> classes() { return service.listClasses(); }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CLASS_WRITE)
    public ClassView createClass(@Valid @RequestBody ClassUpsert in) { return service.createClass(in); }

    @PutMapping("/classes/{id}")
    @PreAuthorize(CLASS_WRITE)
    public ClassView updateClass(@PathVariable UUID id, @Valid @RequestBody ClassUpsert in) {
        return service.updateClass(id, in);
    }

    @DeleteMapping("/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CLASS_WRITE)
    public void deleteClass(@PathVariable UUID id) { service.deleteClass(id); }

    // ---- Class ↔ teachers ---------------------------------------------------
    @GetMapping("/teachers")
    @PreAuthorize(READ)
    public List<TeacherOption> assignableTeachers(@RequestParam(required = false) String level) {
        return service.assignableTeachers(level);
    }

    @GetMapping("/classes/{id}/teachers")
    @PreAuthorize(READ)
    public List<TeacherOption> classTeachers(@PathVariable UUID id) { return service.classTeachers(id); }

    @PutMapping("/classes/{id}/teachers")
    @PreAuthorize(CLASS_ASSIGNMENT_WRITE)
    public List<TeacherOption> setClassTeachers(@PathVariable UUID id, @RequestBody SetClassTeachers in) {
        return service.setClassTeachers(id, in.employeeIds());
    }

    // ---- Subjects -----------------------------------------------------------
    @GetMapping("/subjects")
    @PreAuthorize(READ)
    public List<SubjectView> subjects() { return service.listSubjects(); }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUBJECT_WRITE)
    public SubjectView createSubject(@Valid @RequestBody SubjectUpsert in) { return service.createSubject(in); }

    @PutMapping("/subjects/{id}")
    @PreAuthorize(SUBJECT_WRITE)
    public SubjectView updateSubject(@PathVariable UUID id, @Valid @RequestBody SubjectUpsert in) {
        return service.updateSubject(id, in);
    }

    @DeleteMapping("/subjects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SUBJECT_WRITE)
    public void deleteSubject(@PathVariable UUID id) { service.deleteSubject(id); }

    // ---- Per-class coefficients ---------------------------------------------
    @GetMapping("/subjects/coefficients")
    @PreAuthorize(READ)
    public List<ClassCoefView> coefficients() { return service.listCoefficients(); }

    @PostMapping("/subjects/coefficients")
    @PreAuthorize(CURRICULUM_CLASS_WRITE)
    public ClassCoefView upsertCoefficient(@Valid @RequestBody ClassCoefUpsert in) {
        return service.upsertCoefficient(in);
    }

    @DeleteMapping("/subjects/coefficients")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CURRICULUM_CLASS_WRITE)
    public void deleteCoefficient(@RequestParam UUID classId, @RequestParam UUID subjectId) {
        service.deleteCoefficient(classId, subjectId);
    }

    @PostMapping("/subjects/coefficients/import")
    @PreAuthorize(CURRICULUM_CATALOG_WRITE)
    public CoefImportResult importCoefficients(@Valid @RequestBody CoefImportRequest in) {
        return service.importCoefficients(in);
    }

    // ---- Session-versioned curriculum --------------------------------------

    @GetMapping("/curriculum")
    @PreAuthorize(READ)
    public CurriculumView curriculum(@RequestParam UUID academicSessionId,
                                     @RequestParam UUID classId) {
        return service.curriculum(academicSessionId, classId);
    }

    @PostMapping("/curriculum/copy/preview")
    @PreAuthorize(READ)
    public CurriculumCopyPreview previewCurriculumCopy(@Valid @RequestBody CurriculumCopyPreviewRequest in) {
        return curriculumCopy.preview(in);
    }

    @PostMapping("/curriculum/copy/apply")
    @PreAuthorize(CURRICULUM_CATALOG_WRITE)
    public CurriculumCopyPreview applyCurriculumCopy(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CurriculumCopyApplyRequest in) {
        return idempotency.execute("curriculum-copy:" + in.targetSessionId(), idempotencyKey,
                in, CurriculumCopyPreview.class, () -> curriculumCopy.apply(in, idempotencyKey));
    }

    @PostMapping("/curriculum/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CURRICULUM_CATALOG_WRITE)
    public SubjectGroupView createCurriculumGroup(@Valid @RequestBody SubjectGroupUpsert in) {
        return service.upsertCurriculumGroup(null, in);
    }

    @PutMapping("/curriculum/groups/{id}")
    @PreAuthorize(CURRICULUM_CATALOG_WRITE)
    public SubjectGroupView updateCurriculumGroup(@PathVariable UUID id,
                                                  @Valid @RequestBody SubjectGroupUpsert in) {
        return service.upsertCurriculumGroup(id, in);
    }

    @DeleteMapping("/curriculum/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CURRICULUM_CATALOG_WRITE)
    public void deleteCurriculumGroup(@PathVariable UUID id) { service.deleteCurriculumGroup(id); }

    @PostMapping("/curriculum/subjects")
    @PreAuthorize(CURRICULUM_WRITE)
    public CurriculumSubjectView upsertCurriculumSubject(@Valid @RequestBody CurriculumSubjectUpsert in) {
        return service.upsertCurriculumSubject(in);
    }

    @DeleteMapping("/curriculum/subjects")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CURRICULUM_WRITE)
    public void deleteCurriculumSubject(@RequestParam UUID academicSessionId,
                                        @RequestParam UUID classId,
                                        @RequestParam UUID subjectId) {
        service.deleteCurriculumSubject(academicSessionId, classId, subjectId);
    }

    @PostMapping("/curriculum/teachers")
    @PreAuthorize(ASSIGNMENT_WRITE)
    public CurriculumTeacherView upsertCurriculumTeacher(@Valid @RequestBody CurriculumTeacherUpsert in) {
        return service.upsertCurriculumTeacher(in);
    }

    @PostMapping("/curriculum/homeroom")
    @PreAuthorize(ASSIGNMENT_WRITE)
    public CurriculumTeacherView upsertHomeroom(@Valid @RequestBody HomeroomAssignmentUpsert in) {
        return service.upsertHomeroom(in);
    }

    @PostMapping("/curriculum/assignments/impact-preview")
    @PreAuthorize(READ)
    public AssignmentImpactView assignmentImpactPreview(@Valid @RequestBody AssignmentImpactRequest in) {
        return service.assignmentImpactPreview(in);
    }

    @DeleteMapping("/curriculum/teachers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ASSIGNMENT_WRITE)
    public void deleteCurriculumTeacher(@PathVariable UUID id) { service.deleteCurriculumTeacher(id); }
}
