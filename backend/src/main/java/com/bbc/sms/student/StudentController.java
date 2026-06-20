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

    public StudentController(StudentService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.can('students','read')")
    public List<StudentView> list(@RequestParam(required = false) String className) {
        return service.list(className);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.can('students','read')")
    public StudentView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('students','write')")
    public StudentView create(@Valid @RequestBody StudentUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('students','write')")
    public StudentView update(@PathVariable UUID id, @Valid @RequestBody StudentUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
