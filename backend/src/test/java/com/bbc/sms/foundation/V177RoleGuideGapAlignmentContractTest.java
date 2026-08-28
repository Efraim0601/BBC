package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V177RoleGuideGapAlignmentContractTest {
    @Test
    void alignsPrincipalStaffAndAccountantLeastPrivilegeDefaults() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V177__align_principal_accountant_role_boundaries.sql")) {
            assertThat(stream).as("V177 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("('principal','HR_VIEW','PARCOURS_ALLOWED')")
                .contains("('principal','HR_MANAGE','PARCOURS_ALLOWED')")
                .contains("('accountant','HR_VIEW','SCHOOL_ALL')")
                .contains("('accountant','CHARGE_PREVIEW','SCHOOL_ALL')")
                .contains("('accountant','FINANCE_DOCUMENT_VIEW','SCHOOL_ALL')")
                .contains("'Accountant least-privilege role alignment'")
                .contains("code = 'HR_MANAGE'")
                .doesNotContain("('accountant','STUDENT_PROFILE_CREATE'")
                .doesNotContain("('accountant','STUDENT_IMPORT'")
                .doesNotContain("('accountant','TIMETABLE_PUBLISH'");
    }
}
