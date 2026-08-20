package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V121V122SettingsSetupMigrationContractTest {
    @Test
    void settingsMigrationUsesPreciseCatalogActionsAndLocalizedCopy() throws Exception {
        String sql = read("/db/migration/V121__permission_policy_v2_settings_catalog_actions.sql");
        assertThat(sql)
                .contains("DISCIPLINE_CATALOG_VIEW")
                .contains("MAIL_CONFIG_VIEW")
                .contains("Catalogue disciplinaire")
                .contains("Messagerie")
                .contains("permission_role_template_rule")
                .doesNotContain("Action ' || initcap");
    }

    @Test
    void setupMigrationSeparatesSchoolCatalogFromClassScopedManagement() throws Exception {
        String sql = read("/db/migration/V122__permission_policy_v2_setup_scope_actions.sql");
        assertThat(sql)
                .contains("CURRICULUM_CLASS_MANAGE")
                .contains("CURRICULUM_CATALOG_MANAGE")
                .contains("TEACHING_CLASS_ASSIGNMENT_MANAGE")
                .contains("scope_type")
                .contains("permission_role_template_rule")
                .doesNotContain("ACCOUNT_MANAGE");
    }

    private String read(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
