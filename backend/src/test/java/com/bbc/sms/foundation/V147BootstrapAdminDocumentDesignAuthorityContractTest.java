package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V147BootstrapAdminDocumentDesignAuthorityContractTest {
    @Test
    void grantsOnlyTheEmergencyBootstrapUserDocumentDesignAuthority() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V147__bootstrap_admin_document_design_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_user_action")
                .contains("PERMISSION_MANAGE")
                .contains("DOCUMENT_DESIGN_PUBLISH")
                .contains("Initial emergency policy administrator")
                .contains("Fresh-school bootstrap document design authority")
                .doesNotContain("permission_role_action")
                .doesNotContain("permission_role_template_rule")
                .doesNotContain("role_code")
                .doesNotContain("teacher")
                .doesNotContain("parent");
    }
}
