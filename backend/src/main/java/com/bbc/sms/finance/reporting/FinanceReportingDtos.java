package com.bbc.sms.finance.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only, snapshot-aware DTOs for the BAY-51 finance reporting surface. */
public final class FinanceReportingDtos {
    private FinanceReportingDtos() {}

    public record ReportFilters(UUID academicSessionId, LocalDate fromDate, LocalDate toDate,
                                LocalDate asOfDate, UUID classId, String level,
                                String feeTypeCode, String channelCode, String status,
                                int limit, int offset) {
        public ReportFilters {
            limit = Math.max(1, Math.min(limit <= 0 ? 500 : limit, 2_000));
            offset = Math.max(0, offset);
        }
    }

    public record ReportMeta(UUID academicSessionId, String sessionCode,
                             LocalDate fromDate, LocalDate toDate, LocalDate asOfDate,
                             OffsetDateTime generatedAt, OffsetDateTime dataThrough,
                             String refreshStatus, long lagSeconds, String currency,
                             Map<String, String> appliedFilters, String sourceBasis) {}

    public record ReportEnvelope<T>(ReportMeta meta, T data) {}

    public record SessionOption(UUID id, String code, String label, LocalDate startDate,
                                LocalDate endDate, String status, boolean current) {}

    public record ClassOption(UUID id, String name, String level, String subsystem) {}

    public record ReportContextView(List<SessionOption> sessions, List<ClassOption> classes,
                                    List<String> levels, List<String> feeTypes,
                                    List<String> channels) {}

    public record ReportException(String code, String message, String sourceType,
                                  String sourceId, String actionLink) {}

    public record AgeingBucket(String bucket, long amountMinor, int installmentCount,
                               List<String> sourceIds) {}

    public record InstallmentPerformance(String label, long dueMinor, long paidMinor,
                                         long outstandingMinor, long overdueMinor,
                                         int installmentCount) {}

    public record ReceivableRow(String sourceId, String studentId, String studentName,
                                String feeTypeCode, String classNameSnapshot,
                                String levelSnapshot, String sessionCode, LocalDate chargeDate,
                                long billedMinor, long collectedMinor, long waivedMinor,
                                long outstandingMinor, int sourceCount) {}

    public record ReceivablesReport(long billedMinor, long collectedMinor, long waivedMinor,
                                    long outstandingMinor, long creditedMinor, long refundedMinor,
                                    BigDecimal recoveryPercentage, long mismatchMinor,
                                    int mismatchCount, boolean balanced,
                                    List<ReceivableRow> rows, List<AgeingBucket> ageing,
                                    List<InstallmentPerformance> installmentPerformance,
                                    List<ReportException> exceptions) {}

    public record CollectionRow(String sourceId, String studentId, String studentName,
                                String sessionCode, String channel, String status,
                                LocalDate paymentDate, String reference, String receiptNo,
                                long amountMinor, long allocatedMinor, long remainingCreditMinor,
                                long refundedMinor, UUID journalId) {}

    public record ChannelSummary(String channel, long paymentMinor, long allocatedMinor,
                                 long creditMinor, long refundedMinor, int paymentCount) {}

    public record CashierVarianceRow(String sourceId, String cashierUserId, String status,
                                     LocalDate openedOn, long expectedMinor, long declaredMinor,
                                     long varianceMinor, String note) {}

    public record ProviderSummary(String providerCode, String status, int transactionCount,
                                  long amountMinor) {}

    public record CollectionsReport(long paymentTotalMinor, long allocatedMinor,
                                    long remainingCreditMinor, long refundedMinor,
                                    long reversedMinor, long mismatchMinor, int mismatchCount,
                                    boolean balanced, List<CollectionRow> rows,
                                    List<ChannelSummary> channels,
                                    List<CashierVarianceRow> cashierVariances,
                                    List<ProviderSummary> providers,
                                    List<ReportException> exceptions) {}

    public record DocumentStatusRow(String type, String status, int count, long amountMinor) {}

    public record DocumentRow(String sourceId, String type, String number, String status,
                              LocalDate issueDate, String studentName, String sessionCode,
                              String recipient, long amountMinor, long outstandingMinor,
                              UUID sourcePaymentId, UUID sourceJournalId) {}

    public record DocumentsReport(long invoiceTotalMinor, long invoiceOutstandingMinor,
                                  int invoiceCount, long receiptTotalMinor, int receiptCount,
                                  List<DocumentStatusRow> statuses, List<DocumentRow> rows,
                                  List<ReportException> exceptions) {}

    public record ExpenseRow(String sourceId, String category, String label, LocalDate spentOn,
                             long amountMinor, String status, UUID journalId) {}

    public record ExpensesReport(long postedExpenseMinor, int expenseCount,
                                 boolean legacyAdapter, List<ExpenseRow> rows,
                                 List<ReportException> exceptions) {}

    public record PayrollRunRow(String sourceId, String periodCode, String status,
                                LocalDate startDate, LocalDate endDate, int employeeCount,
                                int exceptionCount, long grossMinor, long deductionMinor,
                                long netMinor, long employerCostMinor, long paidMinor,
                                UUID accrualJournalId, UUID paymentJournalId) {}

    public record PayrollReport(long grossMinor, long deductionMinor, long netMinor,
                                long employerCostMinor, long paidMinor, int runCount,
                                int employeeCount, List<PayrollRunRow> runs,
                                List<ReportException> exceptions) {}

    public record TrialBalanceRow(String accountId, String code, String name, String type,
                                  long debitMinor, long creditMinor, long balanceMinor) {}

    public record TrialBalanceSummary(long debitMinor, long creditMinor, boolean balanced,
                                      int accountCount, List<TrialBalanceRow> rows) {}

    public record IncomeRow(String accountId, String code, String name, String type,
                            long amountMinor) {}

    public record IncomeStatement(long revenueMinor, long expenseMinor, long netMinor,
                                  List<IncomeRow> rows) {}

    public record LedgerRow(String sourceId, String number, LocalDate entryDate,
                            String sourceType, String status, String description,
                            String accountCode, long debitMinor, long creditMinor,
                            long runningBalanceMinor) {}

    public record AccountingReport(TrialBalanceSummary trialBalance,
                                   IncomeStatement incomeStatement, List<LedgerRow> ledger,
                                   List<ReportException> exceptions) {}

    public record ReconciliationRow(String sourceId, String sourceType, String sourceReference,
                                    long expectedMinor, long actualMinor, String currency,
                                    String state, String reason, String actionLink) {}

    public record ReconciliationReport(int openCount, long mismatchMinor,
                                       List<ReconciliationRow> rows,
                                       List<ReportException> exceptions) {}

    public record ExportPayload(String filename, String contentType, byte[] bytes,
                                int rowCount) {}
}
