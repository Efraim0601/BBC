package com.bbc.sms.journey;

import com.bbc.sms.journey.dto.JourneyDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
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
    private final StudentRepository students;

    public JourneyService(JourneyRepository repo, StudentRepository students) {
        this.repo = repo;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public StudentJourney forStudent(UUID studentId) {
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        List<JourneyView> entries = repo
                .findBySchoolIdAndStudentIdOrderByAcademicYearAsc(schoolId, studentId)
                .stream().map(this::toView).toList();

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
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(in.studentId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        validate(in);

        JourneyEntry e = repo
                .findBySchoolIdAndStudentIdAndAcademicYear(schoolId, in.studentId(), in.academicYear())
                .orElseGet(JourneyEntry::new);
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
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        JourneyEntry e = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Entrée de parcours"));
        repo.delete(e);
    }

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
                e.getRank(), e.getClassSize(), e.getDecision(), e.getNote());
    }
}
