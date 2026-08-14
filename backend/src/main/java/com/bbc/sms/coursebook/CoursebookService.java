package com.bbc.sms.coursebook;

import com.bbc.sms.coursebook.dto.CoursebookDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public List<EntryView> forClass(String className) {
        if (className == null || className.isBlank()) return List.of();
        teacherScope.assertClassName(className.trim());
        requirePolicy("COURSEBOOK_VIEW", classId(className.trim()));
        UUID schoolId = TenantContext.get();
        Map<String, String> labels = subjectLabels(schoolId);
        return repo.findBySchoolIdAndClassNameOrderByEntryDateDesc(schoolId, className.trim())
                .stream().map(e -> toView(e, labels)).toList();
    }

    @Transactional
    public EntryView create(EntryUpsert in) {
        teacherScope.assertClassName(in.className());
        requirePolicy("COURSEBOOK_MANAGE", classId(in.className().trim()));
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
        requirePolicy("COURSEBOOK_MANAGE", classId(e.getClassName()));
        requirePolicy("COURSEBOOK_MANAGE", classId(in.className().trim()));
        apply(e, in, schoolId);
        return toView(repo.save(e), subjectLabels(schoolId));
    }

    @Transactional
    public void delete(UUID id) {
        CoursebookEntry e = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Entrée du cahier de textes"));
        teacherScope.assertClassName(e.getClassName());
        requirePolicy("COURSEBOOK_MANAGE", classId(e.getClassName()));
        repo.delete(e);
    }

    private void apply(CoursebookEntry e, EntryUpsert in, UUID schoolId) {
        e.setSchoolId(schoolId);
        e.setClassName(in.className().trim());
        e.setSubjectCode(in.subjectCode().trim());
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

    private void requirePolicy(String action, UUID classId) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, java.time.LocalDate.now(),
                null, classId, null, null, null, null, null, null, null));
    }
}
