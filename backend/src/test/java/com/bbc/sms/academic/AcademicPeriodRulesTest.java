package com.bbc.sms.academic;

import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AcademicPeriodRulesTest {
    @Test
    void onlySequencePeriodsAcceptRawAssessmentConfiguration() {
        AcademicReportingPeriod sequence = period("SEQUENCE");
        AcademicReportingPeriod term = period("TERM_RESULT");
        AcademicReportingPeriod annual = period("ANNUAL_RESULT");

        assertDoesNotThrow(() -> AcademicPeriodRules.assertRawGradePeriod(sequence));
        ApiException termError = assertThrows(ApiException.class,
                () -> AcademicPeriodRules.assertRawGradePeriod(term));
        ApiException annualError = assertThrows(ApiException.class,
                () -> AcademicPeriodRules.assertRawGradePeriod(annual));
        assertEquals("ASSESSMENT_SEQUENCE_ONLY", termError.getCode());
        assertEquals("ASSESSMENT_SEQUENCE_ONLY", annualError.getCode());
    }

    @Test
    void computedPeriodDetectionIsCaseInsensitive() {
        assertTrue(AcademicPeriodRules.isComputed(period("term_result")));
        assertFalse(AcademicPeriodRules.isComputed(period("sequence")));
    }

    private static AcademicReportingPeriod period(String type) {
        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setPeriodType(type);
        return period;
    }
}
