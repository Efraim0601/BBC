package com.bbc.sms.documents;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDesignControllerPolicyGuardContractTest {

    @Test
    void leavesTheV2ActionDecisionToTheServiceAfterTheStaffEnvelope() throws Exception {
        assertThat(guard("current").value()).isEqualTo("@perm.staffOnly()");
        assertThat(guard("publishBranding", DocumentDesignDtos.PublishRequest.class).value())
                .isEqualTo("@perm.staffOnly()");
        assertThat(guard("publishTemplate", java.util.UUID.class, DocumentDesignDtos.PublishRequest.class).value())
                .isEqualTo("@perm.staffOnly()");
    }

    private static PreAuthorize guard(String name, Class<?>... parameterTypes) throws Exception {
        Method method = DocumentDesignController.class.getDeclaredMethod(name, parameterTypes);
        return method.getAnnotation(PreAuthorize.class);
    }
}
