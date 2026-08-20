package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.WindowOverrideUpsert;
import static com.bbc.sms.foundation.session.SessionDtos.WorkflowWindowRuleUpsert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LegacyWindowMutationDeprecationTest {
    @Test
    void legacyRuleWritesReturnStructuredReplacementConflict() {
        var service = new AcademicWindowRuleService(
                mock(JdbcTemplate.class), mock(AcademicWindowPolicyService.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), (WorkflowWindowRuleUpsert) null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("WORKFLOW_WINDOWS_REPLACED");
                });
    }

    @Test
    void legacyOverrideCreatesReturnStructuredReplacementConflict() {
        var service = new AcademicWindowOverrideService(mock(JdbcTemplate.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), (WindowOverrideUpsert) null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("WORKFLOW_WINDOWS_REPLACED");
                });
    }
}
