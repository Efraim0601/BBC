package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V163SecondaryHomeroomOversightContractTest {

    @Test
    void grantsSecondaryTitulaireClassOversightWithoutSubjectGradeEdit() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V163__secondary_homeroom_oversight.sql")) {
            assertThat(stream).as("V163 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("'secondary_teacher'")
                .contains("'ACADEMIC_COUNCIL_INPUT_VIEW'")
                .contains("'ACADEMIC_GRADE_PACKET_REVIEW'")
                .contains("'ATTENDANCE_REOPEN'")
                .contains("'TITULAIRE_CLASSES'")
                .contains("UPDATE permission_action")
                .contains("ATTENDANCE_REOPEN")
                .contains("scope_type='CLASS'")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT");
    }

    @Test
    void correctiveMigrationRemovesOnlyGeneratedCompatibilityDenials() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V164__repair_secondary_homeroom_compatibility_rules.sql")) {
            assertThat(stream).as("V164 corrective migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("role_code='secondary_teacher'")
                .contains("effect='DENY'")
                .contains("Secondary teacher copy: Permission Policy V2 compatibility backfill")
                .contains("ACADEMIC_GRADE_PACKET_REVIEW")
                .contains("ATTENDANCE_REOPEN")
                .contains("'TITULAIRE_CLASSES'");
    }
}
