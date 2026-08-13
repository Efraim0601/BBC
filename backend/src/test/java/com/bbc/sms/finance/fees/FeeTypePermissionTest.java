package com.bbc.sms.finance.fees;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FeeTypePermissionTest {
    @Test
    void everyFeeTypeEndpointUsesExplicitActionPermission() {
        Method[] handlers = FeeTypeController.class.getDeclaredMethods();
        assertThat(handlers).isNotEmpty();
        assertThat(handlers).allSatisfy(method -> {
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as(method.getName()).isNotNull();
            assertThat(authorization.value()).as(method.getName()).contains("canAction");
        });
    }
}
