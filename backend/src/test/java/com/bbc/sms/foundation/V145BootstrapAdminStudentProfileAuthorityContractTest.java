package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V145BootstrapAdminStudentProfileAuthorityContractTest {
    @Test
    void grantsOnlyTheEmergencyBootstrapUserStudentSetupActions() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V145__bootstrap_admin_student_profile_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_user_action")
                .contains("PERMISSION_MANAGE")
                .contains("STUDENT_PROFILE_CREATE")
                .contains("STUDENT_IMPORT")
                .contains("Initial emergency policy administrator")
                .contains("Fresh-school bootstrap student setup authority")
                .doesNotContain("permission_role_action")
                .doesNotContain("role_code")
                .doesNotContain("teacher")
                .doesNotContain("parent");
    }
}
