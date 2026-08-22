package com.bbc.sms.parentportal.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the parent portal module. */
public final class ParentDtos {

    private ParentDtos() {}

    /** One current programme stream available to the family for a child. */
    public record ProgrammeClassView(UUID classId, String className, String subsystem, String level) {}

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

    /** Parent-safe attendance summary; it deliberately omits staff/device metadata. */
    public record ParentAttendanceView(UUID studentId, int total, int present, int late,
                                       int absent, int excused, int attendanceRate,
                                       List<ParentAttendanceRecordView> records) {}

    public record ParentAttendanceRecordView(UUID id, LocalDate date, String status, int lateMinutes) {}

    /** Discipline fields published to a linked parent; no internal actor/audit fields. */
    public record ParentDisciplineView(UUID id, LocalDate incidentDate, String type,
                                       String description, String sanction) {}

    /** Confidential health records remain hidden; only parent-safe infirmary history is returned. */
    public record ParentHealthView(UUID studentId, List<ParentHealthVisitView> visits) {}

    public record ParentHealthVisitView(UUID id, LocalDate visitDate, String reason, String treatment) {}

    public record ParentEventView(UUID id, String title, String type, LocalDate eventDate,
                                  String description) {}

    public record ParentNoticeView(UUID id, String category, String subject, String body,
                                   boolean requiresAck, boolean acknowledged,
                                   Instant acknowledgedAt, String senderName, Instant createdAt) {}

    public record ParentAckRequest(@NotBlank String signedBy) {}
}
