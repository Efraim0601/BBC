package com.bbc.sms.platform.common;

import com.bbc.sms.finance.collections.CashierSession;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerConcurrencyTest {

    @Test
    void mapsOptimisticVersionConflictsToStableHttp409() {
        var handler = new GlobalExceptionHandler();
        var response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(CashierSession.class, UUID.randomUUID()));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        assertThat(response.getBody().status()).isEqualTo(409);
    }
}
