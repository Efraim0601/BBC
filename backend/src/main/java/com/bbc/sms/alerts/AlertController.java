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
    @PreAuthorize("@perm.can('alerts','read')")
    public List<AlertView> list() {
        return service.list();
    }

    @PostMapping("/scan")
    @PreAuthorize("@perm.can('alerts','write')")
    public ScanResult scan() {
        return service.scan();
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("@perm.can('alerts','write')")
    public void ack(@PathVariable UUID id) {
        service.ack(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("@perm.can('alerts','write')")
    public void resolve(@PathVariable UUID id) {
        service.resolve(id);
    }
}
