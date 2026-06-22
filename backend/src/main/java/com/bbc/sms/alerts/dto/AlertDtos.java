package com.bbc.sms.alerts.dto;

import java.time.Instant;
import java.util.UUID;

public class AlertDtos {

    /** One proactive alert, enriched with the student's identity for the list. */
    public record AlertView(
            UUID id,
            UUID studentId,
            String studentName,
            String className,
            String type,
            String severity,
            String title,
            String detail,
            String status,
            Instant createdAt) {}

    /** Result of a scan run. */
    public record ScanResult(int created) {}
}
