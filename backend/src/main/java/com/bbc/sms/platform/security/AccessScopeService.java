package com.bbc.sms.platform.security;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Cloisonnement d'un compte : l'ensemble des classes qu'il a le droit de voir.
 *
 * <p>Deux cloisonnements coexistent, de granularité différente mais de même
 * nature — un sous-ensemble de classes :
 * <ul>
 *   <li><b>Enseignant</b> : les classes qui lui sont assignées ;</li>
 *   <li><b>Admin de section</b> : toutes les classes de son cycle.</li>
 * </ul>
 * Les exprimer d'une seule façon a une conséquence heureuse : les modules déjà
 * cloisonnés pour l'enseignant — élèves, notes, bulletins, présences, discipline,
 * santé, cahier de textes, emploi du temps, parcours — le sont du même coup pour
 * l'admin de section, sans qu'aucun d'eux ne soit modifié.
 *
 * <p>Les autres rôles (direction, censeur, économe) ne sont pas restreints : le
 * filtre par parcours, choisi à la connexion, leur suffit.
 *
 * <p>Un enseignant sans aucune classe assignée ne voit rien — c'est volontaire :
 * mieux vaut une liste vide qu'un accès par défaut à tout l'établissement.
 */
@Service("accessScope")
public class AccessScopeService {

    /** Rôles cloisonnés aux classes qui leur sont assignées. */
    private static final Set<String> RESTRICTED_ROLES = Set.of("teacher", "form_teacher");

    /**
     * Les classes d'un enseignant, par ordre de force du lien :
     *   1. affectation explicite (fiche employé ou écran des classes) ;
     *   2. classe dont il est titulaire (professeur principal) ;
     *   3. classe où il assure au moins un cours dans l'emploi du temps.
     *
     * <p>Le troisième cas est indispensable : donner une matière à un enseignant
     * dans la grille d'une classe, c'est l'affecter à cette classe. Il est aussi
     * auto-nettoyant — le cours retiré, l'accès disparaît avec lui.
     */
    private static final String TEACHER_SQL = """
            SELECT %s FROM school_class c
             WHERE c.school_id = ?
               AND (c.id IN (SELECT class_id FROM teacher_class WHERE employee_id = ?)
                    OR c.name = (SELECT form_class FROM employee WHERE id = ?)
                    OR c.id IN (SELECT class_id FROM timetable_slot
                                 WHERE school_id = ? AND teacher_id = ?))
            """;

    /** Les classes d'une section — le périmètre d'un admin de cycle. */
    private static final String SECTION_SQL = """
            SELECT %s FROM school_class c WHERE c.school_id = ? AND c.level = ?
            """;

    private final JdbcTemplate jdbc;

    public AccessScopeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** L'utilisateur courant est-il cloisonné (enseignant ou admin de section) ? */
    public boolean restricted() {
        return section() != null || teacherEmployeeId() != null;
    }

    /**
     * Section de l'utilisateur courant (maternelle|primary|secondary), null s'il
     * n'est cloisonné à aucun cycle.
     *
     * <p>Pour un admin de section elle se lit dans le rôle ; pour un enseignant,
     * dans sa fiche employé.
     */
    public String section() {
        AppUserPrincipal p = principal();
        if (p == null) return null;
        String adminSection = SectionRoles.sectionOf(p.roleCode());
        if (adminSection != null) return adminSection;
        UUID employeeId = teacherEmployeeId();
        if (employeeId == null) return null;
        return jdbc.query("SELECT level FROM employee WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, employeeId);
    }

    /** La section administrée, ou null — un enseignant n'administre pas son cycle. */
    public String adminSection() {
        AppUserPrincipal p = principal();
        return p == null ? null : SectionRoles.sectionOf(p.roleCode());
    }

    /**
     * Identifiants des classes visibles par l'utilisateur courant, ou null quand
     * il n'est pas cloisonné (aucun filtre à appliquer).
     */
    public Set<UUID> allowedClassIds() {
        return classes("c.id", (rs, n) -> UUID.fromString(rs.getString(1)));
    }

    /** Noms des classes visibles, ou null quand l'utilisateur n'est pas cloisonné. */
    public Set<String> allowedClassNames() {
        return classes("c.name", (rs, n) -> rs.getString(1));
    }

    private <T> Set<T> classes(String column, org.springframework.jdbc.core.RowMapper<T> mapper) {
        String adminSection = adminSection();
        if (adminSection != null) {
            return Set.copyOf(jdbc.query(SECTION_SQL.formatted(column), mapper,
                    TenantContext.get(), adminSection));
        }
        UUID employeeId = teacherEmployeeId();
        if (employeeId == null) return null;                // non cloisonné
        return Set.copyOf(jdbc.query(TEACHER_SQL.formatted(column), mapper,
                TenantContext.get(), employeeId, employeeId, TenantContext.get(), employeeId));
    }

    /** Refuse l'accès à une classe hors du périmètre de l'utilisateur courant. */
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

    /**
     * Refuse l'accès à un élève scolarisé hors des classes de l'utilisateur.
     *
     * <p>Un élève sans classe échappe par nature au filtre par classe. Pour
     * l'enseignant il reste hors de portée — il n'a pas d'élève sans classe. Pour
     * l'admin de section, en revanche, ce sont ses inscriptions du jour : il doit
     * pouvoir les ouvrir pour les affecter, sans quoi elles resteraient
     * impossibles à traiter. Son cycle borne alors l'accès.
     */
    public void assertStudent(UUID studentId) {
        Set<UUID> allowed = allowedClassIds();
        if (allowed == null) return;
        var row = jdbc.query("SELECT class_id, level FROM student WHERE id = ? AND school_id = ?",
                rs -> rs.next()
                        ? new String[] { rs.getString("class_id"), rs.getString("level") }
                        : null,
                studentId, TenantContext.get());
        if (row == null) throw denied();
        if (row[0] != null) {
            if (!allowed.contains(UUID.fromString(row[0]))) throw denied();
            return;
        }
        if (!unassignedVisible(row[1])) throw denied();
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
        String section = adminSection();
        if (section == null) return;
        String level = jdbc.query("SELECT level FROM employee WHERE id = ? AND school_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, employeeId, TenantContext.get());
        if (level != null && !level.equals(section)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cet employé ne relève pas de votre section");
        }
    }

    /** Refuse une opération portant sur une section autre que la sienne. */
    public void assertSection(String level) {
        String section = adminSection();
        if (section == null) return;
        if (level == null || !level.equals(section)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cette section ne relève pas de votre périmètre");
        }
    }

    private static ApiException denied() {
        return new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "Cette classe ne vous est pas assignée");
    }

    /** Employé lié au compte, mais seulement pour les rôles cloisonnés par classe. */
    private UUID teacherEmployeeId() {
        AppUserPrincipal p = principal();
        if (p == null || !RESTRICTED_ROLES.contains(p.roleCode())) return null;
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
