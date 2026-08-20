package com.bbc.sms.journey.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JourneyPromotionDtos {
    private JourneyPromotionDtos() {}

    public record ProgressionPathView(UUID id, UUID sourceSessionId, UUID sourceClassId,
                                      String sourceClassName, UUID targetSessionId, UUID targetClassId,
                                      String targetClassName, boolean terminal, boolean active, long version,
                                      UUID graphVersionId, int graphVersionNo, String graphStatus,
                                      String edgeType, int displayOrder, boolean allowSkip, String skipReason) {
        public ProgressionPathView(UUID id, UUID sourceSessionId, UUID sourceClassId,
                                   String sourceClassName, UUID targetSessionId, UUID targetClassId,
                                   String targetClassName, boolean terminal, boolean active, long version) {
            this(id, sourceSessionId, sourceClassId, sourceClassName, targetSessionId, targetClassId,
                    targetClassName, terminal, active, version, null, 1, "DRAFT", "DEFAULT", 1, false, null);
        }
    }
    public record ProgressionPathUpsert(@NotNull UUID sourceSessionId, @NotNull UUID sourceClassId,
                                        @NotNull UUID targetSessionId, UUID targetClassId,
                                        boolean terminal, Long version, String edgeType,
                                        Integer displayOrder, Boolean allowSkip, String skipReason,
                                        UUID graphVersionId) {
        public ProgressionPathUpsert(UUID sourceSessionId, UUID sourceClassId, UUID targetSessionId,
                                     UUID targetClassId, boolean terminal, Long version) {
            this(sourceSessionId, sourceClassId, targetSessionId, targetClassId, terminal, version,
                    "DEFAULT", 1, false, null, null);
        }
    }

    public record PromotionRuleView(UUID id, UUID academicSessionId, String subsystem, String level,
                                    BigDecimal promoteMin, BigDecimal reviewMin,
                                    boolean requireFinalAverage, boolean active, long version,
                                    UUID ruleSetId, int ruleSetVersion, String ruleSetStatus) {
        public PromotionRuleView(UUID id, UUID academicSessionId, String subsystem, String level,
                                 BigDecimal promoteMin, BigDecimal reviewMin,
                                 boolean requireFinalAverage, boolean active, long version) {
            this(id, academicSessionId, subsystem, level, promoteMin, reviewMin,
                    requireFinalAverage, active, version, null, 0, "DRAFT");
        }
    }
    public record PromotionRuleUpsert(@NotNull UUID academicSessionId, String subsystem, String level,
                                      @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal promoteMin,
                                      @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal reviewMin,
                                      boolean requireFinalAverage, Long version) {}

    public record PromotionPreviewRequest(@NotNull UUID sourceSessionId, @NotNull UUID targetSessionId,
                                          @NotBlank String name, List<UUID> sourceClassIds,
                                          String idempotencyKey, String previewFingerprint,
                                          UUID graphVersionId, UUID ruleSetId) {
        public PromotionPreviewRequest(UUID sourceSessionId, UUID targetSessionId, String name,
                                       List<UUID> sourceClassIds, String idempotencyKey) {
            this(sourceSessionId, targetSessionId, name, sourceClassIds, idempotencyKey, null, null, null);
        }
        public PromotionPreviewRequest(UUID sourceSessionId, UUID targetSessionId, String name,
                                       List<UUID> sourceClassIds, String idempotencyKey,
                                       String previewFingerprint) {
            this(sourceSessionId, targetSessionId, name, sourceClassIds, idempotencyKey,
                    previewFingerprint, null, null);
        }
    }
    public record PromotionPreviewView(String previewToken, String name, UUID sourceSessionId,
                                       String sourceSessionLabel, UUID targetSessionId,
                                       String targetSessionLabel, String fingerprint,
                                       int candidateCount, List<PromotionCandidateView> candidates,
                                       UUID graphVersionId, int graphVersionNo,
                                       UUID ruleSetId, int ruleSetVersion) {
        public PromotionPreviewView(String previewToken, String name, UUID sourceSessionId,
                                    String sourceSessionLabel, UUID targetSessionId,
                                    String targetSessionLabel, String fingerprint,
                                    int candidateCount, List<PromotionCandidateView> candidates) {
            this(previewToken, name, sourceSessionId, sourceSessionLabel, targetSessionId,
                    targetSessionLabel, fingerprint, candidateCount, candidates, null, 0, null, 0);
        }
    }
    public record PromotionActivationRequest(@NotBlank String reason) {}
    public record PromotionActivationView(UUID enrollmentId, UUID sourceEnrollmentId,
                                          UUID studentId, String status, String className) {}
    public record PromotionOverrideRequest(@NotBlank String finalDecision, UUID targetClassId,
                                           @NotBlank String reason, Long version) {}
    public record PromotionCommitRequest(@NotBlank String reason, Long version) {}

    public record PromotionCandidateView(UUID id, UUID studentId, String matricule, String studentName,
                                         UUID sourceEnrollmentId, UUID sourceClassId, String sourceClassName,
                                         UUID mappedTargetClassId, String mappedTargetClassName,
                                         UUID targetClassId, String targetClassName,
                                         BigDecimal finalAverage, String recommendation,
                                         String finalDecision, String overrideReason, String explanation,
                                         long version, UUID annualBulletinId,
                                         BigDecimal annualAverage, Integer annualRank,
                                         String annualDecision, boolean councilApproved,
                                         List<PromotionTargetOption> allowedTargets,
                                         List<String> blockers) {
        public PromotionCandidateView(UUID id, UUID studentId, String matricule, String studentName,
                                      UUID sourceEnrollmentId, UUID sourceClassId, String sourceClassName,
                                      UUID mappedTargetClassId, String mappedTargetClassName,
                                      UUID targetClassId, String targetClassName,
                                      BigDecimal finalAverage, String recommendation,
                                      String finalDecision, String overrideReason, String explanation,
                                      long version) {
            this(id, studentId, matricule, studentName, sourceEnrollmentId, sourceClassId,
                    sourceClassName, mappedTargetClassId, mappedTargetClassName, targetClassId,
                    targetClassName, finalAverage, recommendation, finalDecision, overrideReason,
                    explanation, version, null, finalAverage, null, null, false, List.of(), List.of());
        }
    }
    public record PromotionTargetOption(UUID classId, String className, String edgeType,
                                        boolean terminal, boolean allowSkip) {}
    public record PromotionBatchView(UUID id, String name, UUID sourceSessionId, String sourceSessionLabel,
                                     UUID targetSessionId, String targetSessionLabel, String status,
                                     int candidateCount, int promoteCount, int repeatCount,
                                     int graduateCount, int reviewCount, long version,
                                     Instant createdAt, Instant committedAt,
                                     List<PromotionCandidateView> candidates,
                                     UUID graphVersionId, int graphVersionNo,
                                     UUID ruleSetId, int ruleSetVersion,
                                     String previewFingerprint) {
        public PromotionBatchView(UUID id, String name, UUID sourceSessionId, String sourceSessionLabel,
                                  UUID targetSessionId, String targetSessionLabel, String status,
                                  int candidateCount, int promoteCount, int repeatCount,
                                  int graduateCount, int reviewCount, long version,
                                  Instant createdAt, Instant committedAt,
                                  List<PromotionCandidateView> candidates) {
            this(id, name, sourceSessionId, sourceSessionLabel, targetSessionId, targetSessionLabel,
                    status, candidateCount, promoteCount, repeatCount, graduateCount, reviewCount,
                    version, createdAt, committedAt, candidates, null, 0, null, 0, null);
        }
    }

    public record ProgressionGraphView(UUID id, UUID sourceSessionId, String sourceSessionLabel,
                                       UUID targetSessionId, String targetSessionLabel, int versionNo,
                                       String status, UUID copiedFromId, Instant publishedAt,
                                       long version, int edgeCount, List<String> blockers) {}
    public record ProgressionGraphCopyRequest(@NotNull UUID sourceSessionId, @NotNull UUID targetSessionId,
                                              UUID fromGraphVersionId) {}
    public record ProgressionGraphPreviewView(ProgressionGraphView source,
                                              ProgressionGraphView proposed,
                                              List<ProgressionPathView> added,
                                              List<ProgressionPathView> removed,
                                              List<ProgressionPathView> changed) {}
    public record PromotionRuleSetView(UUID id, UUID academicSessionId, int versionNo,
                                       String status, String conditions, Instant publishedAt,
                                       long version, List<PromotionRuleView> rules) {}
    public record PromotionRuleSetUpsert(@NotNull UUID academicSessionId,
                                         @NotBlank String conditions, Long version) {}
    public record PromotionBatchListItem(UUID id, String name, UUID sourceSessionId, String sourceSessionLabel,
                                         UUID targetSessionId, String targetSessionLabel, String status,
                                         int candidateCount, int blockedCount, Instant createdAt,
                                         Instant committedAt, long version) {}
    public record PromotionCancelRequest(@NotBlank String reason) {}
    public record PromotionDecisionHistoryView(UUID id, UUID decisionId, String fromDecision,
                                               String toDecision, UUID targetClassId, String reason,
                                               UUID actorUserId, Instant createdAt) {}
    public record PromotionCommitPreviewView(UUID batchId, String status, int candidateCount,
                                             int promoteCount, int repeatCount, int graduateCount,
                                             int reviewCount, List<String> blockers,
                                             UUID graphVersionId, int graphVersionNo,
                                             UUID ruleSetId, int ruleSetVersion) {}
    public record PromotionRegisterView(UUID id, UUID batchId, String sha256,
                                        Instant createdAt, String manifest) {}
}
