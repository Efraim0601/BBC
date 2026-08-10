package com.bbc.sms.foundation.session;

import com.bbc.sms.academic.AcademicPeriodRules;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Resolves the effective workflow window using explicit mode semantics. */
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
        if (AcademicPeriodRules.isComputed(period) && isRawAction(action)) {
            throw ApiException.coded(HttpStatus.BAD_REQUEST, "ASSESSMENT_SEQUENCE_ONLY",
                    "Les évaluations et les notes ne peuvent être configurées que pour les séquences S1 à S6. Les résultats calculés sont en lecture seule.");
        }
        WindowSelection selected = effectiveWindow(period, action);
        WindowState state = state(selected, Instant.now());
        if (!state.open()) {
            String code = "CLOSED".equals(state.state()) || "SCHEDULED".equals(state.state())
                    ? "WINDOW_CLOSED" : "WINDOW_NOT_CONFIGURED";
            throw ApiException.coded(HttpStatus.CONFLICT, code,
                    "La fenêtre " + actionLabel(action) + " n'est pas ouverte pour " + period.getLabel()
                            + ". Consultez les paramètres de la session pour connaître la prochaine action.");
        }
    }

    public WindowView effective(UUID periodId, Action action) { return effective(null, periodId, action); }

    public WindowView effective(UUID sessionId, UUID periodId, Action action) {
        AcademicReportingPeriod period = period(periodId);
        if (sessionId != null && !sessionId.equals(period.getAcademicSessionId())) throw ApiException.notFound("Période de résultat");
        if (AcademicPeriodRules.isComputed(period) && isRawAction(action)) {
            return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(),
                    null, null, "NOT_APPLICABLE", null, null, false, "NOT_APPLICABLE",
                    "NOT_APPLICABLE", null, List.of("COMPUTED_RESULT_PERIOD"), period.getTimezone(),
                    "NOT_APPLICABLE", "NOT_APPLICABLE", null);
        }
        WindowSelection configured = configuredWindow(period, action);
        WindowSelection selected = effectiveWindow(period, action);
        WindowState state = state(selected, Instant.now());
        return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(),
                configured.open(), configured.close(), configured.source(), selected.open(), selected.close(),
                state.open(), selected.source(), state.state(), state.nextTransition(), state.blockers(),
                selected.timezone(), configured.mode(), selected.mode(), selected.inheritedFrom());
    }

    public record WindowView(UUID periodId, String periodCode, String periodLabel, String action,
                             Instant configuredOpensAt, Instant configuredClosesAt, String configuredSource,
                             Instant opensAt, Instant closesAt, boolean open, String source, String state,
                             Instant nextTransition, List<String> blockers, String timezone,
                             String configuredMode, String effectiveMode, String inheritedFrom) {}

    private AcademicReportingPeriod period(UUID id) {
        return periods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
    }

    private WindowSelection effectiveWindow(AcademicReportingPeriod period, Action action) {
        WindowSelection emergency = emergencyWindow(period, action);
        return emergency == null ? configuredWindow(period, action) : emergency;
    }

    /**
     * The normalized table is authoritative.  The legacy fallback keeps an
     * installation on an older schema readable during rolling deployment.
     */
    private WindowSelection configuredWindow(AcademicReportingPeriod period, Action action) {
        RuleRow periodRule = rule(period, "PERIOD", period.getId(), action);
        if (periodRule != null && !"INHERIT".equals(periodRule.mode())) return selection(periodRule, null);

        if (period.getAcademicTermId() != null) {
            RuleRow termRule = rule(period, "TERM", period.getAcademicTermId(), action);
            if (termRule != null && !"INHERIT".equals(termRule.mode())) return selection(termRule, null);
        }
        RuleRow sessionRule = rule(period, "SESSION", period.getAcademicSessionId(), action);
        if (sessionRule != null && !"INHERIT".equals(sessionRule.mode())) return selection(sessionRule, null);

        // Compatibility path before V83 has been applied.
        WindowSelection legacy = legacyConfiguredWindow(period, action);
        return legacy == null ? new WindowSelection(null, null, "NOT_CONFIGURED", period.getTimezone(),
                "INHERIT", null) : legacy;
    }

    private RuleRow rule(AcademicReportingPeriod period, String scope, UUID target, Action action) {
        String sql = "SESSION".equals(scope)
                ? "SELECT mode,opens_at,closes_at,timezone FROM academic_workflow_window_rule WHERE school_id=? AND academic_session_id=? AND scope_type='SESSION' AND action=?"
                : "TERM".equals(scope)
                ? "SELECT mode,opens_at,closes_at,timezone FROM academic_workflow_window_rule WHERE school_id=? AND academic_session_id=? AND scope_type='TERM' AND academic_term_id=? AND action=?"
                : "SELECT mode,opens_at,closes_at,timezone FROM academic_workflow_window_rule WHERE school_id=? AND academic_session_id=? AND scope_type='PERIOD' AND reporting_period_id=? AND action=?";
        Object[] args = "SESSION".equals(scope)
                ? new Object[]{TenantContext.get(), period.getAcademicSessionId(), action.name()}
                : new Object[]{TenantContext.get(), period.getAcademicSessionId(), target, action.name()};
        return jdbc.query(sql, rs -> rs.next() ? new RuleRow(scope, rs.getString(1),
                ts(rs.getTimestamp(2)), ts(rs.getTimestamp(3)), rs.getString(4)) : null, args);
    }

    private WindowSelection selection(RuleRow row, String inheritedFrom) {
        return new WindowSelection(row.opensAt(), row.closesAt(), row.scope(), row.timezone(),
                row.mode(), inheritedFrom == null ? row.scope() : inheritedFrom);
    }

    private WindowSelection legacyConfiguredWindow(AcademicReportingPeriod period, Action action) {
        WindowSelection own = periodWindow(period, action);
        if (own.open() != null || own.close() != null) return own;
        if (period.getAcademicTermId() != null) {
            AcademicTerm term = terms.findByIdAndSchoolId(period.getAcademicTermId(), TenantContext.get()).orElse(null);
            if (term != null) {
                WindowSelection inherited = termWindow(term, action);
                if (inherited.open() != null || inherited.close() != null) return inherited;
            }
        }
        AcademicSession session = sessions.findByIdAndSchoolId(period.getAcademicSessionId(), TenantContext.get()).orElse(null);
        return session == null ? null : sessionWindow(session, action);
    }

    private WindowSelection emergencyWindow(AcademicReportingPeriod period, Action action) {
        return jdbc.query("""
                SELECT opens_at,expires_at
                  FROM academic_window_override
                 WHERE school_id=? AND academic_session_id=? AND action=?
                   AND (reporting_period_id=? OR reporting_period_id IS NULL)
                   AND opens_at<=now() AND expires_at>now()
                 ORDER BY CASE WHEN reporting_period_id IS NULL THEN 1 ELSE 0 END, opens_at DESC
                 LIMIT 1
                """, rs -> rs.next() ? new WindowSelection(ts(rs.getTimestamp("opens_at")),
                ts(rs.getTimestamp("expires_at")), "EMERGENCY_OVERRIDE", period.getTimezone(),
                "LIMITED", "EMERGENCY_OVERRIDE") : null,
                TenantContext.get(), period.getAcademicSessionId(), action.name(), period.getId());
    }

    private static WindowSelection periodWindow(AcademicReportingPeriod p, Action a) {
        return new WindowSelection(open(p, a), close(p, a), "PERIOD", p.getTimezone(), "LIMITED", "PERIOD");
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
        return new WindowSelection(open, close, "TERM", t.getTimezone(), "LIMITED", "TERM");
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
            case TEACHER_SUBMISSION -> s.getTeacherSubmissionOpensAt() == null ? s.getTeacherSubmissionClosesAt() : s.getTeacherSubmissionClosesAt();
            case PUBLICATION, VALIDATION -> s.getBulletinPublishClosesAt();
            default -> null;
        };
        return new WindowSelection(open, close, "SESSION", s.getTimezone(), "LIMITED", "SESSION");
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

    private static WindowState state(WindowSelection selected, Instant now) {
        List<String> blockers = new ArrayList<>();
        if ("UNRESTRICTED".equals(selected.mode())) return new WindowState(true, "OPEN", null, blockers);
        if (!"LIMITED".equals(selected.mode())) {
            blockers.add("WINDOW_NOT_CONFIGURED");
            return new WindowState(false, "NOT_CONFIGURED", null, blockers);
        }
        if (selected.open() == null && selected.close() == null) {
            blockers.add("WINDOW_NOT_CONFIGURED");
            return new WindowState(false, "NOT_CONFIGURED", null, blockers);
        }
        if (selected.open() != null && selected.close() != null && !selected.close().isAfter(selected.open())) {
            blockers.add("WINDOW_INVALID");
            return new WindowState(false, "INVALID", null, blockers);
        }
        if (selected.open() != null && now.isBefore(selected.open())) return new WindowState(false, "SCHEDULED", selected.open(), blockers);
        if (selected.close() != null && now.isAfter(selected.close())) return new WindowState(false, "CLOSED", null, blockers);
        return new WindowState(true, "OPEN", selected.close(), blockers);
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

    private static boolean isRawAction(Action action) { return action == Action.GRADE_ENTRY || action == Action.TEACHER_SUBMISSION; }
    private static Instant ts(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }

    private record RuleRow(String scope, String mode, Instant opensAt, Instant closesAt, String timezone) {}
    private record WindowState(boolean open, String state, Instant nextTransition, List<String> blockers) {}
    private record WindowSelection(Instant open, Instant close, String source, String timezone,
                                   String mode, String inheritedFrom) {
        private WindowSelection {
            source = source == null ? "NOT_CONFIGURED" : source;
            timezone = timezone == null || timezone.isBlank() ? "Africa/Douala" : timezone;
            mode = mode == null ? "INHERIT" : mode;
        }
    }
}
