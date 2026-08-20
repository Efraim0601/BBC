package com.bbc.sms.messaging;

import com.bbc.sms.messaging.dto.MessageDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CorrespondenceService {

    private final CorrespondenceRepository repo;
    private final StudentRepository students;
    private final AuthorizationPolicyService policy;

    public CorrespondenceService(CorrespondenceRepository repo, StudentRepository students,
                                 AuthorizationPolicyService policy) {
        this.repo = repo;
        this.students = students;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<NoticeView> list() {
        requireSchool("MESSAGES_VIEW");
        UUID schoolId = TenantContext.get();
        Map<UUID, Student> byId = new HashMap<>();
        students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                .forEach(s -> byId.put(s.getId(), s));
        return repo.findBySchoolIdOrderByCreatedAtDesc(schoolId).stream()
                .map(c -> toView(c, byId.get(c.getStudentId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeView> forStudent(UUID studentId) {
        requireSchool("MESSAGES_VIEW");
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        return repo.findBySchoolIdAndStudentIdOrderByCreatedAtDesc(schoolId, studentId).stream()
                .map(c -> toView(c, student))
                .toList();
    }

    @Transactional
    public NoticeView create(NoticeUpsert in) {
        requireSchool("MESSAGES_MANAGE");
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(in.studentId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        Correspondence c = new Correspondence();
        c.setSchoolId(schoolId);
        c.setStudentId(in.studentId());
        c.setCategory(in.category().trim());
        c.setSubject(in.subject().trim());
        c.setBody(in.body().trim());
        c.setRequiresAck(in.requiresAck());
        c.setSenderName(currentDisplayName());
        c.setCreatedBy(currentUserId());
        return toView(repo.save(c), student);
    }

    @Transactional
    public NoticeView acknowledge(UUID id, AckRequest in) {
        requireSchool("MESSAGES_MANAGE");
        UUID schoolId = TenantContext.get();
        Correspondence c = repo.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Correspondance"));
        c.setAcknowledgedAt(Instant.now());
        c.setAcknowledgedBy(in.signedBy().trim());
        Student student = students.findByIdAndSchoolId(c.getStudentId(), schoolId).orElse(null);
        return toView(repo.save(c), student);
    }

    @Transactional
    public void delete(UUID id) {
        requireSchool("MESSAGES_MANAGE");
        Correspondence c = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Correspondance"));
        repo.delete(c);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private String currentDisplayName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.displayName() : null;
    }

    private NoticeView toView(Correspondence c, Student s) {
        String name = s == null ? "—" : s.getLastName().toUpperCase() + " " + s.getFirstName();
        String className = s == null ? "" : s.getClassName();
        return new NoticeView(c.getId(), c.getStudentId(), name, className,
                c.getCategory(), c.getSubject(), c.getBody(), c.isRequiresAck(),
                c.getAcknowledgedAt() != null, c.getAcknowledgedAt(), c.getAcknowledgedBy(),
                c.getSenderName(), c.getCreatedAt());
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}
