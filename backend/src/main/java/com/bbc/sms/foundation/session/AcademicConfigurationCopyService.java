package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.bbc.sms.foundation.session.SessionDtos.*;

/** Preview-first, non-destructive copy of a previous session's structure. */
@Service
public class AcademicConfigurationCopyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public AcademicConfigurationCopyService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ConfigurationCopyPreview preview(UUID targetSessionId, ConfigurationCopyPreviewRequest request) {
        SessionData source = session(request.sourceSessionId());
        SessionData target = session(targetSessionId);
        if (source.id().equals(target.id())) throw ApiException.badRequest("Choisissez une session source différente de la session cible.");
        CopyScopeSelection scopes = request.scopes() == null ? CopyScopeSelection.all() : request.scopes();
        String mergeMode = mode(request.mergeMode());
        String dateStrategy = dateStrategy(request.dateStrategy());
        long dayShift = ChronoUnit.DAYS.between(source.startDate(), target.startDate());
        List<String> selectedKeys = request.selectedKeys() == null ? List.of() : request.selectedKeys();

        List<ConfigurationCopyRow> terms = scopes.terms()
                ? termRows(source, target, dayShift, mergeMode, request.edits(), selectedKeys) : List.of();
        Map<String, LocalDate[]> termBounds = termBounds(target.id(), terms);
        List<ConfigurationCopyRow> periods = scopes.reportingPeriods()
                ? periodRows(source, target, dayShift, mergeMode, request.edits(), selectedKeys, termBounds) : List.of();
        List<ConfigurationCopyRow> dependencies = scopes.dependencies()
                ? dependencyRows(source, target, mergeMode, request.edits(), selectedKeys) : List.of();
        List<ConfigurationCopyRow> windows = scopes.workflowWindows()
                ? windowRows(source, target, dayShift, mergeMode, request.edits(), selectedKeys) : List.of();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        if (source.status().equals("ARCHIVED")) warnings.add("The source session is archived; only configuration is copied.");
        if (!Set.of("DRAFT", "OPEN").contains(target.status())) blockers.add("TARGET_SESSION_NOT_MUTABLE");
        if (periods.stream().anyMatch(r -> !r.blockers().isEmpty())) blockers.add("REPORTING_PERIOD_REPAIR_REQUIRED");
        if (windows.stream().anyMatch(r -> !r.blockers().isEmpty())) blockers.add("WINDOW_RULE_REPAIR_REQUIRED");
        List<ConfigurationCopyRow> all = new ArrayList<>();
        all.addAll(terms); all.addAll(periods); all.addAll(dependencies); all.addAll(windows);
        int create = (int) all.stream().filter(r -> "CREATE".equals(r.status())).count();
        int update = (int) all.stream().filter(r -> "UPDATE".equals(r.status())).count();
        int keep = (int) all.stream().filter(r -> "KEEP".equals(r.status())).count();
        String fingerprint = fingerprint(source.id(), target.id(), dateStrategy, mergeMode, scopes, all);
        return new ConfigurationCopyPreview(source.id(), target.id(), source.label(), target.label(),
                dateStrategy, mergeMode, scopes, terms, periods, dependencies, windows,
                warnings, blockers, fingerprint, create, update, keep);
    }

    @Transactional
    public ConfigurationCopyPreview apply(UUID targetSessionId, ConfigurationCopyApplyRequest request) {
        SessionData target = session(targetSessionId);
        assertMutable(target);
        ConfigurationCopyPreviewRequest previewRequest = new ConfigurationCopyPreviewRequest(
                request.sourceSessionId(), request.dateStrategy(), request.mergeMode(), request.scopes(), request.edits(), request.selectedKeys());
        ConfigurationCopyPreview preview = preview(targetSessionId, previewRequest);
        if (!Objects.equals(request.previewFingerprint(), preview.fingerprint())) {
            throw ApiException.staleVersion("La proposition de reprise a changé depuis l'aperçu. Rechargez-la avant de l'appliquer.", 0, 0);
        }
        if (!preview.blockers().isEmpty()) throw ApiException.conflict("La reprise contient des éléments à corriger avant application.");
        UUID schoolId = TenantContext.get();
        jdbc.query("SELECT id FROM academic_session WHERE id=? AND school_id=? FOR UPDATE", rs -> null, target.id(), schoolId);
        Map<String, UUID> termIds = targetTermIds(target.id());
        int created = 0, updated = 0, skipped = 0;
        for (ConfigurationCopyRow row : preview.terms()) {
            UUID id = termIds.get(row.code());
            if (id == null) { id = insertTerm(target, row.proposed()); termIds.put(row.code(), id); created++; }
            else if (shouldUpdate(row, request.mergeMode(), request.selectedKeys())) { updateTerm(id, target, row.proposed()); updated++; }
            else skipped++;
        }
        Map<String, UUID> periodIds = targetPeriodIds(target.id());
        for (ConfigurationCopyRow row : preview.reportingPeriods()) {
            UUID id = periodIds.get(row.code());
            if (id == null) { id = insertPeriod(target, row.proposed(), termIds); periodIds.put(row.code(), id); created++; }
            else if (shouldUpdate(row, request.mergeMode(), request.selectedKeys())) { updatePeriod(id, target, row.proposed(), termIds); updated++; }
            else skipped++;
        }
        for (ConfigurationCopyRow row : preview.dependencies()) {
            if (!shouldApply(row, request.mergeMode(), request.selectedKeys())) { skipped++; continue; }
            if (applyDependency(target.id(), row.proposed(), periodIds, request.mergeMode())) {
                if (row.existing() == null) created++; else updated++;
            } else skipped++;
        }
        for (ConfigurationCopyRow row : preview.workflowWindows()) {
            if ("SKIP".equals(row.status())) { skipped++; continue; }
            if (!shouldApply(row, request.mergeMode(), request.selectedKeys())) { skipped++; continue; }
            if (applyWindow(target.id(), row.proposed(), termIds, periodIds, request.mergeMode())) {
                if (row.existing() == null) created++; else updated++;
            } else skipped++;
        }
        jdbc.update("""
                INSERT INTO academic_copy_run(school_id,copy_type,source_session_id,target_session_id,
                    scope_key,preview_fingerprint,status,created_count,updated_count,skipped_count,result_summary,actor_user_id,applied_at)
                VALUES (?,?,?,?,?,?,'APPLIED',?,?,?,?::jsonb,?,now())
                """, schoolId, "SESSION_CONFIGURATION", preview.sourceSessionId(), target.id(),
                "all", preview.fingerprint(), created, updated, skipped,
                json(Map.of("create", created, "update", updated, "skip", skipped)), currentUserId());
        audit.record("ACADEMIC_CONFIGURATION_COPIED", "AcademicSession", target.id().toString(), null,
                Map.of("sourceSessionId", preview.sourceSessionId(), "fingerprint", preview.fingerprint(),
                        "created", created, "updated", updated, "skipped", skipped), request.reason());
        return preview;
    }

    private List<ConfigurationCopyRow> termRows(SessionData source, SessionData target, long shift,
                                                String mergeMode, List<ConfigurationCopyEdit> edits, List<String> selectedKeys) {
        return jdbc.query("""
                SELECT id,code,label,sequence_no,start_date,end_date,grade_entry_opens_at,grade_entry_closes_at,
                       bulletin_publish_opens_at,bulletin_publish_closes_at,teacher_submission_opens_at,
                       teacher_submission_closes_at,timezone
                  FROM academic_term WHERE school_id=? AND academic_session_id=? ORDER BY sequence_no,code
                """, (rs, n) -> {
            String code = rs.getString("code").toUpperCase(Locale.ROOT);
            Map<String, Object> src = map("code", code, "label", rs.getString("label"), "sequenceNo", rs.getInt("sequence_no"),
                    "startDate", date(rs.getObject("start_date")), "endDate", date(rs.getObject("end_date")),
                    "gradeEntryOpensAt", instant(rs.getTimestamp("grade_entry_opens_at")), "gradeEntryClosesAt", instant(rs.getTimestamp("grade_entry_closes_at")),
                    "bulletinPublishOpensAt", instant(rs.getTimestamp("bulletin_publish_opens_at")), "bulletinPublishClosesAt", instant(rs.getTimestamp("bulletin_publish_closes_at")),
                    "teacherSubmissionOpensAt", instant(rs.getTimestamp("teacher_submission_opens_at")), "teacherSubmissionClosesAt", instant(rs.getTimestamp("teacher_submission_closes_at")),
                    "timezone", rs.getString("timezone"));
            Map<String, Object> proposed = shiftDates(src, shift, Objects.toString(src.get("timezone"), source.timezone()), target.timezone());
            applyEdits("TERM:" + code, proposed, edits);
            Map<String, Object> existing = findByCode("academic_term", target.id(), code);
            return row("TERM:" + code, "TERM", code, String.valueOf(src.get("label")), existing, src, proposed, mergeMode, selectedKeys);
        }, TenantContext.get(), source.id());
    }

    private List<ConfigurationCopyRow> periodRows(SessionData source, SessionData target, long shift,
                                                  String mergeMode, List<ConfigurationCopyEdit> edits, List<String> selectedKeys,
                                                  Map<String, LocalDate[]> termBounds) {
        return jdbc.query("""
                SELECT p.id,p.code,p.label,p.period_type,p.display_order,p.start_date,p.end_date,t.code AS term_code,
                       p.grade_entry_opens_at,p.grade_entry_closes_at,p.review_opens_at,p.review_closes_at,
                       p.validation_opens_at,p.validation_closes_at,p.bulletin_publish_opens_at,p.bulletin_publish_closes_at,
                       p.correction_opens_at,p.correction_closes_at,p.teacher_submission_opens_at,
                       p.teacher_submission_closes_at,p.timezone,p.calculation_policy,p.status
                  FROM academic_reporting_period p LEFT JOIN academic_term t ON t.id=p.academic_term_id
                 WHERE p.school_id=? AND p.academic_session_id=? ORDER BY p.display_order,p.code
                """, (rs, n) -> {
            String code = rs.getString("code").toUpperCase(Locale.ROOT);
            Map<String, Object> src = map("code", code, "label", rs.getString("label"), "periodType", rs.getString("period_type"),
                    "displayOrder", rs.getInt("display_order"), "termCode", rs.getString("term_code"),
                    "startDate", date(rs.getObject("start_date")), "endDate", date(rs.getObject("end_date")),
                    "gradeEntryOpensAt", instant(rs.getTimestamp("grade_entry_opens_at")), "gradeEntryClosesAt", instant(rs.getTimestamp("grade_entry_closes_at")),
                    "reviewOpensAt", instant(rs.getTimestamp("review_opens_at")), "reviewClosesAt", instant(rs.getTimestamp("review_closes_at")),
                    "validationOpensAt", instant(rs.getTimestamp("validation_opens_at")), "validationClosesAt", instant(rs.getTimestamp("validation_closes_at")),
                    "bulletinPublishOpensAt", instant(rs.getTimestamp("bulletin_publish_opens_at")), "bulletinPublishClosesAt", instant(rs.getTimestamp("bulletin_publish_closes_at")),
                    "correctionOpensAt", instant(rs.getTimestamp("correction_opens_at")), "correctionClosesAt", instant(rs.getTimestamp("correction_closes_at")),
                    "teacherSubmissionOpensAt", instant(rs.getTimestamp("teacher_submission_opens_at")), "teacherSubmissionClosesAt", instant(rs.getTimestamp("teacher_submission_closes_at")),
                    "timezone", rs.getString("timezone"), "calculationPolicy", rs.getString("calculation_policy"), "status", rs.getString("status"));
            Map<String, Object> proposed = shiftDates(src, shift, Objects.toString(src.get("timezone"), source.timezone()), target.timezone());
            applyEdits("PERIOD:" + code, proposed, edits);
            boolean computed = !"SEQUENCE".equalsIgnoreCase(String.valueOf(proposed.get("periodType")));
            if (computed) {
                for (String field : List.of("gradeEntryOpensAt", "gradeEntryClosesAt", "teacherSubmissionOpensAt", "teacherSubmissionClosesAt")) {
                    proposed.put(field, null);
                }
            }
            Map<String, Object> existing = findByCode("academic_reporting_period", target.id(), code);
            List<String> blockers = new ArrayList<>();
            if (date(proposed.get("startDate")) == null || date(proposed.get("endDate")) == null
                    || date(proposed.get("startDate")).isAfter(date(proposed.get("endDate")))) blockers.add("PERIOD_DATES_INVALID");
            LocalDate[] bounds = termBounds.get(String.valueOf(proposed.get("termCode")).toUpperCase(Locale.ROOT));
            if (bounds != null && bounds[0] != null && bounds[1] != null
                    && date(proposed.get("startDate")) != null && date(proposed.get("endDate")) != null
                    && (date(proposed.get("startDate")).isBefore(bounds[0]) || date(proposed.get("endDate")).isAfter(bounds[1]))) {
                blockers.add("PERIOD_OUTSIDE_TERM");
            }
            List<String> warnings = computed ? List.of("RAW_GRADE_AND_TEACHER_WINDOWS_NOT_APPLICABLE") : List.of();
            String key = "PERIOD:" + code;
            return new ConfigurationCopyRow(key, "REPORTING_PERIOD", code, String.valueOf(src.get("label")),
                    existing == null ? "CREATE" : selected(selectedKeys, key, mergeMode) ? "UPDATE" : "KEEP",
                    src, proposed, existing, warnings, blockers);
        }, TenantContext.get(), source.id());
    }

    private Map<String, LocalDate[]> termBounds(UUID targetSessionId, List<ConfigurationCopyRow> proposedTerms) {
        Map<String, LocalDate[]> result = new HashMap<>();
        if (proposedTerms != null && !proposedTerms.isEmpty()) {
            for (ConfigurationCopyRow row : proposedTerms) {
                result.put(row.code().toUpperCase(Locale.ROOT), new LocalDate[]{date(row.proposed().get("startDate")), date(row.proposed().get("endDate"))});
            }
            return result;
        }
        jdbc.query("SELECT code,start_date,end_date FROM academic_term WHERE school_id=? AND academic_session_id=?",
                rs -> { while (rs.next()) result.put(rs.getString("code").toUpperCase(Locale.ROOT),
                        new LocalDate[]{date(rs.getObject("start_date")), date(rs.getObject("end_date"))}); return null; },
                TenantContext.get(), targetSessionId);
        return result;
    }

    private List<ConfigurationCopyRow> dependencyRows(SessionData source, SessionData target,
                                                      String mergeMode, List<ConfigurationCopyEdit> edits, List<String> selectedKeys) {
        return jdbc.query("""
                SELECT p.code AS parent_code,c.code AS child_code,d.weight,d.optional,d.display_order
                  FROM academic_reporting_period_dependency d
                  JOIN academic_reporting_period p ON p.id=d.parent_period_id
                  JOIN academic_reporting_period c ON c.id=d.child_period_id
                 WHERE d.school_id=? AND d.academic_session_id=? ORDER BY p.display_order,d.display_order,c.code
                """, (rs, n) -> {
            String key = "DEPENDENCY:" + rs.getString("parent_code") + ">" + rs.getString("child_code");
            Map<String, Object> sourceMap = map("parentCode", rs.getString("parent_code"), "childCode", rs.getString("child_code"),
                    "weight", rs.getBigDecimal("weight"), "optional", rs.getBoolean("optional"), "displayOrder", rs.getInt("display_order"));
            Map<String, Object> proposed = new LinkedHashMap<>(sourceMap); applyEdits(key, proposed, edits);
            boolean exists = dependencyExists(target.id(), String.valueOf(proposed.get("parentCode")), String.valueOf(proposed.get("childCode")));
            return row(key, "DEPENDENCY", String.valueOf(proposed.get("parentCode")), String.valueOf(proposed.get("childCode")),
                    exists ? sourceMap : null, sourceMap, proposed, mergeMode, selectedKeys);
        }, TenantContext.get(), source.id());
    }

    private List<ConfigurationCopyRow> windowRows(SessionData source, SessionData target, long shift,
                                                  String mergeMode, List<ConfigurationCopyEdit> edits, List<String> selectedKeys) {
        return jdbc.query("""
                SELECT w.scope_type,w.academic_term_id,t.code AS term_code,w.reporting_period_id,p.code AS period_code,p.period_type,
                       w.action,w.mode,w.opens_at,w.closes_at,w.timezone
                  FROM academic_workflow_window_rule w
                  LEFT JOIN academic_term t ON t.id=w.academic_term_id
                  LEFT JOIN academic_reporting_period p ON p.id=w.reporting_period_id
                 WHERE w.school_id=? AND w.academic_session_id=?
                 ORDER BY w.scope_type,w.action,t.code,p.code
                """, (rs, n) -> {
            String key = "WINDOW:" + rs.getString("scope_type") + ":"
                    + Objects.toString(rs.getString("term_code"), Objects.toString(rs.getString("period_code"), "SESSION"))
                    + ":" + rs.getString("action");
            Map<String, Object> src = map("scopeType", rs.getString("scope_type"), "termCode", rs.getString("term_code"),
                    "periodCode", rs.getString("period_code"), "periodType", rs.getString("period_type"), "action", rs.getString("action"), "mode", rs.getString("mode"),
                    "opensAt", instant(rs.getTimestamp("opens_at")), "closesAt", instant(rs.getTimestamp("closes_at")), "timezone", rs.getString("timezone"));
            Map<String, Object> proposed = shiftDates(src, shift, Objects.toString(src.get("timezone"), source.timezone()), target.timezone()); applyEdits(key, proposed, edits);
            Map<String, Object> existing = windowExisting(target.id(), proposed);
            List<String> blockers = new ArrayList<>();
            boolean notApplicable = "PERIOD".equals(proposed.get("scopeType"))
                    && !"SEQUENCE".equalsIgnoreCase(String.valueOf(proposed.get("periodType")))
                    && Set.of("GRADE_ENTRY", "TEACHER_SUBMISSION").contains(String.valueOf(proposed.get("action")));
            if ("SESSION".equals(proposed.get("scopeType")) && "INHERIT".equals(proposed.get("mode"))) blockers.add("SESSION_WINDOW_CANNOT_INHERIT");
            if ("LIMITED".equals(proposed.get("mode")) && proposed.get("opensAt") == null && proposed.get("closesAt") == null) blockers.add("WINDOW_ENDPOINT_REQUIRED");
            if (proposed.get("opensAt") instanceof Instant open && proposed.get("closesAt") instanceof Instant close && !close.isAfter(open)) blockers.add("WINDOW_INVALID");
            if (notApplicable) blockers.clear();
            String status = notApplicable ? "SKIP" : existing == null ? "CREATE" : selected(selectedKeys, key, mergeMode) ? "UPDATE" : "KEEP";
            return new ConfigurationCopyRow(key, "WORKFLOW_WINDOW", String.valueOf(proposed.get("action")),
                    String.valueOf(proposed.get("scopeType")), status, src, proposed, existing,
                    notApplicable ? List.of("NOT_APPLICABLE_FOR_COMPUTED_PERIOD") : List.of(), blockers);
        }, TenantContext.get(), source.id());
    }

    private UUID insertTerm(SessionData target, Map<String, Object> p) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,
                    grade_entry_opens_at,grade_entry_closes_at,bulletin_publish_opens_at,bulletin_publish_closes_at,
                    teacher_submission_opens_at,teacher_submission_closes_at,timezone)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, TenantContext.get(), target.id(), p.get("code"), p.get("label"), p.get("sequenceNo"),
                date(p.get("startDate")), date(p.get("endDate")), ts(p.get("gradeEntryOpensAt")), ts(p.get("gradeEntryClosesAt")),
                ts(p.get("bulletinPublishOpensAt")), ts(p.get("bulletinPublishClosesAt")), ts(p.get("teacherSubmissionOpensAt")),
                ts(p.get("teacherSubmissionClosesAt")), p.get("timezone"));
        return id;
    }

    private void updateTerm(UUID id, SessionData target, Map<String, Object> p) {
        jdbc.update("""
                UPDATE academic_term SET label=?,sequence_no=?,start_date=?,end_date=?,grade_entry_opens_at=?,grade_entry_closes_at=?,
                    bulletin_publish_opens_at=?,bulletin_publish_closes_at=?,teacher_submission_opens_at=?,teacher_submission_closes_at=?,
                    timezone=?,updated_at=now(),version=version+1 WHERE id=? AND school_id=? AND academic_session_id=?
                """, p.get("label"), p.get("sequenceNo"), date(p.get("startDate")), date(p.get("endDate")),
                ts(p.get("gradeEntryOpensAt")), ts(p.get("gradeEntryClosesAt")), ts(p.get("bulletinPublishOpensAt")), ts(p.get("bulletinPublishClosesAt")),
                ts(p.get("teacherSubmissionOpensAt")), ts(p.get("teacherSubmissionClosesAt")), p.get("timezone"), id, TenantContext.get(), target.id());
    }

    private UUID insertPeriod(SessionData target, Map<String, Object> p, Map<String, UUID> termIds) {
        UUID id = UUID.randomUUID();
        jdbc.update(periodSql(true), periodArgs(id, target, p, termIds));
        return id;
    }

    private void updatePeriod(UUID id, SessionData target, Map<String, Object> p, Map<String, UUID> termIds) {
        jdbc.update(periodSql(false), periodArgs(id, target, p, termIds));
    }

    private String periodSql(boolean insert) {
        if (insert) return """
                INSERT INTO academic_reporting_period(id,school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,
                    grade_entry_opens_at,grade_entry_closes_at,review_opens_at,review_closes_at,validation_opens_at,validation_closes_at,
                    bulletin_publish_opens_at,bulletin_publish_closes_at,correction_opens_at,correction_closes_at,teacher_submission_opens_at,teacher_submission_closes_at,
                    timezone,calculation_policy,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        return """
                UPDATE academic_reporting_period SET academic_term_id=?,label=?,period_type=?,display_order=?,start_date=?,end_date=?,
                    grade_entry_opens_at=?,grade_entry_closes_at=?,review_opens_at=?,review_closes_at=?,validation_opens_at=?,validation_closes_at=?,
                    bulletin_publish_opens_at=?,bulletin_publish_closes_at=?,correction_opens_at=?,correction_closes_at=?,teacher_submission_opens_at=?,teacher_submission_closes_at=?,
                    timezone=?,calculation_policy=?,status=?,updated_at=now(),version=version+1 WHERE id=? AND school_id=? AND academic_session_id=?
                """;
    }

    private Object[] periodArgs(UUID id, SessionData target, Map<String, Object> p, Map<String, UUID> termIds) {
        Object[] values = new Object[]{id, TenantContext.get(), target.id(), termIds.get(p.get("termCode")), p.get("code"), p.get("label"),
                p.get("periodType"), p.get("displayOrder"), date(p.get("startDate")), date(p.get("endDate")),
                ts(p.get("gradeEntryOpensAt")), ts(p.get("gradeEntryClosesAt")), ts(p.get("reviewOpensAt")), ts(p.get("reviewClosesAt")),
                ts(p.get("validationOpensAt")), ts(p.get("validationClosesAt")), ts(p.get("bulletinPublishOpensAt")), ts(p.get("bulletinPublishClosesAt")),
                ts(p.get("correctionOpensAt")), ts(p.get("correctionClosesAt")), ts(p.get("teacherSubmissionOpensAt")), ts(p.get("teacherSubmissionClosesAt")),
                p.get("timezone"), p.get("calculationPolicy"), p.get("status")};
        if (values.length == 25) return values;
        return new Object[]{values[3], values[5], values[6], values[7], values[8], values[9], values[10], values[11], values[12], values[13],
                values[14], values[15], values[16], values[17], values[18], values[19], values[20], values[21], values[22], values[23], values[24], id, TenantContext.get(), target.id()};
    }

    private boolean applyDependency(UUID targetId, Map<String, Object> p, Map<String, UUID> periodIds, String requestedMode) {
        UUID parent = periodIds.get(String.valueOf(p.get("parentCode")));
        UUID child = periodIds.get(String.valueOf(p.get("childCode")));
        if (parent == null || child == null) return false;
        int changed = jdbc.update("""
                INSERT INTO academic_reporting_period_dependency(school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT(school_id,parent_period_id,child_period_id)
                DO UPDATE SET weight=excluded.weight,optional=excluded.optional,display_order=excluded.display_order
                WHERE ?<>'FILL_MISSING'
                """, TenantContext.get(), targetId, parent, child, p.get("weight"), p.get("optional"), p.get("displayOrder"), mode(requestedMode));
        return changed > 0;
    }

    private boolean applyWindow(UUID targetId, Map<String, Object> p, Map<String, UUID> termIds,
                                Map<String, UUID> periodIds, String requestedMode) {
        UUID termId = p.get("termCode") == null ? null : termIds.get(String.valueOf(p.get("termCode")));
        UUID periodId = p.get("periodCode") == null ? null : periodIds.get(String.valueOf(p.get("periodCode")));
        String scope = String.valueOf(p.get("scopeType"));
        Map<String, Object> existing = windowExisting(targetId, p);
        if (existing != null && "FILL_MISSING".equals(mode(requestedMode))) return false;
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO academic_workflow_window_rule(school_id,academic_session_id,scope_type,academic_term_id,reporting_period_id,action,mode,opens_at,closes_at,timezone)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, TenantContext.get(), targetId, scope, termId, periodId, p.get("action"), p.get("mode"),
                    ts(p.get("opensAt")), ts(p.get("closesAt")), p.get("timezone"));
        } else {
            jdbc.update("UPDATE academic_workflow_window_rule SET mode=?,opens_at=?,closes_at=?,timezone=?,version=version+1,updated_at=now() WHERE id=? AND school_id=?",
                    p.get("mode"), ts(p.get("opensAt")), ts(p.get("closesAt")), p.get("timezone"), existing.get("id"), TenantContext.get());
        }
        return true;
    }

    private Map<String, UUID> targetTermIds(UUID sessionId) {
        Map<String, UUID> result = new HashMap<>();
        jdbc.query("SELECT id,code FROM academic_term WHERE school_id=? AND academic_session_id=?",
                rs -> { while (rs.next()) result.put(rs.getString(2).toUpperCase(Locale.ROOT), rs.getObject(1, UUID.class)); return null; }, TenantContext.get(), sessionId);
        return result;
    }

    private Map<String, UUID> targetPeriodIds(UUID sessionId) {
        Map<String, UUID> result = new HashMap<>();
        jdbc.query("SELECT id,code FROM academic_reporting_period WHERE school_id=? AND academic_session_id=?",
                rs -> { while (rs.next()) result.put(rs.getString(2).toUpperCase(Locale.ROOT), rs.getObject(1, UUID.class)); return null; }, TenantContext.get(), sessionId);
        return result;
    }

    private boolean shouldUpdate(ConfigurationCopyRow row, String requestedMode, List<String> selectedKeys) {
        return row.existing() != null && selected(selectedKeys, row.key(), requestedMode);
    }

    private boolean shouldApply(ConfigurationCopyRow row, String requestedMode, List<String> selectedKeys) {
        return row.existing() == null || shouldUpdate(row, requestedMode, selectedKeys);
    }

    private ConfigurationCopyRow row(String key, String kind, String code, String label,
                                     Map<String, Object> existing, Map<String, Object> source,
                                     Map<String, Object> proposed, String mergeMode, List<String> selectedKeys) {
        return new ConfigurationCopyRow(key, kind, code, label, existing == null ? "CREATE" : selected(selectedKeys, key, mergeMode) ? "UPDATE" : "KEEP",
                source, proposed, existing, List.of(), List.of());
    }

    private boolean selected(List<String> selectedKeys, String key, String requestedMode) {
        String merge = mode(requestedMode);
        return "UPDATE_ALL".equals(merge) || ("UPDATE_SELECTED".equals(merge)
                && selectedKeys != null && selectedKeys.contains(key));
    }

    private Map<String, Object> findByCode(String table, UUID sessionId, String code) {
        String sql = "academic_term".equals(table)
                ? "SELECT id,code,label,start_date,end_date FROM academic_term WHERE school_id=? AND academic_session_id=? AND upper(code)=upper(?)"
                : "SELECT id,code,label,start_date,end_date FROM academic_reporting_period WHERE school_id=? AND academic_session_id=? AND upper(code)=upper(?)";
        return jdbc.query(sql, rs -> {
            if (!rs.next()) return null;
            return map("id", rs.getObject(1, UUID.class), "code", rs.getString(2), "label", rs.getString(3),
                    "startDate", date(rs.getObject(4)), "endDate", date(rs.getObject(5)));
        }, TenantContext.get(), sessionId, code);
    }

    private boolean dependencyExists(UUID sessionId, String parentCode, String childCode) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM academic_reporting_period_dependency d
                  JOIN academic_reporting_period p ON p.id=d.parent_period_id
                  JOIN academic_reporting_period c ON c.id=d.child_period_id
                 WHERE d.school_id=? AND d.academic_session_id=? AND upper(p.code)=upper(?) AND upper(c.code)=upper(?)
                """, Integer.class, TenantContext.get(), sessionId, parentCode, childCode);
        return count != null && count > 0;
    }

    private Map<String, Object> windowExisting(UUID sessionId, Map<String, Object> p) {
        String scope = String.valueOf(p.get("scopeType"));
        String sql = "SESSION".equals(scope)
                ? "SELECT id,version FROM academic_workflow_window_rule WHERE school_id=? AND academic_session_id=? AND scope_type='SESSION' AND action=?"
                : "TERM".equals(scope)
                ? "SELECT w.id,w.version FROM academic_workflow_window_rule w JOIN academic_term t ON t.id=w.academic_term_id WHERE w.school_id=? AND w.academic_session_id=? AND w.scope_type='TERM' AND upper(t.code)=upper(?) AND w.action=?"
                : "SELECT w.id,w.version FROM academic_workflow_window_rule w JOIN academic_reporting_period p2 ON p2.id=w.reporting_period_id WHERE w.school_id=? AND w.academic_session_id=? AND w.scope_type='PERIOD' AND upper(p2.code)=upper(?) AND w.action=?";
        Object[] args = "SESSION".equals(scope)
                ? new Object[]{TenantContext.get(), sessionId, p.get("action")}
                : new Object[]{TenantContext.get(), sessionId, "TERM".equals(scope) ? p.get("termCode") : p.get("periodCode"), p.get("action")};
        return jdbc.query(sql, rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "version", rs.getLong(2)) : null, args);
    }

    private SessionData session(UUID id) {
        Map<String, Object> row = jdbc.query("SELECT id,code,label,start_date,end_date,status,timezone FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "code", rs.getString(2), "label", rs.getString(3),
                        "start", date(rs.getObject(4)), "end", date(rs.getObject(5)), "status", rs.getString(6), "timezone", rs.getString(7)) : null,
                id, TenantContext.get());
        if (row == null) throw ApiException.notFound("Academic session");
        return new SessionData((UUID) row.get("id"), String.valueOf(row.get("code")), String.valueOf(row.get("label")),
                date(row.get("start")), date(row.get("end")), String.valueOf(row.get("status")),
                Objects.toString(row.get("timezone"), "Africa/Douala"));
    }

    private void assertMutable(SessionData session) {
        if (!Set.of("DRAFT", "OPEN").contains(session.status())) throw ApiException.conflict("La session cible doit être en brouillon ou ouverte.");
    }

    private static String mode(String raw) {
        String value = raw == null || raw.isBlank() ? "FILL_MISSING" : raw.trim().toUpperCase(Locale.ROOT);
        return Set.of("FILL_MISSING", "UPDATE_SELECTED", "UPDATE_ALL").contains(value) ? value : "FILL_MISSING";
    }
    private static String dateStrategy(String raw) {
        String value = raw == null || raw.isBlank() ? "SHIFT_FROM_SESSION_START" : raw.trim().toUpperCase(Locale.ROOT);
        return "SHIFT_FROM_SESSION_START".equals(value) ? value : "SHIFT_FROM_SESSION_START";
    }

    private static Map<String, Object> shiftDates(Map<String, Object> source, long days, String sourceTimezone, String targetTimezone) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String key : List.of("startDate", "endDate")) if (result.get(key) instanceof LocalDate d) result.put(key, d.plusDays(days));
        ZoneId sourceZone = ZoneId.of(Objects.toString(sourceTimezone, "Africa/Douala"));
        ZoneId targetZone = ZoneId.of(Objects.toString(targetTimezone, "Africa/Douala"));
        for (String key : List.of("gradeEntryOpensAt", "gradeEntryClosesAt", "bulletinPublishOpensAt", "bulletinPublishClosesAt",
                "teacherSubmissionOpensAt", "teacherSubmissionClosesAt", "reviewOpensAt", "reviewClosesAt",
                "validationOpensAt", "validationClosesAt", "correctionOpensAt", "correctionClosesAt", "opensAt", "closesAt")) {
            if (result.get(key) instanceof Instant i) {
                ZonedDateTime localShift = i.atZone(sourceZone).plusDays(days);
                result.put(key, localShift.withZoneSameInstant(targetZone).toInstant());
            }
        }
        return result;
    }

    private static void applyEdits(String key, Map<String, Object> proposed, List<ConfigurationCopyEdit> edits) {
        if (edits == null) return;
        for (ConfigurationCopyEdit edit : edits) if (key.equals(edit.key())) {
            String field = edit.field(); String value = edit.value();
            if (Set.of("startDate", "endDate").contains(field)) proposed.put(field, value == null || value.isBlank() ? null : LocalDate.parse(value));
            else if (Set.of("opensAt", "closesAt", "gradeEntryOpensAt", "gradeEntryClosesAt", "teacherSubmissionOpensAt", "teacherSubmissionClosesAt",
                    "bulletinPublishOpensAt", "bulletinPublishClosesAt", "reviewOpensAt", "reviewClosesAt", "validationOpensAt", "validationClosesAt",
                    "correctionOpensAt", "correctionClosesAt").contains(field)) proposed.put(field, value == null || value.isBlank() ? null : Instant.parse(value));
            else if (Set.of("displayOrder", "sequenceNo").contains(field)) proposed.put(field, Integer.valueOf(value));
            else if (Set.of("weight").contains(field)) proposed.put(field, new BigDecimal(value));
            else if (Set.of("optional").contains(field)) proposed.put(field, Boolean.valueOf(value));
            else proposed.put(field, value);
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return out;
    }
    private static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static java.sql.Timestamp ts(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return java.sql.Timestamp.from(i);
        return java.sql.Timestamp.from(Instant.parse(String.valueOf(value)));
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw ApiException.badRequest("La proposition ne peut pas être sérialisée."); }
    }
    private String fingerprint(Object... values) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(json(values).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }
    private record SessionData(UUID id, String code, String label, LocalDate startDate, LocalDate endDate, String status, String timezone) {}
}
