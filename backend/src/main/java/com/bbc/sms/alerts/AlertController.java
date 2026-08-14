package com.bbc.sms.alerts;

import com.bbc.sms.alerts.dto.AlertDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('ALERTS_VIEW')")
    public List<AlertView> list() {
        return service.list();
    }

    @PostMapping("/scan")
    @PreAuthorize("@perm.canAction('ALERTS_MANAGE')")
    public ScanResult scan() {
        return service.scan();
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("@perm.canAction('ALERTS_MANAGE')")
    public void ack(@PathVariable UUID id) {
        service.ack(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("@perm.canAction('ALERTS_MANAGE')")
    public void resolve(@PathVariable UUID id) {
        service.resolve(id);
    }
}
