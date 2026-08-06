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
                                      String targetClassName, boolean terminal, boolean active, long version) {}
    public record ProgressionPathUpsert(@NotNull UUID sourceSessionId, @NotNull UUID sourceClassId,
                                        @NotNull UUID targetSessionId, UUID targetClassId,
                                        boolean terminal, Long version) {}

    public record PromotionRuleView(UUID id, UUID academicSessionId, String subsystem, String level,
                                    BigDecimal promoteMin, BigDecimal reviewMin,
                                    boolean requireFinalAverage, boolean active, long version) {}
    public record PromotionRuleUpsert(@NotNull UUID academicSessionId, String subsystem, String level,
                                      @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal promoteMin,
                                      @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal reviewMin,
                                      boolean requireFinalAverage, Long version) {}

    public record PromotionPreviewRequest(@NotNull UUID sourceSessionId, @NotNull UUID targetSessionId,
                                          @NotBlank String name, List<UUID> sourceClassIds,
                                          String idempotencyKey) {}
    public record PromotionOverrideRequest(@NotBlank String finalDecision, UUID targetClassId,
                                           @NotBlank String reason, Long version) {}
    public record PromotionCommitRequest(@NotBlank String reason, Long version) {}

    public record PromotionCandidateView(UUID id, UUID studentId, String matricule, String studentName,
                                         UUID sourceEnrollmentId, UUID sourceClassId, String sourceClassName,
                                         UUID mappedTargetClassId, String mappedTargetClassName,
                                         UUID targetClassId, String targetClassName,
                                         BigDecimal finalAverage, String recommendation,
                                         String finalDecision, String overrideReason, String explanation,
                                         long version) {}
    public record PromotionBatchView(UUID id, String name, UUID sourceSessionId, String sourceSessionLabel,
                                     UUID targetSessionId, String targetSessionLabel, String status,
                                     int candidateCount, int promoteCount, int repeatCount,
                                     int graduateCount, int reviewCount, long version,
                                     Instant createdAt, Instant committedAt,
                                     List<PromotionCandidateView> candidates) {}
}
