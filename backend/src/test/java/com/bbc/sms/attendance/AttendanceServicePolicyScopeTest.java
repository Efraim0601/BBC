package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceView;
import com.bbc.sms.attendance.dto.AttendanceDtos.MarkRequest;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyDecision;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.SchoolProfileService;
import com.bbc.sms.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceServicePolicyScopeTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID deniedSessionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 9, 7);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void ordinaryAttendanceMarkTeacherCannotUseLegacyManualMarkRoute() {
        TenantContext.set(schoolId);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        when(policy.require(eq("ATTENDANCE_RECONCILE"), any(PolicyResourceContext.class)))
                .thenThrow(ApiException.forbidden("Legacy marking requires reconciliation authority."));

        AttendanceService service = service(jdbc, policy);

        assertThatThrownBy(() -> service.mark(new MarkRequest(studentId, date, "present", null, 0)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reconciliation");
        verifyNoInteractions(jdbc);
    }

    @Test
    void boardAuthorizesSessionsBeforeStudentIdentityAndKeepsSameClassSubjectsSeparate() {
        TenantContext.set(schoolId);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        UUID allowedOccurrence = UUID.randomUUID();
        UUID deniedOccurrence = UUID.randomUUID();
        Object[][] finalArgs = new Object[1][];
        String[] finalSql = new String[1];

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("SELECT s.id")) {
                return List.of(
                        new AttendanceService.BoardSession(sessionId, sessionId, classId, "6A", "secondary",
                                date, "PERIOD", "P1", "MATH", allowedOccurrence),
                        new AttendanceService.BoardSession(deniedSessionId, sessionId, classId, "6A", "secondary",
                                date, "PERIOD", "P1", "SCIENCE", deniedOccurrence));
            }
            if (sql.contains("SELECT m.student_id")) {
                finalSql[0] = sql;
                finalArgs[0] = java.util.Arrays.copyOfRange(invocation.getArguments(), 2,
                        invocation.getArguments().length);
                return List.of(new AttendanceView(studentId, "S-1", "STUDENT ONE", "6A", date,
                        "present", null, 0, "manual"));
            }
            throw new AssertionError("Unexpected attendance board query: " + sql);
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
        when(policy.decide(eq("ATTENDANCE_ROSTER_VIEW"), any(PolicyResourceContext.class)))
                .thenAnswer(invocation -> {
                    PolicyResourceContext context = invocation.getArgument(1);
                    return "MATH".equals(context.subjectCode())
                            ? PolicyDecision.allow("ATTENDANCE_ROSTER_VIEW", "ROLE:teacher", "TIMETABLE_OCCURRENCE", 1)
                            : PolicyDecision.deny("ATTENDANCE_ROSTER_VIEW", "POLICY_SCOPE_DENIED", "denied", "denied", 1, null);
                });

        AttendanceService service = service(jdbc, policy);
        assertThat(service.board(date).records()).hasSize(1);
        assertThat(finalArgs[0]).containsExactly(schoolId, sessionId);
        assertThat(finalSql[0]).contains("e.school_class_id=s.school_class_id");
        assertThat(finalSql[0]).contains("s.id IN (?)");
    }

    private AttendanceService service(JdbcTemplate jdbc, AuthorizationPolicyService policy) {
        return new AttendanceService(mock(AttendanceRepository.class), mock(DeviceRepository.class),
                mock(StudentRepository.class), mock(RealtimeService.class), mock(SchoolProfileService.class),
                mock(com.bbc.sms.platform.security.TeacherScopeService.class), policy, jdbc);
    }
}
