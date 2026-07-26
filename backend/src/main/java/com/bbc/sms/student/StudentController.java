package com.bbc.sms.student;

import com.bbc.sms.student.dto.StudentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService service;
    private final ParentLinkService parentLinks;

    public StudentController(StudentService service, ParentLinkService parentLinks) {
        this.service = service;
        this.parentLinks = parentLinks;
    }

    @GetMapping
    @PreAuthorize("@parcours.allows() and @perm.can('students','read') and @perm.staffOnly()")
    public List<StudentView> list(@RequestParam(required = false) String className) {
        return service.list(className);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public StudentView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentView create(@Valid @RequestBody StudentUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentView update(@PathVariable UUID id, @Valid @RequestBody StudentUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Bulk-import students into one class; returns a per-row created/failed report. */
    @PostMapping("/import")
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentImportResult importStudents(@Valid @RequestBody StudentImportRequest in) {
        return service.importForClass(in);
    }

    // ---- Parent accounts (review issue #2) ---------------------------------
    @GetMapping("/{id}/parents")
    @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public List<ParentAccountView> parents(@PathVariable UUID id) {
        return parentLinks.list(id);
    }

    @PostMapping("/{id}/parents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public ParentAccountView linkParent(@PathVariable UUID id, @Valid @RequestBody ParentLinkRequest in) {
        return parentLinks.link(id, in);
    }

    @DeleteMapping("/{id}/parents/{parentUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void unlinkParent(@PathVariable UUID id, @PathVariable UUID parentUserId) {
        parentLinks.unlink(id, parentUserId);
    }
}
