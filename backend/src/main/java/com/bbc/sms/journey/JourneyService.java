package com.bbc.sms.journey;

import com.bbc.sms.journey.dto.JourneyDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class JourneyService {

    private final JourneyRepository repo;
    private final StudentService studentAccess;
    private final JdbcTemplate jdbc;

    public JourneyService(JourneyRepository repo, StudentService studentAccess,
                          JdbcTemplate jdbc) {
        this.repo = repo;
        this.studentAccess = studentAccess;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StudentJourney forStudent(UUID studentId) {
        Student student = studentAccess.requireAction(studentId, "JOURNEY_VIEW");
        UUID schoolId = TenantContext.get();

        List<JourneyView> entries = repo
                .findBySchoolIdAndStudentIdOrderByAcademicYearAsc(schoolId, studentId)
                .stream().filter(e -> e.getVoidedAt() == null).map(this::toView).toList();

        BigDecimal best = entries.stream()
                .map(JourneyView::generalAverage)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String name = student.getLastName().toUpperCase() + " " + student.getFirstName();
        return new StudentJourney(student.getId(), name, student.getMatricule(),
                student.getClassName(), entries.size(), best, entries);
    }

    @Transactional
    public JourneyView upsert(JourneyUpsert in) {
        studentAccess.requireAction(in.studentId(), "JOURNEY_MANAGE");
        UUID schoolId = TenantContext.get();
        validate(in);

        JourneyEntry e = repo
                .findBySchoolIdAndStudentIdAndAcademicYear(schoolId, in.studentId(), in.academicYear())
                .orElseGet(JourneyEntry::new);
        if (e.getPromotionBatchId() != null) {
            throw ApiException.conflict("Cette année provient d’un lot de promotion validé et ne peut pas être modifiée manuellement");
        }
        if (e.getVoidedAt() != null) {
            throw ApiException.conflict("Cette entrée a été annulée. Créez une correction distincte au lieu de la réactiver.");
        }
        if (e.getId() != null) recordRevision(e, "CORRECTED", in.note());
        e.setSchoolId(schoolId);
        e.setStudentId(in.studentId());
        e.setAcademicYear(in.academicYear().trim());
        e.setClassName(in.className().trim());
        e.setLevel(in.level());
        e.setSubsystem(in.subsystem());
        e.setResult(in.result() == null || in.result().isBlank() ? "in_progress" : in.result());
        e.setGeneralAverage(in.generalAverage());
        e.setRank(in.rank());
        e.setClassSize(in.classSize());
        e.setDecision(in.decision());
        e.setNote(in.note());
        e.setRecordedBy(currentUserId());
        JourneyEntry saved = repo.save(e);
        jdbc.update("INSERT INTO journey_event(school_id,student_id,event_type,source_type,source_id,payload,visibility) VALUES (?,?,?,'JOURNEY_ENTRY',?,?::jsonb,'INTERNAL')",
                schoolId, saved.getStudentId(), "JOURNEY_ENTRY_CORRECTED", saved.getId(),
                "{\"academicYear\":\"" + json(saved.getAcademicYear()) + "\"}");
        return toView(saved);
    }

    @Transactional
    public void delete(UUID id) {
        throw ApiException.coded(org.springframework.http.HttpStatus.GONE, "JOURNEY_DELETE_REPLACED",
                "Les entrées de parcours sont append-only. Utilisez la correction/annulation auditée avec un motif.");
    }

    @Transactional
    public JourneyView voidEntry(UUID id, JourneyCorrectionRequest request) {
        UUID schoolId = TenantContext.get();
        JourneyEntry e = repo.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Entrée de parcours"));
        studentAccess.requireAction(e.getStudentId(), "JOURNEY_MANAGE");
        if (e.getPromotionBatchId() != null) {
            throw ApiException.conflict("Une décision de promotion validée est immuable et ne peut pas être annulée manuellement");
        }
        if (e.getVoidedAt() != null) throw ApiException.conflict("Cette entrée est déjà annulée");
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank()) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                "Le motif d'annulation est obligatoire.", "reason", "Provide an audit reason.");
        recordRevision(e, "VOIDED", reason);
        e.setVoidedAt(java.time.Instant.now());
        e.setVoidedBy(currentUserId());
        e.setVoidReason(reason);
        JourneyEntry saved = repo.save(e);
        jdbc.update("INSERT INTO journey_event(school_id,student_id,event_type,source_type,source_id,payload,visibility) VALUES (?,?,?,'JOURNEY_ENTRY',?,?::jsonb,'INTERNAL')",
                schoolId, saved.getStudentId(), "JOURNEY_ENTRY_VOIDED", saved.getId(),
                "{\"reason\":\"" + json(reason) + "\"}");
        return toView(saved);
    }

    private void recordRevision(JourneyEntry e, String action, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "Correction de l'entrée de parcours" : reason.trim();
        jdbc.update("INSERT INTO journey_entry_revision(id,school_id,journey_entry_id,action,academic_year,class_name,level,subsystem,result,general_average,rank,class_size,decision,note,actor_user_id,reason) VALUES (gen_random_uuid(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                e.getSchoolId(), e.getId(), action, e.getAcademicYear(), e.getClassName(), e.getLevel(), e.getSubsystem(),
                e.getResult(), e.getGeneralAverage(), e.getRank(), e.getClassSize(), e.getDecision(), e.getNote(), currentUserId(), safeReason);
    }

    private static String json(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private void validate(JourneyUpsert in) {
        if (in.generalAverage() != null
                && (in.generalAverage().signum() < 0
                    || in.generalAverage().compareTo(BigDecimal.valueOf(20)) > 0)) {
            throw ApiException.badRequest("La moyenne doit être comprise entre 0 et 20");
        }
        if (in.rank() != null && in.rank() < 1) {
            throw ApiException.badRequest("Le rang doit être supérieur à 0");
        }
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private JourneyView toView(JourneyEntry e) {
        return new JourneyView(e.getId(), e.getStudentId(), e.getAcademicYear(), e.getClassName(),
                e.getLevel(), e.getSubsystem(), e.getResult(), e.getGeneralAverage(),
                e.getRank(), e.getClassSize(), e.getDecision(), e.getNote(),
                e.getSourceSessionId(), e.getTargetSessionId(), e.getPromotionBatchId(),
                e.getRecommendation(), e.getFinalDecision(), e.getTargetClassName(),
                e.getOverrideReason(), e.getDecisionBy(), e.getDecisionAt());
    }
}
