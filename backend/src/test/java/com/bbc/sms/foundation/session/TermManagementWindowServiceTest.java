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

import static com.bbc.sms.foundation.session.SessionDtos.TermManagementWindowUpsert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermManagementWindowServiceTest {
    private final UUID school = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID termId = UUID.randomUUID();
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
    private final AcademicReportingPeriodRepository periods = mock(AcademicReportingPeriodRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final TermManagementWindowService service = new TermManagementWindowService(
            sessions, terms, periods, audit, Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void limitedWindowRequiresAtLeastOneEndpointAndReturnsFieldErrors() {
        givenOpenSessionAndTerm();

        assertThatThrownBy(() -> service.update(sessionId, termId,
                new TermManagementWindowUpsert(true, null, null, 4L)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo("TERM_WINDOW_ENDPOINT_REQUIRED");
                    assertThat(api.getFieldErrors()).containsKeys("opensAt", "closesAt");
                });
    }

    @Test
    void updateIsOptimisticLockedAuditedAndKeepsUnrestrictedNullDates() {
        AcademicSession session = givenOpenSessionAndTerm();
        AcademicTerm term = term();
        when(terms.findByIdAndSchoolId(termId, school)).thenReturn(Optional.of(term));
        when(terms.saveAndFlush(any(AcademicTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermManagementWindowUpsert limited = new TermManagementWindowUpsert(true,
                Instant.parse("2026-09-15T08:00:00Z"), Instant.parse("2026-11-30T17:00:00Z"), 4L);
        var updated = service.update(sessionId, termId, limited);
        assertThat(updated.limited()).isTrue();
        assertThat(term.getManagementOpensAt()).isEqualTo(limited.opensAt());
        verify(audit).record(eq("TERM_MANAGEMENT_WINDOW_UPDATED"), eq("AcademicTerm"), eq(termId.toString()), any(), any(), isNull());

        term.setVersion(5);
        var unrestricted = service.update(sessionId, termId, new TermManagementWindowUpsert(false, null, null, 5L));
        assertThat(unrestricted.limited()).isFalse();
        assertThat(term.getManagementOpensAt()).isNull();
        assertThat(term.getManagementClosesAt()).isNull();
        assertThatThrownBy(() -> service.update(sessionId, termId,
                new TermManagementWindowUpsert(false, null, null, 4L)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("STALE_VERSION"));
        assertThat(session.getStatus()).isEqualTo("OPEN");
    }

    private AcademicSession givenOpenSessionAndTerm() {
        TenantContext.set(school);
        AcademicSession session = new AcademicSession();
        session.setId(sessionId); session.setSchoolId(school); session.setStatus("OPEN");
        when(sessions.findByIdAndSchoolId(sessionId, school)).thenReturn(Optional.of(session));
        when(terms.findByIdAndSchoolId(termId, school)).thenReturn(Optional.of(term()));
        return session;
    }

    private AcademicTerm term() {
        AcademicTerm term = new AcademicTerm();
        term.setId(termId); term.setSchoolId(school); term.setAcademicSessionId(sessionId);
        term.setCode("T1"); term.setLabel("Trimester 1"); term.setSequenceNo(1);
        term.setStartDate(LocalDate.of(2026, 9, 1)); term.setEndDate(LocalDate.of(2026, 11, 30));
        term.setTimezone("Africa/Douala"); term.setVersion(4);
        return term;
    }
}
