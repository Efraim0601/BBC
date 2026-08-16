package com.bbc.sms.finance.accounting;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingPeriodPreviewLookupTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void closedSessionPeriodIsReportedAsEmptyWithoutThrowing() {
        var repository = mock(AccountingPeriodRepository.class);
        var service = new AccountingPeriodService(repository, mock(JdbcTemplate.class),
                mock(AuditService.class), mock(FinancePolicyService.class));
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TenantContext.set(schoolId);
        LocalDate date = LocalDate.of(2027, 9, 15);
        when(repository.findFirstBySchoolIdAndAcademicSessionIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                schoolId, sessionId, "OPEN", date, date)).thenReturn(Optional.empty());

        assertThat(service.findOpenForDate(date, sessionId)).isEmpty();
    }
}
