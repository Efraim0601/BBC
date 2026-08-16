package com.bbc.sms.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollControllerPolicyGuardContractTest {
    @Test
    void payrollEndpointsUseV2PolicyGuards() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bbc/sms/finance/payroll/PayrollController.java"));
        assertTrue(source.contains("@policy.canAction('PAYROLL_CALCULATE')"));
        assertTrue(source.contains("@policy.canAction('PAYROLL_PERIOD_MANAGE')"));
        assertTrue(source.contains("@policy.canAction('PAYSLIP_VIEW_ALL')"));
        assertFalse(source.contains("@perm.canAction('PAYROLL_"));
        assertTrue(source.contains("@perm.canAction('PAYSLIP_VIEW_SELF')"));
    }
}
