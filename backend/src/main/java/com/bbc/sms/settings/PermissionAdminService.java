package com.bbc.sms.settings;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Reads and edits the permission matrix (role x module -> level) for the current
 * tenant. This is the §13.1 governance module: changes take effect immediately
 * because the live {@code permission_grant} table is what {@code @perm.can(...)}
 * and the frontend navigation both read.
 */
@Service
public class PermissionAdminService {

    /** The 11 functional modules of the CDC (parent portal handled by the 'parent' grant). */
    static final List<String> MODULES = List.of(
            "dashboard", "presence", "students", "hr", "academic", "finance",
            "timetable", "events", "discipline", "reports", "settings");

    private static final Set<String> LEVELS = Set.of("none", "read", "write");

    private final JdbcTemplate jdbc;

    public PermissionAdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public PermissionMatrix getMatrix() {
        UUID schoolId = TenantContext.get();

        List<RoleView> roles = jdbc.query(
                "SELECT code, label_fr, label_en, builtin FROM role ORDER BY builtin DESC, code",
                (rs, i) -> new RoleView(rs.getString("code"), rs.getString("label_fr"),
                        rs.getString("label_en"), rs.getBoolean("builtin")));

        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        for (RoleView r : roles) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String m : MODULES) row.put(m, "none");
            matrix.put(r.code(), row);
        }
        jdbc.query("SELECT role_code, module, level FROM permission_grant WHERE school_id = ?",
                rs -> {
                    Map<String, String> row = matrix.get(rs.getString("role_code"));
                    if (row != null) row.put(rs.getString("module"), rs.getString("level"));
                }, schoolId);

        return new PermissionMatrix(MODULES, roles, matrix);
    }

    @Transactional
    public PermissionMatrix update(UpdateRequest req) {
        UUID schoolId = TenantContext.get();
        for (PermissionUpdate u : req.updates()) {
            if (!LEVELS.contains(u.level())) {
                throw ApiException.badRequest("Niveau invalide : " + u.level());
            }
            jdbc.update("""
                    INSERT INTO permission_grant (school_id, role_code, module, level)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (school_id, role_code, module)
                    DO UPDATE SET level = EXCLUDED.level
                    """, schoolId, u.roleCode(), u.module(), u.level());
        }
        return getMatrix();
    }
}
