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

    /** The parcours a user may access; empty list = unrestricted (all parcours). */
    public List<Scope> allowed(UUID userId) {
        return jdbc.query(
                "SELECT level, subsystem FROM app_user_parcours WHERE user_id = ?",
                (rs, n) -> new Scope(rs.getString("level"), rs.getString("subsystem")),
                userId);
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
