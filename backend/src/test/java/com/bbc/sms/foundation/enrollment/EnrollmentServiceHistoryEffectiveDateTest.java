package com.bbc.sms.foundation.enrollment;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnrollmentServiceHistoryEffectiveDateTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void usesSessionStartBeforeCurrentSessionBegins() {
        TenantContext.set(schoolId);

        AcademicSession session = new AcademicSession();
        session.setId(sessionId);
        session.setSchoolId(schoolId);
        session.setStartDate(LocalDate.of(2026, 9, 1));
        session.setEndDate(LocalDate.of(2027, 7, 16));

        Student student = new Student();
        student.setId(studentId);
        student.setSchoolId(schoolId);

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setSchoolId(schoolId);
        enrollment.setStudentId(studentId);
        enrollment.setAcademicSessionId(sessionId);
        enrollment.setSchoolClassId(classId);
        enrollment.setClassNameSnapshot("MAT-FR-MS-A");
        enrollment.setStatus("ACTIVE");
        enrollment.setEnrolledOn(session.getStartDate());

        StudentRepository students = mock(StudentRepository.class);
        StudentEnrollmentRepository enrollments = mock(StudentEnrollmentRepository.class);
        SchoolClassRepository classes = mock(SchoolClassRepository.class);
        AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
        AcademicSessionService sessionService = mock(AcademicSessionService.class);
        TeacherScopeService teacherScope = mock(TeacherScopeService.class);
        AuditService audit = mock(AuditService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        PolicyResourceContext[] captured = new PolicyResourceContext[1];

        when(sessionService.currentEntity()).thenReturn(session);
        when(students.findByIdAndSchoolId(studentId, schoolId)).thenReturn(Optional.of(student));
        when(enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                schoolId, studentId, sessionId, "ACTIVE")).thenReturn(Optional.of(enrollment));
        when(enrollments.findBySchoolIdAndStudentIdOrderByEnrolledOnDescCreatedAtDesc(schoolId, studentId))
                .thenReturn(List.of(enrollment));
        when(sessions.findByIdAndSchoolId(sessionId, schoolId)).thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(1, PolicyResourceContext.class);
            return null;
        }).when(policy).require(eq("ENROLLMENT_VIEW"), any(PolicyResourceContext.class));

        EnrollmentService service = new EnrollmentService(enrollments, students, classes, sessions,
                sessionService, teacherScope, audit, policy);

        assertThat(service.history(studentId)).hasSize(1);
        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].effectiveDate()).isEqualTo(session.getStartDate());
        verify(policy).require(eq("ENROLLMENT_VIEW"), any(PolicyResourceContext.class));
    }
}
