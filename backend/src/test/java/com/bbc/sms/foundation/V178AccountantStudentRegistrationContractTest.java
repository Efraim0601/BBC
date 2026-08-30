package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V178AccountantStudentRegistrationContractTest {
    @Test
    void grantsOnlyTheActionsNeededByTheAtomicRegistrationWizard() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V178__accountant_student_registration.sql")) {
            assertThat(stream).as("V178 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("('STUDENT_PROFILE_CREATE', 'SCHOOL_ALL')")
                .contains("('GUARDIAN_LINK_MANAGE',   'SCHOOL_ALL')")
                .contains("'accountant', a.action_code, 'ALLOW'")
                .doesNotContain("STUDENT_IMPORT")
                .doesNotContain("STUDENT_PROFILE_EDIT")
                .doesNotContain("STUDENT_PROFILE_DEACTIVATE")
                .doesNotContain("ENROLLMENT_CREATE");
    }
}
