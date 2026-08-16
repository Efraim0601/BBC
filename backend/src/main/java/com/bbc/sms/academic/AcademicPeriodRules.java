package com.bbc.sms.academic;

import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Shared rules for the only periods that may own editable assessments/marks. */
public final class AcademicPeriodRules {
    public static final String SEQUENCE = "SEQUENCE";
    public static final String TERM_RESULT = "TERM_RESULT";
    public static final String ANNUAL_RESULT = "ANNUAL_RESULT";

    private AcademicPeriodRules() {}

    public static boolean isSequence(AcademicReportingPeriod period) {
        return period != null && SEQUENCE.equalsIgnoreCase(period.getPeriodType());
    }

    public static boolean isComputed(AcademicReportingPeriod period) {
        return period != null && !isSequence(period);
    }

    public static void assertRawGradePeriod(AcademicReportingPeriod period) {
        if (!isSequence(period)) {
            throw ApiException.coded(HttpStatus.BAD_REQUEST, "ASSESSMENT_SEQUENCE_ONLY",
                    "Les évaluations et les notes ne peuvent être configurées que pour les séquences S1 à S6. "
                            + "Les trimestres et le résultat annuel sont calculés automatiquement.");
        }
    }

    public static String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
