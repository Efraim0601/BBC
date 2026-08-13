package com.bbc.sms.academic.security;

import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.PermissionService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AcademicAccessPolicyServiceTest {
    private final UUID school = UUID.randomUUID();

    @AfterEach
    void clearSecurityAndTenant() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void deniesDirectAcademicResolutionWithoutAnAuthenticatedPrincipal() {
        TenantContext.set(school);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicAccessPolicyService policy = new AcademicAccessPolicyService(jdbc, mock(PermissionService.class), mock(com.bbc.sms.timetable.TeachingAssignmentResolver.class));

        AcademicAccessPolicyService.AccessDecision decision = policy.resolve(
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW,
                UUID.randomUUID(), UUID.randomUUID(), "MATH", null,
                java.time.LocalDate.of(2026, 9, 1));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("ACADEMIC_CLASS_ACCESS_DENIED");
        verifyNoInteractions(jdbc);
    }

    @Test
    void deniesATeacherAccountWithoutAnActiveEmployeeLinkBeforeResourceLookup() {
        TenantContext.set(school);
        UUID userId = UUID.randomUUID();
        AppUserPrincipal principal = new AppUserPrincipal(userId, school, "teacher", "teacher", "Teacher", "T");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> null).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class));
        AcademicAccessPolicyService policy = new AcademicAccessPolicyService(jdbc, mock(PermissionService.class), mock(com.bbc.sms.timetable.TeachingAssignmentResolver.class));

        AcademicAccessPolicyService.AccessDecision decision = policy.resolve(
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                UUID.randomUUID(), UUID.randomUUID(), "MATH", null,
                java.time.LocalDate.of(2026, 9, 1));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("TEACHER_ACCOUNT_NOT_LINKED");
    }

    @Test
    void requireRaisesTheStableDenialCodeForAnUnauthorizedResource() {
        TenantContext.set(school);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicAccessPolicyService policy = new AcademicAccessPolicyService(jdbc, mock(PermissionService.class), mock(com.bbc.sms.timetable.TeachingAssignmentResolver.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> policy.require(
                        AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW,
                        UUID.randomUUID(), UUID.randomUUID(), null, null,
                        java.time.LocalDate.of(2026, 9, 1)))
                .isInstanceOf(com.bbc.sms.platform.common.ApiException.class)
                .extracting("code").isEqualTo("ACADEMIC_CLASS_ACCESS_DENIED");
    }

    @Test
    void deniesASecondaryTeacherAnotherTeachersSubjectWithStableSubjectCode() {
        TenantContext.set(school);
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID otherTeacher = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);
        AppUserPrincipal principal = new AppUserPrincipal(userId, school, "teacher", "teacher", "Teacher", "T");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.canAction(anyString())).thenReturn(false);
        stubScopeQueries(jdbc, employeeId, sessionId, classId, date);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        TeachingAssignmentResolver assignments = mock(TeachingAssignmentResolver.class);
        when(assignments.resolve(eq(sessionId), eq(classId), eq("MATH"), eq(date)))
                .thenReturn(new TeachingAssignmentResolver.Resolution("MATH", otherTeacher, "Other", "EMP-2",
                        UUID.randomUUID(), 4L, "RESPONSIBLE", "RESOLVED", "ASSIGNMENT_RESOLVED",
                        "Inherited", "Inherited", true));

        AcademicAccessPolicyService policy = new AcademicAccessPolicyService(jdbc, permissions, assignments);
        AcademicAccessPolicyService.AccessDecision decision = policy.resolve(
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                sessionId, classId, "MATH", null, date);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("ACADEMIC_SUBJECT_ACCESS_DENIED");
        assertThat(decision.messageEn()).contains("not assigned");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubScopeQueries(JdbcTemplate jdbc, UUID employeeId, UUID sessionId,
                                  UUID classId, LocalDate date) {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            org.springframework.jdbc.core.ResultSetExtractor extractor = invocation.getArgument(1,
                    org.springframework.jdbc.core.ResultSetExtractor.class);
            java.sql.ResultSet row = mock(java.sql.ResultSet.class);
            if (sql.contains("u.employee_id")) {
                when(row.next()).thenReturn(true);
                when(row.getObject(1, UUID.class)).thenReturn(employeeId);
            } else if (sql.contains("start_date,end_date")) {
                when(row.next()).thenReturn(true);
                when(row.getObject(1, UUID.class)).thenReturn(sessionId);
                when(row.getObject(2, LocalDate.class)).thenReturn(date.minusDays(1));
                when(row.getObject(3, LocalDate.class)).thenReturn(date.plusDays(300));
            } else if (sql.contains("lower(level)")) {
                when(row.next()).thenReturn(true);
                when(row.getObject(1, UUID.class)).thenReturn(classId);
                when(row.getString(2)).thenReturn("secondary");
            } else {
                when(row.next()).thenReturn(false);
            }
            return extractor.extractData(row);
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class));
    }
}
