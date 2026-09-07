package com.bbc.sms.finance.accounts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinanceAccountBillingTest {
    @Test void fullyWaivedChargesDoNotResurrectLegacyFees() {
        assertEquals(0,FinanceAccountService.billedTotal(true,0,100_000));
        assertEquals(30_000,FinanceAccountService.billedTotal(true,30_000,100_000));
    }
    @Test void studentsWithoutChargesRetainLegacyBilling() {
        assertEquals(100_000,FinanceAccountService.billedTotal(false,0,100_000));
    }
}
