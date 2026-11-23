package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V191TitulaireStudentRegistrationContractTest {
    @Test
    void makesTeacherRegistrationClassBoundAndHomeroomOnly() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V191__titulaire_student_registration.sql")) {
            assertThat(stream).as("V191 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("SET scope_type='CLASS'")
                .contains("'STUDENT_PROFILE_CREATE', 'ALLOW', 'TITULAIRE_CLASSES'")
                .contains("('teacher'")
                .contains("('secondary_teacher'")
                .contains("('form_teacher'")
                .doesNotContain("STUDENT_IMPORT")
                .doesNotContain("STUDENT_PROFILE_DEACTIVATE")
                .doesNotContain("ENROLLMENT_TRANSFER");
    }
}
