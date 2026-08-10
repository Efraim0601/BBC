package com.bbc.sms.foundation.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SessionDtos {
    private SessionDtos() {}

    public record TermView(UUID id, String code, String label, int sequenceNo,
                           LocalDate startDate, LocalDate endDate,
                           Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                           Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                           Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                           String timezone, long version) {
        public TermView(UUID id, String code, String label, int sequenceNo,
                        LocalDate startDate, LocalDate endDate,
                        Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                        Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                        long version) {
            this(id, code, label, sequenceNo, startDate, endDate, gradeEntryOpensAt, gradeEntryClosesAt,
                    bulletinPublishOpensAt, bulletinPublishClosesAt, null, null, "Africa/Douala", version);
        }
    }

    public record SessionView(UUID id, String code, String label, LocalDate startDate,
                              LocalDate endDate, String status, boolean current,
                              Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                              Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                              Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                              String timezone, long version, List<TermView> terms) {
        public SessionView(UUID id, String code, String label, LocalDate startDate,
                           LocalDate endDate, String status, boolean current,
                           Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                           Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                           long version, List<TermView> terms) {
            this(id, code, label, startDate, endDate, status, current, gradeEntryOpensAt, gradeEntryClosesAt,
                    bulletinPublishOpensAt, bulletinPublishClosesAt, null, null, "Africa/Douala", version, terms);
        }
    }

    public record SessionUpsert(@NotBlank String code, @NotBlank String label,
                                @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                String status, Boolean current,
                                Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                                String timezone, Long version) {
        public SessionUpsert(String code, String label, LocalDate startDate, LocalDate endDate,
                             String status, Boolean current, Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                             Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt, Long version) {
            this(code, label, startDate, endDate, status, current, gradeEntryOpensAt, gradeEntryClosesAt,
                    bulletinPublishOpensAt, bulletinPublishClosesAt, null, null, "Africa/Douala", version);
        }
    }

    public record TermUpsert(@NotBlank String code, @NotBlank String label,
                             int sequenceNo, @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                             Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                             Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                             String timezone, Long version) {
        public TermUpsert(String code, String label, int sequenceNo, LocalDate startDate,
                          LocalDate endDate, Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                          Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt, Long version) {
            this(code, label, sequenceNo, startDate, endDate, gradeEntryOpensAt, gradeEntryClosesAt,
                    bulletinPublishOpensAt, bulletinPublishClosesAt, null, null, "Africa/Douala", version);
        }
    }

    public record SessionStateRequest(@NotBlank String status, String reason, Long version) {}

    public record ReportingPeriodView(UUID id, UUID academicSessionId, UUID academicTermId,
                                      String code, String label, String periodType, int displayOrder,
                                      LocalDate startDate, LocalDate endDate,
                                      Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                      Instant reviewOpensAt, Instant reviewClosesAt,
                                      Instant validationOpensAt, Instant validationClosesAt,
                                      Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                      Instant correctionOpensAt, Instant correctionClosesAt,
                                      Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                                      String timezone, String calculationPolicy, String status, long version) {
        public ReportingPeriodView(UUID id, UUID academicSessionId, UUID academicTermId,
                                   String code, String label, String periodType, int displayOrder,
                                   LocalDate startDate, LocalDate endDate,
                                   Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                   Instant reviewOpensAt, Instant reviewClosesAt,
                                   Instant validationOpensAt, Instant validationClosesAt,
                                   Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                   Instant correctionOpensAt, Instant correctionClosesAt,
                                   String calculationPolicy, String status, long version) {
            this(id, academicSessionId, academicTermId, code, label, periodType, displayOrder, startDate, endDate,
                    gradeEntryOpensAt, gradeEntryClosesAt, reviewOpensAt, reviewClosesAt,
                    validationOpensAt, validationClosesAt, bulletinPublishOpensAt, bulletinPublishClosesAt,
                    correctionOpensAt, correctionClosesAt, null, null, "Africa/Douala", calculationPolicy, status, version);
        }
    }

    public record ReportingPeriodUpsert(@NotBlank String code, @NotBlank String label,
                                        @NotBlank String periodType, UUID academicTermId,
                                        int displayOrder, @NotNull LocalDate startDate,
                                        @NotNull LocalDate endDate,
                                        Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                        Instant reviewOpensAt, Instant reviewClosesAt,
                                        Instant validationOpensAt, Instant validationClosesAt,
                                        Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                        Instant correctionOpensAt, Instant correctionClosesAt,
                                        Instant teacherSubmissionOpensAt, Instant teacherSubmissionClosesAt,
                                        String timezone, String calculationPolicy, String status, Long version) {
        public ReportingPeriodUpsert(String code, String label, String periodType, UUID academicTermId,
                                     int displayOrder, LocalDate startDate, LocalDate endDate,
                                     Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                     Instant reviewOpensAt, Instant reviewClosesAt,
                                     Instant validationOpensAt, Instant validationClosesAt,
                                     Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                     Instant correctionOpensAt, Instant correctionClosesAt,
                                     String calculationPolicy, String status, Long version) {
            this(code, label, periodType, academicTermId, displayOrder, startDate, endDate,
                    gradeEntryOpensAt, gradeEntryClosesAt, reviewOpensAt, reviewClosesAt,
                    validationOpensAt, validationClosesAt, bulletinPublishOpensAt, bulletinPublishClosesAt,
                    correctionOpensAt, correctionClosesAt, null, null, "Africa/Douala", calculationPolicy, status, version);
        }
    }

    public record StructureDependencyView(UUID parentPeriodId, String parentCode,
                                          UUID childPeriodId, String childCode,
                                          java.math.BigDecimal weight, boolean optional,
                                          int displayOrder) {}

    public record SessionReadinessView(UUID academicSessionId, String sessionStatus,
                                       String phase, boolean ready, String nextAction,
                                       List<String> blockers, List<String> actions,
                                       List<ReadinessSectionView> sections) {
        public SessionReadinessView(UUID academicSessionId, String sessionStatus,
                                    String phase, boolean ready, String nextAction,
                                    List<String> blockers, List<String> actions) {
            this(academicSessionId, sessionStatus, phase, ready, nextAction, blockers, actions, List.of());
        }
    }

    public record ReadinessIssueView(String code, String severity, String label,
                                     String detail, String repairTarget, int count) {}

    public record ReadinessSectionView(String key, String label, String status, boolean ready,
                                       List<ReadinessIssueView> issues) {}

    public record WorkflowWindowRuleView(UUID id, UUID academicSessionId, String scopeType,
                                         UUID academicTermId, UUID reportingPeriodId,
                                         String action, String mode, Instant opensAt,
                                         Instant closesAt, String timezone, long version,
                                         String effectiveMode, String inheritedFrom) {}

    public record WorkflowWindowRuleUpsert(@NotBlank String scopeType, UUID academicTermId,
                                           UUID reportingPeriodId, @NotBlank String action,
                                           @NotBlank String mode, Instant opensAt,
                                           Instant closesAt, String timezone, Long version) {}

    public record CopyScopeSelection(boolean terms, boolean reportingPeriods,
                                     boolean dependencies, boolean workflowWindows) {
        public static CopyScopeSelection all() { return new CopyScopeSelection(true, true, true, true); }
    }

    public record ConfigurationCopyEdit(@NotBlank String key, @NotBlank String field,
                                        String value) {}

    public record ConfigurationCopyPreviewRequest(@NotNull UUID sourceSessionId,
                                                   String dateStrategy, String mergeMode,
                                                   CopyScopeSelection scopes,
                                                   List<ConfigurationCopyEdit> edits,
                                                   List<String> selectedKeys) {
        public ConfigurationCopyPreviewRequest(UUID sourceSessionId, String dateStrategy, String mergeMode,
                                               CopyScopeSelection scopes, List<ConfigurationCopyEdit> edits) {
            this(sourceSessionId, dateStrategy, mergeMode, scopes, edits, List.of());
        }
    }

    public record ConfigurationCopyRow(String key, String kind, String code, String label,
                                       String status, Map<String, Object> source,
                                       Map<String, Object> proposed, Map<String, Object> existing,
                                       List<String> warnings, List<String> blockers) {}

    public record ConfigurationCopyPreview(UUID sourceSessionId, UUID targetSessionId,
                                           String sourceLabel, String targetLabel,
                                           String dateStrategy, String mergeMode,
                                           CopyScopeSelection scopes,
                                           List<ConfigurationCopyRow> terms,
                                           List<ConfigurationCopyRow> reportingPeriods,
                                           List<ConfigurationCopyRow> dependencies,
                                           List<ConfigurationCopyRow> workflowWindows,
                                           List<String> warnings, List<String> blockers,
                                           String fingerprint, int createCount,
                                           int updateCount, int keepCount) {}

    public record ConfigurationCopyApplyRequest(@NotNull UUID sourceSessionId,
                                                String dateStrategy, String mergeMode,
                                                CopyScopeSelection scopes,
                                                List<ConfigurationCopyEdit> edits,
                                                List<String> selectedKeys,
                                                @NotBlank String reason,
                                                @NotBlank String previewFingerprint) {
        public ConfigurationCopyApplyRequest(UUID sourceSessionId, String dateStrategy, String mergeMode,
                                             CopyScopeSelection scopes, List<ConfigurationCopyEdit> edits,
                                             String reason, String previewFingerprint) {
            this(sourceSessionId, dateStrategy, mergeMode, scopes, edits, List.of(), reason, previewFingerprint);
        }
    }

    public record StandardStructureView(UUID academicSessionId, List<ReportingPeriodView> periods,
                                        List<String> warnings, boolean applied,
                                        String fingerprint, List<StructureDependencyView> dependencies) {
        public StandardStructureView(UUID academicSessionId, List<ReportingPeriodView> periods,
                                     List<String> warnings, boolean applied) {
            this(academicSessionId, periods, warnings, applied, null, List.of());
        }
    }

    public record StandardStructureApplyRequest(@NotBlank String reason, String fingerprint,
                                                List<ReportingPeriodView> periods,
                                                List<StructureDependencyView> dependencies) {}

    public record WindowOverrideUpsert(@NotBlank String action, @NotBlank String scope,
                                       @NotBlank String reason, @NotNull Instant opensAt,
                                       @NotNull Instant expiresAt, UUID reportingPeriodId) {}

    public record WindowOverrideView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                                     String action, String scope, String reason,
                                     Instant opensAt, Instant expiresAt, UUID createdBy,
                                     Instant createdAt, long version, boolean active) {}
}
