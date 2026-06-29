package com.bbc.sms.setup;

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

    public SetupController(SetupService service) { this.service = service; }

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
    public List<TeacherOption> assignableTeachers() { return service.assignableTeachers(); }

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
}
