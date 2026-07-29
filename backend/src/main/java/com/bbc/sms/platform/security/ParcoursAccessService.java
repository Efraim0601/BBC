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
 */
@Service("parcours")
public class ParcoursAccessService {

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
        List<Scope> explicit = jdbc.query(
                "SELECT level, subsystem FROM app_user_parcours WHERE user_id = ?",
                (rs, n) -> new Scope(rs.getString("level"), rs.getString("subsystem")),
                userId);
        if (!explicit.isEmpty()) return explicit;
        return fromEmployeeSection(userId);
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
