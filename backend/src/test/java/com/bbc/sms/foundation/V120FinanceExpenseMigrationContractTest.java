package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V120FinanceExpenseMigrationContractTest {
    @Test
    void keepsExpenseLedgerActionsSeparateFromChartOfAccounts() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V120__permission_policy_v2_finance_expense_actions.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("FINANCE_EXPENSE_VIEW")
                .contains("FINANCE_EXPENSE_CREATE")
                .contains("FINANCE_EXPENSE_DELETE")
                .contains("Dépenses — consultation")
                .contains("permission_role_template_rule")
                .doesNotContain("ACCOUNT_MANAGE','ALLOW'");
    }
}
