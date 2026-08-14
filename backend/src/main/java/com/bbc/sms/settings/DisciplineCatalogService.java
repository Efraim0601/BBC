package com.bbc.sms.settings;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.CatalogItemUpsert;
import com.bbc.sms.settings.dto.SettingsDtos.CatalogItemView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

/** Per-school discipline type / sanction lists (editable from Settings). */
@Service
public class DisciplineCatalogService {

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;

    public DisciplineCatalogService(JdbcTemplate jdbc, AuthorizationPolicyService policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemView> list(String kind) {
        require("DISCIPLINE_CATALOG_VIEW");
        UUID schoolId = TenantContext.get();
        if (kind == null || kind.isBlank()) {
            return jdbc.query(
                    "SELECT id, kind, code, label_fr, label_en, sort_order, active FROM discipline_catalog "
                  + "WHERE school_id = ? ORDER BY kind, sort_order, label_fr",
                    this::map, schoolId);
        }
        return jdbc.query(
                "SELECT id, kind, code, label_fr, label_en, sort_order, active FROM discipline_catalog "
              + "WHERE school_id = ? AND kind = ? ORDER BY sort_order, label_fr",
                this::map, schoolId, kind);
    }

    @Transactional
    public CatalogItemView create(CatalogItemUpsert in) {
        require("DISCIPLINE_CATALOG_MANAGE");
        UUID schoolId = TenantContext.get();
        String kind = in.kind();
        String fr = in.labelFr().trim();
        String en = blankTo(in.labelEn(), fr);
        String code = blankTo(in.code(), slug(fr));
        int sort = in.sortOrder() == null ? nextSort(schoolId, kind) : in.sortOrder();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO discipline_catalog (id, school_id, kind, code, label_fr, label_en, sort_order, active)
                    VALUES (?,?,?,?,?,?,?, true)
                    """, id, schoolId, kind, code, fr, en, sort);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("Une entrée « " + code + " » existe déjà");
        }
        return new CatalogItemView(id, kind, code, fr, en, sort, true);
    }

    @Transactional
    public CatalogItemView update(UUID id, CatalogItemUpsert in) {
        require("DISCIPLINE_CATALOG_MANAGE");
        UUID schoolId = TenantContext.get();
        find(id, schoolId);
        String fr = in.labelFr().trim();
        String en = blankTo(in.labelEn(), fr);
        int sort = in.sortOrder() == null ? 0 : in.sortOrder();
        boolean active = in.active() == null || in.active();
        jdbc.update("""
                UPDATE discipline_catalog
                   SET label_fr = ?, label_en = ?, sort_order = ?, active = ?
                 WHERE id = ? AND school_id = ?
                """, fr, en, sort, active, id, schoolId);
        return find(id, schoolId);
    }

    @Transactional
    public void delete(UUID id) {
        require("DISCIPLINE_CATALOG_MANAGE");
        int n = jdbc.update("DELETE FROM discipline_catalog WHERE id = ? AND school_id = ?",
                id, TenantContext.get());
        if (n == 0) throw ApiException.notFound("Entrée catalogue");
    }

    private CatalogItemView find(UUID id, UUID schoolId) {
        List<CatalogItemView> rows = jdbc.query(
                "SELECT id, kind, code, label_fr, label_en, sort_order, active FROM discipline_catalog "
              + "WHERE id = ? AND school_id = ?",
                this::map, id, schoolId);
        if (rows.isEmpty()) throw ApiException.notFound("Entrée catalogue");
        return rows.get(0);
    }

    private void require(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    private int nextSort(UUID schoolId, String kind) {
        Integer m = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order),0) + 1 FROM discipline_catalog WHERE school_id = ? AND kind = ?",
                Integer.class, schoolId, kind);
        return m == null ? 1 : m;
    }

    private CatalogItemView map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new CatalogItemView(
                UUID.fromString(rs.getString("id")),
                rs.getString("kind"),
                rs.getString("code"),
                rs.getString("label_fr"),
                rs.getString("label_en"),
                rs.getInt("sort_order"),
                rs.getBoolean("active"));
    }

    private static String blankTo(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static String slug(String raw) {
        String n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        n = n.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        return n.isBlank() ? "item" : (n.length() > 40 ? n.substring(0, 40) : n);
    }
}
