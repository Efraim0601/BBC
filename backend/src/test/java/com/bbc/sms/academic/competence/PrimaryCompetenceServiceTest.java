package com.bbc.sms.academic.competence;

import com.bbc.sms.academic.competence.PrimaryCompetenceService.GridItem;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrimaryCompetenceServiceTest {

    @Test
    void defaultGridsAddUpToTheReportBookTotals() {
        Map<String, Integer> expected = Map.of("FR1", 280, "FR2", 300, "FR3", 300, "EN1", 280, "EN2", 360, "EN3", 360);
        expected.forEach((key, total) -> {
            List<GridItem> items = items(key.substring(0, 2), Integer.parseInt(key.substring(2)));
            PrimaryCompetenceService.validate(items);
            assertThat(PrimaryCompetenceService.total(items)).as(key).isEqualByComparingTo(BigDecimal.valueOf(total));
        });
    }

    @Test
    void defaultGridsListBothSportLinesAndNoUncountedSubjects() {
        for (String subsystem : List.of("FR", "EN")) for (int level = 1; level <= 3; level++) {
            List<String> codes = items(subsystem, level).stream().map(GridItem::code).toList();
            assertThat(codes).as(subsystem + level).containsSubsequence("6A1", "6A2").doesNotContain("REL");
            assertThat(items(subsystem, level)).as(subsystem + level).allMatch(GridItem::countsInTotal);
        }
    }

    @Test
    void aSplitThatDoesNotAddUpIsRefused() {
        GridItem broken = new GridItem("C2", Map.of(), "2B", Map.of("fr", "Sciences"), BigDecimal.valueOf(20),
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.valueOf(15), BigDecimal.valueOf(5), null, true);
        assertThatThrownBy(() -> PrimaryCompetenceService.validate(List.of(broken)))
                .isInstanceOf(ApiException.class).hasMessageContaining("2B").hasMessageContaining("30");
    }

    @Test
    void alternativesMustBeWorthTheSame() {
        GridItem able = new GridItem("C6", Map.of(), "6A1", Map.of(), BigDecimal.valueOf(20), BigDecimal.valueOf(20),
                null, null, null, "6A", true);
        GridItem challenged = new GridItem("C6", Map.of(), "6A2", Map.of(), BigDecimal.valueOf(10), BigDecimal.valueOf(10),
                null, null, null, "6A", true);
        assertThatThrownBy(() -> PrimaryCompetenceService.validate(List.of(able, challenged))).isInstanceOf(ApiException.class);
    }

    @Test
    void gradeScaleFollowsTheNationalBands() {
        assertThat(PrimaryCompetenceService.cote(new BigDecimal("10.99"))).isEqualTo("CNA");
        assertThat(PrimaryCompetenceService.cote(BigDecimal.valueOf(11))).isEqualTo("ECA");
        assertThat(PrimaryCompetenceService.cote(new BigDecimal("14.75"))).isEqualTo("ECA");
        assertThat(PrimaryCompetenceService.cote(BigDecimal.valueOf(15))).isEqualTo("A");
        assertThat(PrimaryCompetenceService.cote(new BigDecimal("17.99"))).isEqualTo("A");
        assertThat(PrimaryCompetenceService.cote(BigDecimal.valueOf(18))).isEqualTo("A+");
    }

    @Test
    void classNamesMapToTheirPrimaryLevel() {
        assertThat(PrimaryCompetenceService.levelFromClassName("SIL A")).isEqualTo(1);
        assertThat(PrimaryCompetenceService.levelFromClassName("CP B")).isEqualTo(1);
        assertThat(PrimaryCompetenceService.levelFromClassName("CE2 C")).isEqualTo(2);
        assertThat(PrimaryCompetenceService.levelFromClassName("CM1 A")).isEqualTo(3);
        assertThat(PrimaryCompetenceService.levelFromClassName("Class 1")).isEqualTo(1);
        assertThat(PrimaryCompetenceService.levelFromClassName("Class 4 B")).isEqualTo(2);
        assertThat(PrimaryCompetenceService.levelFromClassName("Class 6")).isEqualTo(3);
        assertThat(PrimaryCompetenceService.levelFromClassName("Nursery 1")).isNull();
    }

    @Test
    void evaluationTypeIsReadFromTheAssessmentCode() {
        assertThat(PrimaryCompetenceService.EvaluationType.fromAssessmentCode("S1_APC_1A_O"))
                .isEqualTo(PrimaryCompetenceService.EvaluationType.ORAL);
        assertThat(PrimaryCompetenceService.EvaluationType.fromAssessmentCode("S3_APC_6A1_S"))
                .isEqualTo(PrimaryCompetenceService.EvaluationType.ATTITUDE);
        assertThat(PrimaryCompetenceService.EvaluationType.fromAssessmentCode("S1_MATH")).isNull();
    }

    private static List<GridItem> items(String subsystem, int level) {
        return PrimaryCompetenceDefaults.grid(subsystem, level).stream()
                .map(d -> new GridItem(d.competenceCode(), Map.of(), d.code(), d.label(), d.points(), d.oral(), d.written(),
                        d.practical(), d.attitude(), d.alternativeGroup(), d.countsInTotal()))
                .toList();
    }
}
