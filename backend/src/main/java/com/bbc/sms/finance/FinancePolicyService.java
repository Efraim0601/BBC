package com.bbc.sms.finance;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-resolved finance resource contexts shared by finance sub-services. */
@Service
public class FinancePolicyService {
    private final AuthorizationPolicyService policy;
    private final JdbcTemplate jdbc;

    public FinancePolicyService(AuthorizationPolicyService policy, JdbcTemplate jdbc) {
        this.policy = policy;
        this.jdbc = jdbc;
    }

    public void requireSchool(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    public void requireEnrollment(String action, UUID enrollmentId, LocalDate effectiveDate) {
        UUID schoolId = TenantContext.get();
        LocalDate date = effectiveDate == null ? LocalDate.now() : effectiveDate;
        Map<String, Object> row = jdbc.query("""
                SELECT e.student_id,e.academic_session_id,e.school_class_id,c.level,c.subsystem
                  FROM student_enrollment e
                  JOIN student s ON s.id=e.student_id AND s.school_id=e.school_id AND s.active=true
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.id=? AND e.status='ACTIVE'
                   AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("student", rs.getObject(1, UUID.class));
            result.put("session", rs.getObject(2, UUID.class));
            result.put("class", rs.getObject(3, UUID.class));
            result.put("level", rs.getString(4));
            result.put("subsystem", rs.getString(5));
            return result;
        }, schoolId, enrollmentId, date, date);
        if (row == null) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, "FINANCE_ENROLLMENT_SCOPE_DENIED",
                    "L’inscription financière active est introuvable dans l’établissement courant.");
        }
        PolicyResourceContext context = new PolicyResourceContext(schoolId,
                (UUID) row.get("session"), date,
                row.get("level") == null ? null : new ParcoursContext.Scope(
                        String.valueOf(row.get("level")), String.valueOf(row.get("subsystem"))),
                (UUID) row.get("class"), null, (UUID) row.get("student"),
                null, null, null, null, String.valueOf(row.get("level")));
        policy.require(action, context);
    }

    public void requirePayment(String action, UUID paymentId, LocalDate effectiveDate) {
        UUID schoolId = TenantContext.get();
        Map<String, Object> row = jdbc.query("""
                SELECT p.student_id,p.academic_session_id,p.student_enrollment_id,
                       e.school_class_id,c.level,c.subsystem
                  FROM finance_payment p
                  LEFT JOIN student_enrollment e ON e.id=p.student_enrollment_id AND e.school_id=p.school_id
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE p.school_id=? AND p.id=?
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("student", rs.getObject(1, UUID.class));
            result.put("session", rs.getObject(2, UUID.class));
            result.put("enrollment", rs.getObject(3, UUID.class));
            result.put("class", rs.getObject(4, UUID.class));
            result.put("level", rs.getString(5));
            result.put("subsystem", rs.getString(6));
            return result;
        }, schoolId, paymentId);
        if (row == null) throw ApiException.notFound("Encaissement");
        LocalDate date = effectiveDate == null ? LocalDate.now() : effectiveDate;
        PolicyResourceContext context = new PolicyResourceContext(schoolId,
                (UUID) row.get("session"), date,
                row.get("level") == null ? null : new ParcoursContext.Scope(
                        String.valueOf(row.get("level")), String.valueOf(row.get("subsystem"))),
                (UUID) row.get("class"), null, (UUID) row.get("student"),
                null, null, null, null, String.valueOf(row.get("level")));
        policy.require(action, context);
    }

    public void requireCharge(String action, UUID chargeId, LocalDate effectiveDate) {
        UUID schoolId = TenantContext.get();
        Map<String, Object> row = jdbc.query("""
                SELECT c.student_id,c.academic_session_id,c.student_enrollment_id,
                       c.school_class_id_snapshot,e.level_snapshot,e.subsystem_snapshot
                  FROM student_charge c
                  LEFT JOIN student_enrollment e ON e.id=c.student_enrollment_id AND e.school_id=c.school_id
                 WHERE c.school_id=? AND c.id=?
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("student", rs.getObject(1, UUID.class));
            result.put("session", rs.getObject(2, UUID.class));
            result.put("class", rs.getObject(4, UUID.class));
            result.put("level", rs.getString(5));
            result.put("subsystem", rs.getString(6));
            return result;
        }, schoolId, chargeId);
        if (row == null) throw ApiException.notFound("Charge");
        LocalDate date = effectiveDate == null ? LocalDate.now() : effectiveDate;
        PolicyResourceContext context = new PolicyResourceContext(schoolId,
                (UUID) row.get("session"), date,
                row.get("level") == null ? null : new ParcoursContext.Scope(
                        String.valueOf(row.get("level")), String.valueOf(row.get("subsystem"))),
                (UUID) row.get("class"), null, (UUID) row.get("student"),
                null, null, null, null, String.valueOf(row.get("level")));
        policy.require(action, context);
    }

    public void requireInvoice(String action, UUID invoiceId, LocalDate effectiveDate) {
        UUID schoolId = TenantContext.get();
        Map<String, Object> row = jdbc.query("""
                SELECT i.student_id,i.academic_session_id,i.school_class_id_snapshot,
                       c.level,c.subsystem
                  FROM finance_invoice i
                  LEFT JOIN school_class c ON c.id=i.school_class_id_snapshot AND c.school_id=i.school_id
                 WHERE i.school_id=? AND i.id=?
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("student", rs.getObject(1, UUID.class));
            result.put("session", rs.getObject(2, UUID.class));
            result.put("class", rs.getObject(3, UUID.class));
            result.put("level", rs.getString(4));
            result.put("subsystem", rs.getString(5));
            return result;
        }, schoolId, invoiceId);
        if (row == null) throw ApiException.notFound("Document financier");
        LocalDate date = effectiveDate == null ? LocalDate.now() : effectiveDate;
        PolicyResourceContext context = new PolicyResourceContext(schoolId,
                (UUID) row.get("session"), date,
                row.get("level") == null ? null : new ParcoursContext.Scope(
                        String.valueOf(row.get("level")), String.valueOf(row.get("subsystem"))),
                (UUID) row.get("class"), null, (UUID) row.get("student"),
                null, invoiceId, null, null, String.valueOf(row.get("level")));
        policy.require(action, context);
    }
}
