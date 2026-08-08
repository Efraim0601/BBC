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
 * Enforces the {@code X-Parcours} scope against the user's allowed parcours
 * ({@code app_user_parcours}). An EMPTY allow-list means the user may access every parcours
 * (administrators); a non-empty list restricts them to exactly those (level, subsystem) pairs.
 *
 * <p>Trois sources, par ordre de force : le rôle d'admin de section (verrou, cf.
 * {@link SectionRoles}), la liste explicite {@code app_user_parcours}, puis la
 * section de l'employé pour les enseignants.
 */
@Service("parcours")
public class ParcoursAccessService {

    /** Les deux sous-systèmes : un admin de section administre les deux. */
    private static final List<String> SUBSYSTEMS = List.of("FR", "EN");

    private final JdbcTemplate jdbc;

    public ParcoursAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * The parcours a user may access; empty list = unrestricted (all parcours).
     *
     * <p>Les restrictions explicites ({@code app_user_parcours}) priment. À défaut,
     * un compte rattaché à un employé qui a une section est limité à cette section :
     * l'enseignant est ainsi orienté dans son cycle dès la connexion, sans qu'un
     * administrateur ait à remplir une table de plus. Les sous-systèmes proposés
     * sont ceux de ses classes ; sans classe assignée, les deux restent ouverts.
     */
    public List<Scope> allowed(UUID userId) {
        String adminSection = adminSection(userId);
        if (adminSection != null) {
            // Un admin de section règne sur les deux sous-systèmes de son cycle,
            // et sur eux seuls. Ce verrou prime sur toute restriction saisie à la
            // main : il découle du rôle, on ne le desserre pas par une table.
            return SUBSYSTEMS.stream().map(sub -> new Scope(adminSection, sub)).toList();
        }
        List<Scope> explicit = jdbc.query(
                "SELECT level, subsystem FROM app_user_parcours WHERE user_id = ?",
                (rs, n) -> new Scope(rs.getString("level"), rs.getString("subsystem")),
                userId);
        if (!explicit.isEmpty()) return explicit;
        return fromEmployeeSection(userId);
    }

    /** Section administrée par ce compte, ou null s'il n'est pas un admin de section. */
    private String adminSection(UUID userId) {
        AppUserPrincipal p = currentPrincipal();
        if (p != null && p.userId().equals(userId)) {
            return SectionRoles.sectionOf(p.roleCode());     // cas courant : sans requête
        }
        String role = jdbc.query("SELECT role_code FROM app_user WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        return SectionRoles.sectionOf(role);
    }

    /**
     * Section de l'employé lié au compte, déclinée en parcours (section × sous-systèmes).
     * Réservé aux rôles enseignants : la direction, le censeur ou l'économe peuvent
     * porter une section sans pour autant être cloisonnés à un parcours.
     */
    private List<Scope> fromEmployeeSection(UUID userId) {
        String section = jdbc.query("""
                SELECT e.level FROM app_user u JOIN employee e ON e.id = u.employee_id
                 WHERE u.id = ? AND u.role_code IN ('teacher','form_teacher')
                """, rs -> rs.next() ? rs.getString(1) : null, userId);
        if (section == null || section.isBlank()) return List.of();

        List<String> subsystems = jdbc.query("""
                SELECT DISTINCT c.subsystem FROM school_class c
                 WHERE c.level = ?
                   AND (c.id IN (SELECT tc.class_id FROM teacher_class tc
                                  JOIN app_user u ON u.employee_id = tc.employee_id
                                 WHERE u.id = ?)
                        OR c.name = (SELECT e.form_class FROM app_user u
                                       JOIN employee e ON e.id = u.employee_id
                                      WHERE u.id = ?))
                """, (rs, n) -> rs.getString(1), section, userId, userId);
        if (subsystems.isEmpty()) subsystems = List.of("FR", "EN");
        return subsystems.stream().map(sub -> new Scope(section, sub)).toList();
    }

    /**
     * True when the request's parcours scope is permitted for the current user.
     * No scope bound to the request is always allowed (cross-parcours views).
     *
     * <p>Absence d'en-tête ne vaut pas absence de borne : un admin de section
     * garde son verrou, que les services appliquent à leurs listes
     * ({@link ParcoursContext#sectionLock()}). Ce contrôle-ci ne juge que le
     * parcours <em>demandé</em>.
     */
    public boolean allows() {
        Scope scope = ParcoursContext.get();
        if (scope == null) return true;
        AppUserPrincipal p = currentPrincipal();
        if (p == null) return false;
        List<Scope> allowed = allowed(p.userId());
        return allowed.isEmpty() || allowed.contains(scope);
    }

    private AppUserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal aup ? aup : null;
    }
}
