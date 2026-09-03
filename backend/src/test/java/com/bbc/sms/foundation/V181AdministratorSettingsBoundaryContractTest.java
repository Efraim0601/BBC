package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V181AdministratorSettingsBoundaryContractTest {

    @Test
    void principalRolesAndMailAreExplicitlyDenied() throws Exception {
        try (var in = getClass().getResourceAsStream(
                "/db/migration/V181__reserve_roles_and_mail_for_administrators.sql")) {
            assertThat(in).isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("'principal', 'principal_legacy_compat'")
                    .contains("'ROLE_VIEW'")
                    .contains("'PERMISSION_VIEW'")
                    .contains("'MAIL_CONFIG_VIEW'")
                    .contains("'MAIL_CONFIG_MANAGE'")
                    .contains("'DENY', 'SCHOOL_ALL'")
                    .contains("permission_role_template_rule")
                    .contains("allowed=false");
        }
    }
}
