package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V174PrimaryHomeroomCouncilInputsContractTest {

    @Test
    void grantsPrimaryCouncilInputsOnlyToDatedHomeroomClasses() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V174__primary_homeroom_council_inputs.sql")) {
            assertThat(stream).as("V174 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("'teacher'")
                .contains("'primary_teacher','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','TITULAIRE_CLASSES'")
                .contains("'primary_teacher','ACADEMIC_COUNCIL_INPUT_EDIT','ALLOW','TITULAIRE_CLASSES'")
                .contains("existing.role_code='teacher'")
                .contains("existing.scope_mode='TITULAIRE_CLASSES'")
                .doesNotContain("'secondary_teacher','ACADEMIC_COUNCIL_INPUT_EDIT'")
                .doesNotContain("'SCHOOL_ALL'");
    }
}
