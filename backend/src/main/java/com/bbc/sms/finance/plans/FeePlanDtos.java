package com.bbc.sms.finance.plans;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FeePlanDtos {
    private FeePlanDtos() {}

    public record PlanCreateRequest(
            @NotNull UUID academicSessionId,
            @NotBlank String scopeType,
            @NotBlank String level,
            @NotBlank String subsystem,
            UUID schoolClassId,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String currency) {}

    public record PlanUpdateRequest(
            @NotNull Long version,
            @NotBlank String level,
            @NotBlank String subsystem,
            UUID schoolClassId,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String currency) {}

    public record PlanLineRequest(
            @NotNull UUID feeTypeId,
            @NotNull UUID feeTypeRevisionId,
            @Min(0) long amountMinor,
            String currency,
            boolean mandatory,
            boolean refundable,
            @Min(0) int priority,
            @Positive int lineOrder,
            UUID installmentTemplateId,
            String prorationPolicy,
            Long version) {
        public PlanLineRequest(UUID feeTypeId, UUID feeTypeRevisionId, long amountMinor, String currency,
                               boolean mandatory, boolean refundable, int priority, int lineOrder,
                               UUID installmentTemplateId, Long version) {
            this(feeTypeId, feeTypeRevisionId, amountMinor, currency, mandatory, refundable, priority,
                    lineOrder, installmentTemplateId, "NONE", version);
        }
    }

    public record TemplateRequest(
            @NotBlank String code,
            @NotBlank String nameFr,
            @NotBlank String nameEn,
            UUID sourceSessionId,
            @Valid List<TemplateLineRequest> lines,
            Long version) {}

    public record TemplateLineRequest(
            @Positive int lineOrder,
            @NotBlank String labelFr,
            @NotBlank String labelEn,
            @NotBlank String allocationType,
            @Min(0) Long amountMinor,
            @Min(0) Integer percentageBasisPoints,
            @NotBlank String dueRuleType,
            LocalDate absoluteDueDate,
            Integer dueOffsetDays,
            UUID academicTermId) {}

    public record PlanLineView(UUID id, UUID feeTypeId, UUID feeTypeRevisionId, long amountMinor,
                               String currency, boolean mandatory, boolean refundable, int priority,
                               int lineOrder, UUID installmentTemplateId, String prorationPolicy,
                               long version) {}

    public record PlanView(UUID id, UUID academicSessionId, String scopeType, String level,
                           String subsystem, UUID schoolClassId, int planVersionNo, String lifecycle,
                           LocalDate effectiveFrom, LocalDate effectiveTo, String currency,
                           String inheritanceSource, String effectiveStatus, long version,
                           long totalMinor, long optionalLineCount, List<PlanLineView> lines) {}

    public record TemplateLineView(UUID id, int lineOrder, String labelFr, String labelEn,
                                   String allocationType, Long amountMinor, Integer percentageBasisPoints,
                                   String dueRuleType, LocalDate absoluteDueDate, Integer dueOffsetDays,
                                   UUID academicTermId, long version) {}

    public record TemplateView(UUID id, String code, String nameFr, String nameEn, String lifecycle,
                               UUID sourceSessionId, long version, List<TemplateLineView> lines) {}

    public record StudentContextView(UUID enrollmentId, UUID studentId, String matricule,
                                     String studentName, UUID academicSessionId, String sessionLabel,
                                     UUID schoolClassId, String className, String level,
                                     String subsystem, String enrollmentStatus) {}

    public record ActivationPreview(UUID planId, boolean canActivate, long affectedEnrollmentCount,
                                    long optionalFeeCount, List<String> missingMappings,
                                    List<String> duplicateCoverage, List<String> blockers,
                                    String chargeImpact) {}

    public record CopyPreviewRequest(@NotNull UUID sourcePlanId, @NotNull UUID targetSessionId,
                                     UUID targetClassId, String mergeMode) {}

    public record CopyApplyRequest(@NotNull UUID sourcePlanId, @NotNull UUID targetSessionId,
                                   UUID targetClassId, @NotBlank String mergeMode,
                                   @NotNull Long sourceVersion) {}

    public record CopyPreview(UUID sourcePlanId, UUID targetSessionId, UUID targetClassId,
                              String mergeMode, List<String> changedRevisions, List<String> missingClasses,
                              List<String> changedAmounts, List<String> existingTargetDrafts,
                              List<String> blockers, String dateShift) {}

    public record ResolutionView(UUID enrollmentId, UUID planId, String source, String blocker,
                                 PlanView plan) {}

    public record OverrideRequest(@NotNull UUID enrollmentId, @NotNull UUID feePlanLineId,
                                  @NotBlank String overrideType, Long amountMinor,
                                  Integer percentageBasisPoints, @NotBlank String reason,
                                  @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
                                  Long version) {}

    public record OverrideDecisionRequest(@NotNull Long version, boolean approve,
                                          String decisionReason) {}

    public record OverrideView(UUID id, UUID enrollmentId, UUID feePlanLineId, String overrideType,
                               Long amountMinor, Integer percentageBasisPoints, String reason,
                               String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                               long version) {}

    public record ImpactPreview(UUID enrollmentId, UUID feePlanLineId, long baseAmountMinor,
                                long adjustedAmountMinor, long deltaMinor, String explanation,
                                List<String> blockers) {}

    public record InstallmentPreviewLine(int lineOrder, String labelFr, String labelEn,
                                         long amountMinor, LocalDate dueDate, int finalAdjustmentMinor) {}

    public record InstallmentPreview(UUID planId, UUID feePlanLineId, long lineAmountMinor,
                                     long totalMinor, int finalAdjustmentMinor,
                                     List<InstallmentPreviewLine> lines, List<String> blockers) {}

    public record ElectionRequest(@NotBlank String status, String reason, Long version) {}

    public record PlanActionRequest(@NotNull Long version, String reason) {}

    public record ElectionView(UUID id, UUID enrollmentId, UUID feePlanLineId, String status,
                               String reason, long version) {}

    public record PlanContext(List<Map<String, Object>> sessions, List<Map<String, Object>> classes,
                              List<PlanView> plans) {}
}
