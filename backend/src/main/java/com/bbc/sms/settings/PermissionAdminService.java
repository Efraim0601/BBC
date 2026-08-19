package com.bbc.sms.settings;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/**
 * Reads and edits the permission matrix (role x module -> level) for the current
 * tenant, and manages custom roles ({@code builtin = false}).
 */
@Service
public class PermissionAdminService {

    /** Every module the matrix can grant (aligned with ProductionBootstrap). */
    static final List<String> MODULES = List.of(
            "dashboard", "presence", "students", "hr", "academic", "finance",
            "timetable", "events", "discipline", "reports", "settings", "journey",
            "alerts", "messages", "coursebook", "health", "documents", "classkit",
            "promotion", "library", "parent");

    /** Modules a Parent role may hold — everything else is staff-only. */
    private static final Set<String> PARENT_ALLOWED_MODULES = Set.of("parent");

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
            if (!MODULES.contains(u.module())) {
                throw ApiException.badRequest("Module inconnu : " + u.module());
            }
            assertRoleExists(u.roleCode());
            // Parents may only hold the dedicated parent-portal module.
            if ("parent".equals(u.roleCode())
                    && !"none".equals(u.level())
                    && !PARENT_ALLOWED_MODULES.contains(u.module())) {
                throw ApiException.badRequest(
                        "Le rôle Parent ne peut accéder qu'au portail parent (données de ses enfants).");
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

    @Transactional
    public RoleView createRole(RoleUpsert in) {
        String code = slug(in.code() != null && !in.code().isBlank() ? in.code() : in.labelFr());
        if (code.isBlank()) throw ApiException.badRequest("Code de rôle obligatoire");
        if (code.length() > 32) throw ApiException.badRequest("Code trop long (max 32)");
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM role WHERE code = ?", Integer.class, code);
        if (exists != null && exists > 0) {
            throw ApiException.conflict("Un rôle « " + code + " » existe déjà");
        }
        String fr = in.labelFr().trim();
        String en = (in.labelEn() == null || in.labelEn().isBlank()) ? fr : in.labelEn().trim();
        jdbc.update("INSERT INTO role (code, label_fr, label_en, builtin) VALUES (?,?,?, false)",
                code, fr, en);
        // Default: no grants — admin configures the matrix next.
        return new RoleView(code, fr, en, false);
    }

    /** Labels only — role codes stay immutable (including built-in roles). */
    @Transactional
    public RoleView updateRole(String code, RoleUpsert in) {
        Map<String, Object> row = roleRow(code);
        boolean builtin = Boolean.TRUE.equals(row.get("builtin"));
        String fr = in.labelFr().trim();
        if (fr.isBlank()) throw ApiException.badRequest("Libellé obligatoire");
        String en = (in.labelEn() == null || in.labelEn().isBlank()) ? fr : in.labelEn().trim();
        jdbc.update("UPDATE role SET label_fr = ?, label_en = ? WHERE code = ?", fr, en, code);
        return new RoleView(code, fr, en, builtin);
    }

    @Transactional(readOnly = true)
    public List<RoleView> listRoles() {
        return jdbc.query(
                "SELECT code, label_fr, label_en, builtin FROM role ORDER BY builtin DESC, code",
                (rs, i) -> new RoleView(rs.getString("code"), rs.getString("label_fr"),
                        rs.getString("label_en"), rs.getBoolean("builtin")));
    }

    @Transactional
    public void deleteRole(String code) {
        Map<String, Object> row = roleRow(code);
        if (Boolean.TRUE.equals(row.get("builtin"))) {
            throw ApiException.badRequest("Les rôles intégrés ne peuvent pas être supprimés");
        }
        Integer users = jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE role_code = ?", Integer.class, code);
        if (users != null && users > 0) {
            throw ApiException.conflict("Des comptes utilisent encore ce rôle — réaffectez-les d'abord");
        }
        jdbc.update("DELETE FROM permission_grant WHERE role_code = ?", code);
        jdbc.update("DELETE FROM employee_role WHERE role_code = ?", code);
        jdbc.update("DELETE FROM role WHERE code = ?", code);
    }

    private Map<String, Object> roleRow(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT code, label_fr, label_en, builtin FROM role WHERE code = ?", code);
        if (rows.isEmpty()) throw ApiException.notFound("Rôle");
        return rows.get(0);
    }

    private void assertRoleExists(String code) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM role WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) throw ApiException.badRequest("Rôle inconnu : " + code);
    }

    /** Stable short code from a label: "Surveillant général" → "surveillant-general". */
    static String slug(String raw) {
        if (raw == null) return "";
        String n = Normalizer.normalize(raw.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return n.length() > 32 ? n.substring(0, 32).replaceAll("-$", "") : n;
    }
}
