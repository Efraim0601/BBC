package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V180StudentRegistrationRoleScopesContractTest {
    @Test
    void enforcesPrincipalParcoursAndAccountantSchoolWideRegistration() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V180__enforce_student_registration_role_scopes.sql")) {
            assertThat(stream).as("V180 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("role_code IN ('principal', 'principal_legacy_compat', 'accountant')")
                .contains("('principal',               'PARCOURS_ALLOWED'")
                .contains("('principal_legacy_compat', 'PARCOURS_ALLOWED'")
                .contains("('accountant',              'SCHOOL_ALL'")
                .contains("'STUDENT_PROFILE_CREATE'")
                .doesNotContain("STUDENT_IMPORT")
                .doesNotContain("STUDENT_PROFILE_EDIT")
                .doesNotContain("STUDENT_PROFILE_DEACTIVATE");
    }
}
