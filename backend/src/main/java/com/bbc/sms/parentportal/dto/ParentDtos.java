package com.bbc.sms.parentportal.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.UUID;

/** DTOs for the parent portal module. */
public final class ParentDtos {

    private ParentDtos() {}

    public record ChildView(
            UUID studentId,
            String matricule,
            String name,
            String className,
            long balance,
            String feeStatus,
            int attendanceRate,
            boolean financeVisible,
            boolean attendanceVisible
    ) {}

    /**
     * Labels and {@code coef} come from the subject registry so the portal can name a subject
     * and weight its average exactly like the official bulletin. Both languages travel because
     * the portal switches locale client-side, with no round-trip.
     */
    public record GradeView(
            String subjectCode,
            String subjectLabelFr,
            String subjectLabelEn,
            int coef,
            int sequence,
            BigDecimal mark
    ) {}

    public record SuggestionView(
            UUID id,
            String category,
            String message,
            String status,
            OffsetDateTime createdAt
    ) {}

    public record SuggestionRequest(
            @NotBlank String category,   // suggestion | question | complaint | thanks
            @NotBlank String message
    ) {}

    public record ParentJourneyEventView(UUID id, String eventType, String sessionLabel,
                                         String className, BigDecimal average, String decision,
                                         Instant occurredAt, UUID sourceId) {}
}
