package com.bbc.sms.setup;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.dto.SetupDtos;
import com.bbc.sms.setup.dto.SetupDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Preview-first, non-destructive copy of a session-versioned curriculum. */
@Service
public class CurriculumCopyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final CurriculumVersionService curriculumVersions;

    public CurriculumCopyService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit,
                                 CurriculumVersionService curriculumVersions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.curriculumVersions = curriculumVersions;
    }

    @Transactional(readOnly = true)
    public CurriculumCopyPreview preview(CurriculumCopyPreviewRequest request) {
        UUID targetId = request.targetSessionId();
        if (targetId == null) throw ApiException.badRequest("La session cible est obligatoire.");
        SessionData source = session(request.sourceSessionId());
        SessionData target = session(targetId);
        if (source.id().equals(target.id())) throw ApiException.badRequest("Choisissez une session source différente de la session cible.");
        Set<UUID> classIds = resolveClasses(request, source.id(), target.id());
        long shift = ChronoUnit.DAYS.between(source.startDate(), target.startDate());
        boolean includeGroups = request.includeGroups() == null || request.includeGroups();
        boolean includeTeachers = request.includeTeachers() == null || request.includeTeachers();
        String mergeMode = normalizeMode(request.mergeMode());
        List<SubjectGroupView> groups = includeGroups ? groups(source.id()) : List.of();
        List<CurriculumCopyRow> rows = curriculumRows(source, target, classIds, shift, mergeMode, includeTeachers,
                request.selectedKeys(), request.edits());
        rows = new ArrayList<>(rows);
        rows.addAll(homeroomRows(source, target, classIds, shift, mergeMode, includeTeachers,
                request.selectedKeys(), request.edits()));
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        if (!Set.of("DRAFT", "OPEN").contains(target.status())) blockers.add("TARGET_SESSION_NOT_MUTABLE");
        if (classIds.isEmpty()) blockers.add("NO_MATCHING_CLASSES");
        if (rows.stream().anyMatch(r -> !r.blockers().isEmpty())) blockers.add("CURRICULUM_REPAIR_REQUIRED");
        int create = (int) rows.stream().filter(r -> "CREATE".equals(r.status())).count();
        int update = (int) rows.stream().filter(r -> "UPDATE".equals(r.status())).count();
        int keep = (int) rows.stream().filter(r -> "KEEP".equals(r.status())).count();
        String fp = fingerprint(source.id(), target.id(), groups, rows, request.includeTeachers(), request.mergeMode(),
                request.selectedKeys(), request.edits());
        return new CurriculumCopyPreview(source.id(), target.id(), classIds.size(), groups, rows,
                warnings, blockers, fp, create, update, keep);
    }

    @Transactional
    public CurriculumCopyPreview apply(CurriculumCopyApplyRequest request) {
        return apply(request, null);
    }

    @Transactional
    public CurriculumCopyPreview apply(CurriculumCopyApplyRequest request, String idempotencyKey) {
        UUID targetId = request.targetSessionId();
        UUID schoolId = TenantContext.get();
        lockSessions(request.sourceSessionId(), targetId, schoolId);
        SessionData target = session(targetId);
        if (!Set.of("DRAFT", "OPEN").contains(target.status())) throw ApiException.conflict("La session cible doit être en brouillon ou ouverte.");
        CurriculumCopyPreviewRequest previewRequest = new CurriculumCopyPreviewRequest(
                request.sourceSessionId(), targetId, request.classIds(), request.allMatchingClasses(),
                request.includeGroups(), request.includeTeachers(), request.mergeMode(), request.selectedKeys(), request.edits());
        CurriculumCopyPreview preview = preview(previewRequest);
        if (!Objects.equals(request.previewFingerprint(), preview.fingerprint())) {
            throw ApiException.staleVersion("La proposition de curriculum a changé depuis l'aperçu. Rechargez-la avant de l'appliquer.", 0, 0);
        }
        if (!preview.blockers().isEmpty()) throw ApiException.conflict("La reprise contient des éléments à corriger avant application.");
        Map<String, UUID> groupIds = targetGroupIds(target.id());
        if (request.includeGroups() == null || request.includeGroups()) {
            for (SubjectGroupView group : preview.groups()) groupIds.put(group.code().toUpperCase(Locale.ROOT), upsertGroup(target.id(), group, groupIds));
        }
        int created = 0, updated = 0, skipped = 0;
        UUID copyRunId = UUID.randomUUID();
        Map<UUID, UUID> draftVersions = canonicalDrafts(preview, target.id());
        for (UUID draftVersionId : draftVersions.values()) {
            jdbc.update("UPDATE academic_curriculum_version SET source_copy_run_id=? WHERE id=? AND school_id=?",
                    copyRunId, draftVersionId, schoolId);
        }
        for (CurriculumCopyRow row : preview.rows()) {
            if (row.existing() != null && "UPDATE_SELECTED".equals(normalizeMode(request.mergeMode()))
                    && (request.selectedKeys() == null || !request.selectedKeys().contains(row.key()))) {
                skipped++; continue;
            }
            if (row.subjectId() == null) {
                if (applyHomeroom(target, row, request.includeTeachers() == null || request.includeTeachers(), request.mergeMode())) {
                    if ("CREATE".equals(row.status())) created++; else updated++;
                } else skipped++;
                continue;
            }
            boolean changed = applyCurriculumVersionRow(target, row, groupIds, request.mergeMode(), draftVersions.get(row.classId()));
            if (changed && "CREATE".equals(row.status())) created++; else if (changed) updated++; else skipped++;
            if (request.includeTeachers() == null || request.includeTeachers()) applyResponsibleTeacher(target, row, request.mergeMode());
        }
        jdbc.update("""
                INSERT INTO academic_copy_run(id,school_id,copy_type,source_session_id,target_session_id,scope_key,preview_fingerprint,status,
                    created_count,updated_count,skipped_count,result_summary,idempotency_key,reason,warning_count,actor_user_id,applied_at)
                VALUES (?,?,?,?,?,?,?,'APPLIED',?,?,?,?::jsonb,?,?,?,?,now())
                """, copyRunId, schoolId, "CURRICULUM", preview.sourceSessionId(), target.id(), "selected-classes", preview.fingerprint(),
                created, updated, skipped, json(map("created", created, "updated", updated, "skipped", skipped)), idempotencyKey,
                request.reason(), warningCount(preview), currentUserId());
        audit.record("CURRICULUM_COPIED", "AcademicSession", target.id().toString(), null,
                map("sourceSessionId", preview.sourceSessionId(), "classCount", preview.classCount(),
                        "created", created, "updated", updated, "skipped", skipped), request.reason());
        return preview;
    }

    private Map<UUID, UUID> canonicalDrafts(CurriculumCopyPreview preview, UUID targetSessionId) {
        Map<UUID, UUID> drafts = new HashMap<>();
        for (UUID classId : resolvePreviewClassIds(preview)) {
            CurriculumVersionView source = curriculumVersions.current(preview.sourceSessionId(), classId);
            CurriculumVersionView draft = curriculumVersions.createRevision(new CurriculumDraftRequest(
                    targetSessionId, classId, null, source.effectiveFrom(), source.effectiveTo()));
            drafts.put(classId, draft.id());
        }
        return drafts;
    }

    private Set<UUID> resolvePreviewClassIds(CurriculumCopyPreview preview) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (CurriculumCopyRow row : preview.rows()) ids.add(row.classId());
        return ids;
    }

    private void lockSessions(UUID sourceSessionId, UUID targetSessionId, UUID schoolId) {
        jdbc.query("""
                SELECT id
                  FROM academic_session
                 WHERE school_id=? AND id IN (?,?)
                 ORDER BY id
                 FOR UPDATE
                """, rs -> { while (rs.next()) { /* consume both locked rows */ } return null; },
                schoolId, sourceSessionId, targetSessionId);
    }

    private List<CurriculumCopyRow> curriculumRows(SessionData source, SessionData target, Set<UUID> classIds,
                                                   long shift, String mergeMode, boolean includeTeachers,
                                                   List<String> selectedKeys, List<CurriculumCopyEdit> edits) {
        if (classIds.isEmpty()) return List.of();
        String in = String.join(",", Collections.nCopies(classIds.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(TenantContext.get(), source.id())); args.addAll(classIds);
        String sql = """
                SELECT c.id,c.class_id,cl.name AS class_name,cl.level AS class_level,c.subject_id,s.code AS subject_code,
                       COALESCE(s.label->>'fr',s.label->>'en',s.code) AS subject_label,
                       c.group_id,g.code AS group_code,c.display_order,c.coefficient,c.max_score,c.mandatory,c.pass_threshold,
                       c.show_subject_rank,c.remark_required,c.active_from,c.active_to
                  FROM academic_curriculum_subject c
                  JOIN school_class cl ON cl.id=c.class_id
                  JOIN subject s ON s.id=c.subject_id
                  LEFT JOIN academic_subject_group g ON g.id=c.group_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id IN (%s)
                ORDER BY cl.name,c.display_order,s.code
                """.formatted(in);
        return jdbc.query(sql, (rs, n) -> {
            UUID classId = rs.getObject("class_id", UUID.class);
            UUID subjectId = rs.getObject("subject_id", UUID.class);
            String code = rs.getString("subject_code").toUpperCase(Locale.ROOT);
            String key = "SUBJECT:" + classId + ":" + code;
            Map<String, Object> teacher = sourceResponsible(source.id(), classId, subjectId);
            Map<String, Object> sourceMap = map("classId", classId, "className", rs.getString("class_name"), "subjectId", subjectId,
                    "subjectCode", code, "subjectLabel", rs.getString("subject_label"), "groupCode", rs.getString("group_code"), "displayOrder", rs.getInt("display_order"),
                    "coefficient", rs.getInt("coefficient"), "maxScore", rs.getBigDecimal("max_score"), "mandatory", rs.getBoolean("mandatory"),
                    "passThreshold", rs.getBigDecimal("pass_threshold"), "showSubjectRank", rs.getBoolean("show_subject_rank"),
                    "remarkRequired", rs.getBoolean("remark_required"), "activeFrom", date(rs.getObject("active_from")), "activeTo", date(rs.getObject("active_to")),
                    "teacherId", teacher.get("teacherId"), "employeeId", teacher.get("employeeId"),
                    "teacherName", teacher.get("teacherName"), "teacherCode", teacher.get("teacherCode"), "teacherLevel", teacher.get("teacherLevel"),
                    "teacherRole", teacher.get("teacherRole"), "teacherSource", teacher.get("teacherSource"), "teacherActive", teacher.get("teacherActive"),
                    "teacherVersion", teacher.get("teacherVersion"), "effectiveFrom", teacher.get("effectiveFrom"), "effectiveTo", teacher.get("effectiveTo"));
            Map<String, Object> proposed = rebase(sourceMap, shift);
            applyEdits(key, proposed, edits);
            Map<String, Object> existing = existingCurriculum(target.id(), classId, subjectId);
            List<String> warnings = teacherWarnings(sourceMap, rs.getString("class_level"), includeTeachers);
            return new CurriculumCopyRow(key, classId, rs.getString("class_name"), subjectId, code,
                    rs.getString("subject_label"), existing == null ? "CREATE" : selected(selectedKeys, key, mergeMode) ? "UPDATE" : "KEEP",
                    sourceMap, proposed, existing, teacherStatus(sourceMap, includeTeachers), teacherMessage(sourceMap, includeTeachers), warnings, List.of());
        }, args.toArray());
    }

    private Map<String, Object> sourceResponsible(UUID sessionId, UUID classId, UUID subjectId) {
        return jdbc.query("""
                SELECT ast.id AS teacher_id,ast.employee_id,e.name AS employee_name,e.code AS employee_code,e.level AS teacher_level,
                       ast.role,ast.source,ast.version,ast.effective_from,ast.effective_to,e.active AS employee_active
                  FROM academic_class_subject_teacher ast
                  JOIN employee e ON e.id=ast.employee_id
                 WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=? AND ast.subject_id=?
                   AND ast.role='RESPONSIBLE' AND ast.active=true
                 ORDER BY ast.effective_from DESC NULLS LAST,ast.created_at DESC
                 LIMIT 1
                """, rs -> {
            if (!rs.next()) return map();
            return map("teacherId", rs.getObject("teacher_id", UUID.class), "employeeId", rs.getObject("employee_id", UUID.class),
                    "teacherName", rs.getString("employee_name"), "teacherCode", rs.getString("employee_code"),
                    "teacherLevel", rs.getString("teacher_level"), "teacherRole", rs.getString("role"), "teacherSource", rs.getString("source"),
                    "teacherActive", rs.getBoolean("employee_active"), "teacherVersion", rs.getLong("version"),
                    "effectiveFrom", date(rs.getObject("effective_from")), "effectiveTo", date(rs.getObject("effective_to")));
        }, TenantContext.get(), sessionId, classId, subjectId);
    }

    private List<CurriculumCopyRow> homeroomRows(SessionData source, SessionData target, Set<UUID> classIds,
                                                long shift, String mergeMode, boolean includeTeachers,
                                                List<String> selectedKeys, List<CurriculumCopyEdit> edits) {
        if (classIds.isEmpty()) return List.of();
        String in = String.join(",", Collections.nCopies(classIds.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(TenantContext.get(), source.id())); args.addAll(classIds);
        String sql = """
                SELECT a.class_id,cl.name,cl.level,a.employee_id,e.name,e.code,a.effective_from,a.effective_to,a.version,e.active,a.status
                  FROM class_teacher_assignment a JOIN school_class cl ON cl.id=a.class_id JOIN employee e ON e.id=a.employee_id
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id IN (%s) AND a.role='HOMEROOM' AND a.status='ACTIVE'
                 ORDER BY cl.name,a.effective_from DESC
                """.formatted(in);
        return jdbc.query(sql, (rs, n) -> {
            UUID classId = rs.getObject(1, UUID.class); String key = "HOMEROOM:" + classId;
            Map<String, Object> sourceMap = map("classId", classId, "className", rs.getString(2), "level", rs.getString(3),
                    "teacherId", rs.getObject(4, UUID.class), "employeeId", rs.getObject(4, UUID.class), "teacherName", rs.getString(5),
                    "teacherCode", rs.getString(6), "effectiveFrom", date(rs.getObject(7)), "effectiveTo", date(rs.getObject(8)),
                    "teacherVersion", rs.getLong(9), "teacherActive", rs.getBoolean(10), "status", rs.getString(11));
            Map<String, Object> proposed = rebase(sourceMap, shift);
            applyEdits(key, proposed, edits);
            Map<String, Object> existing = existingHomeroom(target.id(), classId);
            List<String> warnings = teacherWarnings(sourceMap, rs.getString(3), includeTeachers);
            return new CurriculumCopyRow(key, classId, rs.getString(2), null, "__HOMEROOM__", "Homeroom teacher",
                    existing == null ? "CREATE" : selected(selectedKeys, key, mergeMode) ? "UPDATE" : "KEEP",
                    sourceMap, proposed, existing, teacherStatus(sourceMap, includeTeachers), teacherMessage(sourceMap, includeTeachers), warnings, List.of());
        }, args.toArray());
    }

    private Set<UUID> resolveClasses(CurriculumCopyPreviewRequest request, UUID sourceId, UUID targetId) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (request.classIds() != null) ids.addAll(request.classIds());
        if (Boolean.TRUE.equals(request.allMatchingClasses()) || ids.isEmpty()) {
            jdbc.query("""
                    SELECT DISTINCT c.class_id FROM academic_curriculum_subject c
                     JOIN school_class target_class ON target_class.id=c.class_id AND target_class.school_id=?
                     WHERE c.school_id=? AND c.academic_session_id=?
                    """, rs -> { while (rs.next()) ids.add(rs.getObject(1, UUID.class)); return null; },
                    TenantContext.get(), TenantContext.get(), sourceId);
        }
        if (!ids.isEmpty()) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM school_class WHERE school_id=? AND id=ANY(?)",
                    Integer.class, TenantContext.get(), ids.toArray(UUID[]::new));
            if (count == null || count != ids.size()) throw ApiException.badRequest("Une ou plusieurs classes de la session source n'existent plus dans cette école.");
        }
        return ids;
    }

    private List<SubjectGroupView> groups(UUID sessionId) {
        return jdbc.query("SELECT id,code,label->>'fr',label->>'en',display_order,show_subtotal,show_rank,average_policy,version FROM academic_subject_group WHERE school_id=? AND academic_session_id=? ORDER BY display_order,code",
                (rs, n) -> new SubjectGroupView(rs.getObject(1, UUID.class), rs.getString(2), labels(rs.getString(3), rs.getString(4)),
                        rs.getInt(5), rs.getBoolean(6), rs.getBoolean(7), rs.getString(8), rs.getLong(9)), TenantContext.get(), sessionId);
    }

    private UUID upsertGroup(UUID sessionId, SubjectGroupView group, Map<String, UUID> existing) {
        String code = group.code().trim().toUpperCase(Locale.ROOT);
        UUID id = existing.get(code);
        String fr = group.label() == null ? code : Objects.toString(group.label().get("fr"), code);
        String en = group.label() == null ? fr : Objects.toString(group.label().get("en"), fr);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO academic_subject_group(id,school_id,academic_session_id,code,label,display_order,show_subtotal,show_rank,average_policy) VALUES (?,?,?, ?,jsonb_build_object('fr',?,'en',?),?,?,?,?)",
                    id, TenantContext.get(), sessionId, code, fr, en, -1000000, group.showSubtotal(), group.showRank(), group.averagePolicy());
        }
        return id;
    }

    private boolean applyCurriculumRow(SessionData target, CurriculumCopyRow row, Map<String, UUID> groupIds, String rawMode) {
        Map<String, Object> p = row.proposed();
        UUID groupId = p.get("groupCode") == null ? null : groupIds.get(String.valueOf(p.get("groupCode")).toUpperCase(Locale.ROOT));
        Map<String, Object> existing = existingCurriculum(target.id(), row.classId(), row.subjectId());
        String merge = normalizeMode(rawMode);
        if (existing != null && "FILL_MISSING".equals(merge)) return false;
        if (existing != null && groupId == null) groupId = (UUID) existing.get("groupId");
        if (existing == null) {
            jdbc.update("INSERT INTO academic_curriculum_subject(school_id,academic_session_id,class_id,subject_id,group_id,display_order,coefficient,max_score,mandatory,pass_threshold,show_subject_rank,remark_required,active_from,active_to) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    TenantContext.get(), target.id(), row.classId(), row.subjectId(), groupId, p.get("displayOrder"), p.get("coefficient"), p.get("maxScore"), p.get("mandatory"), p.get("passThreshold"), p.get("showSubjectRank"), p.get("remarkRequired"), date(p.get("activeFrom")), date(p.get("activeTo")));
            return true;
        }
        jdbc.update("UPDATE academic_curriculum_subject SET group_id=?,display_order=?,coefficient=?,max_score=?,mandatory=?,pass_threshold=?,show_subject_rank=?,remark_required=?,active_from=?,active_to=?,updated_at=now(),version=version+1 WHERE id=? AND school_id=?",
                groupId, p.get("displayOrder"), p.get("coefficient"), p.get("maxScore"), p.get("mandatory"), p.get("passThreshold"), p.get("showSubjectRank"), p.get("remarkRequired"), date(p.get("activeFrom")), date(p.get("activeTo")), existing.get("id"), TenantContext.get());
        return true;
    }

    private boolean applyCurriculumVersionRow(SessionData target, CurriculumCopyRow row,
                                              Map<String, UUID> groupIds, String rawMode, UUID draftVersionId) {
        if (draftVersionId == null) return applyCurriculumRow(target, row, groupIds, rawMode);
        Map<String, Object> p = row.proposed();
        UUID groupId = p.get("groupCode") == null ? null : groupIds.get(String.valueOf(p.get("groupCode")).toUpperCase(Locale.ROOT));
        Map<String, Object> existing = jdbc.query("""
                SELECT id,group_id,version FROM academic_curriculum_subject
                 WHERE school_id=? AND curriculum_version_id=? AND subject_id=?
                """, rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "groupId", rs.getObject(2, UUID.class), "version", rs.getLong(3)) : null,
                TenantContext.get(), draftVersionId, row.subjectId());
        if (existing != null && "FILL_MISSING".equals(normalizeMode(rawMode))) return false;
        if (existing != null && groupId == null) groupId = (UUID) existing.get("groupId");
        if (existing == null) return false;
        int changed = jdbc.update("""
                UPDATE academic_curriculum_subject
                   SET group_id=?,display_order=?,coefficient=?,max_score=?,mandatory=?,pass_threshold=?,
                       show_subject_rank=?,remark_required=?,active_from=?,active_to=?,updated_at=now(),version=version+1
                 WHERE id=? AND school_id=? AND curriculum_version_id=? AND version=?
                """, groupId, p.get("displayOrder"), p.get("coefficient"), p.get("maxScore"), p.get("mandatory"),
                p.get("passThreshold"), p.get("showSubjectRank"), p.get("remarkRequired"), date(p.get("activeFrom")),
                date(p.get("activeTo")), existing.get("id"), TenantContext.get(), draftVersionId, existing.get("version"));
        if (changed == 1) return true;
        throw ApiException.staleVersion("Le brouillon de curriculum a changé pendant la reprise.",
                ((Number) existing.get("version")).longValue(), ((Number) existing.get("version")).longValue() + 1);
    }

    private void applyResponsibleTeacher(SessionData target, CurriculumCopyRow row, String rawMode) {
        Map<String, Object> p = row.proposed(); UUID employeeId = (UUID) p.get("employeeId");
        if (employeeId == null || !Boolean.TRUE.equals(p.get("teacherActive"))) return;
        Map<String, Object> cls = classData(row.classId());
        if (cls == null || !"secondary".equalsIgnoreCase(String.valueOf(cls.get("level")))) return;
        UUID subjectId = row.subjectId(); if (subjectId == null) return;
        if (existingResponsible(target.id(), row.classId(), subjectId) != null && "FILL_MISSING".equals(normalizeMode(rawMode))) return;
        jdbc.update("UPDATE academic_class_subject_teacher SET active=false,updated_at=now(),version=version+1 WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=? AND role='RESPONSIBLE' AND employee_id<>?",
                TenantContext.get(), target.id(), row.classId(), subjectId, employeeId);
        UUID existing = jdbc.query("SELECT id FROM academic_class_subject_teacher WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=? AND employee_id=? AND role='RESPONSIBLE'",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), target.id(), row.classId(), subjectId, employeeId);
        if (existing == null) jdbc.update("INSERT INTO academic_class_subject_teacher(id,school_id,academic_session_id,class_id,subject_id,employee_id,role,effective_from,effective_to,source,active) VALUES (?,?,?,?,? ,?,'RESPONSIBLE',?,?, 'ACADEMIC_SETUP',true)",
                UUID.randomUUID(), TenantContext.get(), target.id(), row.classId(), subjectId, employeeId, date(p.get("effectiveFrom")), date(p.get("effectiveTo")));
        else jdbc.update("UPDATE academic_class_subject_teacher SET effective_from=?,effective_to=?,source='ACADEMIC_SETUP',active=true,updated_at=now(),version=version+1 WHERE id=? AND school_id=?",
                date(p.get("effectiveFrom")), date(p.get("effectiveTo")), existing, TenantContext.get());
    }

    private boolean applyHomeroom(SessionData target, CurriculumCopyRow row, boolean includeTeachers, String rawMode) {
        if (!includeTeachers || row.proposed().get("employeeId") == null || !Boolean.TRUE.equals(row.proposed().get("teacherActive"))) return false;
        Map<String, Object> p = row.proposed(); UUID employee = (UUID) p.get("employeeId");
        Map<String, Object> existing = existingHomeroom(target.id(), row.classId());
        if (existing != null && "FILL_MISSING".equals(normalizeMode(rawMode))) return false;
        if (existing != null) {
            jdbc.update("UPDATE class_teacher_assignment SET employee_id=?,effective_from=?,effective_to=?,source='CLASS_SUBJECTS',updated_at=now(),version=version+1 WHERE id=? AND school_id=?",
                    employee, date(p.get("effectiveFrom")), date(p.get("effectiveTo")), existing.get("id"), TenantContext.get());
            return true;
        }
        jdbc.update("UPDATE class_teacher_assignment SET status='INACTIVE',updated_at=now(),version=version+1 WHERE school_id=? AND academic_session_id=? AND class_id=? AND role='HOMEROOM' AND status='ACTIVE'",
                TenantContext.get(), target.id(), row.classId());
        jdbc.update("INSERT INTO class_teacher_assignment(id,school_id,academic_session_id,class_id,employee_id,role,effective_from,effective_to,status,source) VALUES (?,?,?,?,?,'HOMEROOM',?,?, 'ACTIVE','CLASS_SUBJECTS')",
                UUID.randomUUID(), TenantContext.get(), target.id(), row.classId(), employee, date(p.get("effectiveFrom")), date(p.get("effectiveTo")));
        return true;
    }

    private UUID existingResponsible(UUID sessionId, UUID classId, UUID subjectId) {
        return jdbc.query("SELECT employee_id FROM academic_class_subject_teacher WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=? AND role='RESPONSIBLE' AND active=true ORDER BY effective_from DESC NULLS LAST LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), sessionId, classId, subjectId);
    }
    private Map<String, Object> existingCurriculum(UUID sessionId, UUID classId, UUID subjectId) {
        return jdbc.query("SELECT id,group_id,version FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?",
                rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "groupId", rs.getObject(2, UUID.class), "version", rs.getLong(3)) : null, TenantContext.get(), sessionId, classId, subjectId);
    }
    private Map<String, Object> existingHomeroom(UUID sessionId, UUID classId) {
        return jdbc.query("SELECT id,employee_id,version FROM class_teacher_assignment WHERE school_id=? AND academic_session_id=? AND class_id=? AND role='HOMEROOM' AND status='ACTIVE' ORDER BY effective_from DESC LIMIT 1",
                rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "employeeId", rs.getObject(2, UUID.class), "version", rs.getLong(3)) : null, TenantContext.get(), sessionId, classId);
    }
    private Map<String, Object> classData(UUID classId) {
        return jdbc.query("SELECT id,level,subsystem FROM school_class WHERE id=? AND school_id=?",
                rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "level", rs.getString(2), "subsystem", rs.getString(3)) : null, classId, TenantContext.get());
    }
    private Map<String, UUID> targetGroupIds(UUID sessionId) {
        Map<String, UUID> result = new HashMap<>();
        jdbc.query("SELECT id,code FROM academic_subject_group WHERE school_id=? AND academic_session_id=?", rs -> { while (rs.next()) result.put(rs.getString(2).toUpperCase(Locale.ROOT), rs.getObject(1, UUID.class)); return null; }, TenantContext.get(), sessionId);
        return result;
    }
    private SessionData session(UUID id) {
        Map<String, Object> r = jdbc.query("SELECT id,code,label,start_date,end_date,status FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? map("id", rs.getObject(1, UUID.class), "code", rs.getString(2), "label", rs.getString(3), "start", rs.getObject(4), "end", rs.getObject(5), "status", rs.getString(6)) : null, id, TenantContext.get());
        if (r == null) throw ApiException.notFound("Academic session");
        return new SessionData((UUID) r.get("id"), String.valueOf(r.get("label")), date(r.get("start")), date(r.get("end")), String.valueOf(r.get("status")));
    }
    private List<String> teacherWarnings(Map<String, Object> p, String classLevel, boolean includeTeachers) {
        if (!includeTeachers || p.get("employeeId") == null) return List.of();
        List<String> out = new ArrayList<>();
        if (!Boolean.TRUE.equals(p.get("teacherActive"))) out.add("TEACHER_INACTIVE");
        String teacherLevel = p.get("teacherLevel") == null ? null : String.valueOf(p.get("teacherLevel"));
        if (teacherLevel != null && classLevel != null && !teacherLevel.equalsIgnoreCase(classLevel)) out.add("TEACHER_LEVEL_MISMATCH");
        return out;
    }
    private String teacherStatus(Map<String, Object> p, boolean include) { return !include || p.get("employeeId") == null ? "NOT_INCLUDED" : Boolean.TRUE.equals(p.get("teacherActive")) ? "READY" : "UNAVAILABLE"; }
    private String teacherMessage(Map<String, Object> p, boolean include) { return !include || p.get("employeeId") == null ? null : Boolean.TRUE.equals(p.get("teacherActive")) ? null : "The previous teacher is inactive and was not assigned."; }
    private int warningCount(CurriculumCopyPreview preview) {
        int count = preview.warnings() == null ? 0 : preview.warnings().size();
        if (preview.rows() != null) for (CurriculumCopyRow row : preview.rows()) count += row.warnings() == null ? 0 : row.warnings().size();
        return count;
    }
    private static boolean selected(List<String> selectedKeys, String key, String requestedMode) {
        String merge = normalizeMode(requestedMode);
        return "UPDATE_ALL".equals(merge) || ("UPDATE_SELECTED".equals(merge)
                && selectedKeys != null && selectedKeys.contains(key));
    }
    private static void applyEdits(String key, Map<String, Object> proposed, List<CurriculumCopyEdit> edits) {
        if (edits == null) return;
        for (CurriculumCopyEdit edit : edits) if (key.equals(edit.key())) {
            String field = edit.field(); String value = edit.value();
            if (Set.of("activeFrom", "activeTo", "effectiveFrom", "effectiveTo").contains(field)) {
                proposed.put(field, value == null || value.isBlank() ? null : LocalDate.parse(value));
            } else if (Set.of("displayOrder").contains(field)) {
                proposed.put(field, Integer.valueOf(value));
            } else if (Set.of("coefficient").contains(field)) {
                proposed.put(field, Integer.valueOf(value));
            } else if (Set.of("maxScore", "passThreshold").contains(field)) {
                proposed.put(field, new BigDecimal(value));
            } else if (Set.of("mandatory", "showSubjectRank", "remarkRequired", "teacherActive").contains(field)) {
                proposed.put(field, Boolean.valueOf(value));
            } else if (Set.of("groupCode", "employeeId").contains(field)) {
                proposed.put(field, "employeeId".equals(field) && value != null && !value.isBlank() ? UUID.fromString(value) : value);
            } else {
                throw ApiException.badRequest("Champ de copie de curriculum non reconnu: " + field);
            }
        }
    }
    private static Map<String, Object> rebase(Map<String, Object> source, long shift) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String k : List.of("activeFrom", "activeTo", "effectiveFrom", "effectiveTo")) if (result.get(k) instanceof LocalDate d) result.put(k, d.plusDays(shift));
        return result;
    }
    private static Map<String, String> labels(String fr, String en) { Map<String, String> m = new LinkedHashMap<>(); m.put("fr", fr); m.put("en", en == null ? fr : en); return m; }
    private static LocalDate date(Object v) { if (v == null) return null; if (v instanceof LocalDate d) return d; if (v instanceof java.sql.Date d) return d.toLocalDate(); return LocalDate.parse(String.valueOf(v)); }
    private static String normalizeMode(String v) { String x = v == null || v.isBlank() ? "FILL_MISSING" : v.trim().toUpperCase(Locale.ROOT); return Set.of("FILL_MISSING", "UPDATE_SELECTED", "UPDATE_ALL").contains(x) ? x : "FILL_MISSING"; }
    private static Map<String, Object> map(Object... pairs) { Map<String, Object> m = new LinkedHashMap<>(); for (int i = 0; i + 1 < pairs.length; i += 2) m.put(String.valueOf(pairs[i]), pairs[i + 1]); return m; }
    private String json(Object v) { try { return mapper.writeValueAsString(v); } catch (JsonProcessingException ex) { throw ApiException.badRequest("La proposition ne peut pas être sérialisée."); } }
    private String fingerprint(Object... v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(v).getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private UUID currentUserId() { var a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return a != null && a.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null; }
    private record SessionData(UUID id, String label, LocalDate startDate, LocalDate endDate, String status) {}
}
