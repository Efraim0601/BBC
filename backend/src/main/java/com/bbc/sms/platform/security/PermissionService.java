package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    /**
     * Fine-grained command authorization. An explicit action grant wins; schools
     * upgraded from older versions safely inherit the corresponding module level.
     */
    public boolean canAction(String actionCode) {
        AppUserPrincipal p = currentPrincipal();
        if (p == null || actionCode == null) return false;
        String code = actionCode.trim().toUpperCase();
        List<Boolean> overrides = jdbc.query(
                "SELECT allowed FROM permission_action_grant WHERE school_id=? AND role_code=? AND action_code=?",
                (rs, i) -> rs.getBoolean(1), TenantContext.get(), p.roleCode(), code);
        if (!overrides.isEmpty()) return overrides.get(0);
        PermissionActions.Requirement fallback = PermissionActions.CATALOG.get(code);
        return fallback != null && can(fallback.module(), fallback.level());
    }

    public Map<String, Boolean> currentActions() {
        return PermissionActions.CATALOG.keySet().stream().sorted()
                .collect(java.util.stream.Collectors.toMap(a -> a, this::canAction,
                        (a, b) -> a, java.util.LinkedHashMap::new));
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
