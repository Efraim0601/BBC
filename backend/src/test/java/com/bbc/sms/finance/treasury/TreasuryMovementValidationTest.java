package com.bbc.sms.finance.treasury;

import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TreasuryMovementValidationTest {
    private final TreasuryService.TreasuryRecord bank = new TreasuryService.TreasuryRecord(
            UUID.randomUUID(), UUID.randomUUID(), "BANK", "Test bank", "XAF", true);
    private final ChartOfAccount offset = new ChartOfAccount();

    @Test void withdrawalNeedsASourceAndDepositNeedsADestination() {
        assertThatThrownBy(() -> TreasuryService.validateMovement("WITHDRAWAL", null, bank, offset))
                .isInstanceOf(ApiException.class).hasMessageContaining("source");
        assertThatThrownBy(() -> TreasuryService.validateMovement("DEPOSIT", bank, null, offset))
                .isInstanceOf(ApiException.class).hasMessageContaining("destinataire");
        assertThatCode(() -> TreasuryService.validateMovement("WITHDRAWAL", bank, null, offset)).doesNotThrowAnyException();
        assertThatCode(() -> TreasuryService.validateMovement("DEPOSIT", null, bank, offset)).doesNotThrowAnyException();
    }
    @Test void transferCannotUseTheSameAccountTwice() {
        assertThatThrownBy(() -> TreasuryService.validateMovement("TRANSFER", bank, bank, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("différents");
    }
}
