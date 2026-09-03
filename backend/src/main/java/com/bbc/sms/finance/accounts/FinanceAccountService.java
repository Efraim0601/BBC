package com.bbc.sms.finance.accounts;

import com.bbc.sms.documents.GeneratedDocument;
import com.bbc.sms.documents.GeneratedDocumentRepository;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.documents.FinancePdfRenderer;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.accounts.FinanceAccountDtos.*;

/** Unified student finance history over the V2 and legacy payment ledgers. */
@Service
public class FinanceAccountService {
    private static final String CURRENCY = "XAF";
    private static final String CONSOLIDATED_RECEIPT_TEMPLATE_VERSION = "layout-v2";

    private final JdbcTemplate jdbc;
    private final FinancePolicyService financePolicy;
    private final DocumentSequenceService sequences;
    private final OfficialDocumentService officialDocuments;
    private final GeneratedDocumentRepository generatedDocuments;
    private final FinancePdfRenderer pdf;
    private final AuditService audit;
    private final TeacherScopeService teacherScope;

    public FinanceAccountService(JdbcTemplate jdbc,
                                 FinancePolicyService financePolicy,
                                 DocumentSequenceService sequences,
                                 OfficialDocumentService officialDocuments,
                                 GeneratedDocumentRepository generatedDocuments,
                                 FinancePdfRenderer pdf,
                                 AuditService audit,
                                 TeacherScopeService teacherScope) {
        this.jdbc = jdbc;
        this.financePolicy = financePolicy;
        this.sequences = sequences;
        this.officialDocuments = officialDocuments;
        this.generatedDocuments = generatedDocuments;
        this.pdf = pdf;
        this.audit = audit;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public StudentAccountView student(UUID studentId) {
        financePolicy.requireSchool("FINANCE_STUDENT_ACCOUNT_VIEW");
        teacherScope.assertSectionStudent(studentId);
        return account(studentId);
    }

    @Transactional(readOnly = true)
    public StudentAccountContextView context() {
        financePolicy.requireSchool("FINANCE_STUDENT_ACCOUNT_VIEW");
        UUID schoolId = TenantContext.get();
        Set<UUID> allowedClasses = teacherScope.allowedClassIds();
        List<StudentAccountClassOption> classes = jdbc.query("""
                SELECT c.id,c.name,c.level,c.subsystem,COUNT(DISTINCT student.id)
                  FROM school_class c
                  LEFT JOIN academic_session session
                    ON session.school_id=c.school_id AND session.is_current=true
                  LEFT JOIN academic_cohort_programme programme
                    ON programme.school_id=c.school_id
                   AND programme.academic_session_id=session.id
                   AND programme.school_class_id=c.id
                   AND programme.active=true
                  LEFT JOIN student_enrollment enrollment
                    ON enrollment.school_id=c.school_id
                   AND enrollment.academic_session_id=session.id
                   AND enrollment.status='ACTIVE'
                   AND (enrollment.school_class_id=c.id
                        OR (programme.cohort_id IS NOT NULL AND enrollment.cohort_id=programme.cohort_id))
                  LEFT JOIN student student
                    ON student.school_id=c.school_id AND student.id=enrollment.student_id AND student.active=true
                 WHERE c.school_id=?
                 GROUP BY c.id,c.name,c.level,c.subsystem,c.grade_order
                 ORDER BY CASE lower(c.level)
                            WHEN 'maternelle' THEN 1 WHEN 'kindergarten' THEN 1
                            WHEN 'primary' THEN 2 WHEN 'secondary' THEN 3 ELSE 4 END,
                          c.grade_order,c.name
                """, (rs, n) -> new StudentAccountClassOption(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getLong(5)), schoolId).stream()
                .filter(option -> allowedClasses == null || allowedClasses.contains(option.id()))
                .toList();
        return new StudentAccountContextView(classes);
    }

    @Transactional(readOnly = true)
    public List<StudentAccountSearchView> search(String query, UUID classId) {
        financePolicy.requireSchool("FINANCE_STUDENT_ACCOUNT_VIEW");
        UUID schoolId = TenantContext.get();
        Set<UUID> allowedStudents = teacherScope.allowedStudentIds();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        String classFilter = "";
        if (classId != null) {
            teacherScope.assertSectionClass(classId);
            Integer exists = jdbc.query("SELECT 1 FROM school_class WHERE school_id=? AND id=?",
                    rs -> rs.next() ? 1 : null, schoolId, classId);
            if (exists == null) throw ApiException.notFound("Classe");
            classFilter = """
                      AND (e.school_class_id=? OR e.cohort_id=(
                           SELECT programme.cohort_id
                             FROM academic_cohort_programme programme
                            WHERE programme.school_id=e.school_id
                              AND programme.academic_session_id=e.academic_session_id
                              AND programme.school_class_id=?
                              AND programme.active=true
                            LIMIT 1))
                    """;
            args.add(classId);
            args.add(classId);
        }

        String sql = """
                WITH current_students AS (
                    SELECT e.school_id,s.id student_id,
                           trim(concat_ws(' ',NULLIF(s.first_name,''),NULLIF(s.last_name,''))) student_name,
                           s.matricule,e.id enrollment_id,e.academic_session_id,
                           COALESCE((
                               SELECT string_agg(programme_class.name,' / '
                                                 ORDER BY programme.display_order,programme_class.name)
                                 FROM academic_cohort_programme programme
                                 JOIN school_class programme_class
                                   ON programme_class.school_id=programme.school_id
                                  AND programme_class.id=programme.school_class_id
                                WHERE programme.school_id=e.school_id
                                  AND programme.academic_session_id=e.academic_session_id
                                  AND programme.cohort_id=e.cohort_id
                                  AND programme.active=true
                           ),c.name,e.class_name_snapshot) class_name,
                           e.enrolled_on,e.exited_on,s.last_name,s.first_name
                      FROM student_enrollment e
                      JOIN student s ON s.id=e.student_id AND s.school_id=e.school_id AND s.active=true
                      JOIN academic_session session ON session.id=e.academic_session_id
                                                   AND session.school_id=e.school_id
                                                   AND session.is_current=true
                      LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                     WHERE e.school_id=? AND e.status='ACTIVE'
                """ + classFilter + """
                ), charge_totals AS (
                    SELECT charge.student_id,
                           SUM(GREATEST(0,charge.adjusted_amount_minor-charge.waived_minor)) billed
                      FROM student_charge charge
                      JOIN current_students current ON current.school_id=charge.school_id
                                                   AND current.student_id=charge.student_id
                     WHERE charge.status IN ('POSTED','PARTIAL','PAID','WAIVED')
                     GROUP BY charge.student_id
                ), legacy_fees AS (
                    SELECT fee.student_id,MAX(fee.total) billed
                      FROM student_fee fee
                      JOIN current_students current ON current.school_id=fee.school_id
                                                   AND current.student_id=fee.student_id
                     GROUP BY fee.student_id
                ), refunds AS (
                    SELECT refund.payment_id,SUM(refund.amount_minor) refunded
                      FROM refund_transaction refund
                      JOIN finance_payment refunded_payment
                        ON refunded_payment.school_id=refund.school_id AND refunded_payment.id=refund.payment_id
                      JOIN current_students current ON current.school_id=refunded_payment.school_id
                                                   AND current.student_id=refunded_payment.student_id
                     GROUP BY refund.payment_id
                ), v2_payments AS (
                    SELECT payment.student_id,
                           SUM(CASE WHEN payment.status IN ('REVERSED','VOID') THEN 0
                                    ELSE GREATEST(0,payment.amount_minor-COALESCE(refunds.refunded,0)) END) paid,
                           COUNT(*) FILTER (WHERE payment.status NOT IN ('REVERSED','VOID')) payment_count
                      FROM finance_payment payment
                      JOIN current_students current ON current.school_id=payment.school_id
                                                   AND current.student_id=payment.student_id
                      LEFT JOIN refunds ON refunds.payment_id=payment.id
                     GROUP BY payment.student_id
                ), legacy_payments AS (
                    SELECT payment.student_id,SUM(payment.amount) paid,COUNT(*) payment_count
                      FROM payment payment
                      JOIN current_students current ON current.school_id=payment.school_id
                                                   AND current.student_id=payment.student_id
                     GROUP BY payment.student_id
                )
                SELECT current.student_id,current.student_name,current.matricule,current.enrollment_id,
                       current.academic_session_id,current.class_name,current.enrolled_on,current.exited_on,
                       COALESCE(charges.billed,0) v2_billed,COALESCE(fees.billed,0) legacy_billed,
                       COALESCE(v2.paid,0) v2_paid,COALESCE(legacy.paid,0) legacy_paid,
                       COALESCE(v2.payment_count,0)+COALESCE(legacy.payment_count,0) payment_count
                  FROM current_students current
                  LEFT JOIN charge_totals charges ON charges.student_id=current.student_id
                  LEFT JOIN legacy_fees fees ON fees.student_id=current.student_id
                  LEFT JOIN v2_payments v2 ON v2.student_id=current.student_id
                  LEFT JOIN legacy_payments legacy ON legacy.student_id=current.student_id
                 ORDER BY current.class_name,current.last_name,current.first_name
                """;

        return jdbc.query(sql, (rs, n) -> new SearchAccountRow(
                        rs.getObject("student_id", UUID.class), rs.getString("student_name"),
                        rs.getString("matricule"), rs.getObject("enrollment_id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class), rs.getString("class_name"),
                        rs.getObject("enrolled_on", LocalDate.class), rs.getObject("exited_on", LocalDate.class),
                        rs.getLong("v2_billed"), rs.getLong("legacy_billed"), rs.getLong("v2_paid"),
                        rs.getLong("legacy_paid"), rs.getLong("payment_count")), args.toArray()).stream()
                .map(this::searchView)
                .filter(v -> allowedStudents == null || allowedStudents.contains(v.studentId()))
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
        teacherScope.assertSectionStudent(studentId);
        StudentAccountView account = account(studentId);
        UUID schoolId = TenantContext.get();
        String documentVersion = documentVersion(account.snapshotHash());
        GeneratedDocument existing = generatedDocuments
                .findFirstBySchoolIdAndDocumentTypeAndAggregateTypeAndAggregateIdAndAggregateVersionAndLocale(
                        schoolId, "CONSOLIDATED_RECEIPT", "FinanceStudentAccount",
                        studentId.toString(), documentVersion, "fr")
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
                documentVersion, "fr", "Relevé des paiements / Consolidated receipt",
                "STAFF", content, consolidatedIdempotencyKey(studentId, account.snapshotHash()), number);
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
                        s.matricule,COALESCE((
                            SELECT string_agg(programme_class.name,' / '
                                              ORDER BY programme.display_order,programme_class.name)
                              FROM academic_cohort_programme programme
                              JOIN school_class programme_class
                                ON programme_class.school_id=programme.school_id
                               AND programme_class.id=programme.school_class_id
                             WHERE programme.school_id=s.school_id
                               AND programme.academic_session_id=current_enrollment.academic_session_id
                               AND programme.cohort_id=current_enrollment.cohort_id
                               AND programme.active=true
                        ),c.name,s.class_name),a.label
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.school_class_id,e.academic_session_id,e.cohort_id
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
                SELECT COALESCE((SELECT SUM(GREATEST(0,c.adjusted_amount_minor-c.waived_minor))
                                   FROM student_charge c
                                  WHERE c.school_id=? AND c.student_id=?
                                    AND c.status IN ('POSTED','PARTIAL','PAID','WAIVED')),0),
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

    private StudentAccountSearchView searchView(SearchAccountRow row) {
        long billed = row.v2Billed() > 0 ? row.v2Billed() : row.legacyBilled();
        long paid = row.v2Paid() + row.legacyPaid();
        return new StudentAccountSearchView(row.studentId(), row.studentName(), row.matricule(),
                row.enrollmentId(), row.academicSessionId(), row.className(), row.enrolledOn(), row.exitedOn(),
                billed, paid, Math.max(0, billed - paid), Math.max(0, paid - billed), row.paymentCount());
    }

    private static String documentVersion(String snapshotHash) {
        return snapshotHash + ":" + CONSOLIDATED_RECEIPT_TEMPLATE_VERSION;
    }

    private static String consolidatedIdempotencyKey(UUID studentId, String snapshotHash) {
        String digest = snapshotHash.substring(0, Math.min(24, snapshotHash.length()));
        return "FINCR:" + studentId + ":" + digest + ":v2";
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

    private record SearchAccountRow(UUID studentId, String studentName, String matricule,
                                    UUID enrollmentId, UUID academicSessionId, String className,
                                    LocalDate enrolledOn, LocalDate exitedOn, long v2Billed,
                                    long legacyBilled, long v2Paid, long legacyPaid,
                                    long paymentCount) {}
}
