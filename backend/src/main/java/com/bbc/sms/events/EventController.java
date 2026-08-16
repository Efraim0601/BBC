package com.bbc.sms.events;

import com.bbc.sms.events.dto.EventDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@policy.canAction('EVENTS_VIEW')")
    public List<EventView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('EVENTS_MANAGE')")
    public EventView create(@Valid @RequestBody EventUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@policy.canAction('EVENTS_MANAGE')")
    public EventView update(@PathVariable UUID id, @Valid @RequestBody EventUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('EVENTS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/notify")
    @PreAuthorize("@policy.canAction('EVENTS_MANAGE')")
    public NotifyResult notify(@PathVariable UUID id) {
        return service.notify(id);
    }
}
