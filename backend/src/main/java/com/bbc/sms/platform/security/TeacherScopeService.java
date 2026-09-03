package com.bbc.sms.platform.security;

import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
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
 *
 * <p><b>Deux cloisonnements coexistent ici</b>, de granularité différente mais de
 * même nature — un sous-ensemble de classes :
 * <ul>
 *   <li><b>Enseignant</b> : les classes que lui ouvre la politique d'accès
 *       académique, à une session et une date données ;</li>
 *   <li><b>Admin de section</b> : toutes les classes de son cycle.</li>
 * </ul>
 * Les exprimer d'une seule façon a une conséquence heureuse : les modules déjà
 * cloisonnés pour l'enseignant — élèves, notes, bulletins, présences, discipline,
 * santé, cahier de textes, emploi du temps, parcours — le sont du même coup pour
 * l'admin de section, sans qu'aucun d'eux ne soit modifié.
 *
 * <p>Le cas « admin de section » est toujours examiné en premier et ne descend
 * jamais dans {@link AcademicAccessPolicyService} : celle-ci raisonne en termes
 * d'affectation d'enseignant, et un administrateur n'en a aucune. L'y envoyer
 * lui refuserait ses propres classes.
 *
 * <p>Les autres rôles (direction, censeur, économe) ne sont pas restreints : le
 * filtre par parcours, choisi à la connexion, leur suffit.
 */
@Service("teacherScope")
public class TeacherScopeService {
    private static final Set<String> RESTRICTED_ROLES = Set.of(
            "teacher", "secondary_teacher", "form_teacher");

    /** Les classes d'une section — le périmètre d'un admin de cycle. */
    private static final String SECTION_SQL = """
            SELECT %s FROM school_class c WHERE c.school_id = ? AND c.level = ?
            """;

    private final JdbcTemplate jdbc;
    private final AcademicAccessPolicyService accessPolicy;

    public TeacherScopeService(JdbcTemplate jdbc, AcademicAccessPolicyService accessPolicy) {
        this.jdbc = jdbc; this.accessPolicy = accessPolicy;
    }

    /**
     * La section administrée, ou null — un enseignant n'administre pas son cycle.
     *
     * <p>Elle se lit dans le seul code de rôle, qui voyage déjà dans le JWT :
     * aucune requête n'est nécessaire pour savoir qui est cloisonné.
     */
    public String adminSection() {
        AppUserPrincipal p = principal();
        return p == null ? null : SectionRoles.sectionOf(p.roleCode());
    }

    /** A missing employee link is still restricted; it must never become broad access. */
    public boolean restricted() {
        AppUserPrincipal p = principal();
        if (p == null) return false;
        if (SectionRoles.sectionOf(p.roleCode()) != null) return true;
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

    /**
     * Section de l'utilisateur courant (maternelle|primary|secondary), null s'il
     * n'est cloisonné à aucun cycle.
     *
     * <p>Pour un admin de section elle se lit dans le rôle ; pour un enseignant,
     * dans sa fiche employé.
     */
    public String section() {
        String adminSection = adminSection();
        if (adminSection != null) return adminSection;
        UUID employeeId = employeeId();
        if (employeeId == null) return null;
        return jdbc.query("SELECT level FROM employee WHERE id=? AND school_id=? AND active=true",
                rs -> rs.next() ? rs.getString(1) : null, employeeId, TenantContext.get());
    }

    /** Current-session compatibility method. */
    public Set<UUID> allowedClassIds() {
        Set<UUID> activeParcours = activeParcoursClassIds();
        String adminSection = adminSection();
        if (adminSection != null) {
            return intersect(sectionClasses("c.id", (rs, n) -> rs.getObject(1, UUID.class), adminSection),
                    activeParcours);
        }
        if (!restricted()) return activeParcours;
        UUID sessionId = jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get());
        LocalDate date = sessionId == null ? LocalDate.now() : jdbc.query(
                "SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : LocalDate.now(), sessionId, TenantContext.get());
        return sessionId == null ? Set.of() : allowedClassIds(sessionId, date);
    }

    /** Session/effective-date aware class scope. */
    public Set<UUID> allowedClassIds(UUID academicSessionId, LocalDate effectiveDate) {
        Set<UUID> activeParcours = activeParcoursClassIds();
        String adminSection = adminSection();
        if (adminSection != null) {
            return intersect(sectionClasses("c.id", (rs, n) -> rs.getObject(1, UUID.class), adminSection),
                    activeParcours);
        }
        if (!restricted()) return activeParcours;
        List<UUID> ids = jdbc.query("""
                SELECT DISTINCT c.id
                  FROM school_class c
                 WHERE c.school_id=?
                """, (rs, n) -> rs.getObject(1, UUID.class), TenantContext.get());
        Set<UUID> assigned = ids.stream().filter(id -> accessPolicy.can(
                AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                academicSessionId, id, null, null, effectiveDate)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return intersect(assigned, activeParcours);
    }

    public Set<String> allowedClassNames() {
        Set<UUID> ids = allowedClassIds();
        if (ids == null) return null;
        if (ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.query("SELECT name FROM school_class WHERE school_id=? AND id = ANY(?)",
                (rs, n) -> rs.getString(1), TenantContext.get(), ids.toArray(UUID[]::new)));
    }

    /**
     * Students reachable through the effective class scope.  This is the
     * list-safe counterpart of {@link #assertStudent(UUID)}: callers can
     * filter aggregate screens without issuing one authorization query per
     * row.  A linked bilingual cohort is deliberately reachable through
     * either of its programme classes.
     *
     * @return {@code null} only for a genuine whole-school request
     */
    public Set<UUID> allowedStudentIds() {
        Set<UUID> classIds = allowedClassIds();
        if (classIds == null) return null;
        if (classIds.isEmpty()) return Set.of();
        LocalDate effectiveDate = currentEffectiveDate();
        UUID[] ids = classIds.toArray(UUID[]::new);
        return Set.copyOf(jdbc.query("""
                SELECT DISTINCT s.id
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.id,e.academic_session_id,e.school_class_id,e.cohort_id
                         FROM student_enrollment e
                         JOIN academic_session session
                           ON session.id=e.academic_session_id
                          AND session.school_id=e.school_id
                        WHERE e.school_id=s.school_id AND e.student_id=s.id
                          AND e.status='ACTIVE' AND session.is_current=true
                          AND e.enrolled_on<=?
                          AND (e.exited_on IS NULL OR e.exited_on>=?)
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) enrollment ON true
                 WHERE s.school_id=? AND s.active=true
                   AND (
                        COALESCE(enrollment.school_class_id,s.class_id)=ANY(?)
                        OR EXISTS (
                            SELECT 1
                              FROM academic_cohort_programme programme
                             WHERE programme.school_id=s.school_id
                               AND programme.academic_session_id=enrollment.academic_session_id
                               AND programme.cohort_id=enrollment.cohort_id
                               AND programme.active=true
                               AND programme.school_class_id=ANY(?)
                        )
                        OR (enrollment.id IS NULL AND EXISTS (
                            SELECT 1 FROM school_class legacy_class
                             WHERE legacy_class.school_id=s.school_id
                               AND legacy_class.name=s.class_name
                               AND legacy_class.id=ANY(?)
                        ))
                   )
                """, (rs, n) -> rs.getObject(1, UUID.class),
                effectiveDate, effectiveDate, TenantContext.get(), ids, ids, ids));
    }

    private <T> Set<T> sectionClasses(String column, org.springframework.jdbc.core.RowMapper<T> mapper, String section) {
        return Set.copyOf(jdbc.query(SECTION_SQL.formatted(column), mapper, TenantContext.get(), section));
    }

    public void assertClass(UUID classId) {
        assertActiveParcoursClass(classId);
        if (adminSection() != null) { assertClassInSection(classId); return; }
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
        assertActiveParcoursClass(classId);
        if (adminSection() != null) { assertClassInSection(classId); return; }
        if (!restricted()) return;
        accessPolicy.require(AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW,
                academicSessionId, classId, null, null, effectiveDate);
    }

    private void assertClassInSection(UUID classId) {
        Set<UUID> allowed = allowedClassIds();
        if (classId == null || allowed == null || !allowed.contains(classId)) {
            throw denied("SECTION_SCOPE_MISMATCH");
        }
    }

    public void assertClassName(String className) {
        Set<String> allowed = allowedClassNames();
        if (allowed != null && (className == null || !allowed.contains(className))) throw denied("ACADEMIC_CLASS_ACCESS_DENIED");
    }

    /** Compatibility student assertion resolves active enrollment in current session. */
    public void assertStudent(UUID studentId) {
        assertActiveParcoursStudent(studentId);
        if (adminSection() != null) { assertStudentInSection(studentId); return; }
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
        assertActiveParcoursStudent(studentId);
        if (classId != null) assertActiveParcoursClass(classId);
        if (adminSection() != null) { assertStudentInSection(studentId); return; }
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

    /**
     * Refuse à un admin de section l'accès à un élève scolarisé hors de son cycle.
     *
     * <p>Un élève sans classe échappe par nature au filtre par classe. Ce sont les
     * inscriptions du jour : l'admin de section doit pouvoir les ouvrir pour les
     * affecter, sans quoi elles resteraient impossibles à traiter. Son cycle borne
     * alors l'accès.
     */
    private void assertStudentInSection(UUID studentId) {
        Set<UUID> allowed = allowedClassIds();
        if (allowed == null) return;
        var row = jdbc.query("SELECT class_id, level FROM student WHERE id = ? AND school_id = ?",
                rs -> rs.next()
                        ? new String[] { rs.getString("class_id"), rs.getString("level") }
                        : null,
                studentId, TenantContext.get());
        if (row == null) throw denied("SECTION_SCOPE_MISMATCH");
        if (row[0] != null) {
            if (!allowed.contains(UUID.fromString(row[0]))) throw denied("SECTION_SCOPE_MISMATCH");
            return;
        }
        if (!unassignedVisible(row[1])) throw denied("SECTION_SCOPE_MISMATCH");
    }

    /**
     * Garde de section pure : borne un élève au cycle de l'admin de section, et
     * ne fait rien pour tout autre compte.
     *
     * <p>Elle s'ajoute aux contrôles de {@link AuthorizationPolicyService} et de
     * {@link AcademicAccessPolicyService} là où ceux-ci décident par module ou par
     * affectation d'enseignant — jamais par cycle. Sans elle, un admin de section
     * lirait les notes, le parcours ou la santé d'un élève d'un autre cycle.
     *
     * <p>Volontairement distincte de {@link #assertStudent(UUID)} : celle-ci
     * imposerait aussi aux enseignants la capacité ACADEMIC_ROSTER_VIEW, là où
     * l'appelant en exige déjà une autre, propre à son module.
     */
    public void assertSectionStudent(UUID studentId) {
        assertActiveParcoursStudent(studentId);
        if (adminSection() == null) return;
        assertStudentInSection(studentId);
    }

    /** Même garde, à partir du nom de la classe. Sans effet hors admin de section. */
    public void assertSectionClassName(String className) {
        assertActiveParcoursClassName(className);
        if (adminSection() == null) return;
        Set<String> allowed = allowedClassNames();
        if (className == null || allowed == null || !allowed.contains(className)) {
            throw denied("SECTION_SCOPE_MISMATCH");
        }
    }

    /** Même garde, pour une classe. Sans effet hors admin de section. */
    public void assertSectionClass(UUID classId) {
        assertActiveParcoursClass(classId);
        if (adminSection() == null) return;
        assertClassInSection(classId);
    }

    private Set<UUID> activeParcoursClassIds() {
        ParcoursContext.Scope scope = ParcoursContext.get();
        if (scope == null) return null;
        return Set.copyOf(jdbc.query("""
                SELECT c.id
                  FROM school_class c
                 WHERE c.school_id=? AND lower(c.level)=lower(?)
                   AND upper(c.subsystem)=upper(?)
                """, (rs, n) -> rs.getObject(1, UUID.class),
                TenantContext.get(), scope.level(), scope.subsystem()));
    }

    private static <T> Set<T> intersect(Set<T> left, Set<T> right) {
        if (right == null) return left;
        if (left == null) return right;
        return left.stream().filter(right::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void assertActiveParcoursClass(UUID classId) {
        ParcoursContext.Scope scope = ParcoursContext.get();
        if (scope == null) return;
        Boolean allowed = jdbc.query("""
                SELECT EXISTS(
                    SELECT 1 FROM school_class c
                     WHERE c.school_id=? AND c.id=?
                       AND lower(c.level)=lower(?) AND upper(c.subsystem)=upper(?)
                )
                """, rs -> rs.next() && rs.getBoolean(1),
                TenantContext.get(), classId, scope.level(), scope.subsystem());
        if (!Boolean.TRUE.equals(allowed)) throw denied("PARCOURS_RESOURCE_MISMATCH");
    }

    private void assertActiveParcoursClassName(String className) {
        ParcoursContext.Scope scope = ParcoursContext.get();
        if (scope == null) return;
        Boolean allowed = jdbc.query("""
                SELECT EXISTS(
                    SELECT 1 FROM school_class c
                     WHERE c.school_id=? AND c.name=?
                       AND lower(c.level)=lower(?) AND upper(c.subsystem)=upper(?)
                )
                """, rs -> rs.next() && rs.getBoolean(1),
                TenantContext.get(), className, scope.level(), scope.subsystem());
        if (!Boolean.TRUE.equals(allowed)) throw denied("PARCOURS_RESOURCE_MISMATCH");
    }

    /**
     * Enforces the selected parcours for a student while preserving bilingual
     * cohorts: an enrollment in the canonical FR class is also in scope from
     * its active linked EN programme class (and vice versa).
     */
    private void assertActiveParcoursStudent(UUID studentId) {
        ParcoursContext.Scope scope = ParcoursContext.get();
        if (scope == null) return;
        LocalDate effectiveDate = currentEffectiveDate();
        Boolean allowed = jdbc.query("""
                SELECT EXISTS(
                    SELECT 1
                      FROM student s
                      LEFT JOIN LATERAL (
                           SELECT e.academic_session_id,e.school_class_id,e.cohort_id
                             FROM student_enrollment e
                             JOIN academic_session session
                               ON session.id=e.academic_session_id
                              AND session.school_id=e.school_id
                            WHERE e.school_id=s.school_id AND e.student_id=s.id
                              AND e.status='ACTIVE' AND session.is_current=true
                              AND e.enrolled_on<=?
                              AND (e.exited_on IS NULL OR e.exited_on>=?)
                            ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                      ) current_enrollment ON true
                      LEFT JOIN school_class direct_class
                        ON direct_class.school_id=s.school_id
                       AND direct_class.id=COALESCE(current_enrollment.school_class_id,s.class_id)
                     WHERE s.school_id=? AND s.id=? AND s.active=true
                       AND (
                            (lower(direct_class.level)=lower(?)
                             AND upper(direct_class.subsystem)=upper(?))
                            OR EXISTS (
                                SELECT 1
                                  FROM academic_cohort_programme programme
                                  JOIN school_class programme_class
                                    ON programme_class.school_id=programme.school_id
                                   AND programme_class.id=programme.school_class_id
                                 WHERE programme.school_id=s.school_id
                                   AND programme.academic_session_id=current_enrollment.academic_session_id
                                   AND programme.cohort_id=current_enrollment.cohort_id
                                   AND programme.active=true
                                   AND lower(programme_class.level)=lower(?)
                                   AND upper(programme_class.subsystem)=upper(?)
                            )
                            OR (direct_class.id IS NULL
                                AND lower(s.level)=lower(?)
                                AND upper(s.subsystem)=upper(?))
                       )
                )
                """, rs -> rs.next() && rs.getBoolean(1), effectiveDate, effectiveDate,
                TenantContext.get(), studentId,
                scope.level(), scope.subsystem(), scope.level(), scope.subsystem(),
                scope.level(), scope.subsystem());
        if (!Boolean.TRUE.equals(allowed)) throw denied("PARCOURS_RESOURCE_MISMATCH");
    }

    /**
     * Date at which the current session must be evaluated.  During year setup
     * the session can start tomorrow (or later); using wall-clock CURRENT_DATE
     * would make a valid future enrollment disappear from direct profile and
     * aggregate checks even though the rest of the academic UI already opens
     * the new session.  The same bounded date is used after the session closes.
     */
    private LocalDate currentEffectiveDate() {
        List<LocalDate[]> rows = jdbc.query("""
                SELECT start_date,end_date
                  FROM academic_session
                 WHERE school_id=? AND is_current=true
                 ORDER BY start_date DESC LIMIT 1
                """, (rs, n) -> new LocalDate[]{
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class)}, TenantContext.get());
        if (rows.isEmpty()) return LocalDate.now();
        LocalDate today = LocalDate.now();
        LocalDate start = rows.getFirst()[0];
        LocalDate end = rows.getFirst()[1];
        if (start != null && today.isBefore(start)) return start;
        if (end != null && today.isAfter(end)) return end;
        return today;
    }

    /**
     * Un élève sans classe est-il dans le périmètre de l'utilisateur courant ?
     * Réservé aux admins de section ; un cycle non renseigné reste visible de
     * tous les admins de section, faute de quoi l'élève ne serait visible de
     * personne.
     */
    public boolean unassignedVisible(String studentLevel) {
        String section = adminSection();
        if (section == null) return false;
        return studentLevel == null || studentLevel.isBlank() || section.equalsIgnoreCase(studentLevel);
    }

    /**
     * Refuse l'accès à un employé d'une autre section. Réservé aux admins de
     * section : un enseignant n'administre pas ses collègues, et le module
     * Personnel lui reste fermé par la matrice.
     *
     * <p>Un employé sans section reste visible : il n'appartient à aucun cycle
     * (économat, intendance) et l'exclure le rendrait invisible de tous.
     */
    public void assertEmployee(UUID employeeId) {
        String section = staffLevelScope();
        if (section == null) return;
        String level = jdbc.query("SELECT level FROM employee WHERE id = ? AND school_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, employeeId, TenantContext.get());
        if (level != null && !level.equals(section)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cet employé ne relève pas de votre section");
        }
    }

    /**
     * Staff follows the active parcours for explicitly scoped management
     * accounts. Global users in All-parcours mode keep a null scope and may
     * review the whole staff directory.
     */
    public String staffLevelScope() {
        String section = adminSection();
        return section != null ? section : ParcoursContext.effectiveLevel();
    }

    /** Refuse une opération portant sur un cycle autre que le parcours actif. */
    public void assertSection(String level) {
        String section = staffLevelScope();
        if (section == null) return;
        if (level == null || !level.equals(section)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cette section ne relève pas de votre périmètre");
        }
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
