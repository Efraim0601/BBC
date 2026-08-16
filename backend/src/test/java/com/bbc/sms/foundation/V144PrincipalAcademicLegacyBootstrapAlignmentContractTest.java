package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V144PrincipalAcademicLegacyBootstrapAlignmentContractTest {
    @Test
    void upgradesExistingSchoolsWithoutGrantingAcademicEditing() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V144__principal_academic_legacy_bootstrap_alignment.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_action_grant")
                .contains("'principal'")
                .contains("'ACADEMIC_GRADE_PACKET_REVIEW'")
                .contains("'ACADEMIC_REPORT_CARD_VALIDATE'")
                .contains("'ACADEMIC_REPORT_CARD_PUBLISH'")
                .contains("allowed = EXCLUDED.allowed")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT")
                .doesNotContain("CURRICULUM_CLASS_MANAGE")
                .doesNotContain("TEACHING_CLASS_ASSIGNMENT_MANAGE");
    }
}
