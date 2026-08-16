package com.bbc.sms.finance.charges;

import com.bbc.sms.finance.plans.FeePlanLine;
import com.bbc.sms.finance.plans.InstallmentTemplate;
import com.bbc.sms.finance.plans.InstallmentTemplateLine;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.bbc.sms.finance.charges.ChargeDtos.GenerationRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChargeGenerationPreviewServiceTest {

    @Test
    void normalizesSupportedPoliciesAndRejectsUnknownValues() {
        assertEquals("NONE", ChargeGenerationPreviewService.normalizePolicy(null));
        assertEquals("DAILY", ChargeGenerationPreviewService.normalizePolicy(" daily "));
        assertEquals("MONTHLY", ChargeGenerationPreviewService.normalizePolicy("MONTHLY"));
        ApiException error = assertThrows(ApiException.class,
                () -> ChargeGenerationPreviewService.normalizePolicy("WEEKLY"));
        assertEquals("INVALID_PRORATION_POLICY", error.getCode());
        assertTrue(error.getFieldErrors().containsKey("prorationPolicy"));
    }

    @Test
    void percentageScheduleUsesIntegerXafAndPutsRoundingOnFinalInstallment() {
        var templates = mock(com.bbc.sms.finance.plans.InstallmentTemplateRepository.class);
        var templateLines = mock(com.bbc.sms.finance.plans.InstallmentTemplateLineRepository.class);
        UUID templateId = UUID.randomUUID();
        InstallmentTemplate template = new InstallmentTemplate();
        template.setId(templateId);
        UUID schoolId = UUID.randomUUID();
        when(templates.findByIdAndSchoolId(templateId, schoolId)).thenReturn(Optional.of(template));

        InstallmentTemplateLine first = percentageLine(1, 3333, 0);
        InstallmentTemplateLine second = percentageLine(2, 3333, 30);
        InstallmentTemplateLine finalLine = percentageLine(3, 3334, 60);
        when(templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(schoolId, templateId))
                .thenReturn(List.of(finalLine, first, second));

        ChargeGenerationPreviewService service = new ChargeGenerationPreviewService(
                null, null, null, null, null, null, null, null, templates, templateLines, null, null, null);
        FeePlanLine planLine = new FeePlanLine();
        planLine.setAmountMinor(1001);
        planLine.setInstallmentTemplateId(templateId);
        AcademicSession session = new AcademicSession();
        session.setStartDate(LocalDate.of(2026, 9, 1));
        session.setEndDate(LocalDate.of(2027, 6, 30));

        ChargeGenerationPreviewService.Schedule schedule = service.schedule(
                planLine, 1001, session, LocalDate.of(2026, 9, 1), schoolId);

        assertTrue(schedule.blockers().isEmpty());
        assertEquals(List.of(333L, 333L, 335L), schedule.lines().stream()
                .map(ChargeGenerationPreviewService.ScheduleLine::amountMinor).toList());
        assertEquals(1001L, schedule.lines().stream()
                .mapToLong(ChargeGenerationPreviewService.ScheduleLine::amountMinor).sum());
        assertEquals(LocalDate.of(2026, 9, 1), schedule.lines().get(0).dueDate());
        assertEquals(LocalDate.of(2026, 10, 1), schedule.lines().get(1).dueDate());
    }

    @Test
    void generationKeyContainsTenantIndependentHistoricalScopeIds() {
        var enrollment = new com.bbc.sms.foundation.enrollment.StudentEnrollment();
        var plan = new com.bbc.sms.finance.plans.FeePlan();
        var line = new FeePlanLine();
        UUID enrollmentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        enrollment.setId(enrollmentId);
        plan.setId(planId);
        line.setId(lineId);
        assertEquals("CHARGE:" + enrollmentId + ":" + planId + ":" + lineId,
                ChargeGenerationPreviewService.stableGenerationKey(enrollment, plan, line));
    }

    private static InstallmentTemplateLine percentageLine(int order, int basisPoints, int offset) {
        InstallmentTemplateLine line = new InstallmentTemplateLine();
        line.setLineOrder(order);
        line.setLabelFr("Échéance " + order);
        line.setLabelEn("Installment " + order);
        line.setAllocationType("PERCENTAGE");
        line.setPercentageBasisPoints(basisPoints);
        line.setDueRuleType("SESSION_START_OFFSET");
        line.setDueOffsetDays(offset);
        return line;
    }
}
