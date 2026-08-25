package com.bbc.sms.student;

import com.bbc.sms.foundation.enrollment.EnrollmentService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyDecision;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.student.dto.StudentDtos.StudentTeacherView;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentServiceBilingualRosterTest {

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        ParcoursContext.clear();
    }

    @Test
    void englishProgrammeRosterKeepsTheSharedFrenchEnrollmentRow() {
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID englishClassId = UUID.randomUUID();
        UUID frenchClassId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        TenantContext.set(schoolId);
        ParcoursContext.set(new ParcoursContext.Scope("primary", "EN"));

        SchoolClass englishClass = new SchoolClass();
        englishClass.setId(englishClassId);
        englishClass.setSchoolId(schoolId);
        englishClass.setName("Class 3 A");
        englishClass.setLevel("primary");
        englishClass.setSubsystem("EN");
        SchoolClassRepository classes = mock(SchoolClassRepository.class);
        when(classes.findByIdAndSchoolId(englishClassId, schoolId)).thenReturn(Optional.of(englishClass));

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setSchoolClassId(frenchClassId);
        enrollment.setClassNameSnapshot("CE1 A");
        enrollment.setLevelSnapshot("primary");
        enrollment.setSubsystemSnapshot("FR");
        EnrollmentService enrollments = mock(EnrollmentService.class);
        when(enrollments.activeRosterRecordsForDirectory(sessionId, englishClassId))
                .thenReturn(List.of(enrollment));

        Student student = new Student();
        student.setId(studentId);
        student.setSchoolId(schoolId);
        student.setMatricule("BBC-1");
        student.setFirstName("Ada");
        student.setLastName("NGONO");
        student.setActive(true);
        StudentRepository students = mock(StudentRepository.class);
        when(students.findBySchoolIdAndIdInAndActiveTrue(eq(schoolId), anyCollection()))
                .thenReturn(List.of(student));

        TeacherScopeService teacherScope = mock(TeacherScopeService.class);
        when(teacherScope.restricted()).thenReturn(true);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        when(policy.decide(eq("STUDENT_DIRECTORY_VIEW"), any()))
                .thenReturn(PolicyDecision.allow("STUDENT_DIRECTORY_VIEW", "ROLE:teacher",
                        "TITULAIRE_CLASSES", 1));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(start);

        StudentService service = new StudentService(students, classes, mock(SetupService.class),
                teacherScope, enrollments, policy, jdbc);

        List<?> roster = service.roster(sessionId, englishClassId);

        assertThat(roster).hasSize(1);
        StudentTeacherView view = (StudentTeacherView) roster.getFirst();
        assertThat(view.classId()).isEqualTo(englishClassId);
        assertThat(view.className()).isEqualTo("Class 3 A");
        assertThat(view.subsystem()).isEqualTo("EN");
    }
}
