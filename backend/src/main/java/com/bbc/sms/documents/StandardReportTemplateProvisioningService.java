package com.bbc.sms.documents;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.documents.DocumentDesignDtos.*;

/**
 * Runtime installer for the four standard report-card families.  It is
 * deliberately keyed by a tenant-local standard key rather than by a
 * migration-time school row, so restored and newly created schools follow the
 * same path.  Published custom rows are never updated or deleted.
 */
@Service
public class StandardReportTemplateProvisioningService {
    public static final List<String> LAYOUT_LEVELS = List.of("maternelle", "primary", "secondary");

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("REPORT_CARD:FR:TERM", "fr", "TERM", "FR_TERM",
                    "Mod\u00e8le standard de bulletin trimestriel fran\u00e7ais"),
            new Definition("REPORT_CARD:FR:ANNUAL", "fr", "ANNUAL", "FR_ANNUAL",
                    "Mod\u00e8le standard de bulletin annuel fran\u00e7ais"),
            new Definition("REPORT_CARD:EN:TERM", "en", "TERM", "EN_TERM",
                    "English standard term report-card template"),
            new Definition("REPORT_CARD:EN:ANNUAL", "en", "ANNUAL", "EN_ANNUAL",
                    "English standard annual report-card template")
    );

    private static final String STANDARD_CONFIG = """
            {"layoutByLevel":{"maternelle":"NURSERY","primary":"PRIMARY","secondary":"SECONDARY"},
             "page":"A4","font":"UnicodeSans","margins":{"top":36,"right":36,"bottom":42,"left":36},
             "repeatingHeader":true,"keepSubtotalWithRows":true,
             "sections":["institutionalHeader","identity","photo","classMaster","subjects","subtotals",
             "components","termAnnualColumns","coefficients","weightedTotals","ranks","teacherProvenance",
             "remarks","classStatistics","trimesterRecap","attendance","conduct","honors","council",
             "signatures","stamp","documentNumber","versionHash","verificationQr"]}
            """.replaceAll("\\s+", "");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public StandardReportTemplateProvisioningService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public StandardTemplateProvisioningView preview() {
        return preview(TenantContext.get());
    }

    @Transactional(readOnly = true)
    public StandardTemplateProvisioningView preview(UUID schoolId) {
        int count = jdbc.queryForObject("""
                SELECT count(*) FROM document_template
                 WHERE school_id=? AND type='REPORT_CARD'
                """, Integer.class, schoolId);
        List<StandardTemplateFamilyView> families = DEFINITIONS.stream()
                .map(definition -> family(schoolId, definition)).toList();
        boolean needs = families.stream().anyMatch(family -> !family.installed());
        return new StandardTemplateProvisioningView(count > 0, needs, count,
                LAYOUT_LEVELS, families);
    }

    @Transactional
    public int installCurrent(String reason) {
        UUID schoolId = TenantContext.get();
        int installed = ensureForSchool(schoolId);
        StandardTemplateProvisioningView after = preview(schoolId);
        audit.record("STANDARD_REPORT_TEMPLATES_PROVISIONED", "School", schoolId.toString(),
                null, after, reason == null ? "" : reason.trim());
        return installed;
    }

    /** Install missing standard versions for every school, without changing existing rows. */
    @Transactional
    public int provisionAllSchools() {
        List<UUID> schools = jdbc.query("SELECT id FROM school ORDER BY id",
                (rs, n) -> rs.getObject(1, UUID.class));
        int inserted = 0;
        for (UUID schoolId : schools) inserted += ensureForSchool(schoolId);
        return inserted;
    }

    /** Idempotent and safe to call concurrently for one tenant. */
    @Transactional
    public int ensureForSchool(UUID schoolId) {
        int inserted = 0;
        for (Definition definition : DEFINITIONS) {
            Integer current = jdbc.query("""
                    SELECT template_version FROM document_template
                     WHERE school_id=? AND standard_key=? AND type='REPORT_CARD'
                       AND status='PUBLISHED' AND active
                       AND (effective_from IS NULL OR effective_from<=CURRENT_DATE)
                       AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
                     ORDER BY template_version DESC LIMIT 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, schoolId, definition.key());
            if (current != null) continue;
            Integer next = jdbc.queryForObject("""
                    SELECT coalesce(max(template_version),0)+1
                      FROM document_template
                     WHERE school_id=? AND type='REPORT_CARD' AND locale=? AND product=?
                       AND subsystem IS NULL
                    """, Integer.class, schoolId, definition.locale(), definition.product());
            int changed = jdbc.update("""
                    INSERT INTO document_template
                        (school_id,type,locale,name,template_version,body_template,active,
                         template_family,product,subsystem,status,reference_family,checksum,
                         config_json,standard_key,effective_from,effective_to,published_at)
                    VALUES (?,?,?,?,?,?,true,?,?,NULL,'PUBLISHED','STANDARD',?,?::jsonb,?,CURRENT_DATE,NULL,now())
                    ON CONFLICT DO NOTHING
                    """, schoolId, "REPORT_CARD", definition.locale(), definition.label(), next,
                    "BAY36|" + definition.key(), definition.family(), definition.product(),
                    sha256("BAY36|" + definition.key() + "\n" + STANDARD_CONFIG), STANDARD_CONFIG,
                    definition.key());
            inserted += changed;
        }
        return inserted;
    }

    public static String standardConfig() { return STANDARD_CONFIG; }

    public static List<String> standardKeys() { return DEFINITIONS.stream().map(Definition::key).toList(); }

    private StandardTemplateFamilyView family(UUID schoolId, Definition definition) {
        return jdbc.query("""
                SELECT template_version,status,effective_from,effective_to
                  FROM document_template
                 WHERE school_id=? AND standard_key=? AND type='REPORT_CARD'
                   AND status='PUBLISHED' AND active
                   AND (effective_from IS NULL OR effective_from<=CURRENT_DATE)
                   AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
                 ORDER BY template_version DESC LIMIT 1
                """, rs -> rs.next() ? new StandardTemplateFamilyView(definition.key(), definition.locale(),
                        definition.product(), definition.family(), definition.label(), true, rs.getInt(1),
                        rs.getString(2), date(rs.getObject("effective_from")), date(rs.getObject("effective_to")))
                        : new StandardTemplateFamilyView(definition.key(), definition.locale(), definition.product(),
                        definition.family(), definition.label(), false, 0, null, null, null), schoolId, definition.key());
    }

    private static String date(Object value) { return value == null ? null : value.toString(); }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash standard report-card template", ex);
        }
    }

    private record Definition(String key, String locale, String product, String family, String label) {}
}
