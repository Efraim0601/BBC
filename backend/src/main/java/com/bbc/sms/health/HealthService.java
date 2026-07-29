package com.bbc.sms.health;

import com.bbc.sms.health.dto.HealthDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HealthService {

    private static final Set<String> CATEGORIES = Set.of("club", "sport", "art", "other");

    private final HealthRecordRepository records;
    private final InfirmaryVisitRepository visits;
    private final StudentActivityRepository activities;
    private final StudentRepository students;
    private final TeacherScopeService teacherScope;

    public HealthService(HealthRecordRepository records,
                         InfirmaryVisitRepository visits,
                         StudentActivityRepository activities,
                         StudentRepository students,
                         TeacherScopeService teacherScope) {
        this.records = records;
        this.visits = visits;
        this.activities = activities;
        this.students = students;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public StudentHealth forStudent(UUID studentId) {
        teacherScope.assertStudent(studentId);
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        HealthRecordView record = records.findBySchoolIdAndStudentId(schoolId, studentId)
                .map(this::toView).orElse(null);

        List<VisitView> visitViews = visits
                .findBySchoolIdAndStudentIdOrderByVisitDateDesc(schoolId, studentId)
                .stream().map(this::toView).toList();

        List<ActivityView> activityViews = activities
                .findBySchoolIdAndStudentIdOrderByNameAsc(schoolId, studentId)
                .stream().map(this::toView).toList();

        String name = student.getLastName().toUpperCase() + " " + student.getFirstName();
        return new StudentHealth(student.getId(), name, student.getMatricule(),
                student.getClassName(), record, visitViews, activityViews);
    }

    @Transactional
    public HealthRecordView upsertRecord(UUID studentId, HealthRecordUpsert in) {
        teacherScope.assertStudent(studentId);
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        HealthRecord r = records.findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(HealthRecord::new);
        r.setSchoolId(schoolId);
        r.setStudentId(studentId);
        r.setBloodGroup(trimToNull(in.bloodGroup()));
        r.setAllergies(trimToNull(in.allergies()));
        r.setConditions(trimToNull(in.conditions()));
        r.setVaccinations(trimToNull(in.vaccinations()));
        r.setDoctorName(trimToNull(in.doctorName()));
        r.setDoctorPhone(trimToNull(in.doctorPhone()));
        r.setHeightCm(in.heightCm());
        r.setWeightKg(in.weightKg());
        r.setUpdatedAt(Instant.now());
        return toView(records.save(r));
    }

    @Transactional
    public VisitView addVisit(UUID studentId, VisitUpsert in) {
        teacherScope.assertStudent(studentId);
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        InfirmaryVisit v = new InfirmaryVisit();
        v.setSchoolId(schoolId);
        v.setStudentId(studentId);
        v.setVisitDate(in.visitDate());
        v.setReason(in.reason().trim());
        v.setTreatment(trimToNull(in.treatment()));
        v.setCreatedBy(currentUserId());
        return toView(visits.save(v));
    }

    @Transactional
    public void deleteVisit(UUID id) {
        InfirmaryVisit v = visits.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Passage à l'infirmerie"));
        visits.delete(v);
    }

    @Transactional
    public ActivityView addActivity(UUID studentId, ActivityUpsert in) {
        teacherScope.assertStudent(studentId);
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        String category = in.category() == null ? "" : in.category().trim().toLowerCase();
        if (!CATEGORIES.contains(category)) {
            throw ApiException.badRequest("Catégorie invalide");
        }

        StudentActivity a = new StudentActivity();
        a.setSchoolId(schoolId);
        a.setStudentId(studentId);
        a.setName(in.name().trim());
        a.setCategory(category);
        a.setRole(trimToNull(in.role()));
        a.setSeason(trimToNull(in.season()));
        return toView(activities.save(a));
    }

    @Transactional
    public void deleteActivity(UUID id) {
        StudentActivity a = activities.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Activité"));
        activities.delete(a);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private HealthRecordView toView(HealthRecord r) {
        return new HealthRecordView(r.getId(), r.getStudentId(), r.getBloodGroup(),
                r.getAllergies(), r.getConditions(), r.getVaccinations(),
                r.getDoctorName(), r.getDoctorPhone(), r.getHeightCm(), r.getWeightKg());
    }

    private VisitView toView(InfirmaryVisit v) {
        return new VisitView(v.getId(), v.getStudentId(), v.getVisitDate(),
                v.getReason(), v.getTreatment());
    }

    private ActivityView toView(StudentActivity a) {
        return new ActivityView(a.getId(), a.getStudentId(), a.getName(),
                a.getCategory(), a.getRole(), a.getSeason());
    }
}
