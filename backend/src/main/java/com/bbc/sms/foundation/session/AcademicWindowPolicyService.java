package com.bbc.sms.foundation.session;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Resolves the effective workflow window without treating a half-configured pair as open. */
@Service
public class AcademicWindowPolicyService {
    public enum Action { GRADE_ENTRY, TEACHER_SUBMISSION, REVIEW, VALIDATION, PUBLICATION, CORRECTION }

    private final AcademicReportingPeriodRepository periods;
    private final AcademicTermRepository terms;
    private final AcademicSessionRepository sessions;
    private final JdbcTemplate jdbc;

    public AcademicWindowPolicyService(AcademicReportingPeriodRepository periods,
                                        AcademicTermRepository terms,
                                        AcademicSessionRepository sessions,
                                        JdbcTemplate jdbc) {
        this.periods = periods;
        this.terms = terms;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    public void assertOpen(UUID periodId, Action action) {
        AcademicReportingPeriod period = period(periodId);
        WindowSelection selected = effectiveWindow(period, action);
        if (selected.open() == null || selected.close() == null) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "WINDOW_NOT_CONFIGURED",
                    "La fenêtre " + actionLabel(action) + " n'est pas configurée pour " + period.getLabel()
                            + ". Configurez-la dans Paramètres → Années et périodes académiques.");
        }
        if (!selected.close().isAfter(selected.open())) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "WINDOW_INVALID",
                    "La fenêtre " + actionLabel(action) + " est invalide pour " + period.getLabel() + ".");
        }
        Instant now = Instant.now();
        if (now.isBefore(selected.open()) || now.isAfter(selected.close())) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "WINDOW_CLOSED",
                    "La fenêtre " + actionLabel(action) + " est fermée pour " + period.getLabel()
                            + " (du " + selected.open() + " au " + selected.close() + ").");
        }
    }

    public WindowView effective(UUID periodId, Action action) {
        return effective(null, periodId, action);
    }

    public WindowView effective(UUID sessionId, UUID periodId, Action action) {
        AcademicReportingPeriod period = period(periodId);
        if (sessionId != null && !sessionId.equals(period.getAcademicSessionId())) {
            throw ApiException.notFound("Période de résultat");
        }
        WindowSelection configured = configuredWindow(period, action);
        WindowSelection selected = effectiveWindow(period, action);
        Instant now = Instant.now();
        boolean complete = selected.open() != null && selected.close() != null && selected.close().isAfter(selected.open());
        boolean open = complete && !now.isBefore(selected.open()) && !now.isAfter(selected.close());
        String state;
        Instant nextTransition = null;
        List<String> blockers = new java.util.ArrayList<>();
        if (selected.open() == null || selected.close() == null) {
            state = "NOT_CONFIGURED";
            blockers.add("WINDOW_NOT_CONFIGURED");
        } else if (!selected.close().isAfter(selected.open())) {
            state = "INVALID";
            blockers.add("WINDOW_INVALID");
        } else if (now.isBefore(selected.open())) {
            state = "SCHEDULED";
            nextTransition = selected.open();
        } else if (now.isAfter(selected.close())) {
            state = "CLOSED";
        } else {
            state = "OPEN";
            nextTransition = selected.close();
        }
        return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(),
                configured.open(), configured.close(), configured.source(), selected.open(), selected.close(),
                open, selected.source(), state, nextTransition, blockers, selected.timezone());
    }

    public record WindowView(UUID periodId, String periodCode, String periodLabel, String action,
                             Instant configuredOpensAt, Instant configuredClosesAt, String configuredSource,
                             Instant opensAt, Instant closesAt, boolean open, String source, String state,
                             Instant nextTransition, List<String> blockers, String timezone) {}

    private AcademicReportingPeriod period(UUID id) {
        return periods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
    }

    private WindowSelection effectiveWindow(AcademicReportingPeriod period, Action action) {
        WindowSelection emergency = emergencyWindow(period, action);
        if (emergency != null) return emergency;
        return configuredWindow(period, action);
    }

    private WindowSelection configuredWindow(AcademicReportingPeriod period, Action action) {
        WindowSelection own = periodWindow(period, action);
        // A single configured endpoint is not a usable inherited override.
        if (own.open() != null || own.close() != null) return own;

        if (period.getAcademicTermId() != null) {
            AcademicTerm term = terms.findByIdAndSchoolId(period.getAcademicTermId(), TenantContext.get()).orElse(null);
            if (term != null) {
                WindowSelection inherited = termWindow(term, action);
                if (inherited.open() != null || inherited.close() != null) return inherited;
            }
        }
        AcademicSession session = sessions.findByIdAndSchoolId(period.getAcademicSessionId(), TenantContext.get()).orElse(null);
        return session == null ? new WindowSelection(null, null, "NOT_CONFIGURED", "Africa/Douala")
                : sessionWindow(session, action);
    }

    private WindowSelection emergencyWindow(AcademicReportingPeriod period, Action action) {
        return jdbc.query("""
                SELECT opens_at,expires_at
                  FROM academic_window_override
                 WHERE school_id=? AND academic_session_id=?
                   AND action=? AND (reporting_period_id=? OR reporting_period_id IS NULL)
                   AND opens_at<=now() AND expires_at>now()
                 ORDER BY CASE WHEN reporting_period_id IS NULL THEN 1 ELSE 0 END, opens_at DESC
                 LIMIT 1
                """, rs -> rs.next()
                ? new WindowSelection(rs.getTimestamp("opens_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                    "EMERGENCY_OVERRIDE", period.getTimezone())
                : null, TenantContext.get(), period.getAcademicSessionId(), action.name(), period.getId());
    }

    private static WindowSelection periodWindow(AcademicReportingPeriod p, Action a) {
        return new WindowSelection(open(p, a), close(p, a), "PERIOD", p.getTimezone());
    }

    private static WindowSelection termWindow(AcademicTerm t, Action a) {
        Instant open = switch (a) {
            case GRADE_ENTRY -> t.getGradeEntryOpensAt();
            case TEACHER_SUBMISSION -> t.getTeacherSubmissionOpensAt();
            case PUBLICATION, VALIDATION -> t.getBulletinPublishOpensAt();
            default -> null;
        };
        Instant close = switch (a) {
            case GRADE_ENTRY -> t.getGradeEntryClosesAt();
            case TEACHER_SUBMISSION -> t.getTeacherSubmissionClosesAt();
            case PUBLICATION, VALIDATION -> t.getBulletinPublishClosesAt();
            default -> null;
        };
        return new WindowSelection(open, close, "TERM", t.getTimezone());
    }

    private static WindowSelection sessionWindow(AcademicSession s, Action a) {
        Instant open = switch (a) {
            case GRADE_ENTRY -> s.getGradeEntryOpensAt();
            case TEACHER_SUBMISSION -> s.getTeacherSubmissionOpensAt();
            case PUBLICATION, VALIDATION -> s.getBulletinPublishOpensAt();
            default -> null;
        };
        Instant close = switch (a) {
            case GRADE_ENTRY -> s.getGradeEntryClosesAt();
            case TEACHER_SUBMISSION -> s.getTeacherSubmissionClosesAt();
            case PUBLICATION, VALIDATION -> s.getBulletinPublishClosesAt();
            default -> null;
        };
        return new WindowSelection(open, close, "SESSION", s.getTimezone());
    }

    private static Instant open(AcademicReportingPeriod p, Action a) {
        return switch (a) {
            case GRADE_ENTRY -> p.getGradeEntryOpensAt();
            case TEACHER_SUBMISSION -> p.getTeacherSubmissionOpensAt();
            case REVIEW -> p.getReviewOpensAt();
            case VALIDATION -> p.getValidationOpensAt();
            case PUBLICATION -> p.getBulletinPublishOpensAt();
            case CORRECTION -> p.getCorrectionOpensAt();
        };
    }

    private static Instant close(AcademicReportingPeriod p, Action a) {
        return switch (a) {
            case GRADE_ENTRY -> p.getGradeEntryClosesAt();
            case TEACHER_SUBMISSION -> p.getTeacherSubmissionClosesAt();
            case REVIEW -> p.getReviewClosesAt();
            case VALIDATION -> p.getValidationClosesAt();
            case PUBLICATION -> p.getBulletinPublishClosesAt();
            case CORRECTION -> p.getCorrectionClosesAt();
        };
    }

    private static String actionLabel(Action action) {
        return switch (action) {
            case GRADE_ENTRY -> "saisie des notes";
            case TEACHER_SUBMISSION -> "soumission des enseignants";
            case REVIEW -> "revue";
            case VALIDATION -> "validation";
            case PUBLICATION -> "publication";
            case CORRECTION -> "correction";
        };
    }

    private record WindowSelection(Instant open, Instant close, String source, String timezone) {
        private WindowSelection {
            source = source == null ? "NOT_CONFIGURED" : source;
            timezone = timezone == null || timezone.isBlank() ? "Africa/Douala" : timezone;
        }
    }
}
