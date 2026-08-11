package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicWindowPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    private final UUID school = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID termId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void unrestrictedTrimesterIsOpenAndExplicit() {
        AcademicTerm term = term(1, "T1");
        AcademicReportingPeriod period = period("S1", "SEQUENCE", termId);
        AcademicWindowPolicyService service = service(period, term);

        AcademicWindowPolicyService.WindowView view = service.effective(periodId, AcademicWindowPolicyService.Action.REVIEW);

        assertThat(view.open()).isTrue();
        assertThat(view.state()).isEqualTo("OPEN");
        assertThat(view.configuredMode()).isEqualTo("UNRESTRICTED");
        assertThat(view.effectiveMode()).isEqualTo("UNRESTRICTED");
        assertThat(view.governingTermCode()).isEqualTo("T1");
        assertThat(view.governedPeriodCodes()).containsExactly("S1", "S2", "T1_RESULT");
    }

    @Test
    void limitedWindowUsesScheduledAndClosedBoundaries() {
        AcademicTerm term = term(2, "T2");
        term.setManagementWindowLimited(true);
        term.setManagementOpensAt(NOW.plusSeconds(3600));
        term.setManagementClosesAt(NOW.plusSeconds(7200));
        AcademicReportingPeriod period = period("S3", "SEQUENCE", termId);
        AcademicWindowPolicyService service = service(period, term);

        AcademicWindowPolicyService.WindowView scheduled = service.effective(periodId, AcademicWindowPolicyService.Action.REVIEW);
        assertThat(scheduled.open()).isFalse();
        assertThat(scheduled.state()).isEqualTo("SCHEDULED");
        assertThat(scheduled.nextTransition()).isEqualTo(term.getManagementOpensAt());

        term.setManagementOpensAt(NOW.minusSeconds(7200));
        term.setManagementClosesAt(NOW.minusSeconds(1));
        AcademicWindowPolicyService.WindowView closed = service.effective(periodId, AcademicWindowPolicyService.Action.REVIEW);
        assertThat(closed.open()).isFalse();
        assertThat(closed.state()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> service.assertOpen(periodId, AcademicWindowPolicyService.Action.REVIEW))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo("TRIMESTER_WINDOW_CLOSED");
                    assertThat(api.getDetails()).containsKeys("governingTrimester", "affectedMilestones",
                            "serverTimezone", "serverTime", "configuredOpensAt", "configuredClosesAt", "repairTarget");
                    assertThat(api.getDetails().get("governingTrimester")).isEqualTo("T2");
                });
    }

    @Test
    void oneSidedWindowsRemainScheduledOrOpenIndefinitely() {
        AcademicTerm term = term(1, "T1");
        term.setManagementWindowLimited(true);
        term.setManagementOpensAt(NOW.plusSeconds(3600));
        AcademicReportingPeriod period = period("S1", "SEQUENCE", termId);
        AcademicWindowPolicyService service = service(period, term);

        assertThat(service.effective(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION).state())
                .isEqualTo("SCHEDULED");

        term.setManagementOpensAt(NOW.minusSeconds(3600));
        assertThat(service.effective(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION).open()).isTrue();

        term.setManagementOpensAt(null);
        term.setManagementClosesAt(NOW.plusSeconds(3600));
        assertThat(service.effective(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION).open()).isTrue();
        term.setManagementClosesAt(NOW.minusSeconds(1));
        assertThat(service.effective(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION).state())
                .isEqualTo("CLOSED");
    }

    @Test
    void computedResultRawActionsRemainProtectedButResultActionsUseTrimesterWindow() {
        AcademicTerm term = term(3, "T3");
        AcademicReportingPeriod result = period("T3_RESULT", "TERM_RESULT", termId);
        AcademicWindowPolicyService service = service(result, term);

        AcademicWindowPolicyService.WindowView raw = service.effective(periodId, AcademicWindowPolicyService.Action.GRADE_ENTRY);
        assertThat(raw.source()).isEqualTo("NOT_APPLICABLE");
        assertThat(raw.blockers()).containsExactly("COMPUTED_RESULT_PERIOD");

        AcademicWindowPolicyService.WindowView resultAction = service.effective(periodId, AcademicWindowPolicyService.Action.REVIEW);
        assertThat(resultAction.source()).isEqualTo("TERM_MANAGEMENT_WINDOW");
        assertThat(resultAction.governedPeriodCodes()).containsExactly("S5", "S6", "T3_RESULT", "ANNUAL");

        AcademicReportingPeriod annual = period("ANNUAL", "ANNUAL_RESULT", termId);
        AcademicWindowPolicyService.WindowView annualAction = service(annual, term).effective(periodId, AcademicWindowPolicyService.Action.REVIEW);
        assertThat(annualAction.governingTermCode()).isEqualTo("T3");
        assertThat(annualAction.governedPeriodCodes()).contains("ANNUAL");
    }

    private AcademicWindowPolicyService service(AcademicReportingPeriod period, AcademicTerm term) {
        TenantContext.set(school);
        AcademicReportingPeriodRepository periods = mock(AcademicReportingPeriodRepository.class);
        AcademicTermRepository terms = mock(AcademicTermRepository.class);
        AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
        AuditService audit = mock(AuditService.class);
        AcademicSession session = new AcademicSession();
        session.setId(sessionId); session.setSchoolId(school); session.setStatus("OPEN");
        when(periods.findByIdAndSchoolId(periodId, school)).thenReturn(Optional.of(period));
        when(terms.findByIdAndSchoolId(termId, school)).thenReturn(Optional.of(term));
        when(sessions.findByIdAndSchoolId(sessionId, school)).thenReturn(Optional.of(session));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        TermManagementWindowService windows = new TermManagementWindowService(sessions, terms, periods, audit, clock);
        return new AcademicWindowPolicyService(periods, windows, clock);
    }

    private AcademicTerm term(int sequence, String code) {
        AcademicTerm term = new AcademicTerm();
        term.setId(termId); term.setSchoolId(school); term.setAcademicSessionId(sessionId);
        term.setCode(code); term.setLabel(code + " label"); term.setSequenceNo(sequence);
        term.setStartDate(LocalDate.of(2026, 9, 1)); term.setEndDate(LocalDate.of(2026, 11, 30));
        term.setTimezone("Africa/Douala"); term.setVersion(4);
        return term;
    }

    private AcademicReportingPeriod period(String code, String type, UUID governingTermId) {
        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId); period.setSchoolId(school); period.setAcademicSessionId(sessionId);
        period.setAcademicTermId(governingTermId); period.setCode(code); period.setLabel(code + " label");
        period.setPeriodType(type); period.setStartDate(LocalDate.of(2026, 9, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30)); period.setTimezone("Africa/Douala");
        return period;
    }
}
