package com.bbc.sms.finance.accounts;

import com.bbc.sms.documents.GeneratedDocument;
import com.bbc.sms.documents.GeneratedDocumentRepository;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.collections.CollectionDtos.StudentSearchView;
import com.bbc.sms.finance.documents.FinancePdfRenderer;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.bbc.sms.finance.accounts.FinanceAccountDtos.*;

/** Unified student finance history over the V2 and legacy payment ledgers. */
@Service
public class FinanceAccountService {
    private static final String CURRENCY = "XAF";

    private final JdbcTemplate jdbc;
    private final FinancePolicyService financePolicy;
    private final DocumentSequenceService sequences;
    private final OfficialDocumentService officialDocuments;
    private final GeneratedDocumentRepository generatedDocuments;
    private final FinancePdfRenderer pdf;
    private final AuditService audit;

    public FinanceAccountService(JdbcTemplate jdbc,
                                 FinancePolicyService financePolicy,
                                 DocumentSequenceService sequences,
                                 OfficialDocumentService officialDocuments,
                                 GeneratedDocumentRepository generatedDocuments,
                                 FinancePdfRenderer pdf,
                                 AuditService audit) {
        this.jdbc = jdbc;
        this.financePolicy = financePolicy;
        this.sequences = sequences;
        this.officialDocuments = officialDocuments;
        this.generatedDocuments = generatedDocuments;
        this.pdf = pdf;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public StudentAccountView student(UUID studentId) {
        financePolicy.requireSchool("FINANCE_STUDENT_ACCOUNT_VIEW");
        return account(studentId);
    }

    @Transactional(readOnly = true)
    public List<StudentSearchView> search(String query) {
        financePolicy.requireSchool("FINANCE_STUDENT_ACCOUNT_VIEW");
        UUID schoolId = TenantContext.get();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT s.id,trim(concat_ws(' ',NULLIF(s.first_name,''),NULLIF(s.last_name,''))),
                       s.matricule,e.id,e.academic_session_id,
                       COALESCE(c.name,e.class_name_snapshot),e.enrolled_on,e.exited_on
                  FROM student_enrollment e
                  JOIN student s ON s.id=e.student_id AND s.school_id=e.school_id AND s.active=true
                  JOIN academic_session session ON session.id=e.academic_session_id
                                               AND session.school_id=e.school_id
                                               AND session.is_current=true
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.status='ACTIVE'
                 ORDER BY COALESCE(c.name,e.class_name_snapshot),s.last_name,s.first_name
                """, (rs, n) -> new StudentSearchView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class),
                        rs.getString(6), rs.getObject(7, LocalDate.class), rs.getObject(8, LocalDate.class),
                        0, 0), schoolId).stream()
                .filter(v -> needle.isBlank()
                        || contains(v.studentName(), needle)
                        || contains(v.matricule(), needle)
                        || contains(v.className(), needle))
                .limit(100)
                .toList();
    }

    @Transactional
    public ConsolidatedReceiptView createConsolidatedReceipt(UUID studentId) {
        financePolicy.requireSchool("FINANCE_CONSOLIDATED_RECEIPT_CREATE");
        StudentAccountView account = account(studentId);
        UUID schoolId = TenantContext.get();
        GeneratedDocument existing = generatedDocuments
                .findFirstBySchoolIdAndDocumentTypeAndAggregateTypeAndAggregateIdAndAggregateVersionAndLocale(
                        schoolId, "CONSOLIDATED_RECEIPT", "FinanceStudentAccount",
                        studentId.toString(), account.snapshotHash(), "fr")
                .orElse(null);
        if (existing != null) return consolidatedView(account, existing);

        String number = sequences.allocate("CONSOLIDATED_RECEIPT",
                String.valueOf(LocalDate.now().getYear()),
                "CR/" + LocalDate.now().getYear() + "/", 6);
        ConsolidatedReceiptView draft = new ConsolidatedReceiptView(
                account.studentId(), account.studentName(), account.matricule(), account.className(),
                account.sessionLabel(), number, LocalDate.now(), account.billedMinor(),
                account.paidMinor(), account.outstandingMinor(), account.creditMinor(),
                account.currency(), "ISSUED", account.snapshotHash(), null, null, null,
                account.payments());
        byte[] content = pdf.consolidatedReceipt(draft, schoolSnapshot(), verificationBase());
        GeneratedDocumentView document = officialDocuments.registerPdf(
                "CONSOLIDATED_RECEIPT", "FinanceStudentAccount", studentId.toString(),
                account.snapshotHash(), "fr", "Relevé des paiements / Consolidated receipt",
                "STAFF", content, "FINANCE_CR:" + studentId + ":" + account.snapshotHash(), number);
        ConsolidatedReceiptView result = consolidatedView(account, document);
        audit.record("FINANCE_CONSOLIDATED_RECEIPT_ISSUED", "FinanceStudentAccount",
                studentId.toString(), null, result, null);
        return result;
    }

    @Transactional
    public ConsolidatedReceiptPdf consolidatedReceiptPdf(UUID studentId) {
        ConsolidatedReceiptView receipt = createConsolidatedReceipt(studentId);
        return new ConsolidatedReceiptPdf(receipt.receiptNumber(),
                officialDocuments.content(receipt.generatedDocumentId()));
    }

    private StudentAccountView account(UUID studentId) {
        UUID schoolId = TenantContext.get();
        StudentIdentity identity = jdbc.query("""
                SELECT s.id,trim(concat_ws(' ',NULLIF(s.first_name,''),NULLIF(s.last_name,''))),
                       s.matricule,COALESCE(c.name,s.class_name),a.label
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.school_class_id,e.academic_session_id
                         FROM student_enrollment e
                         JOIN academic_session session ON session.id=e.academic_session_id
                                                       AND session.school_id=e.school_id
                        WHERE e.school_id=? AND e.student_id=s.id AND e.status='ACTIVE'
                          AND session.is_current=true
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) current_enrollment ON true
                  LEFT JOIN school_class c ON c.id=current_enrollment.school_class_id
                                           AND c.school_id=s.school_id
                  LEFT JOIN academic_session a ON a.id=current_enrollment.academic_session_id
                                               AND a.school_id=s.school_id
                 WHERE s.school_id=? AND s.id=? AND s.active=true
                """, rs -> rs.next() ? new StudentIdentity(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)) : null,
                schoolId, schoolId, studentId);
        if (identity == null) throw ApiException.notFound("Élève");

        BillingTotals billing = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(c.adjusted_amount_minor)
                                   FROM student_charge c
                                  WHERE c.school_id=? AND c.student_id=?
                                    AND c.status NOT IN ('VOID','CANCELLED')),0),
                       COALESCE((SELECT MAX(f.total) FROM student_fee f
                                  WHERE f.school_id=? AND f.student_id=?),0)
                """, (rs, n) -> new BillingTotals(rs.getLong(1), rs.getLong(2)),
                schoolId, studentId, schoolId, studentId);
        long billed = billing.v2Billed() > 0 ? billing.v2Billed() : billing.legacyBilled();
        List<AccountPaymentView> payments = jdbc.query(paymentSql(), (rs, n) -> new AccountPaymentView(
                rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("receipt_no"),
                rs.getObject("payment_date", LocalDate.class), rs.getLong("amount_minor"),
                rs.getLong("refunded_minor"), rs.getLong("net_amount_minor"), rs.getString("currency"),
                rs.getString("channel_code"), rs.getString("channel_label"),
                rs.getString("treasury_account_name"), rs.getString("reference"),
                rs.getLong("allocated_minor"), rs.getLong("credit_minor"), rs.getString("status"),
                rs.getObject("journal_entry_id", UUID.class)), schoolId, studentId, schoolId, studentId);

        long paid = payments.stream()
                .filter(p -> !"REVERSED".equals(p.status()) && !"VOID".equals(p.status()))
                .mapToLong(AccountPaymentView::netAmountMinor).sum();
        long outstanding = Math.max(0, billed - paid);
        long credit = Math.max(0, paid - billed);
        String snapshot = snapshotHash(identity.id(), billed, payments);
        return new StudentAccountView(identity.id(), identity.name(), identity.matricule(), identity.className(),
                identity.sessionLabel(), billed, paid, outstanding, credit, CURRENCY, snapshot, payments);
    }

    private ConsolidatedReceiptView consolidatedView(StudentAccountView account, GeneratedDocument document) {
        return new ConsolidatedReceiptView(account.studentId(), account.studentName(), account.matricule(),
                account.className(), account.sessionLabel(), document.getDocumentNumber(), LocalDate.now(),
                account.billedMinor(), account.paidMinor(), account.outstandingMinor(), account.creditMinor(),
                account.currency(), document.getStatus(), account.snapshotHash(), document.getId(),
                document.getDocumentNumber(), document.getStatus(), account.payments());
    }

    private ConsolidatedReceiptView consolidatedView(StudentAccountView account, GeneratedDocumentView document) {
        return new ConsolidatedReceiptView(account.studentId(), account.studentName(), account.matricule(),
                account.className(), account.sessionLabel(), document.documentNumber(), LocalDate.now(),
                account.billedMinor(), account.paidMinor(), account.outstandingMinor(), account.creditMinor(),
                account.currency(), document.status(), account.snapshotHash(), document.id(),
                document.documentNumber(), document.status(), account.payments());
    }

    private static String paymentSql() {
        return """
                WITH refunds AS (
                    SELECT school_id,payment_id,COALESCE(SUM(amount_minor),0) refunded_minor
                      FROM refund_transaction
                     GROUP BY school_id,payment_id
                ), allocations AS (
                    SELECT school_id,payment_id,COALESCE(SUM(allocated_minor),0) allocated_minor
                      FROM payment_allocation
                     WHERE status='ACTIVE'
                     GROUP BY school_id,payment_id
                )
                SELECT * FROM (
                    SELECT p.id,'COLLECTION' source,p.receipt_no,p.payment_date,p.amount_minor,
                           COALESCE(r.refunded_minor,0) refunded_minor,
                           CASE WHEN p.status IN ('REVERSED','VOID') THEN 0
                                ELSE GREATEST(0,p.amount_minor-COALESCE(r.refunded_minor,0)) END net_amount_minor,
                           p.currency,p.channel_code_snapshot channel_code,
                           COALESCE(pc.label_fr,p.channel_code_snapshot) channel_label,
                           ta.display_name treasury_account_name,p.reference,
                           COALESCE(a.allocated_minor,0) allocated_minor,
                           CASE WHEN p.status IN ('REVERSED','VOID') THEN 0
                                ELSE GREATEST(0,p.amount_minor-COALESCE(r.refunded_minor,0)-COALESCE(a.allocated_minor,0)) END credit_minor,
                           p.status,p.journal_entry_id
                      FROM finance_payment p
                      LEFT JOIN refunds r ON r.school_id=p.school_id AND r.payment_id=p.id
                      LEFT JOIN allocations a ON a.school_id=p.school_id AND a.payment_id=p.id
                      LEFT JOIN payment_channel pc ON pc.school_id=p.school_id AND pc.code=p.channel_code_snapshot
                      LEFT JOIN treasury_account ta ON ta.school_id=p.school_id AND ta.id=p.treasury_account_id
                     WHERE p.school_id=? AND p.student_id=?
                    UNION ALL
                    SELECT p.id,'LEGACY_PAYMENT' source,p.receipt_no,p.paid_on,p.amount,
                           0 refunded_minor,p.amount net_amount_minor,'XAF',p.method channel_code,
                           COALESCE(pc.label_fr,p.method) channel_label,ta.display_name treasury_account_name,
                           p.reference,0 allocated_minor,0 credit_minor,'POSTED' status,p.journal_entry_id
                      FROM payment p
                      LEFT JOIN payment_channel pc ON pc.school_id=p.school_id AND pc.code=p.method
                      LEFT JOIN treasury_account ta ON ta.school_id=p.school_id AND ta.id=p.treasury_account_id
                     WHERE p.school_id=? AND p.student_id=?
                ) history
                ORDER BY payment_date DESC,receipt_no DESC
                """;
    }

    private FinancePdfRenderer.SchoolSnapshot schoolSnapshot() {
        return jdbc.queryForObject("SELECT code,name,authority,address,city,country,phone,email FROM school WHERE id=?",
                (rs, n) -> new FinancePdfRenderer.SchoolSnapshot(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8)), TenantContext.get());
    }

    private static String verificationBase() { return "/api/official-documents/verify/"; }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String snapshotHash(UUID studentId, long billed, List<AccountPaymentView> payments) {
        String value = studentId + "|" + billed + "|" + payments.stream()
                .map(p -> p.id() + ":" + p.status() + ":" + p.netAmountMinor() + ":" + p.paymentDate())
                .reduce("", (a, b) -> a + "|" + b);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record StudentIdentity(UUID id, String name, String matricule,
                                   String className, String sessionLabel) {}

    private record BillingTotals(long v2Billed, long legacyBilled) {}
}
