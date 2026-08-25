package com.bbc.sms.coursebook;

import com.bbc.sms.coursebook.dto.CoursebookDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.dto.SetupDtos.SubjectView;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CoursebookService {

    private final CoursebookRepository repo;
    private final TeacherScopeService teacherScope;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;

    public CoursebookService(CoursebookRepository repo, TeacherScopeService teacherScope, JdbcTemplate jdbc,
                             AuthorizationPolicyService policy) {
        this.repo = repo;
        this.teacherScope = teacherScope;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    /**
     * Return only class references that this principal may use in Coursebook.
     * Academic setup is intentionally not used here: teachers need a scoped
     * read-only selector even when ACADEMIC_STRUCTURE_VIEW is denied.
     */
    @Transactional(readOnly = true)
    public List<ClassRef> classes() {
        UUID schoolId = TenantContext.get();
        Set<UUID> allowed = teacherScope.allowedClassIds();
        ParcoursContext.Scope scope = ParcoursContext.get();
        List<ClassRef> out = new ArrayList<>();
        jdbc.query("SELECT id,name,section_id,subsystem,level FROM school_class WHERE school_id=? ORDER BY name",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String subsystem = rs.getString("subsystem");
                    String level = rs.getString("level");
                    if ((allowed == null || allowed.contains(id))
                            && inScope(scope, level, subsystem)
                            && policy.decide("COURSEBOOK_VIEW", coursebookContext(id, null)).allowed()) {
                        out.add(new ClassRef(id, rs.getString("name"), rs.getString("section_id"), subsystem, level));
                    }
                }, schoolId);
        return out;
    }

    /**
     * Return only subjects that the current user may manage in the selected class.
     *
     * A secondary subject teacher may view the whole class coursebook, but must not
     * be offered another teacher's subject when creating or editing an entry.
     * Primary/Kindergarten homeroom teachers and school-wide administrators keep
     * all subjects allowed by their contextual COURSEBOOK_MANAGE rule.
     */
    @Transactional(readOnly = true)
    public List<SubjectView> subjects(String className) {
        String name = className == null ? "" : className.trim();
        if (name.isBlank()) return List.of();
        teacherScope.assertClassName(name);
        UUID classId = classId(name);
        requirePolicy("COURSEBOOK_VIEW", classId, null);
        UUID schoolId = TenantContext.get();
        AcademicContext academic = currentAcademicContext();
        if (academic == null) return List.of();
        List<SubjectView> curriculum = jdbc.query("""
                SELECT s.id,s.code,s.subsystem,s.label->>'fr',s.label->>'en',cs.coefficient
                  FROM academic_curriculum_subject cs
                  JOIN subject s ON s.id=cs.subject_id
                 WHERE cs.school_id=? AND cs.class_id=?
                   AND cs.academic_session_id=?
                   AND (cs.active_from IS NULL OR cs.active_from<=?)
                   AND (cs.active_to IS NULL OR cs.active_to>=?)
                 ORDER BY cs.display_order,s.code
                """, (rs, n) -> {
                    Map<String, String> label = new LinkedHashMap<>();
                    if (rs.getString(4) != null) label.put("fr", rs.getString(4));
                    if (rs.getString(5) != null) label.put("en", rs.getString(5));
                    return new SubjectView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            label, rs.getInt(6));
                }, schoolId, classId, academic.sessionId(), academic.effectiveDate(), academic.effectiveDate());
        return curriculum.stream()
                .filter(subject -> policy.decide("COURSEBOOK_MANAGE",
                        coursebookContext(classId, normalizeSubject(subject.code()))).allowed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EntryView> forClass(String className) {
        if (className == null || className.isBlank()) return List.of();
        teacherScope.assertClassName(className.trim());
        requirePolicy("COURSEBOOK_VIEW", classId(className.trim()), null);
        UUID schoolId = TenantContext.get();
        Map<String, String> labels = subjectLabels(schoolId);
        return repo.findBySchoolIdAndClassNameOrderByEntryDateDesc(schoolId, className.trim())
                .stream().map(e -> toView(e, labels)).toList();
    }

    @Transactional
    public EntryView create(EntryUpsert in) {
        teacherScope.assertClassName(in.className());
        requirePolicy("COURSEBOOK_MANAGE", classId(in.className().trim()), normalizeSubject(in.subjectCode()));
        UUID schoolId = TenantContext.get();
        CoursebookEntry e = new CoursebookEntry();
        apply(e, in, schoolId);
        e.setCreatedBy(currentUserId());
        return toView(repo.save(e), subjectLabels(schoolId));
    }

    @Transactional
    public EntryView update(UUID id, EntryUpsert in) {
        UUID schoolId = TenantContext.get();
        CoursebookEntry e = repo.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Entrée du cahier de textes"));
        teacherScope.assertClassName(e.getClassName());
        teacherScope.assertClassName(in.className());
        requirePolicy("COURSEBOOK_MANAGE", classId(e.getClassName()), normalizeSubject(e.getSubjectCode()));
        requirePolicy("COURSEBOOK_MANAGE", classId(in.className().trim()), normalizeSubject(in.subjectCode()));
        apply(e, in, schoolId);
        return toView(repo.save(e), subjectLabels(schoolId));
    }

    @Transactional
    public void delete(UUID id) {
        CoursebookEntry e = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Entrée du cahier de textes"));
        teacherScope.assertClassName(e.getClassName());
        requirePolicy("COURSEBOOK_MANAGE", classId(e.getClassName()), normalizeSubject(e.getSubjectCode()));
        repo.delete(e);
    }

    private void apply(CoursebookEntry e, EntryUpsert in, UUID schoolId) {
        e.setSchoolId(schoolId);
        e.setClassName(in.className().trim());
        e.setSubjectCode(normalizeSubject(in.subjectCode()));
        e.setEntryDate(in.entryDate());
        e.setContent(in.content().trim());
        e.setHomework(in.homework() == null || in.homework().isBlank() ? null : in.homework().trim());
        e.setDueDate(in.dueDate());
    }

    private Map<String, String> subjectLabels(UUID schoolId) {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT code, label->>'fr' AS fr FROM subject WHERE school_id = ?",
                rs -> {
                    String fr = rs.getString("fr");
                    out.put(rs.getString("code"), fr != null ? fr : rs.getString("code"));
                }, schoolId);
        return out;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private EntryView toView(CoursebookEntry e, Map<String, String> labels) {
        String label = labels.getOrDefault(e.getSubjectCode(), e.getSubjectCode());
        return new EntryView(e.getId(), e.getClassName(), e.getSubjectCode(), label,
                e.getEntryDate(), e.getContent(), e.getHomework(), e.getDueDate());
    }

    private UUID classId(String className) {
        UUID id = jdbc.query("SELECT id FROM school_class WHERE school_id=? AND name=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), className);
        if (id == null) throw ApiException.notFound("Classe");
        return id;
    }

    private void requirePolicy(String action, UUID classId, String subjectCode) {
        policy.require(action, coursebookContext(classId, subjectCode));
    }

    private PolicyResourceContext coursebookContext(UUID classId, String subjectCode) {
        AcademicContext academic = currentAcademicContext();
        return new PolicyResourceContext(TenantContext.get(), academic == null ? null : academic.sessionId(),
                academic == null ? LocalDate.now() : academic.effectiveDate(),
                ParcoursContext.get(), classId, subjectCode, null, null, null, null, null, null);
    }

    private AcademicContext currentAcademicContext() {
        return jdbc.query("""
                SELECT id,start_date,end_date FROM academic_session
                 WHERE school_id=? AND is_current=true ORDER BY start_date DESC LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            LocalDate start = rs.getObject("start_date", LocalDate.class);
            LocalDate end = rs.getObject("end_date", LocalDate.class);
            LocalDate now = LocalDate.now();
            LocalDate effective = now.isBefore(start) ? start : now.isAfter(end) ? end : now;
            return new AcademicContext(rs.getObject("id", UUID.class), effective);
        }, TenantContext.get());
    }

    private static String normalizeSubject(String subjectCode) {
        return subjectCode == null ? "" : subjectCode.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean inScope(ParcoursContext.Scope scope, String level, String subsystem) {
        if (scope == null || level == null || level.isBlank() || subsystem == null || subsystem.isBlank()) return true;
        return scope.level().equalsIgnoreCase(level) && scope.subsystem().equalsIgnoreCase(subsystem);
    }

    private record AcademicContext(UUID sessionId, LocalDate effectiveDate) {}
}
