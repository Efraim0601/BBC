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

    private static final String READ =
        "@parcours.allows() and ("
      + "@perm.can('settings','read') or @perm.can('students','read') "
      + "or @perm.can('academic','read') or @perm.can('timetable','read'))";
    private static final String WRITE = "@parcours.allows() and @perm.can('settings','write')";

    private final SetupService service;
    private final CurriculumCopyService curriculumCopy;
    private final CurriculumVersionService curriculumVersions;
    private final IdempotencyService idempotency;

    public SetupController(SetupService service, CurriculumCopyService curriculumCopy,
                           CurriculumVersionService curriculumVersions, IdempotencyService idempotency) {
        this.service = service; this.curriculumCopy = curriculumCopy; this.curriculumVersions = curriculumVersions; this.idempotency = idempotency;
    }

    // ---- Sections -----------------------------------------------------------
    @GetMapping("/sections")
    @PreAuthorize(READ)
    public List<SectionView> sections() { return service.listSections(); }

    @PostMapping("/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public SectionView createSection(@Valid @RequestBody SectionUpsert in) { return service.createSection(in); }

    @PutMapping("/sections/{id}")
    @PreAuthorize(WRITE)
    public SectionView updateSection(@PathVariable String id, @Valid @RequestBody SectionUpsert in) {
        return service.updateSection(id, in);
    }

    @DeleteMapping("/sections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteSection(@PathVariable String id) { service.deleteSection(id); }

    // ---- Classes ------------------------------------------------------------
    @GetMapping("/classes")
    @PreAuthorize(READ)
    public List<ClassView> classes() { return service.listClasses(); }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public ClassView createClass(@Valid @RequestBody ClassUpsert in) { return service.createClass(in); }

    @PutMapping("/classes/{id}")
    @PreAuthorize(WRITE)
    public ClassView updateClass(@PathVariable UUID id, @Valid @RequestBody ClassUpsert in) {
        return service.updateClass(id, in);
    }

    @DeleteMapping("/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
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
    @PreAuthorize(WRITE)
    public List<TeacherOption> setClassTeachers(@PathVariable UUID id, @RequestBody SetClassTeachers in) {
        return service.setClassTeachers(id, in.employeeIds());
    }

    // ---- Subjects -----------------------------------------------------------
    @GetMapping("/subjects")
    @PreAuthorize(READ)
    public List<SubjectView> subjects() { return service.listSubjects(); }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public SubjectView createSubject(@Valid @RequestBody SubjectUpsert in) { return service.createSubject(in); }

    @PutMapping("/subjects/{id}")
    @PreAuthorize(WRITE)
    public SubjectView updateSubject(@PathVariable UUID id, @Valid @RequestBody SubjectUpsert in) {
        return service.updateSubject(id, in);
    }

    @DeleteMapping("/subjects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteSubject(@PathVariable UUID id) { service.deleteSubject(id); }

    // ---- Per-class coefficients ---------------------------------------------
    @GetMapping("/subjects/coefficients")
    @PreAuthorize(READ)
    public List<ClassCoefView> coefficients() { return service.listCoefficients(); }

    @PostMapping("/subjects/coefficients")
    @PreAuthorize(WRITE)
    public ClassCoefView upsertCoefficient(@Valid @RequestBody ClassCoefUpsert in) {
        return service.upsertCoefficient(in);
    }

    @DeleteMapping("/subjects/coefficients")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteCoefficient(@RequestParam UUID classId, @RequestParam UUID subjectId) {
        service.deleteCoefficient(classId, subjectId);
    }

    @PostMapping("/subjects/coefficients/import")
    @PreAuthorize(WRITE)
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

    @GetMapping("/curriculum/version")
    @PreAuthorize(READ)
    public CurriculumVersionView curriculumVersion(@RequestParam UUID academicSessionId,
                                                    @RequestParam UUID classId) {
        return curriculumVersions.current(academicSessionId, classId);
    }

    @PostMapping("/curriculum/version/draft")
    @PreAuthorize(WRITE)
    public CurriculumVersionView curriculumDraft(@Valid @RequestBody CurriculumDraftRequest in) {
        return curriculumVersions.createRevision(in);
    }

    @PostMapping("/curriculum/version/revision")
    @PreAuthorize(WRITE)
    public CurriculumVersionView curriculumRevision(@Valid @RequestBody CurriculumRevisionRequest in) {
        return curriculumVersions.revise(in);
    }

    @PostMapping("/curriculum/version/subject")
    @PreAuthorize(WRITE)
    public CurriculumVersionView curriculumVersionSubject(@Valid @RequestBody CurriculumSubjectUpsert in) {
        return curriculumVersions.upsertSubject(in);
    }

    @DeleteMapping("/curriculum/version/subject")
    @PreAuthorize(WRITE)
    public CurriculumVersionView deleteCurriculumVersionSubject(@RequestParam UUID academicSessionId,
                                                                 @RequestParam UUID classId,
                                                                 @RequestParam UUID subjectId) {
        return curriculumVersions.deleteSubject(academicSessionId, classId, subjectId);
    }

    @GetMapping("/curriculum/version/{id}")
    @PreAuthorize(READ)
    public CurriculumVersionView curriculumVersionById(@PathVariable UUID id) { return curriculumVersions.byId(id); }

    @PostMapping("/curriculum/version/{id}/publish/preview")
    @PreAuthorize(READ)
    public CurriculumPublishImpact curriculumPublishPreview(@PathVariable UUID id) { return curriculumVersions.publishPreview(id); }

    @PostMapping("/curriculum/version/publish")
    @PreAuthorize(WRITE)
    public CurriculumVersionView publishCurriculum(@Valid @RequestBody CurriculumPublishRequest in) { return curriculumVersions.publish(in); }

    @GetMapping(value = "/curriculum/migration-exceptions", produces = "text/csv")
    @PreAuthorize(READ)
    public byte[] curriculumMigrationExceptions() { return curriculumVersions.exceptionCsv(); }

    @PostMapping("/curriculum/copy/preview")
    @PreAuthorize(READ)
    public CurriculumCopyPreview previewCurriculumCopy(@Valid @RequestBody CurriculumCopyPreviewRequest in) {
        return curriculumCopy.preview(in);
    }

    @PostMapping("/curriculum/copy/apply")
    @PreAuthorize(WRITE)
    public CurriculumCopyPreview applyCurriculumCopy(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CurriculumCopyApplyRequest in) {
        return idempotency.execute("curriculum-copy:" + in.targetSessionId(), idempotencyKey,
                in, CurriculumCopyPreview.class, () -> curriculumCopy.apply(in, idempotencyKey));
    }

    @PostMapping("/curriculum/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public SubjectGroupView createCurriculumGroup(@Valid @RequestBody SubjectGroupUpsert in) {
        return service.upsertCurriculumGroup(null, in);
    }

    @PutMapping("/curriculum/groups/{id}")
    @PreAuthorize(WRITE)
    public SubjectGroupView updateCurriculumGroup(@PathVariable UUID id,
                                                  @Valid @RequestBody SubjectGroupUpsert in) {
        return service.upsertCurriculumGroup(id, in);
    }

    @DeleteMapping("/curriculum/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteCurriculumGroup(@PathVariable UUID id) { service.deleteCurriculumGroup(id); }

    @PostMapping("/curriculum/subjects")
    @PreAuthorize(WRITE)
    public CurriculumSubjectView upsertCurriculumSubject(@Valid @RequestBody CurriculumSubjectUpsert in) {
        return service.upsertCurriculumSubject(in);
    }

    @DeleteMapping("/curriculum/subjects")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteCurriculumSubject(@RequestParam UUID academicSessionId,
                                        @RequestParam UUID classId,
                                        @RequestParam UUID subjectId) {
        service.deleteCurriculumSubject(academicSessionId, classId, subjectId);
    }

    @PostMapping("/curriculum/teachers")
    @PreAuthorize(WRITE)
    public CurriculumTeacherView upsertCurriculumTeacher(@Valid @RequestBody CurriculumTeacherUpsert in) {
        return service.upsertCurriculumTeacher(in);
    }

    @PostMapping("/curriculum/homeroom")
    @PreAuthorize(WRITE)
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
    @PreAuthorize(WRITE)
    public void deleteCurriculumTeacher(@PathVariable UUID id) { service.deleteCurriculumTeacher(id); }
}
