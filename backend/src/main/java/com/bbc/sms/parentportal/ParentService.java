package com.bbc.sms.parentportal;

import com.bbc.sms.academic.Grade;
import com.bbc.sms.academic.GradeRepository;
import com.bbc.sms.academic.Subject;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.academic.BulletinSnapshotService;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView;
import com.bbc.sms.classkit.ClassKitService;
import com.bbc.sms.classkit.dto.ClassKitDtos.ClassResourceView;
import com.bbc.sms.finance.FeeService;
import com.bbc.sms.finance.dto.FeeDtos.PaymentChannelView;
import com.bbc.sms.finance.dto.FeeDtos.StudentFeeStatementView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ParentInvoiceView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ParentReceiptView;
import com.bbc.sms.finance.documents.FinanceDocumentService;
import com.bbc.sms.guardian.GuardianAccessService;
import com.bbc.sms.parentportal.dto.ParentDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;

/**
 * Parent portal read/write logic. Owns JPA only for {@code parent_suggestion};
 * parent↔child links, fee balances and attendance are read via JdbcTemplate.
 * Student and Grade data are reached through read-only repositories.
 */
@Service
public class ParentService {

    private final JdbcTemplate jdbc;
    private final StudentRepository students;
    private final GradeRepository grades;
    private final SubjectRepository subjects;
    private final SuggestionRepository suggestions;
    private final ClassKitService classKit;
    private final FeeService fees;
    private final GuardianAccessService guardianAccess;
    private final AuthorizationPolicyService policy;
    private final BulletinSnapshotService bulletins;
    private final FinanceDocumentService financeDocuments;

    public ParentService(JdbcTemplate jdbc,
                         StudentRepository students,
                         GradeRepository grades,
                         SubjectRepository subjects,
                         SuggestionRepository suggestions,
                         ClassKitService classKit,
                         FeeService fees,
                         GuardianAccessService guardianAccess,
                         AuthorizationPolicyService policy,
                         BulletinSnapshotService bulletins,
                         FinanceDocumentService financeDocuments) {
        this.jdbc = jdbc;
        this.students = students;
        this.grades = grades;
        this.subjects = subjects;
        this.suggestions = suggestions;
        this.classKit = classKit;
        this.fees = fees;
        this.guardianAccess = guardianAccess;
        this.policy = policy;
        this.bulletins = bulletins;
        this.financeDocuments = financeDocuments;
    }

    /** Student ids linked to the given parent account. */
    List<UUID> childIds(UUID schoolId, UUID parentUserId) {
        return guardianAccess.childIds(schoolId, parentUserId);
    }

    /** Guard: a parent may only ever touch one of its own children. */
    void assertOwnership(UUID schoolId, UUID parentUserId, UUID studentId) {
        guardianAccess.assertAccess(schoolId, parentUserId, studentId, "academic");
    }

    public List<ChildView> children(AppUserPrincipal p) {
        UUID schoolId = p.schoolId();
        List<ChildView> out = new ArrayList<>();
        for (UUID studentId : childIds(schoolId, p.userId())) {
            Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
            if (s == null || !s.isActive()
                    || !policy.decide("PARENT_CHILD_SUMMARY_VIEW", childContext(p, studentId)).allowed()) continue;

            String name = s.getLastName().toUpperCase() + " " + s.getFirstName();

            // Même calcul que l'onglet « Frais & paiements » (grille de la classe moins les
            // versements reçus) : deux écrans du même portail ne peuvent pas annoncer
            // deux soldes différents au parent.
            boolean financeVisible = policy.decide("PARENT_FINANCE_VIEW",
                    childContext(p, studentId)).allowed();
            StudentFeeStatementView st = financeVisible
                    ? fees.statementForParent(schoolId, studentId) : null;
            boolean attendanceVisible = policy.decide("PARENT_ATTENDANCE_VIEW",
                    childContext(p, studentId)).allowed();
            int attendanceRate = attendanceVisible ? attendanceRate(schoolId, studentId) : 0;

            out.add(new ChildView(studentId, s.getMatricule(), name, s.getClassName(),
                    st == null ? 0 : st.balance(), st == null ? null : st.status(),
                    attendanceRate, financeVisible, attendanceVisible));
        }
        return out;
    }

    private int attendanceRate(UUID schoolId, UUID studentId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM attendance_record WHERE school_id = ? AND student_id = ?",
                (rs, i) -> rs.getString("status"),
                schoolId, studentId);
        if (statuses.isEmpty()) return 0;
        long ok = statuses.stream().filter(st -> "present".equals(st) || "late".equals(st)).count();
        return (int) (ok * 100 / statuses.size());
    }

    public List<GradeView> grades(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_ACADEMIC_VIEW", studentId);
        Map<String, Subject> byCode = subjects.findBySchoolIdOrderByCode(p.schoolId()).stream()
                .collect(Collectors.toMap(Subject::getCode, s -> s, (a, b) -> a));
        List<GradeView> out = new ArrayList<>();
        for (Grade g : grades.findBySchoolIdAndStudentId(p.schoolId(), studentId)) {
            Subject s = byCode.get(g.getSubjectCode());
            Map<String, String> label = s == null ? null : s.getLabel();
            out.add(new GradeView(
                    g.getSubjectCode(),
                    labelOr(label, "fr", g.getSubjectCode()),
                    labelOr(label, "en", g.getSubjectCode()),
                    // A grade in a subject that was since deleted still counts, at weight 1.
                    s == null ? 1 : s.getCoef(),
                    g.getSequence(),
                    g.getMark()));
        }
        return out;
    }

    public BulletinSnapshotView publishedBulletin(AppUserPrincipal p, UUID studentId, UUID reportingPeriodId) {
        requireParentAction(p, "PARENT_ACADEMIC_VIEW", studentId);
        return bulletins.published(studentId, reportingPeriodId);
    }

    public BulletinSnapshotView latestPublishedBulletin(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_ACADEMIC_VIEW", studentId);
        return bulletins.publishedLatest(studentId);
    }

    public List<ParentJourneyEventView> journey(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_ACADEMIC_VIEW", studentId);
        List<ParentJourneyEventView> result = new ArrayList<>();
        result.addAll(jdbc.query("""
                SELECT e.id,e.event_type,s.label,e.payload->>'class' AS class_name,
                       NULL::numeric,e.payload->>'decision',e.created_at,e.id
                  FROM journey_event e LEFT JOIN academic_session s ON s.id=e.academic_session_id
                 WHERE e.school_id=? AND e.student_id=? AND e.visibility='PARENT'
                 ORDER BY e.created_at DESC
                """, (rs,n) -> new ParentJourneyEventView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6),
                        rs.getTimestamp(7).toInstant(), rs.getObject(8, UUID.class)), p.schoolId(), studentId));
        result.addAll(jdbc.query("""
                SELECT v.id,'PUBLISHED_RESULT',s.label,e.class_name_snapshot,v.average,
                       v.snapshot_json->'conduct'->>'decisionCode',v.published_at,v.id
                  FROM bulletin_version v
                  JOIN academic_session s ON s.id=v.academic_session_id
                  LEFT JOIN student_enrollment e ON e.id=v.enrollment_id
                 WHERE v.school_id=? AND v.student_id=? AND v.state='PUBLISHED'
                 ORDER BY v.published_at DESC
                """, (rs,n) -> new ParentJourneyEventView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getObject(8, UUID.class)), p.schoolId(), studentId));
        return result.stream().sorted(java.util.Comparator.comparing(ParentJourneyEventView::occurredAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).toList();
    }

    /** Published supplies/books list for the class of one of the parent's children. */
    public ClassResourceView resources(AppUserPrincipal p, UUID studentId, String kind) {
        requireParentAction(p, "PARENT_ACADEMIC_VIEW", studentId);
        Student s = students.findByIdAndSchoolId(studentId, p.schoolId())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        if (s.getClassId() == null) {
            return new ClassResourceView(null, s.getClassName(), kind, false, null, List.of());
        }
        return classKit.publishedForClass(s.getClassId(), kind);
    }

    /**
     * Situation de scolarité d'un enfant : grille de sa classe découpée en tranches,
     * part déjà réglée, reste à payer et reçus. C'est la vue qui permet au parent de
     * suivre un paiement progressif sans passer par le secrétariat.
     */
    public StudentFeeStatementView feeStatement(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_FINANCE_VIEW", studentId);
        return fees.statementForParent(p.schoolId(), studentId);
    }

    /** Moyens de paiement que l'école accepte et publie aux familles (avec leurs coordonnées). */
    public List<PaymentChannelView> paymentChannels(AppUserPrincipal p) {
        requireAnyChild(p, "PARENT_FINANCE_VIEW");
        return fees.parentChannels(p.schoolId());
    }

    public List<ParentInvoiceView> financeInvoices(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_FINANCE_VIEW", studentId);
        return financeDocuments.parentInvoices(studentId);
    }

    public List<ParentReceiptView> financeReceipts(AppUserPrincipal p, UUID studentId) {
        requireParentAction(p, "PARENT_FINANCE_VIEW", studentId);
        return financeDocuments.parentReceipts(studentId);
    }

    public UUID financeDocumentId(AppUserPrincipal p, String type, UUID documentId, UUID studentId) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalizedType.equals("INVOICE") && !normalizedType.equals("RECEIPT")) {
            throw ApiException.badRequest("Type de document financier invalide");
        }
        requireParentAction(p, "PARENT_FINANCE_VIEW", studentId);
        requireParentAction(p, "PARENT_DOCUMENT_DOWNLOAD", studentId);
        return financeDocuments.parentDocumentId(normalizedType, documentId, studentId);
    }

    private static String labelOr(Map<String, String> label, String lang, String fallback) {
        if (label == null) return fallback;
        String v = label.get(lang);
        return v == null || v.isBlank() ? fallback : v;
    }

    public SuggestionView createSuggestion(AppUserPrincipal p, SuggestionRequest req) {
        requireAnyChild(p, "PARENT_SUGGESTION_SUBMIT");
        ParentSuggestion ps = new ParentSuggestion();
        ps.setSchoolId(p.schoolId());
        ps.setParentUserId(p.userId());
        ps.setCategory(req.category());
        ps.setMessage(req.message());
        ps.setStatus("new");
        ParentSuggestion saved = suggestions.save(ps);
        return toView(saved);
    }

    public List<SuggestionView> mySuggestions(AppUserPrincipal p) {
        requireAnyChild(p, "PARENT_CHILD_SUMMARY_VIEW");
        return suggestions.findBySchoolIdAndParentUserIdOrderByCreatedAtDesc(p.schoolId(), p.userId())
                .stream().map(this::toView).toList();
    }

    public List<SuggestionView> allSuggestions(UUID schoolId) {
        policy.require("DASHBOARD_VIEW", new PolicyResourceContext(schoolId, null, LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
        return suggestions.findBySchoolIdOrderByCreatedAtDesc(schoolId)
                .stream().map(this::toView).toList();
    }

    private SuggestionView toView(ParentSuggestion ps) {
        return new SuggestionView(ps.getId(), ps.getCategory(), ps.getMessage(), ps.getStatus(), ps.getCreatedAt());
    }

    private void requireParentAction(AppUserPrincipal p, String action, UUID studentId) {
        policy.require(action, childContext(p, studentId));
    }

    private void requireAnyChild(AppUserPrincipal p, String action) {
        List<UUID> children = childIds(p.schoolId(), p.userId());
        if (children.stream().anyMatch(child ->
                policy.decide(action, childContext(p, child)).allowed())) return;
        throw ApiException.forbidden("Aucun enfant lié et actif n'autorise cette action.");
    }

    private PolicyResourceContext childContext(AppUserPrincipal p, UUID studentId) {
        return new PolicyResourceContext(p.schoolId(), null, LocalDate.now(), ParcoursContext.get(),
                null, null, studentId, null, null, null, null, null);
    }
}
