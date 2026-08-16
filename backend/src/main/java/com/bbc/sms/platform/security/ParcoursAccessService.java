package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Parcours access gate used from controllers via SpEL: {@code @PreAuthorize("@parcours.allows()")}.
 * Enforces the {@code X-Parcours} scope against the user's explicit or derived
 * parcours mode.  The mode is authoritative: an empty result is never treated
 * as unrestricted, which is essential for an unassigned teacher.
 */
@Service("parcours")
public class ParcoursAccessService {

    private final JdbcTemplate jdbc;

    public ParcoursAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Return effective parcours rows; an empty result means no scope, not all. */
    public List<Scope> allowed(UUID userId) {
        String mode = scopeMode(userId);
        return switch (mode) {
            case "GLOBAL" -> schoolParcours(userId);
            case "EXPLICIT" -> jdbc.query(
                    "SELECT level, subsystem FROM app_user_parcours WHERE user_id = ? ORDER BY level, subsystem",
                    (rs, n) -> new Scope(rs.getString("level"), rs.getString("subsystem")), userId);
            case "ASSIGNMENT_DERIVED" -> fromEmployeeAssignments(userId);
            case "CHILD_DERIVED" -> fromLinkedChildren(userId);
            default -> List.of();
        };
    }

    public String scopeMode(UUID userId) {
        // Accountants operate school-wide.  Their role is the source of truth
        // for the parcours picker, just like the finance action policy is the
        // source of truth for fee collection.  Do not depend on the historic
        // per-user column here: accounts created before the scope backfill (or
        // accounts whose role was changed later) may still contain NONE or a
        // teacher-derived value.  A role-level decision keeps the rule true
        // for every current and future accountant account.
        return jdbc.query("""
                SELECT CASE WHEN lower(COALESCE(u.role_code,''))='accountant'
                                  OR EXISTS (
                                      SELECT 1 FROM app_user_role ur
                                       WHERE ur.user_id=u.id
                                         AND lower(ur.role_code)='accountant'
                                         AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                                         AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)
                                  )
                            THEN 'GLOBAL'
                            ELSE COALESCE(u.parcours_scope_mode,'NONE')
                       END
                  FROM app_user u
                 WHERE u.id=?
                """, rs -> rs.next() ? rs.getString(1).toUpperCase() : "NONE", userId);
    }

    public boolean isGlobal(UUID userId) { return "GLOBAL".equals(scopeMode(userId)); }

    /** Explicit readiness signal used by /me and the Access & responsibilities drawer. */
    public boolean isConfigured(UUID userId) {
        String mode = scopeMode(userId);
        return "GLOBAL".equals(mode) || !allowed(userId).isEmpty();
    }

    private List<Scope> schoolParcours(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT c.level, c.subsystem
                  FROM school_class c
                  JOIN app_user u ON u.school_id=c.school_id
                 WHERE u.id=?
                 ORDER BY c.level,c.subsystem
                """, (rs, n) -> new Scope(rs.getString(1), rs.getString(2)), userId);
    }

    /** Derive only from active teaching/titulaire relationships; no FR+EN fallback. */
    private List<Scope> fromEmployeeAssignments(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT c.level, c.subsystem
                  FROM school_class c
                  JOIN app_user u ON u.school_id=c.school_id
                  JOIN employee e ON e.id=u.employee_id AND e.school_id=u.school_id AND e.active=true
                 WHERE u.id=? AND (
                       EXISTS (SELECT 1 FROM teacher_class tc
                                WHERE tc.employee_id=e.id AND tc.class_id=c.id)
                    OR EXISTS (SELECT 1 FROM academic_class_subject_teacher at
                                WHERE at.school_id=c.school_id AND at.class_id=c.id
                                  AND at.employee_id=e.id AND at.active=true)
                    OR EXISTS (SELECT 1 FROM class_teacher_assignment ta
                                WHERE ta.school_id=c.school_id AND ta.class_id=c.id
                                  AND ta.employee_id=e.id AND ta.status='ACTIVE')
                    OR (e.form_class IS NOT NULL AND e.form_class=c.name)
                  )
                 ORDER BY c.level,c.subsystem
                """, (rs, n) -> new Scope(rs.getString(1), rs.getString(2)), userId);
    }

    private List<Scope> fromLinkedChildren(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT c.level, c.subsystem
                  FROM guardian g
                  JOIN student_guardian sg ON sg.guardian_id=g.id
                  JOIN student s ON s.id=sg.student_id AND s.school_id=g.school_id
                  JOIN school_class c ON c.id=s.class_id AND c.school_id=s.school_id
                 WHERE g.app_user_id=? AND g.status='ACTIVE'
                   AND sg.portal_access=true AND sg.effective_to IS NULL
                 ORDER BY c.level,c.subsystem
                """, (rs, n) -> new Scope(rs.getString(1), rs.getString(2)), userId);
    }

    /**
     * True when the request's parcours scope is permitted for the current user.
     * No scope bound to the request is always allowed (cross-parcours views).
     */
    public boolean allows() {
        Scope scope = ParcoursContext.get();
        AppUserPrincipal p = currentPrincipal();
        if (p == null) return false;
        if (scope == null) return isGlobal(p.userId());
        return isAllowed(p.userId(), scope);
    }

    public boolean isAllowed(UUID userId, Scope scope) {
        if (scope == null) return isGlobal(userId);
        return allowed(userId).contains(scope);
    }

    private AppUserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal aup ? aup : null;
    }
}
