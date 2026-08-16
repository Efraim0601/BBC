package com.bbc.sms.coursebook;

import com.bbc.sms.coursebook.dto.CoursebookDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/coursebook")
public class CoursebookController {

    private final CoursebookService service;

    public CoursebookController(CoursebookService service) { this.service = service; }

    /** Read-only class options for the coursebook's assigned-class selector. */
    @GetMapping("/classes")
    @PreAuthorize("@perm.staffOnly()")
    public List<ClassRef> classes() {
        return service.classes();
    }

    /** Read-only curriculum subjects for one already scoped coursebook class. */
    @GetMapping("/subjects")
    @PreAuthorize("@perm.staffOnly()")
    public List<com.bbc.sms.setup.dto.SetupDtos.SubjectView> subjects(@RequestParam String className) {
        return service.subjects(className);
    }

    @GetMapping
    // CoursebookService resolves the class and applies the contextual V2
    // teacher/class decision; retain only the staff envelope here.
    @PreAuthorize("@perm.staffOnly()")
    public List<EntryView> forClass(@RequestParam(required = false) String className) {
        return service.forClass(className);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public EntryView create(@Valid @RequestBody EntryUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.staffOnly()")
    public EntryView update(@PathVariable UUID id, @Valid @RequestBody EntryUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOnly()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
