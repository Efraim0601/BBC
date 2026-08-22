package com.bbc.sms.finance.payroll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PayrollDtos {
    private PayrollDtos() {}

    public record BlockerView(String code, String message, String actionLink) {}

    public record ComponentView(UUID id, String code, String nameFr, String nameEn,
                                String componentKind, String calculationMode,
                                long defaultAmountMinor, int defaultRateBps,
                                UUID expenseAccountId, UUID liabilityAccountId,
                                boolean active, LocalDate effectiveFrom, LocalDate effectiveTo,
                                long version) {}

    public record ComponentUpsert(@NotBlank String code, @NotBlank String nameFr,
                                  @NotBlank String nameEn, @NotBlank String componentKind,
                                  @NotBlank String calculationMode,
                                  @PositiveOrZero long defaultAmountMinor,
                                  @PositiveOrZero int defaultRateBps,
                                  UUID expenseAccountId, UUID liabilityAccountId,
                                  LocalDate effectiveFrom, LocalDate effectiveTo,
                                  Boolean active, Long version) {}

    public record PeriodView(UUID id, String code, LocalDate startDate, LocalDate endDate,
                             LocalDate paymentDate, UUID accountingPeriodId, String status,
                             long version) {}

    public record PeriodRequest(@NotBlank String code, @NotNull LocalDate startDate,
                                @NotNull LocalDate endDate, @NotNull LocalDate paymentDate,
                                @NotNull UUID accountingPeriodId, Long version) {}

    public record RunRequest(@NotNull UUID payrollPeriodId, List<UUID> employeeIds,
                             String prorationMode, Integer defaultHours,
                             Boolean segregationEnabled) {}

    public record EligibilityView(UUID employeeId, String employeeCode, String employeeName,
                                  String employmentType, String employmentMode,
                                  LocalDate hiredOn, LocalDate exitedOn,
                                  long monthlySalaryMinor, long hourlyRateMinor,
                                  int approvedHours, boolean active, boolean eligible,
                                  String status, String exceptionCode,
                                  String exceptionMessage, String formula) {}

    public record PreviewView(UUID payrollPeriodId, String periodCode, LocalDate startDate,
                              LocalDate endDate, String prorationMode, int defaultHours,
                              int employeeCount, int eligibleCount, int exceptionCount,
                              long grossMinor, long deductionMinor, long netMinor,
                              long employerCostMinor, String currency,
                              List<EligibilityView> employees, List<BlockerView> blockers) {}

    public record LineView(UUID id, int lineNo, UUID componentTypeId, String componentCode,
                           String componentNameFr, String componentNameEn, String componentKind,
                           String calculationMode, long quantity, int rateBps, long amountMinor,
                           String source, String reason, UUID expenseAccountId,
                           UUID liabilityAccountId, long version) {}

    public record PaymentView(UUID id, String channelCode, String paymentReference,
                              long amountMinor, String currency, LocalDate paymentDate,
                              String status, UUID journalEntryId, UUID treasuryAccountId,
                              String treasuryAccountName, long version) {}

    public record EmployeeView(UUID id, UUID employeeId, String employeeCode,
                               String employeeName, String employeeEmail,
                               String employmentType, String employmentMode,
                               LocalDate hiredOn, LocalDate exitedOn,
                               long monthlySalaryMinor, long hourlyRateMinor,
                               int approvedHours, boolean eligible, String status,
                               String exceptionCode, String exceptionMessage,
                               String formula, long grossMinor, long deductionMinor,
                               long netMinor, long employerCostMinor, String snapshotHash,
                               long version, List<LineView> lines, List<PaymentView> payments) {}

    public record RunView(UUID id, UUID payrollPeriodId, long runNumber, String status,
                          String prorationMode, int defaultHours, boolean segregationEnabled,
                          int employeeCount, int exceptionCount, long grossMinor,
                          long deductionMinor, long netMinor, long employerCostMinor,
                          String currency, String calculationSnapshotHash,
                          String previousSnapshotHash, boolean snapshotLocked,
                          UUID accrualJournalId, UUID paymentJournalId,
                          UUID calculatedBy, OffsetDateTime calculatedAt,
                          UUID reviewedBy, OffsetDateTime reviewedAt,
                          UUID approvedBy, OffsetDateTime approvedAt,
                          UUID paidBy, OffsetDateTime paidAt, long version) {}

    public record RunDetailView(RunView run, PeriodView period, List<EmployeeView> employees) {}

    public record AdjustmentRequest(@NotNull UUID employeePayrollId, @NotBlank String componentCode,
                                    @PositiveOrZero long amountMinor, @NotBlank String reason,
                                    @NotNull Long version) {}

    public record ActionRequest(@NotNull Long version, String reason) {}

    public record PayRequest(@NotNull UUID paymentChannelId, @NotNull UUID treasuryAccountId,
                             @NotNull LocalDate paymentDate, @NotBlank String reference,
                             Map<UUID, String> employeeReferences, @NotNull Long version) {}

    public record PaymentOptionView(UUID id, String code, String labelFr, String labelEn,
                                    boolean requiresReference, boolean enabled, UUID debitAccountId) {}

    public record AccountOption(UUID id, String code, String nameFr, String nameEn,
                                String accountType, String currency) {}

    public record TreasuryOption(UUID id, UUID chartAccountId, String displayName,
                                 String kind, String currency, long balanceMinor) {}

    public record PaymentOptionsView(List<PaymentOptionView> channels,
                                     List<AccountOption> accounts,
                                     List<TreasuryOption> treasuryAccounts) {}

    public record PaymentResultView(UUID employeePayrollId, String employeeName,
                                    String status, String reference, long amountMinor,
                                    String message, UUID paymentId) {}

    public record PayResultView(UUID runId, String status, long totalPaidMinor,
                                int paidCount, int failedCount, List<PaymentResultView> results,
                                PayslipJobView payslipJob) {}

    public record PayslipView(UUID id, UUID employeePayrollId, UUID employeeId,
                              String employeeName, String payslipNumber, int versionNo,
                              String locale, String status, UUID generatedDocumentId,
                              String generatedDocumentStatus, String snapshotHash,
                              String generationError, long version) {}

    public record PayslipJobView(UUID id, UUID payrollRunId, String status, int totalCount,
                                 int issuedCount, int failedCount, String lastError,
                                 long version) {}

    public record PayslipJobResultView(UUID id, UUID employeePayrollId, UUID payslipId,
                                       String resultStatus, String errorDetail) {}
}
