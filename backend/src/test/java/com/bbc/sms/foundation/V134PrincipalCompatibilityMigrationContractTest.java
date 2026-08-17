package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V134PrincipalCompatibilityMigrationContractTest {
    @Test
    void mirrorsOnlyDirectionAcademicWorkflowActionsInCompatibilityProfile() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V134__principal_compatibility_academic_workflow_authority.sql")) {
            assertThat(stream).as("V134 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("principal_legacy_compat")
                .contains("ACADEMIC_GRADE_PACKET_REVIEW")
                .contains("ACADEMIC_REPORT_CARD_VALIDATE")
                .contains("ACADEMIC_REPORT_CARD_PUBLISH")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT")
                .doesNotContain("CURRICULUM_CLASS_MANAGE");
    }
}
