package com.bbc.sms.academic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public class AcademicDtos {

    public record GradeView(
            UUID id,
            UUID studentId,
            String subjectCode,
            int sequence,
            BigDecimal mark) {}

    public record GradeUpsert(
            @NotNull UUID studentId,
            @NotBlank String subjectCode,
            @Min(1) int sequence,
            @NotNull @DecimalMin("0.0") @DecimalMax("20.0") BigDecimal mark) {}

    public record AssessmentView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                 String code, String label, String assessmentType,
                                 BigDecimal maxScore, BigDecimal weight, boolean mandatory,
                                 int displayOrder, long version, UUID classId,
                                 String subjectCode) {}

    public record AssessmentUpsert(@NotNull UUID reportingPeriodId, @NotBlank String code,
                                   @NotBlank String label, String assessmentType,
                                   @NotNull @DecimalMin("0.01") BigDecimal maxScore,
                                   @NotNull @DecimalMin("0.01") BigDecimal weight,
                                   boolean mandatory, @Min(1) int displayOrder, Long version,
                                   UUID classId, String subjectCode) {}

    public record AcademicGradeView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                    UUID assessmentId, UUID studentId, UUID enrollmentId,
                                    String subjectCode, BigDecimal mark, String valueStatus,
                                    String workflowStatus, long version) {}

    public record AcademicGradeUpsert(@NotNull UUID reportingPeriodId, @NotNull UUID assessmentId,
                                      @NotNull UUID studentId, UUID enrollmentId,
                                      @NotBlank String subjectCode, BigDecimal mark,
                                      String valueStatus, Long version) {}

    public record SubjectResultCommentView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                           UUID studentId, UUID enrollmentId, String subjectCode,
                                           String comment, String appreciationCode,
                                           String workflowStatus, long version) {}

    public record SubjectResultCommentUpsert(@NotNull UUID reportingPeriodId, @NotNull UUID studentId,
                                             UUID enrollmentId, @NotBlank String subjectCode,
                                             String comment, String appreciationCode, Long version) {}

    public record BulletinLineView(String subjectCode, String subjectLabel, int coefficient,
                                   BigDecimal mark, BigDecimal weighted, String teacherRemark,
                                   String appreciation, List<AssessmentEvidenceView> assessments,
                                   List<PeriodMarkView> periodMarks, String teacherName,
                                   String subjectGroupCode, String subjectGroupLabel) {}

    public record PeriodMarkView(String periodCode, BigDecimal mark) {}

    public record AssessmentEvidenceView(String code, String label, BigDecimal mark,
                                         BigDecimal maxScore, BigDecimal weight, String status) {}

    public record BulletinSnapshotView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                       String reportingPeriodCode, String reportingPeriodLabel,
                                       UUID studentId, String studentName, String matricule,
                                       String educationalLevel, String subsystem, String className,
                                       List<BulletinLineView> lines,
                                       BigDecimal average, Integer rank, int classSize,
                                       String state, boolean complete, List<String> blockers,
                                       String snapshotHash, String calculationPolicy,
                                       String generalAppreciation, AttendanceSummaryView attendance,
                                       ConductSummaryView conduct, long version,
                                       ClassStatsView classStats, UUID supersedesId,
                                       UUID correctsBulletinVersionId, String correctionReason,
                                       UUID correctionRequestedBy, Instant correctionRequestedAt,
                                       List<GroupStatsView> groupStats) {}

    /** Class master sheet built from the same session-aware calculation as a bulletin. */
    public record SessionPvRow(UUID snapshotId, UUID studentId, String studentName,
                               BigDecimal average, Integer rank, String state,
                               boolean complete, List<String> blockers) {}

    public record SessionPvView(UUID classId, String className, UUID reportingPeriodId,
                                String reportingPeriodCode, String reportingPeriodLabel,
                                List<SessionPvRow> rows, BigDecimal classAverage,
                                int totalStudents, int completeStudents) {}

    public record ClassStatsView(BigDecimal average, BigDecimal minimum, BigDecimal maximum,
                                 int successCount, BigDecimal successRate, int rankedCount) {}

    public record GroupStatsView(String code, String label, BigDecimal average,
                                 BigDecimal total, int coefficient, int subjectCount) {}

    public record BulletinLifecycleRequest(@NotBlank String reason, Long version) {}
    public record BulletinCorrectionRequest(@NotBlank String reason, Long version) {}

    public record BulletinBatchJobCreateRequest(@NotNull UUID classId, @NotNull UUID reportingPeriodId,
                                                String locale) {}

    public record BulletinBatchJobView(UUID id, UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                                       String locale, String status, int totalItems, int processedItems,
                                       int publishedItems, int blockedItems, int errorItems, int progressPercent,
                                       java.time.OffsetDateTime requestedAt, java.time.OffsetDateTime startedAt,
                                       java.time.OffsetDateTime completedAt, boolean archiveAvailable,
                                       String archiveSha256, Long archiveSizeBytes, String lastError, long version) {}

    public record BulletinBatchItemView(UUID id, UUID studentId, String studentName, String status,
                                        int attempts, String fileName, long sizeBytes, String error) {}

    public record GradeEntryAssessmentView(UUID id, String code, String label,
                                           BigDecimal maxScore, BigDecimal weight,
                                           boolean mandatory, int displayOrder) {}

    public record GradeEntrySubjectView(String code, String label, int coefficient,
                                        UUID teacherId, String teacherName) {}

    public record GradeEntryCellView(UUID assessmentId, BigDecimal mark,
                                     String valueStatus, long version) {}

    public record GradeEntryStudentView(UUID studentId, String matricule, String studentName,
                                        List<GradeEntryCellView> values, String comment,
                                        String workflowStatus) {}

    public record GradeEntryView(UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                                 String className, String subjectCode, String subjectLabel,
                                 int coefficient, UUID teacherId, String teacherName,
                                 String packetStatus, long packetVersion,
                                 List<GradeEntryAssessmentView> assessments,
                                 List<GradeEntryStudentView> students,
                                 int totalStudents, int completedStudents,
                                 List<String> blockers,
                                 List<GradeEntrySubjectView> availableSubjects) {}

    public record GradeEntryCellUpsert(@NotNull UUID assessmentId, BigDecimal mark,
                                       String valueStatus, Long version) {}

    public record GradeEntryStudentUpsert(@NotNull UUID studentId,
                                          List<GradeEntryCellUpsert> values,
                                          String comment) {}

    public record GradeEntrySaveRequest(@NotNull UUID reportingPeriodId, @NotNull UUID classId,
                                        @NotBlank String subjectCode,
                                        @NotNull List<GradeEntryStudentUpsert> students,
                                        Long packetVersion) {}

    public record GradeEntryReviewRequest(@NotNull UUID reportingPeriodId, @NotNull UUID classId,
                                          @NotBlank String subjectCode, @NotBlank String action,
                                          String reason, Long packetVersion) {}

    public record AttendanceSummaryView(int finalizedSessions, int presentCount, int absentCount,
                                        int excusedCount, int lateCount, int lateMinutes,
                                        BigDecimal justifiedAbsenceHours, BigDecimal unjustifiedAbsenceHours,
                                        BigDecimal adjustedJustifiedHours, BigDecimal adjustedUnjustifiedHours,
                                        int adjustedLateMinutes) {}

    public record ConductSummaryView(boolean workWarning, boolean workBlame, boolean conductWarning,
                                     boolean conductBlame, boolean honorRoll, boolean encouragement,
                                     boolean congratulations, int exclusionDays, String decisionCode,
                                     String councilObservation, String status) {}

    public record AttendanceAdjustmentView(UUID id, BigDecimal justifiedAbsenceHours,
                                          BigDecimal unjustifiedAbsenceHours, int lateMinutes,
                                          String reason, String evidenceReference, String status,
                                          long version) {}

    public record ConductInputView(boolean workWarning, boolean workBlame, boolean conductWarning,
                                   boolean conductBlame, boolean honorRoll, boolean encouragement,
                                   boolean congratulations, int exclusionDays, String decisionCode,
                                   String councilObservation, String status, long version) {}

    public record ReportCardInputRow(UUID studentId, String studentName, String matricule,
                                     AttendanceSummaryView attendance,
                                     AttendanceAdjustmentView attendanceAdjustment,
                                     ConductInputView conduct) {}

    public record ReportCardInputsView(UUID academicSessionId, UUID reportingPeriodId,
                                       String reportingPeriodCode, String reportingPeriodLabel,
                                       UUID classId, String className,
                                       List<ReportCardInputRow> rows) {}

    public record ReportCardInputUpsert(@NotNull UUID reportingPeriodId, @NotNull UUID classId,
                                        @NotNull UUID studentId, BigDecimal justifiedAbsenceHours,
                                        BigDecimal unjustifiedAbsenceHours, Integer lateMinutes,
                                        String reason, String evidenceReference,
                                        boolean workWarning, boolean workBlame,
                                        boolean conductWarning, boolean conductBlame,
                                        boolean honorRoll, boolean encouragement,
                                        boolean congratulations, Integer exclusionDays,
                                        String decisionCode, String councilObservation,
                                        Long attendanceVersion, Long conductVersion) {}

    public record ReportCardInputReview(@NotNull UUID reportingPeriodId, @NotNull UUID classId,
                                       @NotBlank String action, String reason,
                                       Long attendanceVersion, Long conductVersion) {}
}
