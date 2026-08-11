package com.bbc.sms.setup;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.dto.SetupDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Canonical aggregate boundary for class curricula.  Compatibility setup
 * endpoints can continue to read the old projection, but all mutations here
 * create/edit a DRAFT and publish it once; database triggers provide the final
 * protection against changing published evidence.
 */
@Service
public class CurriculumVersionService {
    private final JdbcTemplate jdbc;

    public CurriculumVersionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CurriculumVersionView current(UUID sessionId, UUID classId) {
        return findCurrent(sessionId, classId).orElseThrow(() -> ApiException.notFound("Curriculum publié"));
    }

    @Transactional(readOnly = true)
    public CurriculumVersionView byId(UUID id) {
        return find(id).orElseThrow(() -> ApiException.notFound("Version de curriculum"));
    }

    @Transactional
    public CurriculumVersionView createRevision(CurriculumDraftRequest request) {
        UUID school = TenantContext.get();
        assertScope(request.academicSessionId(), request.classId());
        CurriculumVersionView existing = findDraft(request.academicSessionId(), request.classId()).orElse(null);
        if (existing != null) return existing;
        CurriculumVersionView source = request.sourceVersionId() == null
                ? findPublished(request.academicSessionId(), request.classId()).orElse(null)
                : find(request.sourceVersionId()).orElseThrow(() -> ApiException.notFound("Version source"));
        if (source == null) throw ApiException.conflict("Aucun curriculum publié à réviser.");
        UUID id = UUID.randomUUID();
        int number = nextVersionNumber(request.academicSessionId(), request.classId());
        LocalDate from = request.effectiveFrom() == null ? source.effectiveFrom() : request.effectiveFrom();
        LocalDate to = request.effectiveTo() == null ? source.effectiveTo() : request.effectiveTo();
        jdbc.update("""
            INSERT INTO academic_curriculum_version
                (id,school_id,academic_session_id,scope_type,class_id,version_number,state,
                 source_version_id,effective_from,effective_to,created_by)
            VALUES (?,?,?,'CLASS',?,?,'DRAFT',?,?,?,?)
            """, id, school, request.academicSessionId(), request.classId(), number,
                source.id(), from, to, actor());
        jdbc.update("""
            INSERT INTO academic_curriculum_subject
                (id,school_id,academic_session_id,class_id,subject_id,curriculum_version_id,
                 group_id,display_order,coefficient,max_score,mandatory,pass_threshold,
                 show_subject_rank,remark_required,active_from,active_to)
            SELECT gen_random_uuid(),school_id,?,?,subject_id,?,
                   group_id,display_order,coefficient,max_score,mandatory,pass_threshold,
                   show_subject_rank,remark_required,active_from,active_to
              FROM academic_curriculum_subject
             WHERE curriculum_version_id=? AND school_id=?
            """, request.academicSessionId(), request.classId(), id, source.id(), school);
        refreshHash(id);
        return byId(id);
    }

    @Transactional
    public CurriculumVersionView revise(CurriculumRevisionRequest request) {
        CurriculumVersionView source = byId(request.versionId());
        if (request.optimisticVersion() != null && request.optimisticVersion() != source.optimisticVersion())
            throw ApiException.staleVersion("La version du curriculum a changé. Rechargez l'aperçu.",
                    source.optimisticVersion(), request.optimisticVersion());
        return createRevision(new CurriculumDraftRequest(source.academicSessionId(), source.classId(),
                source.id(), source.effectiveFrom(), source.effectiveTo()));
    }

    @Transactional
    public CurriculumVersionView upsertSubject(CurriculumSubjectUpsert request) {
        CurriculumVersionView draft = createRevision(new CurriculumDraftRequest(
                request.academicSessionId(), request.classId(), null, request.activeFrom(), request.activeTo()));
        UUID school = TenantContext.get();
        jdbc.query("SELECT id FROM subject WHERE id=? AND school_id=?", rs -> {
            if (!rs.next()) throw ApiException.notFound("Matière");
            return null;
        }, request.subjectId(), school);
        int order = request.displayOrder() == null ? nextSubjectOrder(draft.id()) : request.displayOrder();
        int coefficient = request.coefficient() == null ? 1 : request.coefficient();
        BigDecimal max = request.maxScore() == null ? BigDecimal.valueOf(20) : request.maxScore();
        BigDecimal threshold = request.passThreshold() == null ? BigDecimal.TEN : request.passThreshold();
        if (order < 1 || coefficient < 1 || max.signum() <= 0 || threshold.signum() < 0 || threshold.compareTo(max) > 0)
            throw ApiException.badRequest("L'ordre, le coefficient, le barème et le seuil sont invalides.");
        Map<String,Object> current = jdbc.query("""
            SELECT id,version FROM academic_curriculum_subject
             WHERE school_id=? AND curriculum_version_id=? AND subject_id=?
            """, rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "version", rs.getLong(2)) : null,
                school, draft.id(), request.subjectId());
        if (current == null) {
            jdbc.update("""
                INSERT INTO academic_curriculum_subject
                    (school_id,academic_session_id,class_id,subject_id,curriculum_version_id,group_id,
                     display_order,coefficient,max_score,mandatory,pass_threshold,show_subject_rank,
                     remark_required,active_from,active_to)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, school, request.academicSessionId(), request.classId(), request.subjectId(), draft.id(),
                    request.groupId(), order, coefficient, max, request.mandatory() == null || request.mandatory(),
                    threshold, request.showSubjectRank() == null || request.showSubjectRank(),
                    Boolean.TRUE.equals(request.remarkRequired()), request.activeFrom(), request.activeTo());
        } else {
            assertVersion(request.version(), (Long) current.get("version"), "La matière");
            jdbc.update("""
                UPDATE academic_curriculum_subject
                   SET group_id=?,display_order=?,coefficient=?,max_score=?,mandatory=?,pass_threshold=?,
                       show_subject_rank=?,remark_required=?,active_from=?,active_to=?,version=version+1,updated_at=now()
                 WHERE id=? AND school_id=? AND curriculum_version_id=?
                """, request.groupId(), order, coefficient, max,
                    request.mandatory() == null || request.mandatory(), threshold,
                    request.showSubjectRank() == null || request.showSubjectRank(),
                    Boolean.TRUE.equals(request.remarkRequired()), request.activeFrom(), request.activeTo(),
                    current.get("id"), school, draft.id());
        }
        refreshHash(draft.id());
        return byId(draft.id());
    }

    @Transactional
    public CurriculumVersionView deleteSubject(UUID sessionId, UUID classId, UUID subjectId) {
        CurriculumVersionView draft = createRevision(new CurriculumDraftRequest(sessionId, classId, null, null, null));
        int count = jdbc.update("DELETE FROM academic_curriculum_subject WHERE school_id=? AND curriculum_version_id=? AND subject_id=?",
                TenantContext.get(), draft.id(), subjectId);
        if (count != 1) throw ApiException.notFound("Affectation matière-classe");
        refreshHash(draft.id());
        return byId(draft.id());
    }

    @Transactional(readOnly = true)
    public CurriculumPublishImpact publishPreview(UUID id) {
        CurriculumVersionView draft = byId(id);
        if (!"DRAFT".equals(draft.state())) throw ApiException.conflict("Seule une version DRAFT peut être publiée.");
        UUID school = TenantContext.get();
        int missing = count("""
            SELECT count(*) FROM academic_curriculum_subject c JOIN school_class cl ON cl.id=c.class_id
             WHERE c.school_id=? AND c.curriculum_version_id=? AND
               ((lower(cl.level)='secondary' AND NOT EXISTS (SELECT 1 FROM academic_class_subject_teacher t
                    WHERE t.school_id=c.school_id AND t.academic_session_id=c.academic_session_id
                      AND t.class_id=c.class_id AND t.subject_id=c.subject_id AND t.role='RESPONSIBLE' AND t.active=true))
                OR (lower(cl.level)<>'secondary' AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment h
                    WHERE h.school_id=c.school_id AND h.academic_session_id=c.academic_session_id
                      AND h.class_id=c.class_id AND h.role='HOMEROOM' AND h.status='ACTIVE')))
            """, school, id);
        int duplicates = count("SELECT count(*)-count(DISTINCT display_order) FROM academic_curriculum_subject WHERE school_id=? AND curriculum_version_id=?", school, id);
        int invalid = count("SELECT count(*) FROM academic_curriculum_subject WHERE school_id=? AND curriculum_version_id=? AND (coefficient<=0 OR max_score<=0 OR pass_threshold<0 OR pass_threshold>max_score)", school, id);
        int assessments = count("SELECT count(*) FROM academic_assessment WHERE school_id=? AND curriculum_version_id=?", school, id);
        int packets = count("SELECT count(*) FROM academic_grade_packet p JOIN academic_curriculum_subject c ON c.class_id=p.class_id JOIN subject s ON s.id=c.subject_id AND upper(s.code)=upper(p.subject_code) WHERE p.school_id=? AND c.curriculum_version_id=?", school, id);
        int snapshots = count("""
            SELECT count(DISTINCT b.id)
              FROM bulletin_version b
              JOIN academic_reporting_period p ON p.id=b.reporting_period_id
              JOIN student_enrollment e ON e.school_id=b.school_id AND e.student_id=b.student_id
                    AND e.academic_session_id=p.academic_session_id AND e.school_class_id=(
                        SELECT c.class_id FROM academic_curriculum_subject c
                         WHERE c.school_id=? AND c.curriculum_version_id=? LIMIT 1)
             WHERE b.school_id=? AND p.academic_session_id=(
                        SELECT v.academic_session_id FROM academic_curriculum_version v
                         WHERE v.school_id=? AND v.id=? LIMIT 1)
            """, school, id, school, school, id);
        List<String> blockers = new ArrayList<>();
        if (missing > 0) blockers.add("MISSING_RESPONSIBLE_TEACHER");
        if (duplicates > 0) blockers.add("DUPLICATE_DISPLAY_ORDER");
        if (invalid > 0) blockers.add("INVALID_COEFFICIENT_OR_MAX");
        List<String> warnings = new ArrayList<>();
        if (assessments > 0) warnings.add("ASSESSMENT_REFERENCES_EXIST");
        if (packets > 0) warnings.add("EXISTING_PACKETS");
        if (snapshots > 0) warnings.add("EXISTING_SNAPSHOTS");
        return new CurriculumPublishImpact(blockers, warnings, missing, duplicates, invalid,
                assessments, packets, snapshots, changedSubjects(id), changedTeachers(id));
    }

    @Transactional
    public CurriculumVersionView publish(CurriculumPublishRequest request) {
        CurriculumVersionView draft = byId(request.versionId());
        if (request.optimisticVersion() != null && request.optimisticVersion() != draft.optimisticVersion())
            throw ApiException.staleVersion("Le brouillon du curriculum a changé. Rechargez-le.", draft.optimisticVersion(), request.optimisticVersion());
        CurriculumPublishImpact impact = publishPreview(request.versionId());
        if (!impact.blockers().isEmpty()) throw ApiException.blockers("CURRICULUM_PUBLISH_BLOCKED", "Corrigez les éléments bloquants avant publication.", impact.blockers());
        UUID school = TenantContext.get();
        jdbc.update("UPDATE academic_curriculum_version SET state='SUPERSEDED',optimistic_version=optimistic_version+1 WHERE school_id=? AND academic_session_id=? AND scope_type='CLASS' AND class_id=? AND state='PUBLISHED'", school, draft.academicSessionId(), draft.classId());
        jdbc.update("UPDATE academic_curriculum_version SET state='PUBLISHED',published_by=?,published_at=now(),optimistic_version=optimistic_version+1 WHERE id=? AND school_id=? AND state='DRAFT'", actor(), draft.id(), school);
        return byId(draft.id());
    }

    @Transactional(readOnly = true)
    public byte[] exceptionCsv() {
        List<String> lines = new ArrayList<>();
        lines.add("id,source_table,source_id,source_sequence,reason_code,status,created_at");
        jdbc.query("SELECT id,source_table,source_id,source_sequence,reason_code,status,created_at FROM legacy_grade_migration_exception WHERE school_id=? ORDER BY created_at,id",
                rs -> { while (rs.next()) lines.add(String.join(",", csv(rs.getObject(1)),csv(rs.getString(2)),csv(rs.getObject(3)),csv(rs.getObject(4)),csv(rs.getString(5)),csv(rs.getString(6)),csv(rs.getObject(7)))); return null; }, TenantContext.get());
        return String.join("\n", lines).concat("\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Optional<CurriculumVersionView> findCurrent(UUID session, UUID classId) {
        return findByScope("state='DRAFT' OR state='PUBLISHED'", session, classId, true);
    }
    private Optional<CurriculumVersionView> findDraft(UUID session, UUID classId) { return findByScope("state='DRAFT'", session, classId, true); }
    private Optional<CurriculumVersionView> findPublished(UUID session, UUID classId) { return findByScope("state='PUBLISHED'", session, classId, true); }
    private Optional<CurriculumVersionView> find(UUID id) {
        return Optional.ofNullable(jdbc.query("SELECT id,academic_session_id,class_id,version_number,state,scope_type,effective_from,effective_to,source_version_id,source_copy_run_id,canonical_content_hash,optimistic_version,created_at,published_at FROM academic_curriculum_version WHERE id=? AND school_id=?",
                rs -> rs.next() ? mapVersion(rs) : null, id, TenantContext.get())).map(this::withSubjects);
    }
    private Optional<CurriculumVersionView> findByScope(String states, UUID session, UUID classId, boolean includeSubjects) {
        return Optional.ofNullable(jdbc.query("SELECT id,academic_session_id,class_id,version_number,state,scope_type,effective_from,effective_to,source_version_id,source_copy_run_id,canonical_content_hash,optimistic_version,created_at,published_at FROM academic_curriculum_version WHERE school_id=? AND academic_session_id=? AND class_id=? AND ("+states+") ORDER BY CASE state WHEN 'DRAFT' THEN 0 ELSE 1 END,version_number DESC LIMIT 1",
                rs -> rs.next() ? mapVersion(rs) : null, TenantContext.get(), session, classId)).map(this::withSubjects);
    }
    private CurriculumVersionView mapVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id=rs.getObject(1,UUID.class);
        return new CurriculumVersionView(id,rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getObject(7,LocalDate.class),rs.getObject(8,LocalDate.class),rs.getObject(9,UUID.class),rs.getObject(10,UUID.class),rs.getString(11),rs.getLong(12),instant(rs,13),instant(rs,14),List.of(),null);
    }
    private Instant instant(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(index);
        return value == null ? null : value.toInstant();
    }
    private CurriculumVersionView withSubjects(CurriculumVersionView version) {
        return new CurriculumVersionView(version.id(), version.academicSessionId(), version.classId(), version.versionNumber(), version.state(), version.scopeType(), version.effectiveFrom(), version.effectiveTo(), version.sourceVersionId(), version.sourceCopyRunId(), version.canonicalContentHash(), version.optimisticVersion(), version.createdAt(), version.publishedAt(), subjects(version.id()), version.impact());
    }
    private List<CurriculumVersionSubjectView> subjects(UUID version) {
        return jdbc.query("""
            SELECT c.id,c.subject_id,s.code,COALESCE(s.label->>'fr',s.label->>'en',s.code),
                   t.employee_id,t.employee_name,c.display_order,c.coefficient,c.max_score,c.mandatory,
                   c.pass_threshold,c.remark_required,c.version
              FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
              LEFT JOIN LATERAL (SELECT ast.employee_id,e.name AS employee_name FROM academic_class_subject_teacher ast
                   JOIN employee e ON e.id=ast.employee_id WHERE ast.school_id=c.school_id AND ast.academic_session_id=c.academic_session_id
                    AND ast.class_id=c.class_id AND ast.subject_id=c.subject_id AND ast.role='RESPONSIBLE' AND ast.active=true
                   ORDER BY ast.effective_from DESC NULLS LAST,ast.created_at DESC LIMIT 1) t ON true
             WHERE c.school_id=? AND c.curriculum_version_id=? ORDER BY c.display_order,s.code
            """, (rs,n)->new CurriculumVersionSubjectView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getString(6),rs.getInt(7),rs.getInt(8),rs.getBigDecimal(9),rs.getBoolean(10),rs.getBigDecimal(11),rs.getBoolean(12),rs.getLong(13)),TenantContext.get(),version);
    }
    private int nextVersionNumber(UUID session, UUID classId) { return jdbc.queryForObject("SELECT COALESCE(max(version_number),0)+1 FROM academic_curriculum_version WHERE school_id=? AND academic_session_id=? AND class_id=?",Integer.class,TenantContext.get(),session,classId); }
    private int nextSubjectOrder(UUID version) { return jdbc.queryForObject("SELECT COALESCE(max(display_order),0)+1 FROM academic_curriculum_subject WHERE school_id=? AND curriculum_version_id=?",Integer.class,TenantContext.get(),version); }
    private void refreshHash(UUID id) { jdbc.update("UPDATE academic_curriculum_version SET canonical_content_hash=(SELECT md5(COALESCE(string_agg(concat_ws('|',subject_id::text,display_order,coefficient,max_score,mandatory,pass_threshold,show_subject_rank,remark_required,COALESCE(active_from::text,''),COALESCE(active_to::text,'')), E'\\n' ORDER BY display_order,subject_id),'')) FROM academic_curriculum_subject WHERE curriculum_version_id=?),optimistic_version=optimistic_version+1 WHERE id=? AND school_id=?",id,id,TenantContext.get()); }
    private void assertScope(UUID session, UUID classId) { if (jdbc.queryForObject("SELECT count(*) FROM academic_session s JOIN school_class c ON c.school_id=s.school_id WHERE s.id=? AND s.school_id=? AND c.id=?",Integer.class,session,TenantContext.get(),classId)==0) throw ApiException.notFound("Session ou classe"); }
    private void assertVersion(Long supplied, long actual, String what) { if (supplied != null && supplied != actual) throw ApiException.staleVersion(what+" a changé. Rechargez.",actual,supplied); }
    private int count(String sql,Object... args) { Integer n=jdbc.queryForObject(sql,Integer.class,args); return n==null?0:n; }
    private UUID actor() { var a=SecurityContextHolder.getContext().getAuthentication(); return a!=null&&a.getPrincipal() instanceof AppUserPrincipal p?p.userId():null; }
    private List<String> changedSubjects(UUID id) { return jdbc.query("SELECT upper(s.code) FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id WHERE c.school_id=? AND c.curriculum_version_id=? ORDER BY c.display_order",(rs,n)->rs.getString(1),TenantContext.get(),id); }
    private List<String> changedTeachers(UUID id) { return jdbc.query("SELECT DISTINCT e.name FROM academic_curriculum_subject c JOIN academic_class_subject_teacher t ON t.school_id=c.school_id AND t.academic_session_id=c.academic_session_id AND t.class_id=c.class_id AND t.subject_id=c.subject_id JOIN employee e ON e.id=t.employee_id WHERE c.school_id=? AND c.curriculum_version_id=? AND t.active=true ORDER BY e.name",(rs,n)->rs.getString(1),TenantContext.get(),id); }
    private static String csv(Object v) { String s=v==null?"":String.valueOf(v); return "\""+s.replace("\"","\"\"")+"\""; }
}
