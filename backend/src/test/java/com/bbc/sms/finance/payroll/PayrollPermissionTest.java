package com.bbc.sms.finance.payroll;

import com.bbc.sms.platform.security.PermissionActions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollPermissionTest {
    @Test
    void payrollCatalogueContainsExplicitActions() {
        assertThat(PermissionActions.CATALOG).containsKeys("PAYROLL_VIEW", "PAYROLL_PERIOD_MANAGE", "PAYROLL_COMPONENT_MANAGE",
                "PAYROLL_CALCULATE", "PAYROLL_ADJUST", "PAYROLL_REVIEW", "PAYROLL_APPROVE", "PAYROLL_PAY",
                "PAYROLL_VOID", "PAYSLIP_VIEW_ALL", "PAYSLIP_REGENERATE");
    }

    @Test
    void everyAdminPayrollEndpointHasAnExplicitPermissionAnnotation() {
        assertThat(PayrollController.class.getDeclaredMethods()).filteredOn(method -> Modifier.isPublic(method.getModifiers()) && !method.getName().toLowerCase().contains("self"))
                .allSatisfy(this::assertExplicitPermission);
    }

    private void assertExplicitPermission(Method method) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).as(method.getName()).isNotNull();
        assertThat(authorization.value()).as(method.getName()).contains("canAction");
    }
}
