package com.bbc.sms.finance.reporting;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.reports.dto.ReportDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bbc.sms.finance.reporting.FinanceReportingDtos.*;

/**
 * Read-only reporting boundary for BAY-51. It deliberately reads immutable snapshots,
 * posted finance rows, and POSTED journals; it does not use the legacy lifetime-sum query
 * as the source of the new reports.
 */
@Service
@Transactional(readOnly = true)
public class FinanceReportingService {
    private final JdbcTemplate jdbc;
    private final FinancePolicyService financePolicy;

    public FinanceReportingService(JdbcTemplate jdbc, FinancePolicyService financePolicy) {
        this.jdbc = jdbc;
        this.financePolicy = financePolicy;
    }

    public ReportContextView contextOptions() {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        UUID schoolId = TenantContext.get();
        List<SessionOption> sessions = jdbc.query("""
                SELECT id, code, label, start_date, end_date, status, is_current
                  FROM academic_session
                 WHERE school_id=?
                 ORDER BY start_date DESC, code
                """, (rs, n) -> new SessionOption(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("label"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("status"), rs.getBoolean("is_current")), schoolId);
        List<ClassOption> classes = jdbc.query("""
                SELECT id, name, level, subsystem
                  FROM school_class
                 WHERE school_id=?
                 ORDER BY level, name
                """, (rs, n) -> new ClassOption(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("level"), rs.getString("subsystem")), schoolId);
        List<String> levels = jdbc.queryForList("SELECT DISTINCT level FROM school_class WHERE school_id=? ORDER BY level",
                String.class, schoolId);
        List<String> feeTypes = jdbc.queryForList("SELECT code FROM fee_type WHERE school_id=? ORDER BY code",
                String.class, schoolId);
        List<String> channels = jdbc.queryForList("SELECT code FROM payment_channel WHERE school_id=? AND enabled=true ORDER BY sort_order, code",
                String.class, schoolId);
        return new ReportContextView(sessions, classes, levels, feeTypes, channels);
    }

    public ReportEnvelope<ReceivablesReport> receivables(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, false);
        List<Object> args = new ArrayList<>(chargeSnapshotArgs(c));
        // Keep all aggregate aliases explicit so the JSON contract and reconciliation diagnostics are stable.
        String totalsSql = """
                SELECT COALESCE(SUM(s.adjusted_amount_minor),0) AS billed,
                       COALESCE(SUM(s.report_paid_minor),0) AS collected,
                       COALESCE(SUM(s.report_waived_minor),0) AS waived,
                       COALESCE(SUM(GREATEST(s.adjusted_amount_minor - s.report_paid_minor - s.report_waived_minor, 0)),0) AS outstanding,
                       COALESCE(SUM(CASE WHEN s.adjusted_amount_minor <> s.report_paid_minor + s.report_waived_minor
                                         THEN ABS(s.adjusted_amount_minor - s.report_paid_minor - s.report_waived_minor
                                                  - GREATEST(s.adjusted_amount_minor - s.report_paid_minor - s.report_waived_minor, 0)) ELSE 0 END),0) AS mismatch,
                       COUNT(*) FILTER (WHERE s.adjusted_amount_minor <> s.report_paid_minor + s.report_waived_minor
                                                     + GREATEST(s.adjusted_amount_minor - s.report_paid_minor - s.report_waived_minor, 0)) AS mismatch_count
                  FROM (
                """ + chargeSnapshotSql(c) + "\n                ) s\n                ";
        Map<String, Object> totals = jdbc.queryForList(totalsSql, args.toArray()).stream().findFirst().orElse(Map.of());
        long billed = number(totals.get("billed"));
        long collected = number(totals.get("collected"));
        long waived = number(totals.get("waived"));
        long outstanding = number(totals.get("outstanding"));
        long mismatch = number(totals.get("mismatch"));
        int mismatchCount = (int) number(totals.get("mismatch_count"));
        long credited = creditBalance(c);
        long refunded = refundTotal(c);
        List<ReceivableRow> rows = receivableRows(c);
        List<AgeingBucket> ageing = ageing(c);
        List<InstallmentPerformance> performance = installmentPerformance(c);
        BigDecimal recovery = billed == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(collected)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(billed), 1, RoundingMode.HALF_UP);
        List<ReportException> exceptions = new ArrayList<>();
        if (mismatch != 0) exceptions.add(new ReportException("CHARGE_BALANCE_MISMATCH",
                "Charge equation is not balanced; investigate the source charge before relying on this total.",
                "STUDENT_CHARGE", null, "/finance/reconciliation"));
        return envelope(c, new ReceivablesReport(billed, collected, waived, outstanding, credited, refunded,
                recovery, mismatch, mismatchCount, mismatch == 0, rows, ageing, performance, exceptions));
    }

    public ReportEnvelope<CollectionsReport> collections(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, false);
        StringBuilder sql = new StringBuilder("""
                WITH allocations AS (
                    SELECT pa.payment_id, COALESCE(SUM(pa.allocated_minor),0) allocated
                      FROM payment_allocation pa
                     WHERE pa.school_id=?
                       AND pa.created_at::date <= ?
                       AND (pa.status='ACTIVE'
                            OR (pa.status='REVERSED' AND NOT EXISTS (
                                SELECT 1 FROM payment_reversal_request rr
                                 WHERE rr.school_id=pa.school_id AND rr.payment_id=pa.payment_id
                                   AND rr.status='POSTED' AND rr.posted_at::date <= ?
                            ))
                            OR (pa.status='REFUNDED' AND NOT EXISTS (
                                SELECT 1 FROM refund_transaction fr
                                 WHERE fr.school_id=pa.school_id AND fr.payment_id=pa.payment_id
                                   AND fr.posted_at::date <= ?
                            )))
                     GROUP BY pa.payment_id
                ), refunds AS (
                    SELECT payment_id, COALESCE(SUM(amount_minor),0) refunded
                      FROM refund_transaction
                     WHERE school_id=? AND posted_at::date <= ?
                     GROUP BY payment_id
                )
                SELECT p.id, p.student_id, CONCAT_WS(' ', st.first_name, st.last_name) student_name,
                       ses.code session_code, COALESCE(p.channel_code_snapshot,'UNKNOWN') channel,
                       p.status, p.payment_date, p.reference, p.receipt_no, p.amount_minor,
                       COALESCE(a.allocated,0) allocated, COALESCE(r.refunded,0) refunded,
                       p.journal_entry_id
                  FROM finance_payment p
                  JOIN student st ON st.school_id=p.school_id AND st.id=p.student_id
                  LEFT JOIN academic_session ses ON ses.school_id=p.school_id AND ses.id=p.academic_session_id
                  LEFT JOIN allocations a ON a.payment_id=p.id
                  LEFT JOIN refunds r ON r.payment_id=p.id
                 WHERE p.school_id=? AND p.status <> 'DRAFT'
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId(), c.effectiveTo(), c.asOf(), c.asOf(),
                c.schoolId(), c.effectiveTo(), c.schoolId()));
        paymentFilters(sql, args, c, "p");
        sql.append(" ORDER BY p.payment_date DESC, p.created_at DESC LIMIT ? OFFSET ?");
        args.add(c.filters().limit()); args.add(c.filters().offset());
        Map<UUID, Long> credits = creditByPayment(c);
        List<CollectionRow> rows = jdbc.query(sql.toString(), (rs, n) -> {
            UUID paymentId = rs.getObject("id", UUID.class);
            return new CollectionRow(paymentId.toString(), string(rs.getObject("student_id")),
                    rs.getString("student_name"), rs.getString("session_code"), rs.getString("channel"),
                    rs.getString("status"), rs.getObject("payment_date", LocalDate.class), rs.getString("reference"),
                    rs.getString("receipt_no"), rs.getLong("amount_minor"), rs.getLong("allocated"),
                    credits.getOrDefault(paymentId, 0L), rs.getLong("refunded"), rs.getObject("journal_entry_id", UUID.class));
        }, args.toArray());
        CollectionTotals totals = collectionTotals(c);
        long paymentTotal = totals.paymentTotal();
        long allocated = totals.allocated();
        long credit = totals.credit();
        long refunded = totals.refunded();
        long reversed = totals.reversed();
        int mismatchCount = totals.mismatchCount();
        long mismatch = totals.mismatch();
        List<ReportException> exceptions = new ArrayList<>();
        if (mismatch != 0) exceptions.add(new ReportException("PAYMENT_BALANCE_MISMATCH",
                "Payment equation is not balanced for one or more posted payments.", "FINANCE_PAYMENT", null,
                "/finance/reconciliation"));
        return envelope(c, new CollectionsReport(paymentTotal, allocated, credit, refunded, reversed, mismatch,
                mismatchCount, mismatch == 0, rows, channelSummary(c), cashierVariances(c), providerSummary(c), exceptions));
    }

    public ReportEnvelope<DocumentsReport> documents(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, false);
        List<DocumentRow> rows = new ArrayList<>();
        List<DocumentStatusRow> statuses = new ArrayList<>();
        String invoiceWhere = documentFilterSql(c, "i");
        List<Object> invoiceArgs = documentArgs(c);
        List<Map<String, Object>> invoiceRows = jdbc.queryForList("""
                SELECT i.id, i.invoice_number, i.status, i.issue_date, i.recipient_name,
                       i.total_minor, i.outstanding_minor, i.student_id, i.generated_document_id,
                       ses.code session_code
                  FROM finance_invoice i
                LEFT JOIN academic_session ses ON ses.school_id=i.school_id AND ses.id=i.academic_session_id
                 WHERE i.school_id=?
                """ + invoiceWhere + " ORDER BY i.issue_date DESC, i.created_at DESC LIMIT ? OFFSET ?",
                withLimit(invoiceArgs, c).toArray());
        for (Map<String, Object> row : invoiceRows) {
            rows.add(new DocumentRow(string(row.get("id")), "INVOICE", string(row.get("invoice_number")),
                    string(row.get("status")), date(row.get("issue_date")), string(row.get("recipient_name")),
                    string(row.get("session_code")), string(row.get("recipient_name")), number(row.get("total_minor")),
                    number(row.get("outstanding_minor")), null, id(row.get("generated_document_id"))));
        }
        statuses.addAll(documentStatuses("INVOICE", "finance_invoice", "total_minor", "issue_date", c));
        String receiptWhere = documentFilterSql(c, "r");
        List<Object> receiptArgs = documentArgs(c);
        List<Map<String, Object>> receiptRows = jdbc.queryForList("""
                SELECT r.id, r.receipt_number, r.status, r.issue_date, r.recipient_name,
                       r.amount_minor, r.outstanding_minor, r.finance_payment_id, r.journal_entry_id,
                       ses.code session_code
                  FROM finance_receipt r
                LEFT JOIN academic_session ses ON ses.school_id=r.school_id AND ses.id=r.academic_session_id
                 WHERE r.school_id=?
                """ + receiptWhere + " ORDER BY r.issue_date DESC, r.created_at DESC LIMIT ? OFFSET ?",
                withLimit(receiptArgs, c).toArray());
        for (Map<String, Object> row : receiptRows) {
            rows.add(new DocumentRow(string(row.get("id")), "RECEIPT", string(row.get("receipt_number")),
                    string(row.get("status")), date(row.get("issue_date")), string(row.get("recipient_name")),
                    string(row.get("session_code")), string(row.get("recipient_name")), number(row.get("amount_minor")),
                    number(row.get("outstanding_minor")), id(row.get("finance_payment_id")), id(row.get("journal_entry_id"))));
        }
        statuses.addAll(documentStatuses("RECEIPT", "finance_receipt", "amount_minor", "issue_date", c));
        rows.sort(Comparator.comparing(DocumentRow::issueDate, Comparator.nullsLast(Comparator.reverseOrder())));
        Map<String, Object> invoiceTotals = documentTotals("finance_invoice", "i", "total_minor", "outstanding_minor", invoiceArgs, invoiceWhere, c);
        Map<String, Object> receiptTotals = documentTotals("finance_receipt", "r", "amount_minor", "outstanding_minor", receiptArgs, receiptWhere, c);
        return envelope(c, new DocumentsReport(number(invoiceTotals.get("amount_minor")), number(invoiceTotals.get("outstanding_minor")),
                (int) number(invoiceTotals.get("row_count")), number(receiptTotals.get("amount_minor")),
                (int) number(receiptTotals.get("row_count")), statuses, rows, List.of()));
    }

    public ReportEnvelope<ExpensesReport> expenses(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, false);
        StringBuilder sql = new StringBuilder("""
                SELECT id, category, label, spent_on, amount
                  FROM expense
                 WHERE school_id=?
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        dateFilters(sql, args, "spent_on", c);
        sql.append(" ORDER BY spent_on DESC, id DESC LIMIT ? OFFSET ?");
        args.add(c.filters().limit()); args.add(c.filters().offset());
        List<ExpenseRow> rows = jdbc.query(sql.toString(), (rs, n) -> new ExpenseRow(
                string(rs.getObject("id")), rs.getString("category"), rs.getString("label"),
                rs.getObject("spent_on", LocalDate.class), rs.getLong("amount"), "LEGACY_ADAPTER", null), args.toArray());
        long posted = scalarLong("""
                SELECT COALESCE(SUM(CASE WHEN a.account_type='EXPENSE' THEN l.debit_minor-l.credit_minor ELSE 0 END),0)
                  FROM journal_line l JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                  JOIN chart_of_account a ON a.school_id=l.school_id AND a.id=l.account_id
                 WHERE l.school_id=? AND j.status='POSTED' AND j.entry_date BETWEEN ? AND ?
                """, c.schoolId(), c.from(), c.effectiveTo());
        List<ReportException> exceptions = new ArrayList<>();
        if (!rows.isEmpty()) exceptions.add(new ReportException("LEGACY_EXPENSE_SOURCE",
                "Legacy expenses are shown as an adapter because the old expense table has no POSTED journal status.",
                "EXPENSE", null, "/finance/accounting"));
        return envelope(c, new ExpensesReport(posted, rows.size(), true, rows, exceptions));
    }

    public ReportEnvelope<PayrollReport> payroll(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, true);
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, pp.code, r.status, pp.start_date, pp.end_date,
                       r.employee_count, r.exception_count, r.gross_minor, r.deduction_minor,
                       r.net_minor, r.employer_cost_minor, r.accrual_journal_id, r.payment_journal_id,
                       COALESCE(paid.paid_minor,0) paid_minor
                  FROM payroll_run r
                  JOIN payroll_period pp ON pp.school_id=r.school_id AND pp.id=r.payroll_period_id
                  LEFT JOIN accounting_period ap ON ap.school_id=pp.school_id AND ap.id=pp.accounting_period_id
                  LEFT JOIN (SELECT ep.payroll_run_id, SUM(pay.amount_minor) paid_minor
                               FROM employee_payroll ep JOIN payroll_payment pay
                                 ON pay.school_id=ep.school_id AND pay.employee_payroll_id=ep.id
                              WHERE ep.school_id=? AND pay.status='POSTED' AND pay.payment_date <= ?
                              GROUP BY ep.payroll_run_id) paid ON paid.payroll_run_id=r.id
                 WHERE r.school_id=? AND r.status <> 'DRAFT'
                   AND pp.start_date <= ? AND pp.end_date >= ?
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId(), c.effectiveTo(), c.schoolId(), c.effectiveTo(), c.from()));
        if (c.filters().academicSessionId() != null) { sql.append(" AND ap.academic_session_id=?"); args.add(c.filters().academicSessionId()); }
        if (c.filters().status() != null) { sql.append(" AND r.status=?"); args.add(c.filters().status()); }
        sql.append(" ORDER BY pp.start_date DESC, r.run_number DESC LIMIT ? OFFSET ?");
        args.add(c.filters().limit()); args.add(c.filters().offset());
        List<PayrollRunRow> runs = jdbc.query(sql.toString(), (rs, n) -> new PayrollRunRow(
                string(rs.getObject("id")), rs.getString("code"), rs.getString("status"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getInt("employee_count"), rs.getInt("exception_count"), rs.getLong("gross_minor"),
                rs.getLong("deduction_minor"), rs.getLong("net_minor"), rs.getLong("employer_cost_minor"),
                rs.getLong("paid_minor"), rs.getObject("accrual_journal_id", UUID.class),
                rs.getObject("payment_journal_id", UUID.class)), args.toArray());
        List<ReportException> exceptions = runs.stream().filter(r -> r.exceptionCount() > 0)
                .map(r -> new ReportException("PAYROLL_EXCEPTIONS", "Payroll run contains calculation exceptions.",
                        "PAYROLL_RUN", r.sourceId(), "/finance/payroll")).toList();
        return envelope(c, new PayrollReport(runs.stream().mapToLong(PayrollRunRow::grossMinor).sum(),
                runs.stream().mapToLong(PayrollRunRow::deductionMinor).sum(), runs.stream().mapToLong(PayrollRunRow::netMinor).sum(),
                runs.stream().mapToLong(PayrollRunRow::employerCostMinor).sum(), runs.stream().mapToLong(PayrollRunRow::paidMinor).sum(),
                runs.size(), runs.stream().mapToInt(PayrollRunRow::employeeCount).sum(), runs, exceptions));
    }

    public ReportEnvelope<AccountingReport> accounting(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, true);
        LocalDate asOf = c.asOf();
        StringBuilder trialSql = new StringBuilder("""
                SELECT a.id, a.code, a.name_fr, a.account_type,
                       COALESCE(SUM(CASE WHEN j.status='POSTED' THEN l.debit_minor ELSE 0 END),0) debit,
                       COALESCE(SUM(CASE WHEN j.status='POSTED' THEN l.credit_minor ELSE 0 END),0) credit
                  FROM chart_of_account a
                  LEFT JOIN journal_line l ON l.school_id=a.school_id AND l.account_id=a.id
                  LEFT JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                       AND j.entry_date <= ? AND j.status='POSTED'
                 WHERE a.school_id=?
                """);
        List<Object> trialArgs = new ArrayList<>(List.of(asOf, c.schoolId()));
        if (c.filters().academicSessionId() != null) {
            trialSql.insert(trialSql.indexOf(" WHERE"), " AND EXISTS (SELECT 1 FROM accounting_period ap WHERE ap.school_id=j.school_id AND ap.id=j.accounting_period_id AND ap.academic_session_id=?)");
            trialArgs.add(1, c.filters().academicSessionId());
        }
        trialSql.append(" GROUP BY a.id, a.code, a.name_fr, a.account_type ORDER BY a.code");
        List<TrialBalanceRow> trialRows = jdbc.query(trialSql.toString(), (rs, n) -> {
            long debit = rs.getLong("debit"), credit = rs.getLong("credit");
            return new TrialBalanceRow(rs.getObject("id", UUID.class).toString(), rs.getString("code"),
                    rs.getString("name_fr"), rs.getString("account_type"), debit, credit, debit - credit);
        }, trialArgs.toArray());
        List<TrialBalanceRow> visibleTrialRows = trialRows.stream().filter(row -> c.filters().status() == null || row.type().equalsIgnoreCase(c.filters().status())).toList();
        long debits = trialRows.stream().mapToLong(TrialBalanceRow::debitMinor).sum();
        long credits = trialRows.stream().mapToLong(TrialBalanceRow::creditMinor).sum();
        TrialBalanceSummary trial = new TrialBalanceSummary(debits, credits, debits == credits, visibleTrialRows.size(), visibleTrialRows);
        StringBuilder incomeSql = new StringBuilder("""
                SELECT a.id, a.code, a.name_fr, a.account_type,
                       COALESCE(SUM(CASE WHEN a.account_type='REVENUE' THEN l.credit_minor-l.debit_minor ELSE l.debit_minor-l.credit_minor END),0) amount
                  FROM journal_line l JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                  JOIN chart_of_account a ON a.school_id=l.school_id AND a.id=l.account_id
                 WHERE l.school_id=? AND j.status='POSTED' AND j.entry_date BETWEEN ? AND ?
                   AND a.account_type IN ('REVENUE','EXPENSE')
                """);
        List<Object> incomeArgs = new ArrayList<>(List.of(c.schoolId(), c.from(), c.effectiveTo()));
        if (c.filters().academicSessionId() != null) {
            incomeSql.append(" AND EXISTS (SELECT 1 FROM accounting_period ap WHERE ap.school_id=j.school_id AND ap.id=j.accounting_period_id AND ap.academic_session_id=?)");
            incomeArgs.add(c.filters().academicSessionId());
        }
        incomeSql.append(" GROUP BY a.id, a.code, a.name_fr, a.account_type ORDER BY a.code");
        List<IncomeRow> incomeRows = jdbc.query(incomeSql.toString(), (rs, n) -> new IncomeRow(rs.getObject("id", UUID.class).toString(), rs.getString("code"),
                rs.getString("name_fr"), rs.getString("account_type"), rs.getLong("amount")), incomeArgs.toArray());
        long revenue = incomeRows.stream().filter(r -> "REVENUE".equals(r.type())).mapToLong(IncomeRow::amountMinor).sum();
        long expense = incomeRows.stream().filter(r -> "EXPENSE".equals(r.type())).mapToLong(IncomeRow::amountMinor).sum();
        StringBuilder ledgerSql = new StringBuilder("""
                SELECT j.id, j.number, j.entry_date, j.source_type, j.status, j.description,
                       a.code, l.debit_minor, l.credit_minor,
                       SUM(l.debit_minor-l.credit_minor) OVER (
                           ORDER BY j.entry_date, j.number, l.line_number
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       ) running_balance
                  FROM journal_line l JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                  JOIN chart_of_account a ON a.school_id=l.school_id AND a.id=l.account_id
                 WHERE l.school_id=? AND j.status='POSTED' AND j.entry_date BETWEEN ? AND ?
                """);
        List<Object> ledgerArgs = new ArrayList<>(List.of(c.schoolId(), c.from(), c.effectiveTo()));
        if (c.filters().academicSessionId() != null) {
            ledgerSql.append(" AND EXISTS (SELECT 1 FROM accounting_period ap WHERE ap.school_id=j.school_id AND ap.id=j.accounting_period_id AND ap.academic_session_id=?)");
            ledgerArgs.add(c.filters().academicSessionId());
        }
        ledgerSql.append(" ORDER BY j.entry_date, j.number, l.line_number LIMIT ? OFFSET ?");
        ledgerArgs.add(c.filters().limit()); ledgerArgs.add(c.filters().offset());
        List<LedgerRow> ledgerRows = jdbc.query(ledgerSql.toString(), (rs, n) -> new RawLedger(rs.getObject("id", UUID.class).toString(), rs.getString("number"),
                rs.getObject("entry_date", LocalDate.class), rs.getString("source_type"), rs.getString("status"),
                rs.getString("description"), rs.getString("code"), rs.getLong("debit_minor"), rs.getLong("credit_minor"),
                rs.getLong("running_balance")),
                ledgerArgs.toArray()).stream()
                .map(row -> new LedgerRow(row.sourceId(), row.number(), row.entryDate(), row.sourceType(), row.status(),
                        row.description(), row.accountCode(), row.debit(), row.credit(), row.running())).toList();
        List<ReportException> exceptions = new ArrayList<>();
        if (!trial.balanced()) exceptions.add(new ReportException("TRIAL_BALANCE_MISMATCH",
                "Posted journal debits and credits do not balance at the selected as-of date.", "JOURNAL", null,
                "/finance/accounting"));
        return envelope(c, new AccountingReport(trial, new IncomeStatement(revenue, expense, revenue - expense, incomeRows), ledgerRows, exceptions));
    }

    public ReportEnvelope<ReconciliationReport> reconciliation(ReportFilters filters) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        Context c = context(filters, true, false);
        StringBuilder sql = new StringBuilder("""
                SELECT id, source_type, source_id, expected_amount, posted_amount, currency, state, reason
                  FROM reconciliation_item WHERE school_id=?
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        if (c.filters().status() != null) { sql.append(" AND state=?"); args.add(c.filters().status()); }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?"); args.add(c.filters().limit()); args.add(c.filters().offset());
        List<ReconciliationRow> rows = jdbc.query(sql.toString(), (rs, n) -> new ReconciliationRow(
                string(rs.getObject("id")), rs.getString("source_type"), rs.getString("source_id"),
                rs.getLong("expected_amount"), rs.getLong("posted_amount"), rs.getString("currency"),
                rs.getString("state"), rs.getString("reason"), "/finance/reconciliation"), args.toArray());
        long mismatch = rows.stream().mapToLong(row -> Math.abs(row.expectedMinor() - row.actualMinor())).sum();
        int open = (int) rows.stream().filter(row -> !Set.of("MATCHED", "IGNORED").contains(row.state())).count();
        List<ReportException> exceptions = rows.stream().filter(row -> !Set.of("MATCHED", "IGNORED").contains(row.state()))
                .map(row -> new ReportException("RECONCILIATION_BLOCKER", row.reason(), row.sourceType(), row.sourceId(), row.actionLink())).toList();
        return envelope(c, new ReconciliationReport(open, mismatch, rows, exceptions));
    }

    /** Backward-compatible adapter for /api/reports/finance, derived from the new finance tables. */
    public ReportDtos.FinanceReport legacyFinance() {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        UUID schoolId = TenantContext.get();
        long revenue = scalarLong("SELECT COALESCE(SUM(amount_minor),0) FROM finance_payment WHERE school_id=? AND status IN ('POSTED','PARTIALLY_REFUNDED','REFUNDED')", schoolId);
        if (revenue == 0) revenue = scalarLong("SELECT COALESCE(SUM(amount),0) FROM payment WHERE school_id=?", schoolId);
        long expense = scalarLong("""
                SELECT COALESCE(SUM(l.debit_minor-l.credit_minor),0)
                  FROM journal_line l JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                  JOIN chart_of_account a ON a.school_id=l.school_id AND a.id=l.account_id
                 WHERE l.school_id=? AND j.status='POSTED' AND a.account_type='EXPENSE'
                """, schoolId);
        if (expense == 0) expense = scalarLong("SELECT COALESCE(SUM(amount),0) FROM expense WHERE school_id=?", schoolId);
        long billed = scalarLong("SELECT COALESCE(SUM(adjusted_amount_minor),0) FROM student_charge WHERE school_id=? AND status NOT IN ('DRAFT','REVERSED')", schoolId);
        long collected = scalarLong("SELECT COALESCE(SUM(paid_minor),0) FROM student_charge WHERE school_id=? AND status NOT IN ('DRAFT','REVERSED')", schoolId);
        if (billed == 0) billed = scalarLong("SELECT COALESCE(SUM(total),0) FROM student_fee WHERE school_id=?", schoolId);
        if (collected == 0) collected = scalarLong("SELECT COALESCE(SUM(paid),0) FROM student_fee WHERE school_id=?", schoolId);
        double recovery = billed == 0 ? 0d : ((double) collected / billed) * 100d;
        return new ReportDtos.FinanceReport(revenue, expense, revenue - expense, recovery);
    }

    public ExportPayload export(String report, ReportFilters filters, String format) {
        financePolicy.requireSchool("FINANCE_EXPORT");
        String normalized = report == null ? "" : report.trim().toLowerCase(Locale.ROOT);
        String type = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        ReportFilters effective = filters == null ? new ReportFilters(null, null, null, null, null, null, null, null, null, 2_000, 0) : filters;
        List<String[]> lines = new ArrayList<>();
        switch (normalized) {
            case "receivables" -> exportReceivables(lines, receivables(effective));
            case "collections" -> exportCollections(lines, collections(effective));
            case "documents" -> exportDocuments(lines, documents(effective));
            case "expenses" -> exportExpenses(lines, expenses(effective));
            case "payroll" -> exportPayroll(lines, payroll(effective));
            case "accounting" -> exportAccounting(lines, accounting(effective));
            case "reconciliation" -> exportReconciliation(lines, reconciliation(effective));
            default -> throw ApiException.badRequest("Unknown finance report: " + report);
        }
        if ("pdf".equals(type)) {
            StringBuilder text = new StringBuilder("BAY-51 ").append(normalized).append("\n");
            for (String[] line : lines) text.append(String.join(" | ", line)).append('\n');
            return new ExportPayload("finance-" + normalized + ".pdf", "application/pdf",
                    FinanceReportPdfRenderer.render(text.toString()), Math.max(0, lines.size() - 1));
        }
        if (!"csv".equals(type)) throw ApiException.badRequest("Export format must be CSV or PDF.");
        StringBuilder csv = new StringBuilder("# report=").append(normalized).append('\n');
        for (String[] line : lines) csv.append(ArraysCsv(line)).append('\n');
        return new ExportPayload("finance-" + normalized + ".csv", "text/csv;charset=UTF-8",
                ('\uFEFF' + csv.toString()).getBytes(StandardCharsets.UTF_8), Math.max(0, lines.size() - 1));
    }

    private void exportReceivables(List<String[]> out, ReportEnvelope<ReceivablesReport> e) {
        out.add(new String[]{"sourceId","feeType","classSnapshot","billedMinor","collectedMinor","waivedMinor","outstandingMinor"});
        for (ReceivableRow row : e.data().rows()) out.add(new String[]{row.sourceId(), row.feeTypeCode(), row.classNameSnapshot(),
                Long.toString(row.billedMinor()), Long.toString(row.collectedMinor()), Long.toString(row.waivedMinor()), Long.toString(row.outstandingMinor())});
    }
    private void exportCollections(List<String[]> out, ReportEnvelope<CollectionsReport> e) {
        out.add(new String[]{"sourceId","student","channel","status","paymentDate","amountMinor","allocatedMinor","creditMinor","refundedMinor"});
        for (CollectionRow row : e.data().rows()) out.add(new String[]{row.sourceId(), row.studentName(), row.channel(), row.status(),
                String.valueOf(row.paymentDate()), Long.toString(row.amountMinor()), Long.toString(row.allocatedMinor()),
                Long.toString(row.remainingCreditMinor()), Long.toString(row.refundedMinor())});
    }
    private void exportDocuments(List<String[]> out, ReportEnvelope<DocumentsReport> e) {
        out.add(new String[]{"sourceId","type","number","status","issueDate","recipient","amountMinor","outstandingMinor"});
        for (DocumentRow row : e.data().rows()) out.add(new String[]{row.sourceId(), row.type(), row.number(), row.status(), String.valueOf(row.issueDate()),
                row.recipient(), Long.toString(row.amountMinor()), Long.toString(row.outstandingMinor())});
    }
    private void exportExpenses(List<String[]> out, ReportEnvelope<ExpensesReport> e) {
        out.add(new String[]{"sourceId","category","label","spentOn","amountMinor","status"});
        for (ExpenseRow row : e.data().rows()) out.add(new String[]{row.sourceId(), row.category(), row.label(), String.valueOf(row.spentOn()), Long.toString(row.amountMinor()), row.status()});
    }
    private void exportPayroll(List<String[]> out, ReportEnvelope<PayrollReport> e) {
        out.add(new String[]{"runId","period","status","employees","exceptions","grossMinor","deductionMinor","netMinor","paidMinor"});
        for (PayrollRunRow row : e.data().runs()) out.add(new String[]{row.sourceId(), row.periodCode(), row.status(), Integer.toString(row.employeeCount()),
                Integer.toString(row.exceptionCount()), Long.toString(row.grossMinor()), Long.toString(row.deductionMinor()), Long.toString(row.netMinor()), Long.toString(row.paidMinor())});
    }
    private void exportAccounting(List<String[]> out, ReportEnvelope<AccountingReport> e) {
        out.add(new String[]{"accountId","code","name","type","debitMinor","creditMinor","balanceMinor"});
        for (TrialBalanceRow row : e.data().trialBalance().rows()) out.add(new String[]{row.accountId(), row.code(), row.name(), row.type(),
                Long.toString(row.debitMinor()), Long.toString(row.creditMinor()), Long.toString(row.balanceMinor())});
    }
    private void exportReconciliation(List<String[]> out, ReportEnvelope<ReconciliationReport> e) {
        out.add(new String[]{"sourceId","sourceType","expectedMinor","actualMinor","currency","state","reason"});
        for (ReconciliationRow row : e.data().rows()) out.add(new String[]{row.sourceId(), row.sourceType(), Long.toString(row.expectedMinor()),
                Long.toString(row.actualMinor()), row.currency(), row.state(), row.reason()});
    }

    private List<ReceivableRow> receivableRows(Context c) {
        StringBuilder sql = new StringBuilder("""
                SELECT snapshot.id, snapshot.student_id, CONCAT_WS(' ', st.first_name, st.last_name) student_name,
                       snapshot.fee_type_code, snapshot.class_name_snapshot, snapshot.level_snapshot, s.code session_code,
                       snapshot.charge_date, snapshot.adjusted_amount_minor, snapshot.report_paid_minor paid_minor,
                       snapshot.report_waived_minor waived_minor,
                       GREATEST(snapshot.adjusted_amount_minor - snapshot.report_paid_minor - snapshot.report_waived_minor, 0)
                           outstanding_minor
                  FROM (
                """).append(chargeSnapshotSql(c)).append("\n                ) snapshot\n                  JOIN student st ON st.school_id=snapshot.school_id AND st.id=snapshot.student_id\n                  LEFT JOIN academic_session s ON s.school_id=snapshot.school_id AND s.id=snapshot.academic_session_id\n                 ORDER BY snapshot.charge_date DESC, snapshot.created_at DESC LIMIT ? OFFSET ?\n                ");
        List<Object> args = new ArrayList<>(chargeSnapshotArgs(c)); args.add(c.filters().limit()); args.add(c.filters().offset());
        return jdbc.query(sql.toString(), (rs, n) -> new ReceivableRow(string(rs.getObject("id")), string(rs.getObject("student_id")),
                rs.getString("student_name"), rs.getString("fee_type_code"), rs.getString("class_name_snapshot"),
                rs.getString("level_snapshot"), rs.getString("session_code"), rs.getObject("charge_date", LocalDate.class),
                rs.getLong("adjusted_amount_minor"), rs.getLong("paid_minor"), rs.getLong("waived_minor"),
                rs.getLong("outstanding_minor"), 1), args.toArray());
    }

    private List<AgeingBucket> ageing(Context c) {
        String sql = """
                SELECT snapshot.charge_id, snapshot.due_date,
                       GREATEST(snapshot.amount_minor - snapshot.report_paid_minor - snapshot.report_waived_minor, 0) outstanding_minor
                  FROM (
                """ + installmentSnapshotSql(c) + "\n                ) snapshot\n"
                + " WHERE snapshot.amount_minor - snapshot.report_paid_minor - snapshot.report_waived_minor > 0";
        List<Object> args = new ArrayList<>(installmentSnapshotArgs(c));
        Map<String, AgeingAccumulator> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            LocalDate due = date(row.get("due_date")); String bucket = ageingBucket(due, c.asOf());
            AgeingAccumulator acc = buckets.computeIfAbsent(bucket, k -> new AgeingAccumulator());
            acc.amount += number(row.get("outstanding_minor")); acc.count++;
            if (acc.sourceIds.size() < 40) acc.sourceIds.add(string(row.get("charge_id")));
        }
        return buckets.entrySet().stream().map(e -> new AgeingBucket(e.getKey(), e.getValue().amount, e.getValue().count, e.getValue().sourceIds)).toList();
    }

    private List<InstallmentPerformance> installmentPerformance(Context c) {
        String sql = """
                SELECT snapshot.installment_no, COALESCE(SUM(snapshot.amount_minor),0) due,
                       COALESCE(SUM(snapshot.report_paid_minor),0) paid,
                       COALESCE(SUM(GREATEST(snapshot.amount_minor - snapshot.report_paid_minor - snapshot.report_waived_minor, 0)),0) outstanding,
                       COUNT(*) count_rows
                  FROM (
                """ + installmentSnapshotSql(c) + "\n                ) snapshot\n"
                + " GROUP BY snapshot.installment_no ORDER BY snapshot.installment_no";
        List<Object> args = new ArrayList<>(installmentSnapshotArgs(c));
        return jdbc.query(sql.toString(), (rs, n) -> {
            long due = rs.getLong("due"), paid = rs.getLong("paid"), outstanding = rs.getLong("outstanding");
            return new InstallmentPerformance("Installment " + rs.getInt("installment_no"), due, paid, outstanding,
                    dueDateOutstanding(c, rs.getInt("installment_no")), rs.getInt("count_rows"));
        }, args.toArray());
    }

    private long dueDateOutstanding(Context c, int installmentNo) {
        String sql = """
                SELECT COALESCE(SUM(GREATEST(snapshot.amount_minor - snapshot.report_paid_minor - snapshot.report_waived_minor, 0)),0)
                  FROM (
                """ + installmentSnapshotSql(c) + "\n                ) snapshot\n"
                + " WHERE snapshot.installment_no=? AND snapshot.due_date < ?";
        List<Object> args = new ArrayList<>(installmentSnapshotArgs(c));
        args.add(installmentNo); args.add(c.asOf());
        return scalarLong(sql, args.toArray());
    }

    private List<ChannelSummary> channelSummary(List<CollectionRow> rows) {
        Map<String, List<CollectionRow>> byChannel = rows.stream().collect(Collectors.groupingBy(CollectionRow::channel, LinkedHashMap::new, Collectors.toList()));
        return byChannel.entrySet().stream().map(e -> new ChannelSummary(e.getKey(), e.getValue().stream().mapToLong(CollectionRow::amountMinor).sum(),
                e.getValue().stream().mapToLong(CollectionRow::allocatedMinor).sum(), e.getValue().stream().mapToLong(CollectionRow::remainingCreditMinor).sum(),
                e.getValue().stream().mapToLong(CollectionRow::refundedMinor).sum(), e.getValue().size())).toList();
    }

    private List<ChannelSummary> channelSummary(Context c) {
        StringBuilder sql = new StringBuilder("""
                WITH scoped AS (
                    SELECT p.id, p.status, p.amount_minor, COALESCE(p.channel_code_snapshot,'UNKNOWN') channel
                      FROM finance_payment p
                     WHERE p.school_id=?
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        paymentFilters(sql, args, c, "p");
        sql.append("""
                ), allocations AS (
                    SELECT pa.payment_id, COALESCE(SUM(pa.allocated_minor),0) allocated
                      FROM payment_allocation pa JOIN scoped p ON p.id=pa.payment_id
                     WHERE pa.school_id=? AND pa.created_at::date <= ?
                       AND (pa.status='ACTIVE'
                            OR (pa.status='REVERSED' AND NOT EXISTS (
                                SELECT 1 FROM payment_reversal_request rr
                                 WHERE rr.school_id=pa.school_id AND rr.payment_id=pa.payment_id
                                   AND rr.status='POSTED' AND rr.posted_at::date <= ?
                            ))
                            OR (pa.status='REFUNDED' AND NOT EXISTS (
                                SELECT 1 FROM refund_transaction fr
                                 WHERE fr.school_id=pa.school_id AND fr.payment_id=pa.payment_id
                                   AND fr.posted_at::date <= ?
                            )))
                     GROUP BY pa.payment_id
                ), refunds AS (
                    SELECT r.payment_id, COALESCE(SUM(r.amount_minor),0) refunded
                      FROM refund_transaction r JOIN scoped p ON p.id=r.payment_id
                     WHERE r.school_id=? AND r.posted_at::date <= ?
                     GROUP BY r.payment_id
                ), credits AS (
                    SELECT created.payment_id,
                           COALESCE(SUM(CASE WHEN movement.id=created.id THEN created.amount_minor
                                             ELSE -movement.amount_minor END),0) credit
                      FROM student_credit_ledger created JOIN scoped p ON p.id=created.payment_id
                      LEFT JOIN student_credit_ledger movement
                        ON movement.school_id=created.school_id
                       AND (movement.id=created.id OR movement.source_credit_id=created.id)
                       AND movement.entry_date <= ?
                     WHERE created.school_id=? AND created.entry_type='CREATED'
                       AND created.entry_date <= ?
                     GROUP BY created.payment_id
                ), per_payment AS (
                    SELECT p.channel, p.status, p.amount_minor, COALESCE(a.allocated,0) allocated,
                           GREATEST(0,COALESCE(c.credit,0)) credit, COALESCE(r.refunded,0) refunded
                      FROM scoped p
                      LEFT JOIN allocations a ON a.payment_id=p.id
                      LEFT JOIN credits c ON c.payment_id=p.id
                      LEFT JOIN refunds r ON r.payment_id=p.id
                )
                SELECT channel,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN amount_minor ELSE 0 END),0) payment_minor,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN allocated ELSE 0 END),0) allocated_minor,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN credit ELSE 0 END),0) credit_minor,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN refunded ELSE 0 END),0) refunded_minor,
                       COUNT(*) FILTER (WHERE status <> 'REVERSED') payment_count
                  FROM per_payment
                 GROUP BY channel ORDER BY channel
                """);
        args.add(c.schoolId()); args.add(c.effectiveTo()); args.add(c.asOf()); args.add(c.asOf());
        args.add(c.schoolId()); args.add(c.effectiveTo());
        args.add(c.asOf()); args.add(c.schoolId()); args.add(c.effectiveTo());
        return jdbc.query(sql.toString(), (rs, n) -> new ChannelSummary(rs.getString("channel"), rs.getLong("payment_minor"),
                rs.getLong("allocated_minor"), rs.getLong("credit_minor"), rs.getLong("refunded_minor"), rs.getInt("payment_count")),
                args.toArray());
    }

    private List<CashierVarianceRow> cashierVariances(Context c) {
        return jdbc.query("""
                SELECT id, cashier_user_id, status, opened_at::date AS opened_on,
                       COALESCE(expected_cash_minor,0) AS expected_minor,
                       COALESCE(declared_cash_minor,0) AS declared_minor,
                       COALESCE(variance_minor,0) AS variance_value, close_note
                  FROM cashier_session WHERE school_id=? AND opened_at::date BETWEEN ? AND ?
                 ORDER BY opened_at DESC LIMIT ?
                """, (rs, n) -> new CashierVarianceRow(string(rs.getObject("id")), string(rs.getObject("cashier_user_id")),
                rs.getString("status"), rs.getObject("opened_on", LocalDate.class), rs.getLong("expected_minor"),
                rs.getLong("declared_minor"), rs.getLong("variance_value"), rs.getString("close_note")), c.schoolId(), c.from(), c.effectiveTo(), c.filters().limit());
    }

    private List<ProviderSummary> providerSummary(Context c) {
        return jdbc.query("""
                SELECT pt.provider_code, pt.status, COUNT(*) count_rows, COALESCE(SUM(COALESCE(pt.amount_minor,0)),0) amount
                  FROM provider_transaction pt JOIN finance_payment p
                    ON p.school_id=pt.school_id AND p.id=pt.finance_payment_id
                 WHERE pt.school_id=? AND p.academic_session_id=? AND pt.received_at::date BETWEEN ? AND ?
                 GROUP BY pt.provider_code, pt.status ORDER BY pt.provider_code, pt.status
                """, (rs, n) -> new ProviderSummary(rs.getString("provider_code"), rs.getString("status"),
                rs.getInt("count_rows"), rs.getLong("amount")), c.schoolId(), c.filters().academicSessionId(), c.from(), c.effectiveTo());
    }

    /**
     * Collection headline values must be calculated over the complete filtered
     * source set, not over the page returned to the table.  The row query is
     * intentionally paginated; this query is not.
     */
    private CollectionTotals collectionTotals(Context c) {
        StringBuilder sql = new StringBuilder("""
                WITH scoped AS (
                    SELECT p.id, p.status, p.amount_minor
                      FROM finance_payment p
                     WHERE p.school_id=?
                """);
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        paymentFilters(sql, args, c, "p");
        sql.append("""
                ), allocations AS (
                    SELECT pa.payment_id, COALESCE(SUM(pa.allocated_minor),0) allocated
                      FROM payment_allocation pa
                      JOIN scoped p ON p.id=pa.payment_id
                     WHERE pa.school_id=?
                       AND pa.created_at::date <= ?
                       AND (pa.status='ACTIVE'
                            OR (pa.status='REVERSED' AND NOT EXISTS (
                                SELECT 1 FROM payment_reversal_request rr
                                 WHERE rr.school_id=pa.school_id AND rr.payment_id=pa.payment_id
                                   AND rr.status='POSTED' AND rr.posted_at::date <= ?
                            ))
                            OR (pa.status='REFUNDED' AND NOT EXISTS (
                                SELECT 1 FROM refund_transaction fr
                                 WHERE fr.school_id=pa.school_id AND fr.payment_id=pa.payment_id
                                   AND fr.posted_at::date <= ?
                            )))
                     GROUP BY pa.payment_id
                ), refunds AS (
                    SELECT r.payment_id, COALESCE(SUM(r.amount_minor),0) refunded
                      FROM refund_transaction r
                      JOIN scoped p ON p.id=r.payment_id
                     WHERE r.school_id=? AND r.posted_at::date <= ?
                     GROUP BY r.payment_id
                ), credits AS (
                    SELECT created.payment_id,
                           COALESCE(SUM(CASE WHEN movement.id=created.id THEN created.amount_minor
                                             ELSE -movement.amount_minor END),0) credit
                      FROM student_credit_ledger created
                      JOIN scoped p ON p.id=created.payment_id
                      LEFT JOIN student_credit_ledger movement
                        ON movement.school_id=created.school_id
                       AND (movement.id=created.id OR movement.source_credit_id=created.id)
                       AND movement.entry_date <= ?
                     WHERE created.school_id=? AND created.entry_type='CREATED'
                       AND created.entry_date <= ?
                     GROUP BY created.payment_id
                ), per_payment AS (
                    SELECT p.status, p.amount_minor,
                           COALESCE(a.allocated,0) allocated,
                           GREATEST(0, COALESCE(cr.credit,0)) credit,
                           COALESCE(r.refunded,0) refunded
                      FROM scoped p
                      LEFT JOIN allocations a ON a.payment_id=p.id
                      LEFT JOIN credits cr ON cr.payment_id=p.id
                      LEFT JOIN refunds r ON r.payment_id=p.id
                )
                SELECT COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN amount_minor ELSE 0 END),0) payment_total,
                       COALESCE(SUM(allocated),0) allocated,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN credit ELSE 0 END),0) credit,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED' THEN refunded ELSE 0 END),0) refunded,
                       COALESCE(SUM(CASE WHEN status='REVERSED' THEN amount_minor ELSE 0 END),0) reversed,
                       COALESCE(SUM(CASE WHEN status <> 'REVERSED'
                                         THEN ABS(amount_minor - allocated - credit - refunded) ELSE 0 END),0) mismatch,
                       COUNT(*) FILTER (WHERE status <> 'REVERSED'
                                         AND amount_minor <> allocated + credit + refunded) mismatch_count
                  FROM per_payment
                """);
        // paymentFilters already added the scoped-payment arguments. The CTE
        // arguments follow in the same order as their placeholders.
        args.add(c.schoolId()); args.add(c.effectiveTo()); args.add(c.asOf()); args.add(c.asOf());
        args.add(c.schoolId()); args.add(c.effectiveTo());
        args.add(c.asOf()); args.add(c.schoolId()); args.add(c.effectiveTo());
        Map<String, Object> row = jdbc.queryForList(sql.toString(), args.toArray()).stream()
                .findFirst().orElse(Map.of());
        return new CollectionTotals(number(row.get("payment_total")), number(row.get("allocated")),
                number(row.get("credit")), number(row.get("refunded")), number(row.get("reversed")),
                number(row.get("mismatch")), (int) number(row.get("mismatch_count")));
    }

    private List<DocumentStatusRow> documentStatuses(String type, String table, String amountColumn, String dateColumn, Context c) {
        StringBuilder sql = new StringBuilder("SELECT status, COUNT(*) count_rows, COALESCE(SUM(" + amountColumn + "),0) amount FROM " + table + " WHERE school_id=?");
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        if (c.filters().academicSessionId() != null) { sql.append(" AND academic_session_id=?"); args.add(c.filters().academicSessionId()); }
        sql.append(" AND ").append(dateColumn).append(" BETWEEN ? AND ? GROUP BY status ORDER BY status"); args.add(c.from()); args.add(c.effectiveTo());
        return jdbc.query(sql.toString(), (rs, n) -> new DocumentStatusRow(type, rs.getString("status"), rs.getInt("count_rows"), rs.getLong("amount")), args.toArray());
    }

    private Map<String, Object> documentTotals(String table, String alias, String amountColumn, String outstandingColumn,
                                                List<Object> filterArgs, String where, Context c) {
        String sql = "SELECT COALESCE(SUM(" + amountColumn + "),0) amount_minor, "
                + "COALESCE(SUM(" + outstandingColumn + "),0) outstanding_minor, COUNT(*) row_count "
                + "FROM " + table + " " + alias + " WHERE " + alias + ".school_id=?" + where;
        return jdbc.queryForList(sql, withSchool(filterArgs, c).toArray()).stream().findFirst().orElse(Map.of());
    }

    private long creditBalance(Context c) {
        return scalarLong("""
                WITH created AS (SELECT id, amount_minor FROM student_credit_ledger WHERE school_id=? AND entry_type='CREATED'),
                     movements AS (SELECT source_credit_id, SUM(amount_minor) amount FROM student_credit_ledger
                                     WHERE school_id=? AND entry_type IN ('CONSUMED','REFUNDED','REVERSED')
                                       AND entry_date <= ? GROUP BY source_credit_id)
                SELECT COALESCE(SUM(GREATEST(0, created.amount_minor-COALESCE(movements.amount,0))),0)
                  FROM created LEFT JOIN movements ON movements.source_credit_id=created.id
                 WHERE EXISTS (SELECT 1 FROM student_credit_ledger l JOIN finance_payment p ON p.school_id=l.school_id AND p.id=l.payment_id
                                WHERE l.id=created.id AND p.school_id=? AND p.academic_session_id=?
                                  AND p.payment_date BETWEEN ? AND ? AND l.entry_date <= ?)
                """, c.schoolId(), c.schoolId(), c.asOf(), c.schoolId(), c.filters().academicSessionId(),
                c.from(), c.effectiveTo(), c.asOf());
    }

    private long refundTotal(Context c) {
        return scalarLong("""
                SELECT COALESCE(SUM(r.amount_minor),0) FROM refund_transaction r
                  JOIN finance_payment p ON p.school_id=r.school_id AND p.id=r.payment_id
                 WHERE r.school_id=? AND p.academic_session_id=? AND r.posted_at::date BETWEEN ? AND ?
                   AND r.posted_at::date <= ?
                """, c.schoolId(), c.filters().academicSessionId(), c.from(), c.effectiveTo(), c.asOf());
    }

    private Map<UUID, Long> creditByPayment(Context c) {
        return jdbc.query("""
                WITH scoped AS (
                    SELECT p.id FROM finance_payment p
                     WHERE p.school_id=?
                """ + paymentFilterSql(c, "p") + """
                ), created AS (
                    SELECT l.id, l.payment_id, l.amount_minor
                      FROM student_credit_ledger l JOIN scoped p ON p.id=l.payment_id
                     WHERE l.school_id=? AND l.entry_type='CREATED' AND l.entry_date <= ?
                ), movements AS (
                    SELECT source_credit_id, SUM(amount_minor) amount
                      FROM student_credit_ledger
                     WHERE school_id=? AND entry_type IN ('CONSUMED','REFUNDED','REVERSED')
                       AND entry_date <= ?
                     GROUP BY source_credit_id
                )
                SELECT created.payment_id,
                       COALESCE(SUM(GREATEST(0, created.amount_minor-COALESCE(movements.amount,0))),0) credit
                  FROM created LEFT JOIN movements ON movements.source_credit_id=created.id
                 GROUP BY created.payment_id
                """, (rs, n) -> Map.entry(rs.getObject("payment_id", UUID.class), rs.getLong("credit")),
                creditArgs(c))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Context context(ReportFilters raw, boolean sessionRequired, boolean datesRequired) {
        ReportFilters input = raw == null ? new ReportFilters(null, null, null, null, null, null, null, null, null, 500, 0) : raw;
        UUID schoolId = TenantContext.get();
        UUID sessionId = input.academicSessionId(); String sessionCode = null; LocalDate sessionStart = null; LocalDate sessionEnd = null;
        if (sessionId != null) {
            List<Map<String, Object>> sessions = jdbc.queryForList("SELECT code, start_date, end_date FROM academic_session WHERE school_id=? AND id=?", schoolId, sessionId);
            if (sessions.isEmpty()) throw ApiException.structured(org.springframework.http.HttpStatus.NOT_FOUND, "REPORT_SESSION_NOT_FOUND",
                    "The selected academic session does not belong to this school.", Map.of("academicSessionId", "Choose a session from this school."), List.of());
            Map<String, Object> row = sessions.getFirst(); sessionCode = string(row.get("code")); sessionStart = date(row.get("start_date")); sessionEnd = date(row.get("end_date"));
        }
        if (sessionRequired && sessionId == null) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REPORT_CONTEXT_REQUIRED", "Select an academic session before viewing this report.", Map.of("academicSessionId", "Academic session is required."), List.of());
        LocalDate from = input.fromDate() != null ? input.fromDate() : sessionStart;
        LocalDate to = input.toDate() != null ? input.toDate() : sessionEnd;
        if (datesRequired && (from == null || to == null)) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REPORT_DATE_REQUIRED", "Choose a reporting date range before viewing this report.", Map.of("fromDate", "Start date is required.", "toDate", "End date is required."), List.of());
        if (from == null) from = LocalDate.of(1900, 1, 1); if (to == null) to = LocalDate.now();
        if (from.isAfter(to)) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "REPORT_DATE_RANGE_INVALID",
                "The reporting start date must be on or before the end date.", Map.of("fromDate", "Start date must not be after end date."), List.of());
        LocalDate asOf = input.asOfDate() == null ? to : input.asOfDate();
        if (asOf.isBefore(from)) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "REPORT_AS_OF_INVALID",
                "The as-of date cannot precede the reporting range.", Map.of("asOfDate", "Choose an as-of date on or after the start date."), List.of());
        if (sessionId != null && (from.isBefore(sessionStart) || to.isAfter(sessionEnd) || asOf.isAfter(sessionEnd))) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "REPORT_DATE_OUTSIDE_SESSION",
                    "The reporting dates must stay inside the selected academic session.",
                    Map.of("fromDate", "Choose a date inside the selected session.",
                            "toDate", "Choose a date inside the selected session.",
                            "asOfDate", "Choose an as-of date inside the selected session."), List.of());
        }
        ReportFilters normalized = new ReportFilters(sessionId, from, to, asOf, input.classId(), trim(input.level()), upper(input.feeTypeCode()), upper(input.channelCode()), upper(input.status()), input.limit(), input.offset());
        OffsetDateTime generated = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate effectiveTo = to.isBefore(asOf) ? to : asOf;
        OffsetDateTime through = effectiveTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
        if (through.isAfter(generated)) through = generated;
        long lag = Math.max(0, Duration.between(through, generated).getSeconds());
        Map<String, String> applied = new LinkedHashMap<>();
        put(applied, "academicSessionId", sessionId); put(applied, "sessionCode", sessionCode); put(applied, "fromDate", from); put(applied, "toDate", to); put(applied, "asOfDate", asOf);
        put(applied, "classId", input.classId()); put(applied, "level", normalized.level()); put(applied, "feeTypeCode", normalized.feeTypeCode()); put(applied, "channelCode", normalized.channelCode()); put(applied, "status", normalized.status());
        ReportMeta meta = new ReportMeta(sessionId, sessionCode, from, to, asOf, generated, through,
                through.equals(generated) ? "LIVE" : "HISTORICAL", lag, "XAF", applied,
                "POSTED_SOURCE_SNAPSHOTS_AND_POSTED_JOURNALS");
        return new Context(schoolId, normalized, meta, from, to, asOf);
    }

    private <T> ReportEnvelope<T> envelope(Context c, T data) { return new ReportEnvelope<>(c.meta(), data); }

    private void chargeFilters(StringBuilder sql, List<Object> args, Context c, String alias) {
        sql.append(" AND ").append(alias).append(".school_id=?"); args.add(c.schoolId());
        if (c.filters().academicSessionId() != null) { sql.append(" AND ").append(alias).append(".academic_session_id=?"); args.add(c.filters().academicSessionId()); }
        if (c.filters().classId() != null) { sql.append(" AND ").append(alias).append(".school_class_id_snapshot=?"); args.add(c.filters().classId()); }
        if (c.filters().level() != null) { sql.append(" AND ").append(alias).append(".level_snapshot=?"); args.add(c.filters().level()); }
        if (c.filters().feeTypeCode() != null) { sql.append(" AND ").append(alias).append(".fee_type_code=?"); args.add(c.filters().feeTypeCode()); }
        dateFilters(sql, args, alias + ".charge_date", c);
        if (c.filters().status() != null) { sql.append(" AND ").append(alias).append(".status=?"); args.add(c.filters().status()); }
    }

    private String chargeFilterSql(Context c, String alias) { StringBuilder sql = new StringBuilder(); List<Object> ignored = new ArrayList<>(); chargeFilters(sql, ignored, c, alias); return sql.toString(); }

    /**
     * Rebuilds the mutable charge aggregates from immutable allocation and
     * adjustment events at the report cut-off. The charge row remains the
     * historical dimension/snapshot; only its current mutable rollups are
     * deliberately excluded from as-of arithmetic.
     */
    private String chargeSnapshotSql(Context c) {
        return """
                SELECT c.*,
                       COALESCE(paid.report_paid_minor,0) report_paid_minor,
                       COALESCE(waived.report_waived_minor,0) report_waived_minor
                  FROM student_charge c
                  LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pa.allocated_minor),0) report_paid_minor
                          FROM payment_allocation pa
                          JOIN charge_installment i
                            ON i.school_id=pa.school_id AND i.id=pa.charge_installment_id
                          JOIN finance_payment p
                            ON p.school_id=pa.school_id AND p.id=pa.payment_id
                         WHERE pa.school_id=c.school_id
                           AND i.charge_id=c.id
                           AND p.academic_session_id=c.academic_session_id
                           AND p.status <> 'DRAFT'
                           AND pa.created_at::date <= ?
                           AND p.payment_date <= ?
                           AND (pa.status='ACTIVE'
                                OR (pa.status='REVERSED' AND NOT EXISTS (
                                    SELECT 1 FROM payment_reversal_request rr
                                     WHERE rr.school_id=pa.school_id AND rr.payment_id=pa.payment_id
                                       AND rr.status='POSTED' AND rr.posted_at::date <= ?
                                ))
                                OR (pa.status='REFUNDED' AND NOT EXISTS (
                                    SELECT 1 FROM refund_transaction fr
                                     WHERE fr.school_id=pa.school_id AND fr.payment_id=pa.payment_id
                                       AND fr.posted_at::date <= ?
                                )))
                  ) paid ON TRUE
                  LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(ca.amount_minor),0) report_waived_minor
                          FROM charge_adjustment ca
                         WHERE ca.school_id=c.school_id AND ca.charge_id=c.id
                           AND ca.adjustment_type='WAIVER' AND ca.status='POSTED'
                           AND ca.effective_date <= ?
                  ) waived ON TRUE
                 WHERE c.status NOT IN ('DRAFT','REVERSED')
                """ + chargeFilterSql(c, "c");
    }

    private List<Object> chargeSnapshotArgs(Context c) {
        List<Object> args = new ArrayList<>(List.of(c.effectiveTo(), c.effectiveTo(), c.asOf(), c.asOf(), c.asOf()));
        args.addAll(chargeFilterArgs(c));
        return args;
    }

    private String installmentSnapshotSql(Context c) {
        return """
                SELECT i.*,
                       CASE WHEN EXISTS (
                                    SELECT 1 FROM payment_allocation pa0
                                     WHERE pa0.school_id=i.school_id AND pa0.charge_installment_id=i.id
                                )
                            THEN COALESCE(paid.report_paid_minor,0) ELSE i.paid_minor END report_paid_minor,
                       CASE WHEN EXISTS (
                                    SELECT 1 FROM charge_adjustment ca0
                                     WHERE ca0.school_id=i.school_id AND ca0.installment_id=i.id
                                )
                            THEN COALESCE(waived.report_waived_minor,0) ELSE i.waived_minor END report_waived_minor
                  FROM charge_installment i
                  JOIN student_charge c ON c.school_id=i.school_id AND c.id=i.charge_id
                  LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pa.allocated_minor),0) report_paid_minor
                          FROM payment_allocation pa
                          JOIN finance_payment p
                            ON p.school_id=pa.school_id AND p.id=pa.payment_id
                         WHERE pa.school_id=i.school_id AND pa.charge_installment_id=i.id
                           AND p.academic_session_id=c.academic_session_id
                           AND p.status <> 'DRAFT'
                           AND pa.created_at::date <= ?
                           AND p.payment_date <= ?
                           AND (pa.status='ACTIVE'
                                OR (pa.status='REVERSED' AND NOT EXISTS (
                                    SELECT 1 FROM payment_reversal_request rr
                                     WHERE rr.school_id=pa.school_id AND rr.payment_id=pa.payment_id
                                       AND rr.status='POSTED' AND rr.posted_at::date <= ?
                                ))
                                OR (pa.status='REFUNDED' AND NOT EXISTS (
                                    SELECT 1 FROM refund_transaction fr
                                     WHERE fr.school_id=pa.school_id AND fr.payment_id=pa.payment_id
                                       AND fr.posted_at::date <= ?
                                )))
                  ) paid ON TRUE
                  LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(ca.amount_minor),0) report_waived_minor
                          FROM charge_adjustment ca
                         WHERE ca.school_id=i.school_id AND ca.installment_id=i.id
                           AND ca.adjustment_type='WAIVER' AND ca.status='POSTED'
                           AND ca.effective_date <= ?
                  ) waived ON TRUE
                 WHERE c.status NOT IN ('DRAFT','REVERSED')
                """ + chargeFilterSql(c, "c");
    }

    private List<Object> installmentSnapshotArgs(Context c) {
        List<Object> args = new ArrayList<>(List.of(c.effectiveTo(), c.effectiveTo(), c.asOf(), c.asOf(), c.asOf()));
        args.addAll(chargeFilterArgs(c));
        return args;
    }

    private List<Object> chargeFilterArgs(Context c) {
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        if (c.filters().academicSessionId() != null) args.add(c.filters().academicSessionId());
        if (c.filters().classId() != null) args.add(c.filters().classId());
        if (c.filters().level() != null) args.add(c.filters().level());
        if (c.filters().feeTypeCode() != null) args.add(c.filters().feeTypeCode());
        args.add(c.from()); args.add(c.effectiveTo());
        if (c.filters().status() != null) args.add(c.filters().status());
        return args;
    }

    private void paymentFilters(StringBuilder sql, List<Object> args, Context c, String alias) {
        if (c.filters().academicSessionId() != null) { sql.append(" AND ").append(alias).append(".academic_session_id=?"); args.add(c.filters().academicSessionId()); }
        if (c.filters().channelCode() != null) { sql.append(" AND ").append(alias).append(".channel_code_snapshot=?"); args.add(c.filters().channelCode()); }
        if (c.filters().status() != null) { sql.append(" AND ").append(alias).append(".status=?"); args.add(c.filters().status()); }
        sql.append(" AND ").append(alias).append(".payment_date BETWEEN ? AND ?"); args.add(c.from()); args.add(c.effectiveTo());
    }

    private String paymentFilterSql(Context c, String alias) {
        StringBuilder sql = new StringBuilder();
        if (c.filters().academicSessionId() != null) sql.append(" AND ").append(alias).append(".academic_session_id=?");
        if (c.filters().channelCode() != null) sql.append(" AND ").append(alias).append(".channel_code_snapshot=?");
        if (c.filters().status() != null) sql.append(" AND ").append(alias).append(".status=?");
        sql.append(" AND ").append(alias).append(".payment_date BETWEEN ? AND ?");
        return sql.toString();
    }

    private Object[] creditArgs(Context c) {
        List<Object> args = new ArrayList<>(List.of(c.schoolId()));
        if (c.filters().academicSessionId() != null) args.add(c.filters().academicSessionId());
        if (c.filters().channelCode() != null) args.add(c.filters().channelCode());
        if (c.filters().status() != null) args.add(c.filters().status());
        args.add(c.from()); args.add(c.effectiveTo());
        args.add(c.schoolId()); args.add(c.effectiveTo());
        args.add(c.schoolId()); args.add(c.asOf());
        return args.toArray();
    }

    private String documentFilterSql(Context c, String alias) {
        StringBuilder sql = new StringBuilder();
        if (c.filters().academicSessionId() != null) { sql.append(" AND ").append(alias).append(".academic_session_id=?"); }
        sql.append(" AND ").append(alias).append(".issue_date BETWEEN ? AND ?");
        if (c.filters().status() != null) sql.append(" AND ").append(alias).append(".status=?");
        return sql.toString();
    }

    private List<Object> documentArgs(Context c) {
        List<Object> args = new ArrayList<>(); if (c.filters().academicSessionId() != null) args.add(c.filters().academicSessionId());
        args.add(c.from()); args.add(c.effectiveTo()); if (c.filters().status() != null) args.add(c.filters().status()); return args;
    }

    private List<Object> withLimit(List<Object> args, Context c) { List<Object> result = new ArrayList<>(); result.add(c.schoolId()); result.addAll(args); result.add(c.filters().limit()); result.add(c.filters().offset()); return result; }

    private List<Object> withSchool(List<Object> args, Context c) { List<Object> result = new ArrayList<>(); result.add(c.schoolId()); result.addAll(args); return result; }

    private void dateFilters(StringBuilder sql, List<Object> args, String column, Context c) { sql.append(" AND ").append(column).append(" BETWEEN ? AND ?"); args.add(c.from()); args.add(c.effectiveTo()); }

    private long scalarLong(String sql, Object... args) { return jdbc.query(sql, args, rs -> rs.next() ? rs.getLong(1) : 0L); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private long number(Object value, String ignored) { return number(value); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private UUID id(Object value) { return value instanceof UUID u ? u : value == null ? null : UUID.fromString(value.toString()); }
    private LocalDate date(Object value) { return value instanceof LocalDate d ? d : value == null ? null : LocalDate.parse(value.toString()); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upper(String value) { String v = trim(value); return v == null ? null : v.toUpperCase(Locale.ROOT); }
    private static void put(Map<String, String> map, String key, Object value) { if (value != null) map.put(key, value.toString()); }
    private static String ageingBucket(LocalDate due, LocalDate asOf) {
        if (due == null || !due.isBefore(asOf)) return "CURRENT";
        long days = Duration.between(due.atStartOfDay(), asOf.atStartOfDay()).toDays();
        if (days <= 30) return "1_30"; if (days <= 60) return "31_60"; if (days <= 90) return "61_90"; return "90_PLUS";
    }
    private static String ArraysCsv(String[] values) { return java.util.Arrays.stream(values).map(v -> v == null ? "" : "\"" + v.replace("\"", "\"\"") + "\"").collect(Collectors.joining(",")); }
    private static String upperOrDefault(String value, String fallback) { return value == null ? fallback : value; }

    private record Context(UUID schoolId, ReportFilters filters, ReportMeta meta, LocalDate from, LocalDate to, LocalDate asOf) {
        private LocalDate effectiveTo() { return to.isBefore(asOf) ? to : asOf; }
    }
    private record RawLedger(String sourceId, String number, LocalDate entryDate, String sourceType, String status, String description,
                             String accountCode, long debit, long credit, long running) {}
    private record CollectionTotals(long paymentTotal, long allocated, long credit, long refunded, long reversed,
                                    long mismatch, int mismatchCount) {}
    private static final class AgeingAccumulator { long amount; int count; List<String> sourceIds = new ArrayList<>(); }
}
