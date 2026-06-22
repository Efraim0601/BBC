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

    @GetMapping
    @PreAuthorize("@perm.can('coursebook','read')")
    public List<EntryView> forClass(@RequestParam(required = false) String className) {
        return service.forClass(className);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('coursebook','write')")
    public EntryView create(@Valid @RequestBody EntryUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('coursebook','write')")
    public EntryView update(@PathVariable UUID id, @Valid @RequestBody EntryUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('coursebook','write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
