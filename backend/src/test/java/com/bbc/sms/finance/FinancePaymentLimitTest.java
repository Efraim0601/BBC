package com.bbc.sms.finance;

import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancePaymentLimitTest {

    @Test
    void rejectsAnotherPaymentWhenFeesAreAlreadySettled() {
        assertThatThrownBy(() -> FinanceService.requireCollectibleAmount(100_000, 100_000, 1_000))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).contains("déjà entièrement réglés");
                });
    }

    @Test
    void rejectsAnAmountAboveTheRemainingBalance() {
        assertThatThrownBy(() -> FinanceService.requireCollectibleAmount(100_000, 45_000, 60_000))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getMessage()).contains("55,000".replace(",", ""));
                });
    }

    @Test
    void acceptsAnAmountUpToTheRemainingBalance() {
        assertThat(FinanceService.requireCollectibleAmount(100_000, 45_000, 55_000))
                .isEqualTo(55_000);
    }
}
