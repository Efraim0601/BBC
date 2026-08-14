package com.bbc.sms.discipline;

import com.bbc.sms.discipline.dto.DisciplineDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/discipline")
public class DisciplineController {

    private final DisciplineService service;

    public DisciplineController(DisciplineService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('DISCIPLINE_VIEW')")
    public List<IncidentView> list() {
        return service.list();
    }

    /** Auto-fill student card from matricule or UUID while typing an incident. */
    @GetMapping("/lookup")
    @PreAuthorize("@perm.canAction('DISCIPLINE_VIEW')")
    public StudentLookup lookup(@RequestParam("q") String q) {
        return service.lookup(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('DISCIPLINE_MANAGE')")
    public IncidentView create(@Valid @RequestBody IncidentUpsert in) {
        return service.create(in);
    }

    @PostMapping("/notify")
    @PreAuthorize("@perm.canAction('DISCIPLINE_MANAGE')")
    public NotifyResult notify(@Valid @RequestBody NotifyRequest in) {
        return service.notifyParent(in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('DISCIPLINE_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
