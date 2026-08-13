package com.bbc.sms.platform.security;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cloisonnement d'un enseignant : sa section (cycle) et les classes qui lui sont
 * assignées.
 *
 * <p>Un enseignant ne voit que les données de sa section et de ses classes. Les
 * autres rôles (direction, censeur, économe) ne sont pas restreints — le filtre
 * par parcours, choisi à la connexion, leur suffit.
 *
 * <p>La restriction est résolue à partir du compte : {@code app_user.employee_id}
 * donne l'employé, {@code teacher_class} ses classes, {@code employee.level} sa
 * section. Un enseignant sans aucune classe assignée ne voit rien — c'est
 * volontaire : mieux vaut une liste vide qu'un accès par défaut à tout
 * l'établissement.
 */
@Service("teacherScope")
public class TeacherScopeService {

    /** Rôles cloisonnés aux classes qui leur sont assignées. */
    private static final Set<String> RESTRICTED_ROLES = Set.of("teacher", "form_teacher");

    private final JdbcTemplate jdbc;

    public TeacherScopeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** L'utilisateur courant est-il un enseignant cloisonné ? */
    public boolean restricted() {
        AppUserPrincipal p = principal();
        return p != null && RESTRICTED_ROLES.contains(p.roleCode()) && employeeId(p) != null;
    }

    /** Section de l'enseignant courant (maternelle|primary|secondary), null si non cloisonné. */
    public String section() {
        AppUserPrincipal p = principal();
        if (p == null || !RESTRICTED_ROLES.contains(p.roleCode())) return null;
        UUID employeeId = employeeId(p);
        if (employeeId == null) return null;
        return jdbc.query("SELECT level FROM employee WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, employeeId);
    }

    /**
     * Identifiants des classes visibles par l'utilisateur courant, ou null quand
     * il n'est pas cloisonné (aucun filtre à appliquer).
     */
    public Set<UUID> allowedClassIds() {
        AppUserPrincipal p = principal();
        if (p == null || !RESTRICTED_ROLES.contains(p.roleCode())) return null;
        UUID employeeId = employeeId(p);
        if (employeeId == null) return null;
        // Classes explicitement assignées + la classe dont il est titulaire.
        List<UUID> ids = jdbc.query("""
                SELECT c.id FROM school_class c
                 WHERE c.school_id = ?
                   AND (c.id IN (
                            SELECT a.class_id FROM class_teacher_assignment a
                             JOIN academic_session s ON s.id=a.academic_session_id
                            WHERE a.school_id=? AND a.employee_id=? AND a.role='HOMEROOM'
                              AND a.status='ACTIVE' AND s.is_current
                        ) OR c.id IN (
                            SELECT a.class_id FROM academic_class_subject_teacher a
                             JOIN academic_session s ON s.id=a.academic_session_id
                            WHERE a.school_id=? AND a.employee_id=? AND a.role='RESPONSIBLE'
                              AND a.active AND s.is_current
                        ))
                """,
                (rs, n) -> UUID.fromString(rs.getString(1)),
                TenantContext.get(), TenantContext.get(), employeeId, TenantContext.get(), employeeId);
        return Set.copyOf(ids);
    }

    /** Noms des classes visibles, ou null quand l'utilisateur n'est pas cloisonné. */
    public Set<String> allowedClassNames() {
        AppUserPrincipal p = principal();
        if (p == null || !RESTRICTED_ROLES.contains(p.roleCode())) return null;
        UUID employeeId = employeeId(p);
        if (employeeId == null) return null;
        List<String> names = jdbc.query("""
                SELECT c.name FROM school_class c
                 WHERE c.school_id = ?
                   AND (c.id IN (
                            SELECT a.class_id FROM class_teacher_assignment a
                             JOIN academic_session s ON s.id=a.academic_session_id
                            WHERE a.school_id=? AND a.employee_id=? AND a.role='HOMEROOM'
                              AND a.status='ACTIVE' AND s.is_current
                        ) OR c.id IN (
                            SELECT a.class_id FROM academic_class_subject_teacher a
                             JOIN academic_session s ON s.id=a.academic_session_id
                            WHERE a.school_id=? AND a.employee_id=? AND a.role='RESPONSIBLE'
                              AND a.active AND s.is_current
                        ))
                """,
                (rs, n) -> rs.getString(1),
                TenantContext.get(), TenantContext.get(), employeeId, TenantContext.get(), employeeId);
        return Set.copyOf(names);
    }

    /** Refuse l'accès à une classe qui n'est pas assignée à l'enseignant courant. */
    public void assertClass(UUID classId) {
        Set<UUID> allowed = allowedClassIds();
        if (allowed == null) return;                       // utilisateur non cloisonné
        if (classId == null || !allowed.contains(classId)) throw denied();
    }

    /** Même contrôle, à partir du nom de la classe (les écrans historiques l'utilisent). */
    public void assertClassName(String className) {
        Set<String> allowed = allowedClassNames();
        if (allowed == null) return;
        if (className == null || !allowed.contains(className)) throw denied();
    }

    /** Refuse l'accès à un élève scolarisé hors des classes de l'enseignant. */
    public void assertStudent(UUID studentId) {
        Set<UUID> allowed = allowedClassIds();
        if (allowed == null) return;
        UUID classId = jdbc.query("SELECT class_id FROM student WHERE id = ? AND school_id = ?",
                rs -> rs.next() && rs.getString(1) != null ? UUID.fromString(rs.getString(1)) : null,
                studentId, TenantContext.get());
        if (classId == null || !allowed.contains(classId)) throw denied();
    }

    private static ApiException denied() {
        return new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "Cette classe ne vous est pas assignée");
    }

    private UUID employeeId(AppUserPrincipal p) {
        return jdbc.query("SELECT employee_id FROM app_user WHERE id = ?",
                rs -> rs.next() && rs.getString(1) != null ? UUID.fromString(rs.getString(1)) : null,
                p.userId());
    }

    private AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal aup ? aup : null;
    }
}
