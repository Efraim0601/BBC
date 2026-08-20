package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V137FinanceRolePolicyMigrationContractTest {
    @Test
    void materializesOnlyReviewedFinancePersonaAuthorities() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V137__finance_role_policy_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("'finance_collector'")
                .contains("'accountant','FINANCE_REPORT_VIEW'")
                .contains("'econome','FINANCE_REPORT_VIEW'")
                .contains("'finance_collector','PAYMENT_COLLECT'")
                .contains("'finance_collector','CASHIER_SESSION_OPEN'")
                .contains("'econome','CASHIER_SESSION_APPROVE'")
                .contains("'accountant','LEDGER_REOPEN'")
                .contains("'econome','PAYROLL_APPROVE'")
                .doesNotContain("'teacher','FINANCE_")
                .doesNotContain("'parent','FINANCE_");
    }
}
