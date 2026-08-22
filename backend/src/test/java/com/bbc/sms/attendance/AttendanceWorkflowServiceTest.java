package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyDecision;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TimetableSlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceWorkflowServiceTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    // Keep the scope test independent of the day on which the build runs.
    // Attendance deliberately has no daily roster on weekends.
    private final LocalDate today = LocalDate.of(2026, 8, 20);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void classListUsesTheCurrentEffectiveDateForTitulaireScope() {
        TenantContext.set(schoolId);
        JdbcTemplate jdbc = jdbcForDailyModel();
        TeacherScopeService teacherScope = mock(TeacherScopeService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        AcademicSession session = session(true);
        SchoolClass schoolClass = schoolClass();

        when(sessions().findBySchoolIdOrderByStartDateDesc(schoolId)).thenReturn(List.of(session));
        when(classes().findBySchoolIdOrderByName(schoolId)).thenReturn(List.of(schoolClass));
        when(teacherScope.allowedClassIds(sessionId, today)).thenReturn(Set.of(classId));
        when(policy.decide(eq("ATTENDANCE_ROSTER_VIEW"), any())).thenReturn(
                PolicyDecision.allow("ATTENDANCE_ROSTER_VIEW", "ROLE:secondary_teacher", "TITULAIRE_CLASSES", 1));

        AttendanceWorkflowService service = service(jdbc, classes(), sessions(), teacherScope, policy);

        assertThat(service.attendanceClasses()).extracting(AttendanceDtos.AttendanceClass::id).containsExactly(classId);
        verify(teacherScope).allowedClassIds(sessionId, today);
        verify(teacherScope, never()).allowedClassIds();
    }

    @Test
    void sessionOptionsChecksTheSelectedAttendanceDateAndSession() {
        TenantContext.set(schoolId);
        JdbcTemplate jdbc = jdbcForDailyModel();
        TeacherScopeService teacherScope = mock(TeacherScopeService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        AcademicSession session = session(false);
        SchoolClass schoolClass = schoolClass();

        when(sessions().findBySchoolIdOrderByStartDateDesc(schoolId)).thenReturn(List.of(session));
        when(classes().findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));
        when(policy.decide(eq("ATTENDANCE_ROSTER_VIEW"), any())).thenReturn(
                PolicyDecision.deny("ATTENDANCE_ROSTER_VIEW", "POLICY_SCOPE_DENIED", "Denied", "Denied", 1, null));

        AttendanceWorkflowService service = service(jdbc, classes(), sessions(), teacherScope, policy);

        assertThat(service.sessionOptions(classId, today)).isEmpty();
        verify(teacherScope).assertClass(sessionId, classId, today);
        verify(teacherScope, never()).assertClass(classId);
    }

    private JdbcTemplate jdbcForDailyModel() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("SELECT model FROM attendance_policy")) return "DAILY";
            if (sql.contains("SELECT school_class_id, count(*)")) return Map.of();
            return null;
        }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        return jdbc;
    }

    private AttendanceWorkflowService service(JdbcTemplate jdbc, SchoolClassRepository classes,
                                              AcademicSessionRepository sessions,
                                              TeacherScopeService teacherScope,
                                              AuthorizationPolicyService policy) {
        return new AttendanceWorkflowService(jdbc, classes, mock(TimetableSlotRepository.class),
                sessions, teacherScope, policy);
    }

    private AcademicSession session(boolean current) {
        AcademicSession session = new AcademicSession();
        session.setId(sessionId);
        session.setSchoolId(schoolId);
        session.setStartDate(today.minusDays(10));
        // End the fixture on the chosen weekday so the service's current
        // effective date is deterministic as well.
        session.setEndDate(today);
        session.setCurrent(current);
        return session;
    }

    private SchoolClass schoolClass() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setSchoolId(schoolId);
        schoolClass.setName("6ème A");
        schoolClass.setLevel("primary");
        schoolClass.setSubsystem("francophone");
        return schoolClass;
    }

    private AcademicSessionRepository sessions() {
        return sessionsMock;
    }

    private SchoolClassRepository classes() {
        return classesMock;
    }

    private final AcademicSessionRepository sessionsMock = mock(AcademicSessionRepository.class);
    private final SchoolClassRepository classesMock = mock(SchoolClassRepository.class);
}
