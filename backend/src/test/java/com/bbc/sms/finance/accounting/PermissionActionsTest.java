package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.security.PermissionActions;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionActionsTest {
    @Test
    void accountingActionsAreExplicitFinancePermissions() {
        assertThat(PermissionActions.CATALOG).containsKeys(
                "ACCOUNT_MANAGE", "POSTING_RULE_MANAGE", "LEDGER_POST", "LEDGER_REVERSE",
                "LEDGER_CLOSE", "LEDGER_REOPEN", "FINANCE_REPORT_VIEW", "FINANCE_EXPORT");
        assertThat(PermissionActions.CATALOG.get("LEDGER_POST").module()).isEqualTo("finance");
        assertThat(PermissionActions.CATALOG.get("LEDGER_POST").level()).isEqualTo("write");
    }

    @Test
    void everyAccountingControllerHandlerUsesAnExplicitActionGate() {
        Method[] handlers = AccountingController.class.getDeclaredMethods();
        assertThat(handlers).isNotEmpty();
        assertThat(handlers).allSatisfy(method -> {
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as(method.getName()).isNotNull();
            assertThat(authorization.value()).as(method.getName()).contains("canAction");
        });
    }
}
