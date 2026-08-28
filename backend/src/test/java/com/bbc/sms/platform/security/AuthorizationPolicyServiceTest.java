package com.bbc.sms.platform.security;

import com.bbc.sms.academic.security.AcademicScopeResolver;
import com.bbc.sms.guardian.GuardianAccessService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationPolicyServiceTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerScopeComparesAgainstAuthenticatedEmployeeNotTheContextItself() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubEmployee(jdbc, employeeId);
        AuthorizationPolicyService service = service(jdbc);
        AppUserPrincipal principal = principal();

        assertThat(service.ownerMatches(principal,
                PolicyResourceContext.empty().forSchool(schoolId).withOwnerEmployeeId(employeeId))).isTrue();
        assertThat(service.ownerMatches(principal,
                PolicyResourceContext.empty().forSchool(schoolId).withOwnerEmployeeId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void allActiveRoleClassificationKeepsTeacherInvariantForMultiRoleAccounts() {
        assertThat(AuthorizationPolicyService.isTeacher(List.of("principal", "teacher"))).isTrue();
        assertThat(AuthorizationPolicyService.isTeacher(List.of("secondary_teacher"))).isTrue();
        assertThat(AuthorizationPolicyService.isTeacher(List.of("principal", "accountant"))).isFalse();
        assertThat(AuthorizationPolicyService.isAdministrator(List.of("principal"))).isFalse();
        assertThat(AuthorizationPolicyService.isAdministrator(List.of("principal", "administrator"))).isTrue();
    }

    @Test
    void unknownScopeValuesAndNoneOnResourceActionsFailClosed() {
        AuthorizationPolicyService.Action studentAction =
                new AuthorizationPolicyService.Action("STUDENT_PROFILE_VIEW", "students", "STUDENT", "read");

        assertThat(AuthorizationPolicyService.safeMode("NOT_A_SCOPE")).isNull();
        assertThat(AuthorizationPolicyService.scopeCompatible(studentAction, PolicyScopeMode.NONE)).isFalse();
        assertThat(AuthorizationPolicyService.scopeCompatible(studentAction, PolicyScopeMode.SCHOOL_ALL)).isTrue();
    }

    @Test
    void unmatchedDenyDoesNotBlockMatchingAllowButMatchingDenyWins() {
        AuthorizationPolicyService service = service(mock(JdbcTemplate.class));
        AuthorizationPolicyService.Action action =
                new AuthorizationPolicyService.Action("SESSION_VIEW", "settings", "SCHOOL", "read");
        PolicyResourceContext context = PolicyResourceContext.empty().forSchool(schoolId);
        AppUserPrincipal principal = principal();

        AuthorizationPolicyService.Rule unmatchedDeny = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE", "DENY", "CLASS_SET",
                "{\"classIds\":[\"" + UUID.randomUUID() + "\"]}", null, null);
        AuthorizationPolicyService.Rule allow = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE", "ALLOW", "SCHOOL_ALL", null, null, null);
        assertThat(service.evaluateRules(action, context, principal,
                List.of(unmatchedDeny, allow), List.of("teacher"), 7).allowed()).isTrue();

        AuthorizationPolicyService.Rule matchingDeny = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE", "DENY", "SCHOOL_ALL", null, null, null);
        PolicyDecision decision = service.evaluateRules(action, context, principal,
                List.of(allow, matchingDeny), List.of("teacher"), 7);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialCode()).isEqualTo("POLICY_EXPLICIT_DENY");
    }

    @Test
    void domainIncompatibleAllowDoesNotHideLaterCompatibleScopedAllow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AttendanceScopeResolver attendance = mock(AttendanceScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class), attendance);
        AppUserPrincipal principal = principal();
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.of(2026, 9, 28), null, UUID.randomUUID(), "FRANCAIS", null,
                UUID.randomUUID(), null, null, "P1", "secondary");
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "ATTENDANCE_ROSTER_VIEW", "presence", "OCCURRENCE", "read");
        AuthorizationPolicyService.Rule titulaire = new AuthorizationPolicyService.Rule(
                "ROLE:form_teacher", "ALLOW", "TITULAIRE_CLASSES", null, null, null);
        AuthorizationPolicyService.Rule occurrence = new AuthorizationPolicyService.Rule(
                "ROLE:form_teacher", "ALLOW", "TIMETABLE_OCCURRENCES_ASSIGNED", null, null, null);

        when(academic.can("ACADEMIC_CLASS_RESULTS_VIEW", context)).thenReturn(true);
        when(attendance.allowsTeacher(principal, context, "TITULAIRE_CLASSES")).thenReturn(false);
        when(attendance.publishedOccurrenceAssigned(principal, context)).thenReturn(true);
        when(attendance.allowsTeacher(principal, context, "TIMETABLE_OCCURRENCES_ASSIGNED")).thenReturn(true);

        PolicyDecision decision = service.evaluateRules(action, context, principal,
                List.of(titulaire, occurrence), List.of("form_teacher"), 8);

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.matchedScope()).isEqualTo("TIMETABLE_OCCURRENCES_ASSIGNED");
    }

    @Test
    void selectedParcoursIsRetainedForSchoolScopedRoleRules() {
        PolicyResourceContext context = PolicyResourceContext.empty().forSchool(schoolId);
        ParcoursContext.Scope requested = new ParcoursContext.Scope("secondary", "FR");

        PolicyResourceContext bound = AuthorizationPolicyService.bindRequestedParcours(context, requested);

        assertThat(bound.parcours()).isEqualTo(requested);
        assertThat(AuthorizationPolicyService.bindRequestedParcours(bound,
                new ParcoursContext.Scope("primary", "EN")).parcours()).isEqualTo(requested);
    }

    @Test
    void attendanceTitulaireRuleUsesAttendanceResolverAtMatchStage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AttendanceScopeResolver attendance = mock(AttendanceScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class), attendance);
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), null, UUID.randomUUID(), null, null,
                null, null, null, "DAILY", "primary");
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "ATTENDANCE_ROSTER_VIEW", "presence", "TIMETABLE_OCCURRENCE", "read");
        AuthorizationPolicyService.Rule rule = new AuthorizationPolicyService.Rule(
                "ROLE:teacher", "ALLOW", "TITULAIRE_CLASSES", null, null, null);
        AppUserPrincipal principal = principal();

        when(attendance.allowsTeacher(principal, context, "TITULAIRE_CLASSES")).thenReturn(true);

        assertThat(service.matches(rule, action, context, principal)).isTrue();
        verify(attendance).allowsTeacher(principal, context, "TITULAIRE_CLASSES");
        verify(academic, org.mockito.Mockito.never()).can(anyString(), any());
    }

    @Test
    void teacherCannotUseMasterExportOrArbitraryTeacherTimetableActions() {
        AuthorizationPolicyService service = service(mock(JdbcTemplate.class));
        AppUserPrincipal principal = principal();
        PolicyResourceContext context = PolicyResourceContext.empty().forSchool(schoolId);
        AuthorizationPolicyService.Rule roleAllow = new AuthorizationPolicyService.Rule(
                "ROLE:teacher", "ALLOW", "SCHOOL_ALL", null, null, null);

        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "TIMETABLE_MASTER_VIEW", "timetable", "SCHOOL", "read"),
                context, principal, List.of(roleAllow), List.of("teacher"), 1).allowed()).isFalse();
        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "TIMETABLE_EXPORT", "timetable", "SCHOOL", "read"),
                context, principal, List.of(roleAllow), List.of("teacher"), 1).allowed()).isFalse();
        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL", "timetable", "SCHOOL", "read"),
                context, principal, List.of(roleAllow), List.of("teacher"), 1).allowed()).isFalse();
    }

    @Test
    void titulaireAnySubjectEditRequiresExplicitClassSetAndAcademicDomainInvariant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class),
                mock(AttendanceScopeResolver.class));
        AppUserPrincipal principal = principal();
        UUID classId = UUID.randomUUID();
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), null, classId, "SCIENCE", null,
                null, null, null, null, "secondary");
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS", "academic", "CLASS", "write");
        AuthorizationPolicyService.Rule classGrant = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE:" + userId, "ALLOW", "CLASS_SET",
                "{\"classIds\":[\"" + classId + "\"]}",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        when(academic.can("GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS", context)).thenReturn(true);

        assertThat(service.evaluateRules(action, context, principal, List.of(classGrant),
                List.of("teacher"), 4).allowed()).isTrue();
        PolicyResourceContext wrongClass = new PolicyResourceContext(schoolId,
                context.academicSessionId(), context.effectiveDate(), context.parcours(),
                UUID.randomUUID(), context.subjectCode(), context.studentId(),
                context.timetableOccurrenceId(), context.documentId(), context.ownerEmployeeId(),
                context.periodKey(), context.level());
        assertThat(service.evaluateRules(action, wrongClass, principal, List.of(classGrant),
                List.of("teacher"), 4)).isNull();
        assertThat(service.evaluateRules(action, context, principal,
                List.of(new AuthorizationPolicyService.Rule("ROLE:teacher", "ALLOW", "SCHOOL_ALL",
                        null, null, null)), List.of("teacher"), 4)).isNull();
    }

    @Test
    void titulaireAnySubjectEditFailsWhenAcademicDomainResolverRejectsNonTitulaire() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class),
                mock(AttendanceScopeResolver.class));
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), null, UUID.randomUUID(), "SCIENCE", null,
                null, null, null, null, "secondary");
        AuthorizationPolicyService.Rule grant = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE:" + userId, "ALLOW", "CLASS_SET",
                "{\"classIds\":[\"" + context.classId() + "\"]}", null, null);
        when(academic.can("GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS", context)).thenReturn(false);

        PolicyDecision decision = service.evaluateRules(new AuthorizationPolicyService.Action(
                "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS", "academic", "CLASS", "write"),
                context, principal(), List.of(grant), List.of("teacher"), 4);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialCode()).isEqualTo("ACADEMIC_SCOPE_DENIED");
    }

    @Test
    void defaultTeacherStudentMutationIsDeniedEvenWhenModuleRuleExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class),
                mock(AttendanceScopeResolver.class));
        UUID classId = UUID.randomUUID();
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.now(), null, classId, null, UUID.randomUUID(), null, null, null, null, "primary");
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "STUDENT_PROFILE_EDIT", "students", "STUDENT", "write");
        AuthorizationPolicyService.Rule roleAllow = new AuthorizationPolicyService.Rule(
                "ROLE:teacher", "ALLOW", "SCHOOL_ALL", null, null, null);
        when(academic.can("ACADEMIC_ROSTER_VIEW", context)).thenReturn(true);

        PolicyDecision decision = service.evaluateRules(action, context, principal(),
                List.of(roleAllow), List.of("teacher"), 1);

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void datedExactStudentMutationOverrideIsAllowedOnlyInsideTeacherScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AcademicScopeResolver academic = mock(AcademicScopeResolver.class);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                academic, mock(ParcoursAccessService.class), mock(GuardianAccessService.class),
                mock(AttendanceScopeResolver.class));
        UUID classId = UUID.randomUUID();
        PolicyResourceContext context = new PolicyResourceContext(schoolId, UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), null, classId, null, UUID.randomUUID(), null, null, null, null, "primary");
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "STUDENT_PROFILE_EDIT", "students", "STUDENT", "write");
        AuthorizationPolicyService.Rule datedGrant = new AuthorizationPolicyService.Rule(
                "USER_OVERRIDE:" + userId, "ALLOW", "CLASS_SET",
                "{\"classIds\":[\"" + classId + "\"]}",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        when(academic.can("ACADEMIC_ROSTER_VIEW", context)).thenReturn(true);

        assertThat(service.evaluateRules(action, context, principal(), List.of(datedGrant),
                List.of("teacher"), 1).allowed()).isTrue();
        PolicyResourceContext wrongClass = new PolicyResourceContext(schoolId, context.academicSessionId(),
                context.effectiveDate(), null, UUID.randomUUID(), null, context.studentId(), null,
                null, null, null, "primary");
        assertThat(service.evaluateRules(action, wrongClass, principal(), List.of(datedGrant),
                List.of("teacher"), 1)).isNull();
    }

    @Test
    void parentFinanceDocumentDownloadUsesFinanceChildFeature() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GuardianAccessService guardian = mock(GuardianAccessService.class);
        when(guardian.canAccess(eq(schoolId), eq(userId), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                eq("finance"), any(LocalDate.class))).thenReturn(true);
        AuthorizationPolicyService service = new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                mock(AcademicScopeResolver.class), mock(ParcoursAccessService.class), guardian,
                mock(AttendanceScopeResolver.class));
        AppUserPrincipal principal = new AppUserPrincipal(userId, schoolId, "parent", "parent", "Parent", "P");
        UUID childId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PolicyResourceContext context = new PolicyResourceContext(schoolId, null, LocalDate.now(), null,
                null, null, childId, null, null, null, null, null);
        AuthorizationPolicyService.Rule roleAllow = new AuthorizationPolicyService.Rule(
                "ROLE:parent", "ALLOW", "LINKED_CHILDREN", null, null, null);

        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "PARENT_DOCUMENT_DOWNLOAD", "parent", "CHILD", "read"),
                context, principal, List.of(roleAllow), List.of("parent"), 1).allowed()).isTrue();
        verify(guardian, times(2)).canAccess(eq(schoolId), eq(userId), eq(childId), eq("finance"), any(LocalDate.class));
    }

    @Test
    void teacherStudentEnrollmentInvariantAcceptsAnotherProgrammeClassInSameCohort() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthorizationPolicyService service = service(jdbc);
        UUID sessionId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);
        AuthorizationPolicyService.Action action = new AuthorizationPolicyService.Action(
                "STUDENT_DIRECTORY_VIEW", "students", "STUDENT", "read");
        PolicyResourceContext context = new PolicyResourceContext(schoolId, sessionId, date,
                null, classId, null, studentId, null, null, null, null, "primary");

        when(jdbc.queryForObject(contains("FROM student WHERE"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("FROM student_enrollment\n"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("academic_cohort_programme"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        assertThat(service.activeEnrollmentInvariant(action, context, List.of("teacher"))).isTrue();
        verify(jdbc).queryForObject(contains("p.school_class_id=? AND p.active"),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    void financePersonaCannotUseAcademicAttendanceOrSensitiveStudentActions() {
        AuthorizationPolicyService service = service(mock(JdbcTemplate.class));
        AppUserPrincipal financePrincipal = new AppUserPrincipal(userId, schoolId, "accountant", "accountant",
                "Accountant", "A");
        PolicyResourceContext schoolContext = PolicyResourceContext.empty().forSchool(schoolId);
        AuthorizationPolicyService.Rule financeRead = new AuthorizationPolicyService.Rule(
                "ROLE:accountant", "ALLOW", "SCHOOL_ALL", null, null, null);

        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "FINANCE_OVERVIEW_VIEW", "finance", "SCHOOL", "read"), schoolContext,
                financePrincipal, List.of(financeRead), List.of("accountant"), 1).allowed()).isTrue();

        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "ATTENDANCE_MARK", "presence", "OCCURRENCE", "write"), schoolContext,
                financePrincipal, List.of(), List.of("accountant"), 1)).isNull();
        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "ACADEMIC_SUBJECT_GRADE_VIEW", "academic", "CLASS_SUBJECT", "read"), schoolContext,
                financePrincipal, List.of(), List.of("accountant"), 1)).isNull();
        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "STUDENT_PROFILE_EDIT", "students", "STUDENT", "write"), schoolContext,
                financePrincipal, List.of(), List.of("accountant"), 1)).isNull();
        assertThat(service.evaluateRules(new AuthorizationPolicyService.Action(
                "HEALTH_CONFIDENTIAL_VIEW", "health", "STUDENT", "write"), schoolContext,
                financePrincipal, List.of(), List.of("accountant"), 1)).isNull();
    }

    private AuthorizationPolicyService service(JdbcTemplate jdbc) {
        return new AuthorizationPolicyService(jdbc, new ObjectMapper(),
                mock(AcademicScopeResolver.class), mock(ParcoursAccessService.class),
                mock(GuardianAccessService.class), mock(AttendanceScopeResolver.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubEmployee(JdbcTemplate jdbc, UUID employee) {
        doAnswer(invocation -> {
            org.springframework.jdbc.core.ResultSetExtractor extractor = invocation.getArgument(1,
                    org.springframework.jdbc.core.ResultSetExtractor.class);
            java.sql.ResultSet row = mock(java.sql.ResultSet.class);
            org.mockito.Mockito.when(row.next()).thenReturn(true);
            org.mockito.Mockito.when(row.getObject(1, UUID.class)).thenReturn(employee);
            return extractor.extractData(row);
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class));
    }

    private AppUserPrincipal principal() {
        AppUserPrincipal principal = new AppUserPrincipal(userId, schoolId, "teacher", "teacher",
                "Teacher", "T");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return principal;
    }
}
