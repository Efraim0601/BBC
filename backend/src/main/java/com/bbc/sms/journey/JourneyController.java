package com.bbc.sms.journey;

import com.bbc.sms.journey.dto.JourneyDtos.*;
import com.bbc.sms.platform.common.ApiException;
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

    @PostMapping("/{id}/void")
    @PreAuthorize("@perm.can('journey','write')")
    public JourneyView voidEntry(@PathVariable UUID id, @Valid @RequestBody JourneyCorrectionRequest request) {
        return service.voidEntry(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.can('journey','write')")
    public void delete(@PathVariable UUID id) {
        throw ApiException.coded(HttpStatus.GONE, "JOURNEY_DELETE_REPLACED", "Les entrées de parcours sont append-only. Utilisez la correction/annulation auditée avec un motif.");
    }
}
