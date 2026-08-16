package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V133PrincipalAcademicWorkflowMigrationContractTest {
    @Test
    void keepsDirectionWorkflowGatesAlignedWithoutSetupWrites() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V133__principal_academic_workflow_authority.sql")) {
            assertThat(stream).as("V133 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("ACADEMIC_GRADE_PACKET_REVIEW")
                .contains("ACADEMIC_REPORT_CARD_VALIDATE")
                .contains("ACADEMIC_REPORT_CARD_PUBLISH")
                .contains("permission_action_grant")
                .contains("permission_role_action")
                .contains("principal_oversight")
                .doesNotContain("CURRICULUM_CLASS_MANAGE")
                .doesNotContain("TEACHING_CLASS_ASSIGNMENT_MANAGE");
    }
}
