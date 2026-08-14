package com.bbc.sms.platform.security;

import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Compatibility class-scope adapter for non-academic screens.
 *
 * <p>Academic services use AcademicAccessPolicyService directly.  This
 * adapter is still session/effective-date aware so legacy callers cannot fall
 * back to student.class_id or the current session by accident.</p>
 */
@Service("teacherScope")
public class TeacherScopeService {
    private static final Set<String> RESTRICTED_ROLES = Set.of("teacher", "form_teacher");
    private final JdbcTemplate jdbc;
    private final AcademicAccessPolicyService accessPolicy;

    public TeacherScopeService(JdbcTemplate jdbc, AcademicAccessPolicyService accessPolicy) {
        this.jdbc = jdbc; this.accessPolicy = accessPolicy;
    }

    /** A missing employee link is still restricted; it must never become broad access. */
    public boolean restricted() {
        AppUserPrincipal p = principal();
        if (p == null) return false;
        LocalDate today = LocalDate.now();
        List<String> roles = jdbc.query("""
                SELECT DISTINCT lower(role_code) FROM app_user_role
                 WHERE school_id=? AND user_id=?
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                UNION SELECT lower(?)
                """, (rs, n) -> rs.getString(1), TenantContext.get(), p.userId(), today, today, p.roleCode());
        return roles.stream().map(TeacherScopeService::normalize).anyMatch(RESTRICTED_ROLES::contains);
    }

    public String section() {
        UUID employeeId = employeeId();
        if (employeeId == null) return null;
        return jdbc.query("SELECT level FROM employee WHERE id=? AND school_id=? AND active=true",
                rs -> rs.next() ? rs.getString(1) : null, employeeId, TenantContext.get());
    }

    /** Current-session compatibility method. */
    public Set<UUID> allowedClassIds() {
        if (!restricted()) return null;
        UUID sessionId = jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get());
        LocalDate date = sessionId == null ? LocalDate.now() : jdbc.query(
                "SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : LocalDate.now(), sessionId, TenantContext.get());
        return sessionId == null ? Set.of() : allowedClassIds(sessionId, date);
    }

    /** Session/effective-date aware class scope. */
    public Set<UUID> allowedClassIds(UUID academicSessionId, LocalDate effectiveDate) {
        if (!restricted()) return null;
        List<UUID> ids = jdbc.query("""
                SELECT DISTINCT c.id
                  FROM school_class c
                 WHERE c.school_id=?
                """, (rs, n) -> rs.getObject(1, UUID.class), TenantContext.get());
        return ids.stream().filter(id -> accessPolicy.can(
                AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                academicSessionId, id, null, null, effectiveDate)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<String> allowedClassNames() {
        Set<UUID> ids = allowedClassIds();
        if (ids == null) return null;
        if (ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.query("SELECT name FROM school_class WHERE school_id=? AND id = ANY(?)",
                (rs, n) -> rs.getString(1), TenantContext.get(), ids.toArray(UUID[]::new)));
    }

    public void assertClass(UUID classId) {
        if (!restricted()) return;
        UUID sessionId = jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get());
        LocalDate date = sessionId == null ? null : jdbc.query(
                "SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, sessionId, TenantContext.get());
        if (sessionId == null || date == null) throw denied("ACADEMIC_CLASS_ACCESS_DENIED");
        accessPolicy.require(AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                sessionId, classId, null, null, date);
    }

    public void assertClass(UUID academicSessionId, UUID classId, LocalDate effectiveDate) {
        if (!restricted()) return;
        accessPolicy.require(AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                academicSessionId, classId, null, null, effectiveDate);
    }

    public void assertClassName(String className) {
        Set<String> allowed = allowedClassNames();
        if (allowed != null && (className == null || !allowed.contains(className))) throw denied("ACADEMIC_CLASS_ACCESS_DENIED");
    }

    /** Compatibility student assertion resolves active enrollment in current session. */
    public void assertStudent(UUID studentId) {
        if (!restricted()) return;
        UUID sessionId = jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get());
        LocalDate date = sessionId == null ? null : jdbc.query(
                "SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, sessionId, TenantContext.get());
        if (sessionId == null || date == null) throw denied("ACADEMIC_CLASS_ACCESS_DENIED");
        assertStudent(studentId, sessionId, date, null);
    }

    /** Session-aware enrollment scope. classId may be null when only class membership is needed. */
    public void assertStudent(UUID studentId, UUID academicSessionId, LocalDate effectiveDate, UUID classId) {
        if (!restricted()) return;
        UUID enrolledClass = jdbc.query("""
                SELECT school_class_id FROM student_enrollment
                 WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'
                   AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                 ORDER BY enrolled_on DESC,created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), studentId, academicSessionId, effectiveDate, effectiveDate);
        if (enrolledClass == null || (classId != null && !classId.equals(enrolledClass))) {
            throw denied("ENROLLMENT_SCOPE_MISMATCH");
        }
        accessPolicy.require(AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                academicSessionId, enrolledClass, null, studentId, effectiveDate);
    }

    public UUID employeeId() {
        AppUserPrincipal p = principal();
        if (p == null) return null;
        return jdbc.query("SELECT employee_id FROM app_user WHERE id=? AND school_id=? AND active=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, p.userId(), TenantContext.get());
    }

    private AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal aup ? aup : null;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(); }

    private static ApiException denied(String code) {
        return ApiException.coded(org.springframework.http.HttpStatus.FORBIDDEN, code,
                "Cette ressource académique n'est pas accessible dans votre périmètre.");
    }
}
