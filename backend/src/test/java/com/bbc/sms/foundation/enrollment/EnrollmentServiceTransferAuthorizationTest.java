package com.bbc.sms.foundation.enrollment;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.platform.common.ApiException;
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
import java.util.Optional;
import java.util.UUID;

import static com.bbc.sms.foundation.enrollment.EnrollmentDtos.TransferRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnrollmentServiceTransferAuthorizationTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID sourceClassId = UUID.randomUUID();
    private final UUID assignedTargetClassId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void deniesOutOfScopeSourceBeforeLookingUpOrUsingAssignedTarget() {
        TenantContext.set(schoolId);

        Student student = new Student();
        student.setId(studentId);
        student.setSchoolId(schoolId);

        AcademicSession session = new AcademicSession();
        session.setId(sessionId);
        session.setSchoolId(schoolId);
        session.setStartDate(LocalDate.of(2026, 1, 1));
        session.setEndDate(LocalDate.of(2026, 12, 31));

        StudentEnrollment source = new StudentEnrollment();
        source.setId(UUID.randomUUID());
        source.setSchoolId(schoolId);
        source.setStudentId(studentId);
        source.setAcademicSessionId(sessionId);
        source.setSchoolClassId(sourceClassId);
        source.setStatus("ACTIVE");
        source.setEnrolledOn(LocalDate.of(2026, 1, 10));

        StudentRepository students = mock(StudentRepository.class);
        StudentEnrollmentRepository enrollments = mock(StudentEnrollmentRepository.class);
        SchoolClassRepository classes = mock(SchoolClassRepository.class);
        AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
        AcademicSessionService sessionService = mock(AcademicSessionService.class);
        TeacherScopeService teacherScope = mock(TeacherScopeService.class);
        AuditService audit = mock(AuditService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);

        when(students.findByIdAndSchoolId(studentId, schoolId)).thenReturn(Optional.of(student));
        when(sessions.findByIdAndSchoolId(sessionId, schoolId)).thenReturn(Optional.of(session));
        when(enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                schoolId, studentId, sessionId, "ACTIVE")).thenReturn(Optional.of(source));

        var sourceContext = new PolicyResourceContext[1];
        doAnswer(invocation -> {
            sourceContext[0] = invocation.getArgument(1, PolicyResourceContext.class);
            throw ApiException.forbidden("Source enrollment is outside the permitted class scope");
        }).when(policy).require(eq("ENROLLMENT_TRANSFER"), any(PolicyResourceContext.class));

        EnrollmentService service = new EnrollmentService(enrollments, students, classes, sessions,
                sessionService, teacherScope, audit, policy);

        assertThatThrownBy(() -> service.transfer(studentId,
                new TransferRequest(sessionId, assignedTargetClassId, LocalDate.of(2026, 2, 1),
                        "Attempted cross-class transfer", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("outside the permitted class scope");

        assertThat(sourceContext[0]).isNotNull();
        assertThat(sourceContext[0].studentId()).isEqualTo(studentId);
        assertThat(sourceContext[0].classId()).isEqualTo(sourceClassId);
        verify(classes, never()).findByIdAndSchoolId(assignedTargetClassId, schoolId);
        verify(enrollments, never()).saveAndFlush(any(StudentEnrollment.class));
    }
}
