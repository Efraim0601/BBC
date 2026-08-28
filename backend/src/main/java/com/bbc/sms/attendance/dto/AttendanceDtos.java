package com.bbc.sms.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class AttendanceDtos {

    public record AttendanceView(
            UUID studentId,
            String matricule,
            String studentName,
            String className,
            LocalDate date,
            String status,
            String checkInTime,
            int lateMinutes,
            String source) {}

    /** Manual marking by a staff member from the UI. */
    public record MarkRequest(
            @NotNull UUID studentId,
            @NotNull LocalDate date,
            @NotBlank String status,         // present | late | absent
            String checkInTime,
            int lateMinutes) {}

    /** Posted by the on-site fingerprint agent. */
    public record DeviceCheckin(
            @NotBlank String matricule,
            String time,                     // HH:mm; defaults to now on the device
            @NotBlank String dedupKey) {}    // device + timestamp, for idempotency

    public record DailyBoard(
            LocalDate date,
            int present,
            int late,
            int absent,
            List<AttendanceView> records) {}

    /**
     * Reader health. {@code online} means the device checked in within the freshness
     * window — see AttendanceService.ONLINE_WINDOW — not that a green dot was hardcoded.
     */
    public record DeviceView(
            UUID id,
            String label,
            String location,
            String model,
            boolean active,
            boolean online,
            OffsetDateTime lastSeenAt,
            Long minutesSinceLastSeen) {}

    /**
     * Device registration is a staff setup operation.  The generated API key
     * is returned only in the registration response and is never included in
     * the reader-health view.
     */
    public record DeviceRegistrationRequest(
            @NotBlank String label,
            String location,
            String model) {}

    public record DeviceRegistrationView(
            UUID id,
            String label,
            String location,
            String model,
            String apiKey) {}

    public record PolicyView(UUID id, String level, String model, int lateAfterMinutes,
                             BigDecimal chronicAbsencePercent, boolean requireAbsenceReason) {}
    public record PolicyRequest(@NotBlank String model, int lateAfterMinutes,
                                BigDecimal chronicAbsencePercent, boolean requireAbsenceReason) {}

    public record AttendanceClass(UUID id, String name, String level, String subsystem, String model, int enrolledCount) {}
    public record SessionSummary(UUID id, UUID classId, String className, LocalDate date,
                                 String model, String periodKey, String subjectCode, String status,
                                 long version, int total, int marked) {}
    public record RosterMark(UUID studentId, String matricule, String studentName, String status,
                             String reason, String note, int lateMinutes, String source) {}
    public record RosterCapabilities(boolean canMark, boolean canFinalize, boolean canReopen) {}
    public record RosterView(SessionSummary session, List<RosterMark> marks, List<SessionEventView> events,
                             RosterCapabilities capabilities) {}
    public record SessionEventView(String action, String actor, String reason,
                                   OffsetDateTime occurredAt) {}
    public record MarkInput(@NotNull UUID studentId, @NotBlank String status,
                            String reason, String note, int lateMinutes) {}
    public record BulkMarkRequest(@NotNull UUID sessionId, long version, @NotNull List<MarkInput> marks) {}
    public record ActionRequest(long version, String reason) {}
    public record GenerationResult(boolean preview, LocalDate from, LocalDate to,
                                   int expectedSessions, int synchronizedSessions) {}
    public record StudentAnalytics(UUID studentId, String matricule, String studentName, String className,
                                   int expected, int present, int late, int absent, int excused,
                                   int unmarked, BigDecimal attendancePercent) {}
    public record AnalyticsView(LocalDate from, LocalDate to, int expected, int present, int late,
                                int absent, int excused, int unmarked, BigDecimal attendancePercent,
                                List<StudentAnalytics> students) {}
    public record DeviceReconciliation(UUID deviceRecordId, UUID studentId, String matricule,
                                       String studentName, String className, LocalDate date,
                                       String status, String checkInTime, boolean reconciled,
                                       UUID sessionId) {}
    public record ReconcileRequest(@NotNull UUID deviceRecordId, @NotNull UUID sessionId) {}
    public record AlertScanResult(int createdOrUpdated, BigDecimal thresholdPercent) {}
    public record NotificationView(UUID id, UUID sessionId, UUID studentId, String studentName,
                                   String guardianName, String channel, String recipient,
                                   String status, int attemptCount, OffsetDateTime createdAt) {}
}
