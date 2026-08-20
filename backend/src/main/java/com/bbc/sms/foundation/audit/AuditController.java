package com.bbc.sms.foundation.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bbc.sms.foundation.audit.AuditDtos.AuditView;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }

    @GetMapping("/{aggregateType}/{aggregateId}")
    @PreAuthorize("@perm.canAction('AUDIT_VIEW')")
    public List<AuditView> aggregate(@PathVariable String aggregateType,
                                     @PathVariable String aggregateId,
                                     @RequestParam(defaultValue = "50") int limit) {
        return service.forAggregate(aggregateType, aggregateId, limit);
    }
}
