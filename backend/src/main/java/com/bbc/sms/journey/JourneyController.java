package com.bbc.sms.journey;

import com.bbc.sms.journey.dto.JourneyDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/journey")
public class JourneyController {

    private final JourneyService service;

    public JourneyController(JourneyService service) { this.service = service; }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("@perm.can('journey','read')")
    public StudentJourney forStudent(@PathVariable UUID studentId) {
        return service.forStudent(studentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('journey','write')")
    public JourneyView upsert(@Valid @RequestBody JourneyUpsert in) {
        return service.upsert(in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('journey','write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
