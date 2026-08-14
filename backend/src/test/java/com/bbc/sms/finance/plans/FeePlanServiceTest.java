package com.bbc.sms.finance.plans;

import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.fees.FeeTypeRepository;
import com.bbc.sms.finance.fees.FeeTypeRevisionRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.bbc.sms.finance.plans.FeePlanDtos.PlanCreateRequest;
import static com.bbc.sms.finance.plans.FeePlanDtos.TemplateLineRequest;
import static com.bbc.sms.finance.plans.FeePlanDtos.TemplateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeePlanServiceTest {
    private static final UUID SCHOOL = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID CLASS = UUID.randomUUID();
    private static final UUID ENROLLMENT = UUID.randomUUID();

    @Mock FeePlanRepository plans;
    @Mock FeePlanLineRepository lines;
    @Mock InstallmentTemplateRepository templates;
    @Mock InstallmentTemplateLineRepository templateLines;
    @Mock StudentFeeElectionRepository elections;
    @Mock StudentFeeOverrideRepository overrides;
    @Mock FeeTypeRepository feeTypes;
    @Mock FeeTypeRevisionRepository revisions;
    @Mock ChartOfAccountRepository accounts;
    @Mock AcademicSessionRepository sessions;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock SchoolClassRepository classes;
    @Mock StudentRepository students;
    @Mock AuditService audit;

    private FeePlanService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(SCHOOL);
        service = new FeePlanService(plans, lines, templates, templateLines, elections, overrides,
                feeTypes, revisions, accounts, sessions, enrollments, classes, students, audit);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void resolvesActiveClassPlanBeforeLevelPlan() {
        StudentEnrollment enrollment = enrollment();
        FeePlan classPlan = plan("CLASS", CLASS);
        FeePlan levelPlan = plan("LEVEL", null);
        when(enrollments.findByIdAndSchoolId(ENROLLMENT, SCHOOL)).thenReturn(Optional.of(enrollment));
        when(plans.findForScope(SCHOOL, SESSION, "CLASS", "primary", "FR", CLASS, "ACTIVE"))
                .thenReturn(List.of(classPlan));
        when(lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(SCHOOL, classPlan.getId())).thenReturn(List.of());

        var result = service.resolve(ENROLLMENT);

        assertThat(result.source()).isEqualTo("CLASS_OVERRIDE");
        assertThat(result.planId()).isEqualTo(classPlan.getId());
        verify(plans, never()).findForScope(SCHOOL, SESSION, "LEVEL", "primary", "FR", null, "ACTIVE");
    }

    @Test
    void keepsSubsystemsIsolatedAndReturnsExplicitNoPlanBlocker() {
        StudentEnrollment enrollment = enrollment();
        when(enrollments.findByIdAndSchoolId(ENROLLMENT, SCHOOL)).thenReturn(Optional.of(enrollment));
        when(plans.findForScope(any(), any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(List.of());

        var result = service.resolve(ENROLLMENT);

        assertThat(result.source()).isEqualTo("NONE");
        assertThat(result.blocker()).isEqualTo("NO_ACTIVE_FEE_PLAN");
        verify(plans).findForScope(SCHOOL, SESSION, "CLASS", "primary", "FR", CLASS, "ACTIVE");
        verify(plans).findForScope(SCHOOL, SESSION, "LEVEL", "primary", "FR", null, "ACTIVE");
    }

    @Test
    void rejectsClassPlanWhenClassSnapshotDoesNotMatchScope() {
        var session = new com.bbc.sms.foundation.session.AcademicSession();
        session.setId(SESSION); session.setSchoolId(SCHOOL); session.setStartDate(LocalDate.of(2026, 9, 1)); session.setEndDate(LocalDate.of(2027, 7, 31));
        var schoolClass = new com.bbc.sms.timetable.SchoolClass();
        schoolClass.setId(CLASS); schoolClass.setSchoolId(SCHOOL); schoolClass.setLevel("secondary"); schoolClass.setSubsystem("FR"); schoolClass.setName("6A");
        when(sessions.findByIdAndSchoolId(SESSION, SCHOOL)).thenReturn(Optional.of(session));
        when(classes.findByIdAndSchoolId(CLASS, SCHOOL)).thenReturn(Optional.of(schoolClass));

        assertThatThrownBy(() -> service.createDraft(new PlanCreateRequest(SESSION, "CLASS", "primary", "FR", CLASS,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 31), "XAF")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getFieldErrors()).containsKey("schoolClassId"));
    }

    @Test
    void refusesEditingAnActivePlanWithAStableBlocker() {
        FeePlan active = plan("LEVEL", null); active.setLifecycle("ACTIVE"); active.setVersion(2);
        when(plans.findByIdAndSchoolId(active.getId(), SCHOOL)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.updateDraft(active.getId(), new FeePlanDtos.PlanUpdateRequest(
                2L, "primary", "FR", null, LocalDate.of(2026, 9, 1), null, "XAF")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("FEE_PLAN_IMMUTABLE"));
    }

    @Test
    void normalizesTemplateCodeAndPersistsOrderedPercentageLines() {
        UUID templateId = UUID.randomUUID();
        when(templates.findBySchoolIdAndCode(SCHOOL, "TERM_ONE")).thenReturn(Optional.empty());
        when(templates.saveAndFlush(any(InstallmentTemplate.class))).thenAnswer(invocation -> {
            InstallmentTemplate value = invocation.getArgument(0);
            value.setId(templateId);
            return value;
        });
        when(templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(SCHOOL, templateId)).thenReturn(List.of());

        var result = service.createTemplate(new TemplateRequest(" term_one ", "Terme 1", "Term 1", null,
                List.of(new TemplateLineRequest(1, "Première", "First", "PERCENTAGE", null, 10000,
                        "SESSION_START_OFFSET", null, 0, null)), null));

        assertThat(result.code()).isEqualTo("TERM_ONE");
        verify(templateLines).save(argThat(line -> "PERCENTAGE".equals(line.getAllocationType())
                && line.getLineOrder() == 1 && line.getPercentageBasisPoints() == 10000));
        verify(templates).findBySchoolIdAndCode(SCHOOL, "TERM_ONE");
    }

    private StudentEnrollment enrollment() {
        StudentEnrollment value = new StudentEnrollment(); value.setId(ENROLLMENT); value.setSchoolId(SCHOOL); value.setAcademicSessionId(SESSION); value.setSchoolClassId(CLASS); value.setLevelSnapshot("primary"); value.setSubsystemSnapshot("FR"); return value;
    }

    private FeePlan plan(String scope, UUID classId) {
        FeePlan value = new FeePlan(); value.setId(UUID.randomUUID()); value.setSchoolId(SCHOOL); value.setAcademicSessionId(SESSION); value.setScopeType(scope); value.setLevel("primary"); value.setSubsystem("FR"); value.setSchoolClassId(classId); value.setPlanVersionNo(1); value.setLifecycle("ACTIVE"); value.setEffectiveFrom(LocalDate.of(2026, 9, 1)); value.setCurrency("XAF"); return value;
    }
}
