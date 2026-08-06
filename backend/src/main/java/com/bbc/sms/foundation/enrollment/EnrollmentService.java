package com.bbc.sms.foundation.enrollment;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.enrollment.EnrollmentDtos.*;

@Service
public class EnrollmentService {
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final AcademicSessionRepository sessions;
    private final AcademicSessionService sessionService;
    private final TeacherScopeService teacherScope;
    private final AuditService audit;

    public EnrollmentService(StudentEnrollmentRepository enrollments, StudentRepository students,
                             SchoolClassRepository classes, AcademicSessionRepository sessions,
                             AcademicSessionService sessionService, TeacherScopeService teacherScope,
                             AuditService audit) {
        this.enrollments = enrollments;
        this.students = students;
        this.classes = classes;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.teacherScope = teacherScope;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentView> history(UUID studentId) {
        teacherScope.assertStudent(studentId);
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId).orElseThrow(() -> ApiException.notFound("Élève"));
        return enrollments.findBySchoolIdAndStudentIdOrderByEnrolledOnDescCreatedAtDesc(schoolId, studentId)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentView> roster(UUID sessionId, UUID classId) {
        teacherScope.assertClass(classId);
        AcademicSession session = findSession(sessionId);
        findClass(classId);
        return enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                session.getSchoolId(), sessionId, classId, "ACTIVE").stream().map(this::view).toList();
    }

    @Transactional
    public EnrollmentView enroll(UUID studentId, EnrollmentRequest in) {
        teacherScope.assertStudent(studentId);
        Student student = findStudent(studentId);
        AcademicSession session = findSession(in.academicSessionId());
        if ("ARCHIVED".equals(session.getStatus())) throw ApiException.conflict("Session archivée");
        SchoolClass cls = in.classId() == null ? null : findClass(in.classId());
        validateDate(session, in.enrolledOn());
        if (active(studentId, session.getId()).isPresent()) throw ApiException.conflict("L’élève est déjà inscrit dans cette session");
        StudentEnrollment e = createRecord(student, session, cls, in.enrolledOn(),
                in.source() == null ? "MANUAL" : in.source(), in.reason(), null);
        syncLegacyIfCurrent(student, session, cls);
        audit.record("ENROLLMENT_CREATED", "StudentEnrollment", e.getId().toString(), null, view(e), in.reason());
        return view(e);
    }

    @Transactional
    public EnrollmentView transfer(UUID studentId, TransferRequest in) {
        teacherScope.assertStudent(studentId);
        Student student = findStudent(studentId);
        AcademicSession session = in.academicSessionId() == null ? sessionService.currentEntity() : findSession(in.academicSessionId());
        SchoolClass target = in.classId() == null ? null : findClass(in.classId());
        validateDate(session, in.effectiveDate());
        StudentEnrollment old = active(studentId, session.getId())
                .orElseThrow(() -> ApiException.conflict("Aucune inscription active dans cette session"));
        if (in.version() != null && in.version() != old.getVersion()) throw ApiException.conflict("Inscription modifiée par un autre utilisateur");
        if (java.util.Objects.equals(old.getSchoolClassId(), in.classId())) throw ApiException.conflict("La classe cible est déjà la classe active");
        EnrollmentView before = view(old);
        old.setStatus("TRANSFERRED");
        old.setExitedOn(in.effectiveDate());
        old.setReason(in.reason());
        enrollments.saveAndFlush(old);
        StudentEnrollment next = createRecord(student, session, target, in.effectiveDate(), "TRANSFER", in.reason(), old.getId());
        syncLegacyIfCurrent(student, session, target);
        audit.record("ENROLLMENT_TRANSFERRED", "Student", studentId.toString(), before, view(next), in.reason());
        return view(next);
    }

    @Transactional
    public EnrollmentView withdraw(UUID enrollmentId, WithdrawRequest in) {
        StudentEnrollment e = enrollments.findByIdAndSchoolId(enrollmentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        teacherScope.assertStudent(e.getStudentId());
        if (!"ACTIVE".equals(e.getStatus())) throw ApiException.conflict("Cette inscription n’est plus active");
        if (in.version() != null && in.version() != e.getVersion()) throw ApiException.conflict("Inscription modifiée par un autre utilisateur");
        if (in.effectiveDate().isBefore(e.getEnrolledOn())) throw ApiException.badRequest("Date de sortie antérieure à l’inscription");
        EnrollmentView before = view(e);
        e.setStatus("WITHDRAWN");
        e.setExitedOn(in.effectiveDate());
        e.setReason(in.reason());
        e = enrollments.saveAndFlush(e);
        AcademicSession current = sessionService.currentEntity();
        if (current.getId().equals(e.getAcademicSessionId())) {
            Student s = findStudent(e.getStudentId());
            s.setClassId(null); s.setClassName(null);
            students.save(s);
        }
        audit.record("ENROLLMENT_WITHDRAWN", "StudentEnrollment", e.getId().toString(), before, view(e), in.reason());
        return view(e);
    }

    /** Called by legacy student create/update/import paths to maintain the projection. */
    @Transactional
    public void syncCurrent(Student student) {
        AcademicSession session = sessionService.currentEntity();
        StudentEnrollment existing = active(student.getId(), session.getId()).orElse(null);
        if (existing == null) {
            SchoolClass cls = student.getClassId() == null ? null : findClass(student.getClassId());
            LocalDate effective = LocalDate.now();
            if (effective.isBefore(session.getStartDate())) effective = session.getStartDate();
            if (effective.isAfter(session.getEndDate())) effective = session.getEndDate();
            createRecord(student, session, cls, effective, "STUDENT_FORM", null, null);
        } else if (!java.util.Objects.equals(existing.getSchoolClassId(), student.getClassId())) {
            SchoolClass target = student.getClassId() == null ? null : findClass(student.getClassId());
            LocalDate effective = LocalDate.now().isAfter(session.getEndDate()) ? session.getEndDate() : LocalDate.now();
            if (effective.isBefore(session.getStartDate())) effective = session.getStartDate();
            existing.setStatus("TRANSFERRED");
            existing.setExitedOn(effective);
            enrollments.saveAndFlush(existing);
            createRecord(student, session, target, effective, "STUDENT_FORM", "Classe modifiée depuis la fiche élève", existing.getId());
        }
    }

    private StudentEnrollment createRecord(Student student, AcademicSession session, SchoolClass cls,
                                           LocalDate date, String source, String reason, UUID previous) {
        StudentEnrollment e = new StudentEnrollment();
        e.setSchoolId(TenantContext.get());
        e.setStudentId(student.getId());
        e.setAcademicSessionId(session.getId());
        e.setSchoolClassId(cls == null ? null : cls.getId());
        e.setClassNameSnapshot(cls == null ? student.getClassName() : cls.getName());
        e.setLevelSnapshot(cls == null ? student.getLevel() : cls.getLevel());
        e.setSubsystemSnapshot(cls == null ? student.getSubsystem() : cls.getSubsystem());
        e.setEnrolledOn(date);
        e.setSource(source == null || source.isBlank() ? "MANUAL" : source.trim().toUpperCase());
        e.setReason(reason);
        e.setPreviousEnrollmentId(previous);
        try { return enrollments.saveAndFlush(e); }
        catch (DataIntegrityViolationException ex) { throw ApiException.conflict("Une inscription active existe déjà pour cette session"); }
    }

    private void syncLegacyIfCurrent(Student student, AcademicSession session, SchoolClass cls) {
        AcademicSession current = sessionService.currentEntity();
        if (!current.getId().equals(session.getId())) return;
        student.setClassId(cls == null ? null : cls.getId());
        student.setClassName(cls == null ? null : cls.getName());
        student.setLevel(cls == null ? null : cls.getLevel());
        student.setSubsystem(cls == null ? null : cls.getSubsystem());
        students.save(student);
    }

    private java.util.Optional<StudentEnrollment> active(UUID studentId, UUID sessionId) {
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, sessionId, "ACTIVE");
    }
    private Student findStudent(UUID id) { return students.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève")); }
    private SchoolClass findClass(UUID id) { return classes.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Classe")); }
    private AcademicSession findSession(UUID id) { return sessions.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Session académique")); }
    private static void validateDate(AcademicSession session, LocalDate date) {
        if (date.isBefore(session.getStartDate()) || date.isAfter(session.getEndDate())) throw ApiException.badRequest("Date hors de la session académique");
    }
    private EnrollmentView view(StudentEnrollment e) {
        String label = sessions.findByIdAndSchoolId(e.getAcademicSessionId(), e.getSchoolId()).map(AcademicSession::getLabel).orElse("—");
        return new EnrollmentView(e.getId(), e.getStudentId(), e.getAcademicSessionId(), label,
                e.getSchoolClassId(), e.getClassNameSnapshot(), e.getLevelSnapshot(), e.getSubsystemSnapshot(),
                e.getStatus(), e.getEnrolledOn(), e.getExitedOn(), e.getSource(), e.getReason(),
                e.getPreviousEnrollmentId(), e.getVersion());
    }
}
