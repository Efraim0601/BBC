package com.bbc.sms.finance.charges;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HTTP contracts for the BAY-46 charge and debt read/write boundary. */
public final class ChargeDtos {
    private ChargeDtos() {}

    public record GenerationRequest(
            @NotNull UUID academicSessionId,
            UUID schoolClassId,
            String level,
            String subsystem,
            @NotNull LocalDate chargeDate,
            String prorationPolicy,
            String transferPolicy) {}

    public record BlockerView(String entityType, String entityId, String code,
                              String message, String actionLink) {}

    public record PreviewRow(UUID enrollmentId, UUID studentId, String studentName,
                             String matricule, UUID planId, int planVersionNo,
                             String scopeType, String className, UUID planLineId,
                             String feeTypeCode, String feeTypeName,
                             long originalAmountMinor, long adjustedAmountMinor,
                             int installmentCount, boolean optional,
                             String optionalDecision, boolean transfer,
                             String prorationPolicy, String prorationFormula,
                             String resultStatus, String blockerCode,
                             String blockerMessage, String actionLink) {}

    public record GenerationPreview(UUID academicSessionId, UUID schoolClassId,
                                    String level, String subsystem, LocalDate chargeDate,
                                    String prorationPolicy, String transferPolicy,
                                    int enrollmentCount, int coveredEnrollmentCount,
                                    int uncoveredEnrollmentCount, int candidateLineCount,
                                    int installmentCount, int optionalPendingCount,
                                    int transferCount, int alreadyGeneratedCount,
                                    long estimatedTotalMinor, String currency,
                                    List<PreviewRow> rows, List<BlockerView> blockers) {}

    public record GenerationJobView(UUID id, UUID academicSessionId, UUID schoolClassId,
                                    String level, String subsystem, LocalDate chargeDate,
                                    String prorationPolicy, String transferPolicy,
                                    String status, int enrollmentCount, int generatedCount,
                                    int alreadyExistsCount, int blockedCount, int failedCount,
                                    long totalAmountMinor, String currency,
                                    String lastError, long version) {}

    public record GenerationResultView(UUID id, UUID jobId, UUID enrollmentId, UUID studentId,
                                       UUID feePlanId, UUID feePlanLineId, UUID chargeId,
                                       UUID schoolClassId, String classNameSnapshot,
                                       String resultStatus, long amountMinor, String currency,
                                       String blockerCode, String blockerMessage,
                                       String actionLink, String errorDetail) {}

    public record ChargeInstallmentView(UUID id, int installmentNo, String labelFr,
                                        String labelEn, LocalDate dueDate, long amountMinor,
                                        long paidMinor, long waivedMinor, long outstandingMinor,
                                        String status, long version) {}

    public record AdjustmentView(UUID id, UUID chargeId, UUID installmentId,
                                 String adjustmentType, long amountMinor, String currency,
                                 String reason, String evidenceReference, UUID contraAccountId,
                                 LocalDate effectiveDate, String status, UUID requestedBy,
                                 UUID approvedBy, String decisionReason, UUID journalEntryId,
                                 long version) {}

    public record ChargeView(UUID id, UUID studentEnrollmentId, UUID studentId,
                             UUID academicSessionId, UUID feePlanId, UUID feePlanLineId,
                             UUID feeTypeId, UUID feeTypeRevisionId, int feePlanVersionNo,
                             String feeTypeCode, String feeTypeNameFr, String feeTypeNameEn,
                             String feeTypeCategory, String scopeType, String levelSnapshot,
                             String subsystemSnapshot, UUID schoolClassIdSnapshot,
                             String classNameSnapshot, long originalAmountMinor,
                             long adjustedAmountMinor, long paidMinor, long waivedMinor,
                             long outstandingMinor, String currency, LocalDate chargeDate,
                             String prorationPolicy, String prorationFormula,
                             UUID transferFromEnrollmentId, String transferPolicy, String status,
                             UUID journalEntryId, long version,
                             List<ChargeInstallmentView> installments,
                             List<AdjustmentView> adjustments) {}

    public record ChargeListFilters(String status, UUID academicSessionId, UUID schoolClassId,
                                    UUID studentId, String feeTypeCode, LocalDate dueFrom,
                                    LocalDate dueTo, Long minAmountMinor, Long maxAmountMinor,
                                    String query) {}

    public record AdjustmentRequest(
            @NotBlank String adjustmentType,
            @Positive long amountMinor,
            UUID installmentId,
            @NotBlank String reason,
            String evidenceReference,
            @NotNull UUID contraAccountId,
            @NotNull LocalDate effectiveDate,
            Long version) {}

    public record AdjustmentDecisionRequest(@NotNull Long version,
                                            boolean approve,
                                            @NotBlank String decisionReason) {}

    public record AdjustmentImpact(UUID chargeId, UUID installmentId,
                                   long currentOutstandingMinor, long requestedAmountMinor,
                                   long projectedOutstandingMinor, boolean allowed,
                                   List<String> blockers) {}

    public record StudentAccountView(UUID studentId, String studentName, String matricule,
                                     UUID enrollmentId, String className, String level,
                                     String subsystem, UUID academicSessionId,
                                     long chargedMinor, long paidMinor, long waivedMinor,
                                     long outstandingMinor, long currentMinor, long days1To30Minor,
                                     long days31To60Minor, long days61To90Minor, long over90Minor,
                                     List<LedgerEntryView> ledger,
                                     List<String> placeholders) {}

    public record LedgerEntryView(String entryType, UUID chargeId, UUID installmentId,
                                  UUID adjustmentId, LocalDate entryDate, String label,
                                  long debitMinor, long creditMinor, long runningBalanceMinor,
                                  String status) {}

    public record AgeingRow(UUID studentId, String studentName, String matricule,
                            UUID enrollmentId, String className, long currentMinor,
                            long days1To30Minor, long days31To60Minor,
                            long days61To90Minor, long over90Minor, long outstandingMinor) {}

    public record AgeingView(LocalDate asOfDate, String currency, long currentMinor,
                             long days1To30Minor, long days31To60Minor, long days61To90Minor,
                             long over90Minor, List<AgeingRow> rows) {}

    public record ContextView(List<SessionOption> sessions, List<ClassOption> classes) {}
    public record SessionOption(UUID id, String code, String label, LocalDate startDate,
                                LocalDate endDate, String status) {}
    public record ClassOption(UUID id, String code, String name, String level, String subsystem) {}
}
