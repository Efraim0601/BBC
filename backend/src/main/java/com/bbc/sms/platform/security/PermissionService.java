package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RBAC gate used from controllers via SpEL: @PreAuthorize("@perm.can('finance','write')").
 * Reads the live permission matrix (role x module -> level) for the current tenant.
 * NEVER trust the front-end: every protected endpoint must call this.
 */
@Service("perm")
public class PermissionService {

    private static final List<String> RANK = List.of("none", "read", "write");

    private final JdbcTemplate jdbc;

    public PermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean can(String module, String requiredLevel) {
        AppUserPrincipal p = currentPrincipal();
        if (p == null) return false;
        String level = jdbc.query(
                "SELECT level FROM permission_grant WHERE school_id = ? AND role_code = ? AND module = ?",
                rs -> rs.next() ? rs.getString(1) : "none",
                TenantContext.get(), p.roleCode(), module);
        return RANK.indexOf(level) >= RANK.indexOf(requiredLevel);
    }

    public boolean isParent() {
        AppUserPrincipal p = currentPrincipal();
        return p != null && "parent".equals(p.roleCode());
    }

    /**
     * Staff modules (Academic, Students roster, Finance…) must never be reachable
     * by a Parent account — even if the matrix was misconfigured historically.
     * Use: {@code @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")}.
     */
    public boolean staffOnly() {
        return !isParent();
    }

    private AppUserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof AppUserPrincipal aup ? aup : null;
    }
}
