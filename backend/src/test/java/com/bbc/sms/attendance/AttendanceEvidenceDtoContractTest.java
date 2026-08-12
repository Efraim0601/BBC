package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceMetricValues;
import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceReadinessIssueView;
import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceSessionEvidenceView;
import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceSummaryView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceEvidenceDtoContractTest {
    @Test
    void retainsPreciseAndDisplayValuesWithDailyAndPeriodRepairEvidence() {
        UUID expectedDaily = UUID.randomUUID();
        UUID expectedPeriod = UUID.randomUUID();
        AttendanceSessionEvidenceView daily = new AttendanceSessionEvidenceView(expectedDaily, null,
                LocalDate.of(2026, 9, 1), "DAILY", null, null, "MISSING", false,
                480, BigDecimal.valueOf(8), "ATTENDANCE_ROLL_CALL_MISSING", "/presence");
        AttendanceSessionEvidenceView period = new AttendanceSessionEvidenceView(expectedPeriod, null,
                LocalDate.of(2026, 9, 1), "PERIOD", "P3", "MAT", "DRAFT", false,
                45, BigDecimal.valueOf(.75), "ATTENDANCE_SESSION_UNFINALIZED", "/presence?periodKey=P3");
        AttendanceMetricValues raw = new AttendanceMetricValues(BigDecimal.valueOf(8.125), BigDecimal.valueOf(6.75),
                BigDecimal.valueOf(66.666666), BigDecimal.valueOf(90), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(45), BigDecimal.valueOf(.75), BigDecimal.valueOf(45), BigDecimal.valueOf(.75),
                BigDecimal.valueOf(7), BigDecimal.ZERO);
        AttendanceSummaryView view = new AttendanceSummaryView(3, 2, 1, 0, 1, 7,
                BigDecimal.valueOf(.75), BigDecimal.valueOf(.75), BigDecimal.ZERO, BigDecimal.ZERO, 0,
                4, raw.expectedHours(), raw.finalizedHours(), raw.coveragePercent(), List.of(daily, period),
                List.of(UUID.randomUUID()), raw.totalAbsenceMinutes(), raw.totalAbsenceHours(),
                raw.justifiedAbsenceMinutes(), raw.unjustifiedAbsenceMinutes(), 0, List.of(),
                "attendance-policy-v1", List.of(new AttendanceReadinessIssueView("ATTENDANCE_DURATION_MISSING", "BLOCKER",
                        UUID.randomUUID(), daily.date(), expectedDaily, null, "fr", "en", "/settings")), List.of(), raw,
                new AttendanceMetricValues(BigDecimal.valueOf(8.13), BigDecimal.valueOf(6.75), BigDecimal.valueOf(66.67),
                        BigDecimal.valueOf(90), BigDecimal.valueOf(1.5), BigDecimal.valueOf(45), BigDecimal.valueOf(.75),
                        BigDecimal.valueOf(45), BigDecimal.valueOf(.75), BigDecimal.valueOf(7), BigDecimal.ZERO),
                null, false, List.of());

        assertThat(view.expectedHours()).isEqualByComparingTo("8.125");
        assertThat(view.displayValues().coveragePercent()).isEqualByComparingTo("66.67");
        assertThat(view.missingSessions()).extracting(AttendanceSessionEvidenceView::model)
                .containsExactly("DAILY", "PERIOD");
        assertThat(view.blockers()).extracting(AttendanceReadinessIssueView::code)
                .containsExactly("ATTENDANCE_DURATION_MISSING");
    }

    @Test
    void legacyConstructorRemainsReadableForOlderBulletinSnapshots() {
        AttendanceSummaryView legacy = new AttendanceSummaryView(2, 1, 1, 0, 0, 0,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        assertThat(legacy.expectedSessionCount()).isEqualTo(2);
        assertThat(legacy.blockers()).isEmpty();
        assertThat(legacy.sourceSnapshotIds()).isEmpty();
    }
}
