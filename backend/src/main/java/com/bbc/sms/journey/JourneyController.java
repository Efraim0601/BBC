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
    // JourneyService resolves the student and evaluates the contextual V2
    // JOURNEY_VIEW grant; a context-free legacy check rejects that valid path.
    @PreAuthorize("@perm.staffOnly()")
    public StudentJourney forStudent(@PathVariable UUID studentId) {
        return service.forStudent(studentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.staffOnly()")
    public JourneyView upsert(@Valid @RequestBody JourneyUpsert in) {
        return service.upsert(in);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@perm.staffOnly()")
    public JourneyView voidEntry(@PathVariable UUID id, @Valid @RequestBody JourneyCorrectionRequest request) {
        return service.voidEntry(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.staffOnly()")
    public void delete(@PathVariable UUID id) {
        throw ApiException.coded(HttpStatus.GONE, "JOURNEY_DELETE_REPLACED", "Les entrées de parcours sont append-only. Utilisez la correction/annulation auditée avec un motif.");
    }
}
