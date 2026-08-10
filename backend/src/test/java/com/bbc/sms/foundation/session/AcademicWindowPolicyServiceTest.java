package com.bbc.sms.foundation.session;

import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AcademicWindowPolicyServiceTest {
    private final UUID school = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID termId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void unrestrictedSessionRuleIsOpenAndExplicit() throws Exception {
        AcademicReportingPeriod period = period();
        AcademicWindowPolicyService service = service(period, "UNRESTRICTED", null, null);

        AcademicWindowPolicyService.WindowView view = service.effective(periodId, AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);

        assertThat(view.open()).isTrue();
        assertThat(view.state()).isEqualTo("OPEN");
        assertThat(view.configuredMode()).isEqualTo("UNRESTRICTED");
        assertThat(view.effectiveMode()).isEqualTo("UNRESTRICTED");
    }

    @Test
    void limitedCloseOnlyRuleIsOpenBeforeItsCloseBoundary() throws Exception {
        AcademicReportingPeriod period = period();
        AcademicWindowPolicyService service = service(period, "LIMITED", null, Instant.now().plusSeconds(3600));

        AcademicWindowPolicyService.WindowView view = service.effective(periodId, AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);

        assertThat(view.open()).isTrue();
        assertThat(view.state()).isEqualTo("OPEN");
        assertThat(view.opensAt()).isNull();
        assertThat(view.closesAt()).isNotNull();
    }

    private AcademicWindowPolicyService service(AcademicReportingPeriod period, String mode,
                                                Instant opensAt, Instant closesAt) throws Exception {
        TenantContext.set(school);
        AcademicReportingPeriodRepository periods = mock(AcademicReportingPeriodRepository.class);
        when(periods.findByIdAndSchoolId(periodId, school)).thenReturn(Optional.of(period));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            @SuppressWarnings("unchecked") ResultSetExtractor<Object> extractor = invocation.getArgument(1, ResultSetExtractor.class);
            ResultSet rs = mock(ResultSet.class);
            if (sql.contains("academic_window_override")) {
                when(rs.next()).thenReturn(false);
            } else if (sql.contains("scope_type='SESSION'")) {
                when(rs.next()).thenReturn(true);
                when(rs.getString(1)).thenReturn(mode);
                when(rs.getTimestamp(2)).thenReturn(opensAt == null ? null : Timestamp.from(opensAt));
                when(rs.getTimestamp(3)).thenReturn(closesAt == null ? null : Timestamp.from(closesAt));
                when(rs.getString(4)).thenReturn("Africa/Douala");
            } else {
                when(rs.next()).thenReturn(false);
            }
            return extractor.extractData(rs);
        }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        return new AcademicWindowPolicyService(periods, mock(AcademicTermRepository.class),
                mock(AcademicSessionRepository.class), jdbc);
    }

    private AcademicReportingPeriod period() {
        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId); period.setSchoolId(school); period.setAcademicSessionId(session);
        period.setAcademicTermId(termId); period.setCode("S1"); period.setLabel("Sequence 1");
        period.setPeriodType("SEQUENCE"); period.setStartDate(LocalDate.of(2026, 9, 1));
        period.setEndDate(LocalDate.of(2026, 10, 1)); period.setTimezone("Africa/Douala");
        return period;
    }
}
