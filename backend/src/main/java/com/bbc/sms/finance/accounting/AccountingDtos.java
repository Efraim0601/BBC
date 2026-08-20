package com.bbc.sms.finance.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** HTTP contracts for the first finance/accounting vertical slice. */
public final class AccountingDtos {
    private AccountingDtos() {}

    public record AccountView(UUID id, String code, String nameFr, String nameEn,
                              String accountType, String normalSide, String currency,
                              UUID parentId, boolean postingAllowed, boolean active,
                              LocalDate effectiveFrom, LocalDate effectiveTo,
                              long version, long postedUsageCount) {}

    public record AccountUpsert(
            @NotBlank String code,
            @NotBlank String nameFr,
            @NotBlank String nameEn,
            @NotBlank String accountType,
            @NotBlank String normalSide,
            String currency,
            UUID parentId,
            Boolean postingAllowed,
            Boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Long version) {}

    public record PostingRuleView(UUID id, String eventType, String side, String scopeCode,
                                  String feeTypeCode, String paymentChannelCode, String componentCode,
                                  UUID targetAccountId, String targetAccountCode,
                                  int priority, LocalDate effectiveFrom, LocalDate effectiveTo,
                                  boolean enabled, long version) {}

    public record PostingRuleUpsert(
            @NotBlank String eventType,
            @NotBlank String side,
            String scopeCode,
            String feeTypeCode,
            String paymentChannelCode,
            String componentCode,
            @NotNull UUID targetAccountId,
            int priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean enabled,
            Long version) {}

    public record PeriodView(UUID id, String code, String nameFr, String nameEn,
                             LocalDate startDate, LocalDate endDate, UUID academicSessionId,
                             String status, OffsetDateTime closedAt, UUID closedBy,
                             String closeReason, OffsetDateTime reopenedAt, UUID reopenedBy,
                             String reopenReason, long version) {}

    public record PeriodUpsert(
            @NotBlank String code,
            @NotBlank String nameFr,
            @NotBlank String nameEn,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            UUID academicSessionId,
            String status,
            Long version) {}

    public record GeneratePeriodsRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                         UUID academicSessionId, String prefix) {}

    public record PeriodActionRequest(long version, @NotBlank String reason) {}

    public record JournalLineInput(
            @NotNull UUID accountId,
            @PositiveOrZero long debitMinor,
            @PositiveOrZero long creditMinor,
            UUID studentId,
            UUID enrollmentId,
            UUID employeeId,
            UUID classId,
            String feeTypeCode,
            String description) {}

    public record JournalUpsert(
            @NotNull LocalDate entryDate,
            @NotBlank String description,
            String currency,
            @NotNull UUID accountingPeriodId,
            String sourceType,
            String sourceId,
            String sourceEventKey,
            @NotNull @Valid List<JournalLineInput> lines,
            Long version) {}

    public record JournalLineView(UUID id, int lineNumber, UUID accountId, String accountCode,
                                  String accountName, long debitMinor, long creditMinor,
                                  UUID studentId, UUID enrollmentId, UUID employeeId,
                                  UUID classId, String feeTypeCode, String description,
                                  long version) {}

    public record JournalView(UUID id, String number, LocalDate entryDate, String status,
                              String sourceType, String sourceId, String sourceEventKey,
                              String description, String currency, UUID accountingPeriodId,
                              UUID reversalOfId, UUID reversedBy, OffsetDateTime postedAt,
                              UUID postedBy, long version, long totalDebitMinor,
                              long totalCreditMinor, List<JournalLineView> lines) {}

    public record ReverseRequest(@NotNull LocalDate entryDate, @NotBlank String reason, long version) {}

    public record TrialBalanceRow(UUID accountId, String accountCode, String accountName,
                                  String accountType, String currency, long debitMinor,
                                  long creditMinor, long balanceMinor) {}

    public record TrialBalanceView(LocalDate asOfDate, String currency, List<TrialBalanceRow> rows,
                                   long totalDebitMinor, long totalCreditMinor, boolean balanced) {}

    public record GeneralLedgerLine(UUID journalId, String journalNumber, LocalDate entryDate,
                                    String status, String sourceType, String description,
                                    long debitMinor, long creditMinor, long runningBalanceMinor) {}

    public record GeneralLedgerView(UUID accountId, String accountCode, String accountName,
                                    LocalDate fromDate, LocalDate toDate,
                                    List<GeneralLedgerLine> lines, long totalDebitMinor,
                                    long totalCreditMinor) {}

    public record ReconciliationView(UUID id, String sourceType, String sourceId,
                                     long expectedAmount, long postedAmount, String currency,
                                     String state, String reason, OffsetDateTime resolvedAt,
                                     UUID resolvedBy, String resolutionNote, long version) {}

    public record ReconciliationResolveRequest(@NotBlank String state, @NotBlank String reason,
                                               long version) {}

    public record BlockerView(String entityType, String entityId, String label, String action) {}

    public record ReadinessCheck(String key, String label, String detail, String status,
                                 String action, List<BlockerView> blockers) {}

    public record ReadinessView(boolean ready, List<ReadinessCheck> checks,
                                OffsetDateTime generatedAt) {}

    public record ClosePreview(UUID periodId, String periodCode, int draftJournals,
                               int unreconciledItems, List<BlockerView> blockers,
                               boolean ready) {}

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
}
