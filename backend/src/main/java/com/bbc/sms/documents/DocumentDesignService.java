package com.bbc.sms.documents;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.identity.School;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.bbc.sms.documents.DocumentDesignDtos.*;

/** Version ledger for the design inputs that official report snapshots reference. */
@Service
public class DocumentDesignService {
    private final JdbcTemplate jdbc;
    private final SchoolRepository schools;
    private final AuditService audit;

    public DocumentDesignService(JdbcTemplate jdbc, SchoolRepository schools, AuditService audit) {
        this.jdbc = jdbc;
        this.schools = schools;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DocumentDesignView current() {
        UUID schoolId = TenantContext.get();
        List<TemplateVersionView> templates = jdbc.query("""
                SELECT id,type,locale,name,template_version,template_family,product,subsystem,
                       status,reference_family,checksum,published_at
                  FROM document_template
                 WHERE school_id=?
                 ORDER BY type,locale,template_version DESC
                """, (rs, n) -> new TemplateVersionView(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("locale"), rs.getString("name"), rs.getInt("template_version"),
                rs.getString("template_family"), rs.getString("product"), rs.getString("subsystem"),
                rs.getString("status"), rs.getString("reference_family"), rs.getString("checksum"),
                instant(rs, "published_at")), schoolId);
        List<BrandingVersionView> branding = jdbc.query("""
                SELECT id,locale,version,status,school_name,school_name_en,motto,ministry_text,
                       city,country,principal_name,principal_title,class_master_title,council_title,
                       content_hash,created_at,published_at
                  FROM document_branding_version
                 WHERE school_id=?
                 ORDER BY locale,version DESC
                """, (rs, n) -> new BrandingVersionView(rs.getObject("id", UUID.class), rs.getString("locale"),
                rs.getInt("version"), rs.getString("status"), rs.getString("school_name"),
                rs.getString("school_name_en"), rs.getString("motto"), rs.getString("ministry_text"),
                rs.getString("city"), rs.getString("country"), rs.getString("principal_name"),
                rs.getString("principal_title"), rs.getString("class_master_title"),
                rs.getString("council_title"), rs.getString("content_hash"),
                instant(rs, "created_at"), instant(rs, "published_at")), schoolId);
        return new DocumentDesignView(templates, branding);
    }

    @Transactional
    public TemplateVersionView publishTemplate(UUID templateId, String reason) {
        UUID schoolId = TenantContext.get();
        TemplateSeed source = jdbc.query("""
                SELECT id,type,locale,name,body_template,template_family,product,subsystem,
                       reference_family,config_json::text
                  FROM document_template
                 WHERE id=? AND school_id=?
                """, rs -> rs.next() ? new TemplateSeed(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("locale"), rs.getString("name"), rs.getString("body_template"),
                rs.getString("template_family"), rs.getString("product"), rs.getString("subsystem"),
                rs.getString("reference_family"), rs.getString("config_json")) : null, templateId, schoolId);
        if (source == null) throw ApiException.notFound("Version de modèle");
        int nextVersion = jdbc.queryForObject("""
                SELECT coalesce(max(template_version),0)+1 FROM document_template
                 WHERE school_id=? AND type=? AND locale=?
                """, Integer.class, schoolId, source.type(), source.locale());
        UUID id = UUID.randomUUID();
        String checksum = sha256(source.bodyTemplate());
        UUID actor = currentUserId();
        jdbc.update("""
                INSERT INTO document_template
                    (id,school_id,type,locale,name,template_version,body_template,template_family,
                     product,subsystem,status,reference_family,checksum,published_at,published_by,
                     config_json,active)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,true)
                """, id, schoolId, source.type(), source.locale(), source.name(), nextVersion,
                source.bodyTemplate(), source.templateFamily(), source.product(), source.subsystem(),
                "PUBLISHED", source.referenceFamily(), checksum, Instant.now(), actor,
                source.configJson() == null || source.configJson().isBlank() ? "{}" : source.configJson());
        TemplateVersionView result = findTemplate(id);
        audit.record("DOCUMENT_TEMPLATE_PUBLISHED", "DocumentTemplate", id.toString(), source, result, reason.trim());
        return result;
    }

    @Transactional
    public BrandingVersionView publishBranding(PublishRequest request) {
        UUID schoolId = TenantContext.get();
        School school = schools.findById(schoolId).orElseThrow(() -> ApiException.notFound("Établissement"));
        String locale = normalizeLocale(request.locale());
        BrandingSeed previous = jdbc.query("""
                SELECT id,school_name_en,delegation_text,logo_content_type,logo_bytes,
                       stamp_content_type,stamp_bytes,principal_name,principal_title,
                       class_master_title,council_title,signatory_manifest::text,asset_manifest::text
                  FROM document_branding_version
                 WHERE school_id=? AND locale=?
                 ORDER BY version DESC LIMIT 1
                """, rs -> rs.next() ? new BrandingSeed(rs.getObject("id", UUID.class),
                rs.getString("school_name_en"), rs.getString("delegation_text"),
                rs.getString("logo_content_type"), rs.getBytes("logo_bytes"),
                rs.getString("stamp_content_type"), rs.getBytes("stamp_bytes"),
                rs.getString("principal_name"), rs.getString("principal_title"),
                rs.getString("class_master_title"), rs.getString("council_title"),
                rs.getString("signatory_manifest"), rs.getString("asset_manifest")) : null,
                schoolId, locale);
        int version = jdbc.queryForObject("""
                SELECT coalesce(max(version),0)+1 FROM document_branding_version
                 WHERE school_id=? AND locale=?
                """, Integer.class, schoolId, locale);
        UUID id = UUID.randomUUID();
        UUID actor = currentUserId();
        String signatories = previous == null || previous.signatoryManifest() == null ? "{}" : previous.signatoryManifest();
        String assets = previous == null || previous.assetManifest() == null ? "{}" : previous.assetManifest();
        String contentHash = sha256(String.join("|", value(school.getName()), value(school.getMotto()),
                value(school.getAuthority()), value(school.getCity()), value(school.getCountry()),
                value(school.getAddress()), value(school.getPhone()), value(school.getEmail()),
                value(school.getWebsite()), value(previous == null ? null : previous.principalName()),
                value(previous == null ? null : previous.principalTitle()),
                value(previous == null ? null : previous.classMasterTitle()),
                value(previous == null ? null : previous.councilTitle()),
                bytesHash(previous == null ? null : previous.logoBytes()),
                bytesHash(previous == null ? null : previous.stampBytes())));
        jdbc.update("""
                UPDATE document_branding_version SET status='RETIRED'
                 WHERE school_id=? AND locale=? AND status='PUBLISHED'
                """, schoolId, locale);
        jdbc.update("""
                INSERT INTO document_branding_version
                    (id,school_id,locale,version,status,school_name,school_name_en,motto,
                     ministry_text,delegation_text,address,city,country,phone,email,website,
                     logo_content_type,logo_bytes,stamp_content_type,stamp_bytes,principal_name,
                     principal_title,class_master_title,council_title,signatory_manifest,asset_manifest,
                     content_hash,created_by,created_at,published_by,published_at)
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """, id, schoolId, locale, version, "PUBLISHED", school.getName(),
                previous == null ? null : previous.schoolNameEn(), school.getMotto(), school.getAuthority(),
                previous == null ? null : previous.delegationText(), school.getAddress(), school.getCity(),
                school.getCountry(), school.getPhone(), school.getEmail(), school.getWebsite(),
                previous == null ? null : previous.logoContentType(), previous == null ? null : previous.logoBytes(),
                previous == null ? null : previous.stampContentType(), previous == null ? null : previous.stampBytes(),
                previous == null ? null : previous.principalName(), previous == null ? null : previous.principalTitle(),
                previous == null ? null : previous.classMasterTitle(), previous == null ? null : previous.councilTitle(),
                signatories, assets, contentHash, actor, Instant.now(), actor, Instant.now());
        BrandingVersionView result = findBranding(id);
        audit.record("DOCUMENT_BRANDING_PUBLISHED", "DocumentBrandingVersion", id.toString(), previous, result, request.reason().trim());
        return result;
    }

    private TemplateVersionView findTemplate(UUID id) {
        return jdbc.query("""
                SELECT id,type,locale,name,template_version,template_family,product,subsystem,
                       status,reference_family,checksum,published_at
                  FROM document_template WHERE id=? AND school_id=?
                """, rs -> rs.next() ? new TemplateVersionView(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("locale"), rs.getString("name"), rs.getInt("template_version"),
                rs.getString("template_family"), rs.getString("product"), rs.getString("subsystem"),
                rs.getString("status"), rs.getString("reference_family"), rs.getString("checksum"),
                instant(rs, "published_at")) : null, id, TenantContext.get());
    }

    private BrandingVersionView findBranding(UUID id) {
        return jdbc.query("""
                SELECT id,locale,version,status,school_name,school_name_en,motto,ministry_text,
                       city,country,principal_name,principal_title,class_master_title,council_title,
                       content_hash,created_at,published_at
                  FROM document_branding_version WHERE id=? AND school_id=?
                """, rs -> rs.next() ? new BrandingVersionView(rs.getObject("id", UUID.class), rs.getString("locale"),
                rs.getInt("version"), rs.getString("status"), rs.getString("school_name"),
                rs.getString("school_name_en"), rs.getString("motto"), rs.getString("ministry_text"),
                rs.getString("city"), rs.getString("country"), rs.getString("principal_name"),
                rs.getString("principal_title"), rs.getString("class_master_title"),
                rs.getString("council_title"), rs.getString("content_hash"),
                instant(rs, "created_at"), instant(rs, "published_at")) : null,
                id, TenantContext.get());
    }

    private record TemplateSeed(UUID id, String type, String locale, String name, String bodyTemplate,
                                String templateFamily, String product, String subsystem,
                                String referenceFamily, String configJson) {}

    private record BrandingSeed(UUID id, String schoolNameEn, String delegationText,
                                String logoContentType, byte[] logoBytes, String stampContentType,
                                byte[] stampBytes, String principalName, String principalTitle,
                                String classMasterTitle, String councilTitle,
                                String signatoryManifest, String assetManifest) {}

    private static String normalizeLocale(String raw) {
        String locale = raw == null || raw.isBlank() ? "fr" : raw.trim().toLowerCase(Locale.ROOT);
        if (!locale.matches("[a-z]{2}(-[a-z]{2})?")) throw ApiException.badRequest("Locale de modèle invalide");
        return locale;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String value(String raw) { return raw == null ? "" : raw; }

    private static String bytesHash(byte[] bytes) {
        return bytes == null ? "" : sha256(bytes);
    }

    private static String sha256(String raw) { return sha256(raw.getBytes(StandardCharsets.UTF_8)); }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
