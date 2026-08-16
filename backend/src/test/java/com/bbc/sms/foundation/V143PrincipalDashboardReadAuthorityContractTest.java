package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V143PrincipalDashboardReadAuthorityContractTest {
    @Test
    void alignsDirectionDashboardReadsWithoutAddingMutationAuthority() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V143__principal_dashboard_read_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_action_grant")
                .contains("permission_role_action")
                .contains("permission_role_template_rule")
                .contains("'principal'")
                .contains("'principal_oversight'")
                .contains("'STUDENT_DIRECTORY_VIEW'")
                .contains("'STUDENT_PROFILE_VIEW'")
                .contains("'ATTENDANCE_ROSTER_VIEW'")
                .contains("'FINANCE_OVERVIEW_VIEW'")
                .contains("'FINANCE_REPORT_VIEW'")
                .contains("'FINANCE_EXPORT'")
                .contains("'SCHOOL_ALL'")
                .doesNotContain("PAYMENT_COLLECT")
                .doesNotContain("FEE_PLAN_ACTIVATE")
                .doesNotContain("CURRICULUM_CLASS_MANAGE")
                .doesNotContain("TEACHING_CLASS_ASSIGNMENT_MANAGE")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT");
    }
}
