package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V139FinanceRoleFallbackDenyContractTest {
    @Test
    void closesModuleFallbackAndPreservesSelfPayslipCompatibility() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V139__finance_role_fallback_denies.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_action_grant")
                .contains("allowed=EXCLUDED.allowed")
                .contains("pa.module IN ('finance','hr')")
                .contains("a.action_code IS NULL")
                .contains("'accountant','econome','finance_collector'")
                .contains("'PAYSLIP_VIEW_SELF', 'ALLOW', 'SELF'")
                .doesNotContain("'teacher'")
                .doesNotContain("'parent'");
    }
}
