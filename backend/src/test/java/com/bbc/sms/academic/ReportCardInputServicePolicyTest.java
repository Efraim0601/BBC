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
