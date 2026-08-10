package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.WorkflowWindowRuleUpsert;
import static com.bbc.sms.foundation.session.SessionDtos.WorkflowWindowRuleView;

/**
 * CRUD for the normalized workflow-window policy.  Legacy session/term/period
 * columns remain readable for compatibility, but new settings writes go
 * through this service so mode and endpoint intent cannot be lost.
 */
@Service
public class AcademicWindowRuleService {
    private static final List<String> ACTIONS = List.of(
            "GRADE_ENTRY", "TEACHER_SUBMISSION", "REVIEW", "VALIDATION", "PUBLICATION", "CORRECTION");
    private final JdbcTemplate jdbc;
    private final AcademicWindowPolicyService policy;
    private final AuditService audit;

    public AcademicWindowRuleService(JdbcTemplate jdbc, AcademicWindowPolicyService policy, AuditService audit) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkflowWindowRuleView> list(UUID sessionId) {
        assertSession(sessionId);
        UUID schoolId = TenantContext.get();
        return jdbc.query("""
                SELECT id,academic_session_id,scope_type,academic_term_id,reporting_period_id,
                       action,mode,opens_at,closes_at,timezone,version
                  FROM academic_workflow_window_rule
                 WHERE school_id=? AND academic_session_id=?
                 ORDER BY CASE scope_type WHEN 'SESSION' THEN 1 WHEN 'TERM' THEN 2 ELSE 3 END,
                          action, academic_term_id NULLS FIRST, reporting_period_id NULLS FIRST
                """, (rs, n) -> new WorkflowWindowRuleView(
                rs.getObject("id", UUID.class), rs.getObject("academic_session_id", UUID.class),
                rs.getString("scope_type"), rs.getObject("academic_term_id", UUID.class),
                rs.getObject("reporting_period_id", UUID.class), rs.getString("action"),
                rs.getString("mode"), instant(rs.getTimestamp("opens_at")),
                instant(rs.getTimestamp("closes_at")), rs.getString("timezone"),
                rs.getLong("version"), null, null), schoolId, sessionId);
    }

    @Transactional
    public WorkflowWindowRuleView upsert(UUID sessionId, WorkflowWindowRuleUpsert in) {
        UUID schoolId = TenantContext.get();
        MapTarget target = target(sessionId, in);
        String action = normalize(in.action());
        if (!ACTIONS.contains(action)) throw field("WINDOW_ACTION_INVALID", "action", "Choose a valid workflow action.");
        String mode = normalize(in.mode());
        if (!List.of("INHERIT", "UNRESTRICTED", "LIMITED").contains(mode)) {
            throw field("WINDOW_MODE_INVALID", "mode", "Use INHERIT, UNRESTRICTED, or LIMITED.");
        }
        if ("SESSION".equals(target.scopeType()) && "INHERIT".equals(mode)) {
            throw field("SESSION_WINDOW_CANNOT_INHERIT", "mode", "A session window must be unrestricted or limited.");
        }
        if ("PERIOD".equals(target.scopeType()) && isComputed(target.periodType())
                && ("GRADE_ENTRY".equals(action) || "TEACHER_SUBMISSION".equals(action))) {
            throw field("RAW_WINDOW_NOT_APPLICABLE", "mode", "Calculated result periods do not accept raw grade windows.");
        }
        Instant opens = in.opensAt();
        Instant closes = in.closesAt();
        if (!"LIMITED".equals(mode)) {
            if (opens != null || closes != null) throw field("WINDOW_ENDPOINTS_NOT_ALLOWED", "mode", "Only limited windows use dates.");
            opens = null; closes = null;
        } else {
            if (opens == null && closes == null) throw field("WINDOW_ENDPOINT_REQUIRED", "opensAt", "Add an opening or closing date for a limited window.");
            if (opens != null && closes != null && !closes.isAfter(opens)) {
                throw field("WINDOW_INVALID", "closesAt", "The closing date must be after the opening date.");
            }
        }
        String timezone = in.timezone() == null || in.timezone().isBlank()
                ? target.timezone() : in.timezone().trim();
        Existing existing = findExisting(schoolId, target, action);
        if (existing != null && in.version() != null && in.version() != existing.version()) {
            throw ApiException.staleVersion("The workflow window changed since it was loaded.", in.version(), existing.version());
        }
        var before = existing == null ? null : ruleMap(existing.id());
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO academic_workflow_window_rule
                        (id,school_id,academic_session_id,scope_type,academic_term_id,reporting_period_id,
                         action,mode,opens_at,closes_at,timezone)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, id, schoolId, sessionId, target.scopeType(), target.termId(), target.periodId(),
                    action, mode, timestamp(opens), timestamp(closes), timezone);
        } else {
            jdbc.update("""
                    UPDATE academic_workflow_window_rule
                       SET mode=?,opens_at=?,closes_at=?,timezone=?,updated_at=now(),version=version+1
                     WHERE id=? AND school_id=?
                    """, mode, timestamp(opens), timestamp(closes), timezone, id, schoolId);
        }
        WorkflowWindowRuleView result = jdbc.queryForObject("""
                SELECT id,academic_session_id,scope_type,academic_term_id,reporting_period_id,
                       action,mode,opens_at,closes_at,timezone,version
                  FROM academic_workflow_window_rule WHERE id=? AND school_id=?
                """, (rs, n) -> new WorkflowWindowRuleView(
                rs.getObject("id", UUID.class), rs.getObject("academic_session_id", UUID.class),
                rs.getString("scope_type"), rs.getObject("academic_term_id", UUID.class),
                rs.getObject("reporting_period_id", UUID.class), rs.getString("action"),
                rs.getString("mode"), instant(rs.getTimestamp("opens_at")), instant(rs.getTimestamp("closes_at")),
                rs.getString("timezone"), rs.getLong("version"), null, null), id, schoolId);
        audit.record(existing == null ? "WORKFLOW_WINDOW_CREATED" : "WORKFLOW_WINDOW_UPDATED",
                "AcademicWorkflowWindowRule", id.toString(), before, result, null);
        return result;
    }

    private Existing findExisting(UUID schoolId, MapTarget target, String action) {
        String sql = """
                SELECT id,version FROM academic_workflow_window_rule
                 WHERE school_id=? AND academic_session_id=? AND scope_type=? AND action=?
                   AND ((?='SESSION' AND academic_term_id IS NULL AND reporting_period_id IS NULL)
                     OR (?='TERM' AND academic_term_id=? AND reporting_period_id IS NULL)
                     OR (?='PERIOD' AND reporting_period_id=?))
                LIMIT 1
                """;
        return jdbc.query(sql, rs -> rs.next() ? new Existing(rs.getObject(1, UUID.class), rs.getLong(2)) : null,
                schoolId, target.sessionId(), target.scopeType(), action,
                target.scopeType(), target.scopeType(), target.termId(), target.scopeType(), target.periodId());
    }

    private MapTarget target(UUID sessionId, WorkflowWindowRuleUpsert in) {
        assertSession(sessionId);
        String scope = normalize(in.scopeType());
        if (!List.of("SESSION", "TERM", "PERIOD").contains(scope)) {
            throw field("WINDOW_SCOPE_INVALID", "scopeType", "Use SESSION, TERM, or PERIOD.");
        }
        UUID termId = in.academicTermId();
        UUID periodId = in.reportingPeriodId();
        UUID schoolId = TenantContext.get();
        String timezone = jdbc.queryForObject("SELECT timezone FROM academic_session WHERE id=? AND school_id=?",
                String.class, sessionId, schoolId);
        String periodType = null;
        if ("SESSION".equals(scope)) {
            if (termId != null || periodId != null) throw field("WINDOW_SCOPE_TARGET_INVALID", "scopeType", "A session rule cannot target a term or period.");
        } else if ("TERM".equals(scope)) {
            if (termId == null || periodId != null) throw field("WINDOW_SCOPE_TARGET_REQUIRED", "academicTermId", "Choose a term for a term rule.");
            Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_term WHERE id=? AND school_id=? AND academic_session_id=?",
                    Integer.class, termId, schoolId, sessionId);
            if (count == null || count == 0) throw ApiException.notFound("Academic term");
            timezone = jdbc.queryForObject("SELECT timezone FROM academic_term WHERE id=? AND school_id=?", String.class, termId, schoolId);
        } else {
            if (periodId == null) throw field("WINDOW_SCOPE_TARGET_REQUIRED", "reportingPeriodId", "Choose a reporting period for a period rule.");
            Map<String, Object> row = jdbc.query("SELECT academic_term_id,period_type,timezone FROM academic_reporting_period WHERE id=? AND school_id=? AND academic_session_id=?",
                    rs -> {
                        if (!rs.next()) return null;
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("term", rs.getObject(1, UUID.class));
                        result.put("type", rs.getString(2));
                        result.put("timezone", rs.getString(3));
                        return result;
                    },
                    periodId, schoolId, sessionId);
            if (row == null) throw ApiException.notFound("Reporting period");
            termId = (UUID) row.get("term"); periodType = (String) row.get("type");
            timezone = (String) row.get("timezone");
        }
        return new MapTarget(sessionId, scope, termId, periodId, timezone, periodType);
    }

    private java.util.Map<String, Object> ruleMap(UUID id) {
        return jdbc.query("SELECT id,scope_type,action,mode,opens_at,closes_at,version FROM academic_workflow_window_rule WHERE id=?",
                rs -> rs.next() ? java.util.Map.of("id", id, "scope", rs.getString(2), "action", rs.getString(3),
                        "mode", rs.getString(4), "opensAt", String.valueOf(rs.getTimestamp(5)),
                        "closesAt", String.valueOf(rs.getTimestamp(6)), "version", rs.getLong(7)) : null, id);
    }

    private void assertSession(UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_session WHERE id=? AND school_id=?",
                Integer.class, id, TenantContext.get());
        if (count == null || count == 0) throw ApiException.notFound("Academic session");
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean isComputed(String type) { return type != null && !"SEQUENCE".equalsIgnoreCase(type); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static ApiException field(String code, String field, String message) {
        return ApiException.field(HttpStatus.BAD_REQUEST, code, message, field, message);
    }

    private record Existing(UUID id, long version) {}
    private record MapTarget(UUID sessionId, String scopeType, UUID termId, UUID periodId,
                             String timezone, String periodType) {}
}
