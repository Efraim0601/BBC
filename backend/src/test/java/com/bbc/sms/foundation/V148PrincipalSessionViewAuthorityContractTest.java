package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V148PrincipalSessionViewAuthorityContractTest {
    @Test
    void grantsDirectionOnlyTheReadAuthorityNeededByTheAcademicWorkspace() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V148__principal_session_view_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_action_grant")
                .contains("permission_role_action")
                .contains("permission_role_template_rule")
                .contains("'principal'")
                .contains("'principal_legacy_compat'")
                .contains("'principal_oversight'")
                .contains("'SESSION_VIEW'")
                .contains("'SCHOOL_ALL'")
                .doesNotContain("SESSION_MANAGE")
                .doesNotContain("CLASS_MANAGE")
                .doesNotContain("ACADEMIC_STRUCTURE_MANAGE")
                .doesNotContain("TEACHING_CLASS_ASSIGNMENT_MANAGE");
    }
}
