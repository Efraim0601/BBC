package com.bbc.sms.academic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;

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

    public record AssessmentDefaultsPreviewRequest(@NotNull UUID academicSessionId,
                                                   @NotNull UUID classId,
                                                   @NotBlank String mode,
                                                   UUID reportingPeriodId,
                                                   List<AssessmentDefaultsRowInput> rows,
                                                   String scopeFingerprint) {}

    public record AssessmentDefaultsRowInput(@NotBlank String clientRowId,
                                             @NotNull UUID reportingPeriodId,
                                             @NotBlank String subjectCode,
                                             String code, String label,
                                             BigDecimal maxScore, BigDecimal weight,
                                             Boolean mandatory) {}

    public record AssessmentDefaultsRow(String clientRowId, UUID reportingPeriodId,
                                        String reportingPeriodCode, String reportingPeriodLabel,
                                        UUID curriculumSubjectId, String subjectCode,
                                        String subjectLabel, int coefficient,
                                        BigDecimal maxScore, BigDecimal weight,
                                        boolean mandatory, UUID teacherId,
                                        String teacherName, String teacherStatus,
                                        String proposedCode, String proposedLabel,
                                        String status, List<String> errors,
                                        UUID existingAssessmentId, long existingVersion) {}

    public record AssessmentDefaultsPeriod(UUID reportingPeriodId, String code,
                                           String label, List<AssessmentDefaultsRow> rows) {}

    public record AssessmentDefaultsPreview(UUID academicSessionId, UUID classId,
                                            String className, String subsystem,
                                            String contentLanguage, String mode,
                                            String scopeFingerprint,
                                            List<AssessmentDefaultsPeriod> periods,
                                            int totalRows, int proposedRows,
                                            int existingRows, int excludedRows) {}

    public record AssessmentDefaultsApplyResponse(AssessmentDefaultsPreview preview,
                                                  UUID generationBatchId,
                                                  int createdCount, int existingCount,
                                                  int skippedCount) {}

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
                                       List<GroupStatsView> groupStats,
                                       SnapshotEvidenceView evidence,
                                       String reportingPeriodType, String product,
                                       BulletinWorkflowMetaView workflowMeta,
                                       List<BulletinIssueView> issues) {
        public BulletinSnapshotView {
            lines = lines == null ? List.of() : List.copyOf(lines);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            groupStats = groupStats == null ? List.of() : List.copyOf(groupStats);
            issues = issues == null ? List.of() : List.copyOf(issues);
            reportingPeriodType = reportingPeriodType == null ? "SEQUENCE" : reportingPeriodType;
            product = product == null ? productFor(reportingPeriodType) : product;
        }

        /** Source-compatible constructor used by historical tests and callers. */
        public BulletinSnapshotView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                    String reportingPeriodCode, String reportingPeriodLabel,
                                    UUID studentId, String studentName, String matricule,
                                    String educationalLevel, String subsystem, String className,
                                    List<BulletinLineView> lines, BigDecimal average, Integer rank, int classSize,
                                    String state, boolean complete, List<String> blockers,
                                    String snapshotHash, String calculationPolicy,
                                    String generalAppreciation, AttendanceSummaryView attendance,
                                    ConductSummaryView conduct, long version, ClassStatsView classStats,
                                    UUID supersedesId, UUID correctsBulletinVersionId, String correctionReason,
                                    UUID correctionRequestedBy, Instant correctionRequestedAt,
                                    List<GroupStatsView> groupStats, SnapshotEvidenceView evidence) {
            this(id, academicSessionId, reportingPeriodId, reportingPeriodCode, reportingPeriodLabel,
                    studentId, studentName, matricule, educationalLevel, subsystem, className, lines,
                    average, rank, classSize, state, complete, blockers, snapshotHash, calculationPolicy,
                    generalAppreciation, attendance, conduct, version, classStats, supersedesId,
                    correctsBulletinVersionId, correctionReason, correctionRequestedBy, correctionRequestedAt,
                    groupStats, evidence, null, null, null, null);
        }

        private static String productFor(String periodType) {
            return switch (periodType) {
                case "TERM_RESULT" -> "TERM";
                case "ANNUAL_RESULT" -> "ANNUAL";
                default -> "SEQUENCE";
            };
        }
    }

    public record BulletinWorkflowMetaView(
            String inputReadiness,
            String versionRelation,
            String currentSourceHash,
            UUID persistedVersionId,
            String persistedVersionState,
            Long persistedVersionNumber,
            String persistedSnapshotHash,
            BigDecimal persistedAverage,
            boolean refreshRequired,
            List<DependencyReadinessView> dependencies,
            BulletinCapabilitiesView capabilities) {
        public BulletinWorkflowMetaView {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            capabilities = capabilities == null
                    ? new BulletinCapabilitiesView(false, false, false, false, List.of())
                    : capabilities;
        }
    }

    public record DependencyReadinessView(
            UUID periodId, String code, String label, String periodType,
            BigDecimal weight, boolean optional, String readiness,
            int expectedPacketCount, int acceptedPacketCount, int lockedPacketCount,
            int submittedPacketCount, int draftPacketCount, int returnedPacketCount,
            int missingPacketCount) {}

    public record BulletinCapabilitiesView(
            boolean canCreateDraft, boolean canRefreshDraft,
            boolean canValidate, boolean canPublish, List<String> validationBlockers) {
        public BulletinCapabilitiesView {
            validationBlockers = validationBlockers == null ? List.of() : List.copyOf(validationBlockers);
        }
    }

    public record BulletinIssueView(
            String code, String severity, String periodCode, String subjectCode,
            String messageFr, String messageEn, String repairTarget) {}

    /** Immutable evidence references used to render and audit an official result. */
    public record SnapshotEvidenceView(
            ProfileAssetEvidenceView profilePhoto,
            DocumentDesignEvidenceView documentDesign,
            List<ChildSnapshotEvidenceView> childSnapshots,
            String formulaVersion,
            String calculationPolicy,
            List<DependencySourceEvidenceView> dependencySources,
            List<PacketTraceEvidenceView> packetTraces,
            String sourceHash) {
        public SnapshotEvidenceView {
            childSnapshots = childSnapshots == null ? List.of() : List.copyOf(childSnapshots);
            dependencySources = dependencySources == null ? List.of() : List.copyOf(dependencySources);
            packetTraces = packetTraces == null ? List.of() : List.copyOf(packetTraces);
        }

        public SnapshotEvidenceView(ProfileAssetEvidenceView profilePhoto,
                                    DocumentDesignEvidenceView documentDesign,
                                    List<ChildSnapshotEvidenceView> childSnapshots,
                                    String formulaVersion,
                                    String calculationPolicy) {
            this(profilePhoto, documentDesign, childSnapshots, formulaVersion, calculationPolicy,
                    List.of(), List.of(), null);
        }
    }

    public record ProfileAssetEvidenceView(UUID assetVersionId, String ownerType, UUID ownerId,
                                           String contentType, long byteSize,
                                           Instant capturedAt, String sha256) {}

    public record DocumentDesignEvidenceView(UUID templateId, String templateFamily,
                                             String product, String locale,
                                             int templateVersion, String templateHash,
                                             UUID brandingId, int brandingVersion,
                                             String brandingHash, String principalName,
                                             String principalTitle, String classMasterTitle,
                                             String councilTitle) {}

    public record ChildSnapshotEvidenceView(UUID reportingPeriodId, String periodCode,
                                            UUID snapshotId, long snapshotVersion,
                                            String state, String snapshotHash) {}

    public record DependencySourceEvidenceView(UUID childPeriodId, String childPeriodCode,
                                               long childPeriodVersion, BigDecimal dependencyWeight,
                                               boolean optional, String sourceKind, String sourceHash,
                                               List<PacketTraceEvidenceView> packetTraces) {
        public DependencySourceEvidenceView {
            packetTraces = packetTraces == null ? List.of() : List.copyOf(packetTraces);
        }
    }

    public record PacketTraceEvidenceView(UUID packetId, UUID classId,
                                          UUID childReportingPeriodId, String childReportingPeriodCode,
                                          String subjectCode, String status, long version,
                                          UUID teacherId, UUID responsibleAssignmentId,
                                          Long responsibleAssignmentVersion,
                                          Instant submittedAt, Instant reviewedAt) {}

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
    public record BulletinRefreshRequest(@NotBlank String reason, @NotNull Long version) {}

    public record BulletinBatchPreviewRequest(@NotNull UUID classId, @NotNull UUID reportingPeriodId,
                                               String locale) {}

    public record BulletinBatchJobCreateRequest(@NotNull UUID classId, @NotNull UUID reportingPeriodId,
                                                String locale, String scopeFingerprint,
                                                Boolean includeReadyStudentsWhenPartiallyBlocked) {
        public BulletinBatchJobCreateRequest(UUID classId, UUID reportingPeriodId, String locale) {
            this(classId, reportingPeriodId, locale, null, false);
        }
        public boolean includeReadyStudents() { return Boolean.TRUE.equals(includeReadyStudentsWhenPartiallyBlocked); }
    }
    public record BulletinBatchCancelRequest(@NotBlank String reason) {}

    public record BulletinBatchReasonCount(String code, int count) {}

    public record BulletinBatchRepairTarget(String route, Map<String, String> query) {
        public BulletinBatchRepairTarget {
            query = query == null ? Map.of() : Map.copyOf(query);
        }
    }

    public record BulletinBatchSnapshotEvidence(UUID id, long version, String hash,
                                                Instant publishedAt, String state) {}

    public record BulletinBatchWindowView(String state, boolean launchAllowed,
                                          String governingTrimesterCode, String governingTrimesterLabel,
                                          List<String> affectedMilestones, String timezone,
                                          Instant serverTime, Instant opensAt, Instant closesAt,
                                          Instant nextTransition, BulletinBatchRepairTarget repairTarget) {
        public BulletinBatchWindowView {
            affectedMilestones = affectedMilestones == null ? List.of() : List.copyOf(affectedMilestones);
        }
    }

    public record BulletinBatchPreviewView(String policy, UUID academicSessionId,
                                           String academicSessionLabel, UUID classId, String className,
                                           UUID reportingPeriodId, String reportingPeriodCode,
                                           String reportingPeriodLabel, int totalStudents, int readyStudents,
                                           int blockedStudents, List<BulletinBatchReasonCount> reasonCounts,
                                           List<Row> rows, String scopeFingerprint, Instant generatedAt,
                                           BulletinBatchWindowView window) {
        public BulletinBatchPreviewView {
            reasonCounts = reasonCounts == null ? List.of() : List.copyOf(reasonCounts);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
        public BulletinBatchPreviewView(String policy, UUID academicSessionId, String academicSessionLabel,
                                        UUID classId, String className, UUID reportingPeriodId,
                                        String reportingPeriodCode, String reportingPeriodLabel, int totalStudents,
                                        int readyStudents, int blockedStudents, List<BulletinBatchReasonCount> reasonCounts,
                                        List<Row> rows, String scopeFingerprint, Instant generatedAt) {
            this(policy, academicSessionId, academicSessionLabel, classId, className, reportingPeriodId,
                    reportingPeriodCode, reportingPeriodLabel, totalStudents, readyStudents, blockedStudents,
                    reasonCounts, rows, scopeFingerprint, generatedAt, null);
        }
        public record Row(UUID studentId, String studentName, String matricule, String eligibility,
                          String code, String category, String messageKey, Map<String, Object> messageArgs,
                          String currentState, boolean retryableNow,
                          BulletinBatchRepairTarget repairTarget,
                          BulletinBatchSnapshotEvidence snapshot) {
            public Row {
                messageArgs = messageArgs == null ? Map.of() : Map.copyOf(messageArgs);
            }
        }
    }

    public record BulletinBatchJobView(UUID id, UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                                       String locale, String status, int totalItems, int processedItems,
                                       int publishedItems, int blockedItems, int errorItems, int progressPercent,
                                       java.time.OffsetDateTime requestedAt, java.time.OffsetDateTime startedAt,
                                       java.time.OffsetDateTime completedAt, boolean archiveAvailable,
                                       String archiveSha256, Long archiveSizeBytes, String lastError, long version,
                                        String policy, String scopeFingerprint, String resultCategory,
                                        String headlineCode, Map<String, Object> headlineArgs,
                                        List<BulletinBatchReasonCount> reasonCounts,
                                        boolean studentArchiveAvailable, boolean diagnosticReportAvailable,
                                        int retryableErrorItems, int nowEligibleBlockedItems, int stillBlockedItems,
                                        String diagnosticSha256, Long diagnosticSizeBytes,
                                        BulletinBatchWindowView window) {
        public BulletinBatchJobView {
            headlineArgs = headlineArgs == null ? Map.of() : Map.copyOf(headlineArgs);
            reasonCounts = reasonCounts == null ? List.of() : List.copyOf(reasonCounts);
        }
        public BulletinBatchJobView(UUID id, UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                                    String locale, String status, int totalItems, int processedItems,
                                    int publishedItems, int blockedItems, int errorItems, int progressPercent,
                                    java.time.OffsetDateTime requestedAt, java.time.OffsetDateTime startedAt,
                                    java.time.OffsetDateTime completedAt, boolean archiveAvailable,
                                    String archiveSha256, Long archiveSizeBytes, String lastError, long version) {
            this(id, academicSessionId, reportingPeriodId, classId, locale, status, totalItems, processedItems,
                    publishedItems, blockedItems, errorItems, progressPercent, requestedAt, startedAt, completedAt,
                    archiveAvailable, archiveSha256, archiveSizeBytes, lastError, version, "PUBLISHED_ONLY", null,
                     null, null, Map.of(), List.of(), archiveAvailable, false, 0, 0, 0, null, null, null);
        }
    }

    public record BulletinBatchItemView(UUID id, UUID studentId, String studentName, String status,
                                        int attempts, String fileName, long sizeBytes, String error,
                                        String resultCode, String category, String messageKey,
                                        Map<String, Object> messageArgs, String currentState,
                                        boolean retryableNow, BulletinBatchRepairTarget repairTarget,
                                        BulletinBatchSnapshotEvidence snapshot, String correlationId,
                                        String technicalDetail) {
        public BulletinBatchItemView {
            messageArgs = messageArgs == null ? Map.of() : Map.copyOf(messageArgs);
        }
        public BulletinBatchItemView(UUID id, UUID studentId, String studentName, String status,
                                     int attempts, String fileName, long sizeBytes, String error) {
            this(id, studentId, studentName, status, attempts, fileName, sizeBytes, error,
                    null, null, null, Map.of(), null, false, null, null, null, null);
        }
    }

    public record GradeEntryAssessmentView(UUID id, String code, String label,
                                           BigDecimal maxScore, BigDecimal weight,
                                           boolean mandatory, int displayOrder) {}

    public record TeacherAssignmentReadinessView(String status, String code,
                                                 UUID teacherId, String teacherName, String teacherCode,
                                                 UUID assignmentId, long assignmentVersion,
                                                 String source, String role,
                                                 LocalDate effectiveFrom, LocalDate effectiveTo,
                                                 String messageFr, String messageEn, boolean repairable) {}

    public record GradeEntryBlockerView(String code, String subjectCode, String studentName,
                                        String messageFr, String messageEn,
                                        String repairTarget, String severity) {}

    public record GradeEntryCapabilitiesView(boolean canEditDraft, boolean canSubmit,
                                             boolean canReview, boolean restrictedTeacher,
                                             String explanation) {}

    public record GradeEntrySubjectView(String code, String label, int coefficient,
                                        UUID teacherId, String teacherName,
                                        String status, String errorCode, String message,
                                        boolean remarkRequired,
                                        TeacherAssignmentReadinessView assignmentReadiness) {
        public GradeEntrySubjectView(String code, String label, int coefficient,
                                     UUID teacherId, String teacherName) {
            this(code, label, coefficient, teacherId, teacherName,
                    teacherId == null ? "MISSING" : "RESOLVED",
                    teacherId == null ? "ASSIGNMENT_MISSING" : "ASSIGNMENT_RESOLVED", null, false,
                    teacherId == null
                            ? new TeacherAssignmentReadinessView("MISSING", "ASSIGNMENT_MISSING", null, null, null,
                            null, 0, null, null, null, null, null, null, true)
                            : new TeacherAssignmentReadinessView("RESOLVED", "ASSIGNMENT_RESOLVED", teacherId, teacherName,
                            null, null, 0, null, null, null, null, null, null, false));
        }

        public GradeEntrySubjectView(String code, String label, int coefficient,
                                     UUID teacherId, String teacherName, String status,
                                     String errorCode, String message, boolean remarkRequired) {
            this(code, label, coefficient, teacherId, teacherName, status, errorCode, message,
                    remarkRequired, new TeacherAssignmentReadinessView(status, errorCode, teacherId, teacherName,
                            null, null, 0, null, null, null, null, message, message, teacherId == null));
        }
    }

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
                                 List<GradeEntrySubjectView> availableSubjects,
                                 List<GradeEntryBlockerView> completionBlockers,
                                 List<GradeEntryBlockerView> submissionBlockers,
                                 List<GradeEntryBlockerView> warnings,
                                 TeacherAssignmentReadinessView assignmentReadiness,
                                 GradeEntryCapabilitiesView capabilities) {
        public GradeEntryView(UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                              String className, String subjectCode, String subjectLabel,
                              int coefficient, UUID teacherId, String teacherName,
                              String packetStatus, long packetVersion,
                              List<GradeEntryAssessmentView> assessments,
                              List<GradeEntryStudentView> students,
                              int totalStudents, int completedStudents,
                              List<String> blockers,
                              List<GradeEntrySubjectView> availableSubjects) {
            this(academicSessionId, reportingPeriodId, classId, className, subjectCode, subjectLabel,
                    coefficient, teacherId, teacherName, packetStatus, packetVersion, assessments, students,
                    totalStudents, completedStudents, blockers, availableSubjects,
                    blockers.stream().map(message -> new GradeEntryBlockerView(
                            "GRADE_ENTRY_INCOMPLETE", subjectCode, null, message, message,
                            "grade-entry", "BLOCKER")).toList(),
                    teacherId == null ? List.of(new GradeEntryBlockerView(
                            "ASSIGNMENT_MISSING", subjectCode, null,
                            "A responsible teacher assignment is required before submission.",
                            "A responsible teacher assignment is required before submission.",
                            "class-subjects", "BLOCKER")) : List.of(), List.of(),
                    availableSubjects.stream().filter(x -> subjectCode.equalsIgnoreCase(x.code()))
                            .findFirst().map(GradeEntrySubjectView::assignmentReadiness).orElse(null),
                    new GradeEntryCapabilitiesView(true, teacherId != null && blockers.isEmpty(), false,
                            false, null));
        }
    }

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
