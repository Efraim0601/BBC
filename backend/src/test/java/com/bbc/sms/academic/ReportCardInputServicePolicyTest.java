package com.bbc.sms.academic;

import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportCardInputServicePolicyTest {

    @Test
    void managementReviewDoesNotRequireEditingButTeacherOversightDoes() {
        assertThat(ReportCardInputService.councilReviewInputCapability("principal"))
                .isEqualTo(com.bbc.sms.academic.security.AcademicAccessPolicyService.Capability.COUNCIL_INPUT_VIEW);
        assertThat(ReportCardInputService.councilReviewInputCapability("secondary_teacher"))
                .isEqualTo(com.bbc.sms.academic.security.AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT);
    }

    @org.junit.jupiter.api.BeforeEach
    void bindTenant() {
        com.bbc.sms.platform.tenant.TenantContext.set(java.util.UUID.randomUUID());
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.bbc.sms.platform.tenant.TenantContext.clear();
    }

    @Test
    void gradeReviewPermissionCannotBypassReadOnlyCouncilPermission() {
        var periods = org.mockito.Mockito.mock(com.bbc.sms.foundation.session.AcademicReportingPeriodRepository.class);
        var policy = org.mockito.Mockito.mock(com.bbc.sms.academic.security.AcademicAccessPolicyService.class);
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var period = new AcademicReportingPeriod();
        period.setId(java.util.UUID.randomUUID()); period.setAcademicSessionId(java.util.UUID.randomUUID());
        period.setStartDate(LocalDate.of(2026, 9, 1));
        org.mockito.Mockito.when(periods.findByIdAndSchoolId(org.mockito.ArgumentMatchers.eq(period.getId()),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(period));
        org.mockito.Mockito.doThrow(ApiException.forbidden("Council is read-only"))
                .when(policy).require(org.mockito.ArgumentMatchers.eq(
                        com.bbc.sms.academic.security.AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var service = new ReportCardInputService(jdbc, periods, null, null, null, null, policy, null);
        assertThatThrownBy(() -> service.review(period.getId(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                new com.bbc.sms.academic.dto.AcademicDtos.ReportCardInputReview(period.getId(),
                        java.util.UUID.randomUUID(), "APPROVE", "Review", null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("read-only");
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void blankReasonIsAllowedWhenThereIsNoAttendanceCorrection() {
        assertThat(ReportCardInputService.correctionReason(
                BigDecimal.ZERO, BigDecimal.ZERO, 0, "  ")).isNull();
    }

    @Test
    void reasonIsRequiredAsSoonAsAManualCorrectionIsEntered() {
        assertThatThrownBy(() -> ReportCardInputService.correctionReason(
                new BigDecimal("0.25"), BigDecimal.ZERO, 0, ""))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void correctionReasonIsTrimmed() {
        assertThat(ReportCardInputService.correctionReason(
                BigDecimal.ZERO, BigDecimal.ONE, 0, "  Certificat médical  "))
                .isEqualTo("Certificat médical");
    }

    @Test
    void attendanceWindowMustStayInsideItsSequence() {
        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setStartDate(LocalDate.of(2026, 9, 1));
        period.setEndDate(LocalDate.of(2026, 10, 15));

        ReportCardInputService.validateAttendanceWindow(period,
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 10));

        assertThatThrownBy(() -> ReportCardInputService.validateAttendanceWindow(period,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("séquence");
        assertThatThrownBy(() -> ReportCardInputService.validateAttendanceWindow(period,
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("début");
    }
}
