package com.bbc.sms.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    public record RosterView(SessionSummary session, List<RosterMark> marks, List<SessionEventView> events) {}
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

    /** A precise value is retained for calculation; display is rounded for the UI/PDF. */
    public record AttendanceMetricValues(BigDecimal expectedHours, BigDecimal finalizedHours,
                                         BigDecimal coveragePercent, BigDecimal totalAbsenceMinutes,
                                         BigDecimal totalAbsenceHours, BigDecimal justifiedAbsenceMinutes,
                                         BigDecimal justifiedAbsenceHours, BigDecimal unjustifiedAbsenceMinutes,
                                         BigDecimal unjustifiedAbsenceHours, BigDecimal lateMinutes,
                                         BigDecimal exclusionDays) {}

    public record AttendanceSessionEvidenceView(UUID expectedSessionId, UUID rollCallId,
                                                LocalDate date, String model, String periodKey,
                                                String subjectCode, String status, boolean cancelled,
                                                Integer durationMinutes, BigDecimal durationHours,
                                                String issue, String repairTarget) {}

    public record AttendanceAdjustmentEvidenceView(UUID id, BigDecimal justifiedAbsenceHours,
                                                   BigDecimal unjustifiedAbsenceHours, int lateMinutes,
                                                   String reason, String evidenceReference, String status,
                                                   long version, UUID actorUserId, String actorUsername,
                                                   Instant createdAt, boolean reclassifiesAbsence,
                                                   UUID correctsAdjustmentId) {}

    public record AttendanceReadinessIssueView(String code, String severity, UUID studentId,
                                               LocalDate date, UUID expectedSessionId, UUID rollCallId,
                                               String messageFr, String messageEn, String repairTarget) {}

    /**
     * Full report-card attendance evidence. The first eleven fields preserve the
     * pre-BAY-67 DTO shape used by existing bulletin readers.
     */
    public record AttendanceSummaryView(int finalizedSessions, int presentCount, int absentCount,
                                        int excusedCount, int lateCount, int lateMinutes,
                                        BigDecimal justifiedAbsenceHours, BigDecimal unjustifiedAbsenceHours,
                                        BigDecimal adjustedJustifiedHours, BigDecimal adjustedUnjustifiedHours,
                                        int adjustedLateMinutes, int expectedSessionCount,
                                        BigDecimal expectedHours, BigDecimal finalizedHours,
                                        BigDecimal coveragePercent,
                                        List<AttendanceSessionEvidenceView> missingSessions,
                                        List<UUID> sourceRollCallIds, BigDecimal totalAbsenceMinutes,
                                        BigDecimal totalAbsenceHours, BigDecimal justifiedAbsenceMinutes,
                                        BigDecimal unjustifiedAbsenceMinutes, int exclusionDays,
                                        List<AttendanceAdjustmentEvidenceView> approvedAdjustments,
                                        String policyVersion, List<AttendanceReadinessIssueView> blockers,
                                        List<AttendanceReadinessIssueView> warnings,
                                        AttendanceMetricValues rawValues, AttendanceMetricValues displayValues,
                                        String annualEvidenceVersion, boolean annualDraftRequired,
                                        List<UUID> sourceSnapshotIds) {
        public AttendanceSummaryView {
            missingSessions = missingSessions == null ? List.of() : List.copyOf(missingSessions);
            sourceRollCallIds = sourceRollCallIds == null ? List.of() : List.copyOf(sourceRollCallIds);
            approvedAdjustments = approvedAdjustments == null ? List.of() : List.copyOf(approvedAdjustments);
            sourceSnapshotIds = sourceSnapshotIds == null ? List.of() : List.copyOf(sourceSnapshotIds);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            policyVersion = policyVersion == null ? "attendance-policy-v1" : policyVersion;
        }

        /** Source-compatible constructor used by the existing snapshot code/tests. */
        public AttendanceSummaryView(int finalizedSessions, int presentCount, int absentCount,
                                     int excusedCount, int lateCount, int lateMinutes,
                                     BigDecimal justifiedAbsenceHours, BigDecimal unjustifiedAbsenceHours,
                                     BigDecimal adjustedJustifiedHours, BigDecimal adjustedUnjustifiedHours,
                                     int adjustedLateMinutes) {
            this(finalizedSessions, presentCount, absentCount, excusedCount, lateCount, lateMinutes,
                    justifiedAbsenceHours, unjustifiedAbsenceHours, adjustedJustifiedHours,
                    adjustedUnjustifiedHours, adjustedLateMinutes, finalizedSessions,
                    null, null, null, List.of(), List.of(), null, null, null, null, 0,
                    List.of(), "attendance-policy-v1", List.of(), List.of(), null, null, null, false, List.of());
        }
    }

    public record AttendanceAggregationView(UUID academicSessionId, UUID reportingPeriodId,
                                            UUID classId, UUID studentId, String className,
                                            String model, AttendanceSummaryView attendance) {}

    public record AttendanceSourceBreakdownView(UUID expectedSessionId, UUID rollCallId,
                                                UUID studentId, LocalDate date, String model,
                                                String periodKey, String subjectCode, String sessionStatus,
                                                String markStatus, int durationMinutes,
                                                BigDecimal absenceMinutes, int lateMinutes,
                                                String markSource, String reason, String note,
                                                boolean cancelled, long sessionVersion) {}

    public record AttendanceAdjustmentRowRequest(@NotNull UUID studentId,
                                                 BigDecimal justifiedAbsenceHours,
                                                 BigDecimal unjustifiedAbsenceHours,
                                                 Integer lateMinutes, @NotBlank String reason,
                                                 String evidenceReference, Long version,
                                                 String correctionReason, String correctionEvidenceReference) {}

    public record AttendanceAdjustmentBatchRequest(@NotNull UUID reportingPeriodId,
                                                   @NotNull UUID classId,
                                                   @NotNull List<AttendanceAdjustmentRowRequest> rows) {}

    public record AttendanceAdjustmentRowResult(UUID studentId, String outcome, UUID adjustmentId,
                                                String status, long version,
                                                Map<String, String> fieldErrors,
                                                String messageFr, String messageEn,
                                                boolean retryable) {
        public AttendanceAdjustmentRowResult {
            fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
        }
    }

    public record AttendanceAdjustmentBatchResponse(UUID reportingPeriodId, UUID classId,
                                                    List<AttendanceAdjustmentRowResult> rows,
                                                    boolean allSucceeded) {
        public AttendanceAdjustmentBatchResponse {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record AttendanceAdjustmentTransitionRequest(@NotBlank String status, String reason, Long version) {}

    public record AttendanceWorkflowHistoryView(UUID id, UUID aggregateId, String fromStatus,
                                               String toStatus, UUID actorUserId, String actorUsername,
                                               String reason, String evidenceReference,
                                               long sourceVersion, Instant occurredAt) {}

    public record ConductRecommendationView(boolean workWarning, boolean workBlame,
                                            boolean conductWarning, boolean conductBlame,
                                            boolean honorRoll, boolean encouragement,
                                            boolean congratulations, int exclusionDays,
                                            String policyVersion, String reason,
                                            long version, Instant calculatedAt) {}
}
