package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V179AccountantRegistrationBoundaryContractTest {
    @Test
    void removesOngoingFamilyManagementButKeepsRegistrationNarrow() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V179__limit_accountant_family_authority.sql")) {
            assertThat(stream).as("V179 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("role_code = 'accountant'")
                .contains("action_code = 'GUARDIAN_LINK_MANAGE'")
                .contains("effect = 'INHERIT'")
                .contains("allowed = false")
                .doesNotContain("action_code = 'STUDENT_PROFILE_CREATE'");
    }
}
