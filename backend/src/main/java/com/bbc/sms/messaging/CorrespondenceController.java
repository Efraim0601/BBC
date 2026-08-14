package com.bbc.sms.messaging;

import com.bbc.sms.messaging.dto.MessageDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class CorrespondenceController {

    private final CorrespondenceService service;

    public CorrespondenceController(CorrespondenceService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('MESSAGES_VIEW')")
    public List<NoticeView> list(@RequestParam(required = false) UUID studentId) {
        return studentId != null ? service.forStudent(studentId) : service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('MESSAGES_MANAGE')")
    public NoticeView create(@Valid @RequestBody NoticeUpsert in) {
        return service.create(in);
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("@perm.canAction('MESSAGES_MANAGE')")
    public NoticeView acknowledge(@PathVariable UUID id, @Valid @RequestBody AckRequest in) {
        return service.acknowledge(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('MESSAGES_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
