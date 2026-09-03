package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V182RoleBoundaryAlignmentContractTest {

    @Test
    void migrationAlignsPrincipalTeacherAndPrefectBoundaries() throws Exception {
        try (var in = getClass().getResourceAsStream(
                "/db/migration/V182__align_role_boundaries_after_full_qa.sql")) {
            assertThat(in).isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                    .contains("Principal finance is read-only")
                    .contains("'FINANCE_REPORT_VIEW','FINANCE_EXPORT'")
                    .contains("Teachers do not register students")
                    .contains("Secondary report cards require titulaire scope")
                    .contains("role_code='prefect'")
                    .contains("'ATTENDANCE_RECONCILE'")
                    .contains("'DISCIPLINE_MANAGE'")
                    .contains("'TIMETABLE_MASTER_VIEW'")
                    .doesNotContain("('prefect','FINANCE_OVERVIEW_VIEW'")
                    .doesNotContain("('prefect','STUDENT_PROFILE_CREATE'");
        }
    }
}
