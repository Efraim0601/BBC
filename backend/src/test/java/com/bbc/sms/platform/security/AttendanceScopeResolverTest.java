package com.bbc.sms.platform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceScopeResolverTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 9, 7); // Monday / day_idx 0

    @AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void primaryAttendanceUsesDailyDatedTitulaireAndNeedsNoOccurrence() {
        JdbcTemplate jdbc = jdbcForLevel("primary");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        AppUserPrincipal principal = principal();
        PolicyResourceContext context = context(null, null, "DAILY");

        assertThat(resolver.allowsTeacher(principal, context, "TITULAIRE_CLASSES")).isTrue();
        assertThat(resolver.allowsTeacher(principal, context, "ASSIGNED_CLASSES")).isTrue();
        assertThat(resolver.allowsTeacher(principal, context, "TIMETABLE_OCCURRENCES_ASSIGNED")).isFalse();
        verify(jdbc, org.mockito.Mockito.times(2)).queryForObject(
                org.mockito.ArgumentMatchers.contains("a.role='HOMEROOM'"),
                eq(Integer.class), any(Object[].class));
        verify(jdbc, org.mockito.Mockito.times(2)).queryForObject(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("academic_cohort_programme requested")
                        && sql.contains("h.mode='SHARED_BILINGUAL'")
                        && sql.contains("assigned.school_class_id=a.class_id")),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    void primaryAttendanceRejectsPeriodAndOccurrenceContexts() {
        JdbcTemplate jdbc = jdbcForLevel("primary");
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);

        assertThat(resolver.hasRequiredContext(context(UUID.randomUUID(), "MATH", "P1"))).isFalse();
        assertThat(resolver.hasRequiredContext(context(null, null, "P1"))).isFalse();
        assertThat(resolver.hasRequiredContext(context(null, "", "daily"))).isTrue();
    }

    @Test
    void secondaryAttendanceRequiresPublishedOccurrenceDimensions() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        final Object[][] captured = new Object[1][];
        doAnswer(invocation -> {
            captured[0] = java.util.Arrays.copyOfRange(invocation.getArguments(), 2,
                    invocation.getArguments().length);
            return 1;
        }).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        AppUserPrincipal principal = principal();
        UUID occurrenceId = UUID.randomUUID();
        PolicyResourceContext context = context(occurrenceId, "MATH", "P1");

        assertThat(resolver.allowsTeacher(principal, context,
                "TIMETABLE_OCCURRENCES_ASSIGNED")).isTrue();
        assertThat(resolver.allowsTeacher(principal, context, "ASSIGNED_CLASSES")).isTrue();
        assertThat(captured[0]).contains(schoolId, occurrenceId, sessionId, classId,
                "MATH", "P1", employeeId);
        verify(jdbc, org.mockito.Mockito.times(2)).queryForObject(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("v.status='PUBLISHED'")
                        && sql.contains("sub.timetable_version_id=v.id")
                        && sql.contains("s.day_idx=?")
                        && sql.contains("s.class_id=?")),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    void secondaryAttendanceWithoutOccurrenceFailsClosed() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);

        assertThat(resolver.hasRequiredContext(context(null, "MATH", "P1"))).isFalse();
    }

    @Test
    void secondaryTitulaireCanUseTitulaireScopeToReopenAClassSession() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        AppUserPrincipal principal = principal();

        assertThat(resolver.allowsTeacher(principal,
                context(UUID.randomUUID(), "MATH", "P1"),
                "TITULAIRE_CLASSES")).isTrue();
    }

    @Test
    void secondaryWrongSubjectOrPeriodIsRejectedByThePublishedOccurrenceBinding() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        AppUserPrincipal principal = principal();

        assertThat(resolver.allowsTeacher(principal,
                context(UUID.randomUUID(), "SCIENCE", "P9"),
                "TIMETABLE_OCCURRENCES_ASSIGNED")).isFalse();
    }

    @Test
    void ownScheduleRemainsViewableWhenTheTeacherHasNoPublishedSlots() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        PolicyResourceContext ownSchedule = new PolicyResourceContext(
                schoolId, null, date, null, null, null,
                null, null, null, employeeId, null, null);

        assertThat(resolver.ownPublishedSchedule(principal(), ownSchedule)).isTrue();
    }

    @Test
    void ownScheduleRejectsAnotherTeacherIdentifier() {
        JdbcTemplate jdbc = jdbcForLevel("secondary");
        AttendanceScopeResolver resolver = new AttendanceScopeResolver(jdbc);
        PolicyResourceContext anotherTeacher = new PolicyResourceContext(
                schoolId, null, date, null, null, null,
                null, null, null, UUID.randomUUID(), null, null);

        assertThat(resolver.ownPublishedSchedule(principal(), anotherTeacher)).isFalse();
    }

    private JdbcTemplate jdbcForLevel(String level) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("lower(level)")) return level;
            if (sql.contains("SELECT employee_id")) return employeeId;
            throw new AssertionError("Unexpected ResultSetExtractor query: " + sql);
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class));
        return jdbc;
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(userId, schoolId, "teacher", "teacher", "Teacher", "T");
    }

    private PolicyResourceContext context(UUID occurrenceId, String subjectCode, String periodKey) {
        return new PolicyResourceContext(schoolId, sessionId, date, null, classId, subjectCode,
                null, occurrenceId, null, null, periodKey, null);
    }
}
