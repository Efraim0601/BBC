package com.bbc.sms.academic.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.bbc.sms.academic.calculation.AcademicCalculationEngine.*;
import static org.assertj.core.api.Assertions.assertThat;

class AcademicCalculationEngineTest {
    @Test
    void sequenceNormalizesMarksToTwentyAndExcludesExemptEvidence() {
        Result result = AcademicCalculationEngine.sequence(List.of(
                new AssessmentInput(BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.ONE, MarkStatus.SCORED),
                new AssessmentInput(BigDecimal.valueOf(8), BigDecimal.valueOf(10), BigDecimal.ONE, MarkStatus.SCORED),
                new AssessmentInput(null, null, BigDecimal.ONE, MarkStatus.EXEMPT)));

        assertThat(result.product()).isEqualTo(Product.SEQUENCE);
        assertThat(result.value()).isEqualByComparingTo("15.5");
        assertThat(result.denominator()).isEqualByComparingTo("2");
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void missingAndAbsentEvidenceBlockCompletenessInsteadOfBecomingZero() {
        Result result = AcademicCalculationEngine.sequence(List.of(
                new AssessmentInput(BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.ONE, MarkStatus.SCORED),
                new AssessmentInput(null, null, BigDecimal.ONE, MarkStatus.MISSING),
                new AssessmentInput(null, null, BigDecimal.ONE, MarkStatus.ABSENT)));

        assertThat(result.value()).isEqualByComparingTo("15");
        assertThat(result.complete()).isFalse();
        assertThat(result.blockers()).contains("assessment-2:MISSING", "assessment-3:ABSENT");
    }

    @Test
    void fullyExemptEvidenceIsNotAZeroOrCompletenessBlocker() {
        Result result = AcademicCalculationEngine.sequence(List.of(
                new AssessmentInput(null, null, BigDecimal.ONE, MarkStatus.EXEMPT)));

        assertThat(result.exempt()).isTrue();
        assertThat(result.complete()).isTrue();
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void termIncludesOptionalCompOnlyWhenACompleteCompProductExists() {
        Result s1 = scored("12"), s2 = scored("16"), comp = scored("20");
        Result withComp = AcademicCalculationEngine.term(s1, s2, comp);
        Result withoutComp = AcademicCalculationEngine.term(s1, s2, null);

        assertThat(withComp.product()).isEqualTo(Product.TERM);
        assertThat(withComp.value()).isEqualByComparingTo("16");
        assertThat(withComp.includedComponents()).containsExactly("S1", "S2", "COMP");
        assertThat(withoutComp.value()).isEqualByComparingTo("14");
        assertThat(withoutComp.includedComponents()).containsExactly("S1", "S2");
    }

    @Test
    void t3AndAnnualAreDistinctProductsAndAnnualUsesAllThreeTerms() {
        Result t1 = AcademicCalculationEngine.term(scored("10"), scored("12"), null);
        Result t2 = AcademicCalculationEngine.term(scored("14"), scored("16"), null);
        Result t3 = AcademicCalculationEngine.term(scored("18"), scored("20"), null);

        Result annual = AcademicCalculationEngine.annual(t1, t2, t3);

        assertThat(t3.product()).isEqualTo(Product.TERM);
        assertThat(t3.value()).isEqualByComparingTo("19");
        assertThat(annual.product()).isEqualTo(Product.ANNUAL);
        assertThat(annual.value()).isEqualByComparingTo("15");
        assertThat(annual.includedComponents()).containsExactly("T1", "T2", "T3");
    }

    @Test
    void configuredDependencyWeightsAreAppliedWithoutRoundingChildren() {
        Result s1 = scored("10"), s2 = scored("20");
        Result weighted = AcademicCalculationEngine.term(s1, new BigDecimal("0.25"),
                s2, new BigDecimal("0.75"), null, BigDecimal.ONE);

        assertThat(weighted.value()).isEqualByComparingTo("17.5");
        assertThat(weighted.denominator()).isEqualByComparingTo("1.00");
    }

    @Test
    void genericTermAggregationUsesConfiguredDependencyCodesAndWeights() {
        Result result = AcademicCalculationEngine.aggregate(Product.TERM, List.of(
                new ChildInput("S3", scored("10"), new BigDecimal("0.25"), false),
                new ChildInput("S4", scored("18"), new BigDecimal("0.75"), false)));

        assertThat(result.value()).isEqualByComparingTo("16");
        assertThat(result.includedComponents()).containsExactly("S3", "S4");
    }

    @Test
    void missingRequiredChildBlocksButDoesNotCreateAZeroValue() {
        Result result = AcademicCalculationEngine.aggregate(Product.TERM, List.of(
                new ChildInput("S1", scored("12"), BigDecimal.ONE, false),
                new ChildInput("S2", new Result(Product.SEQUENCE, null, BigDecimal.ZERO,
                        List.of("MISSING"), List.of()), BigDecimal.ONE, false)));

        assertThat(result.value()).isEqualByComparingTo("12");
        assertThat(result.complete()).isFalse();
        assertThat(result.blockers()).contains("S2:MISSING");
    }

    @Test
    void optionalMissingChildDoesNotBlockOrChangeAvailableResult() {
        Result result = AcademicCalculationEngine.aggregate(Product.TERM, List.of(
                new ChildInput("S1", scored("12"), BigDecimal.ONE, false),
                new ChildInput("COMP", null, BigDecimal.ONE, true)));

        assertThat(result.value()).isEqualByComparingTo("12");
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void competitionRankingTreatsDifferentBigDecimalScalesAsTies() {
        assertThat(AcademicCalculationEngine.competitionRanks(List.of(
                new BigDecimal("18.0"), new BigDecimal("18.00"), new BigDecimal("12"))))
                .containsExactly(1, 1, 3);
    }

    @Test
    void competitionRankingUsesOneBasedStandardCompetitionRanks() {
        assertThat(AcademicCalculationEngine.competitionRanks(List.of(
                BigDecimal.valueOf(18), BigDecimal.valueOf(18), BigDecimal.valueOf(12), BigDecimal.valueOf(9))))
                .containsExactly(1, 1, 3, 4);
    }

    private static Result scored(String value) {
        return AcademicCalculationEngine.sequence(List.of(
                new AssessmentInput(new BigDecimal(value), BigDecimal.valueOf(20), BigDecimal.ONE, MarkStatus.SCORED)));
    }
}
