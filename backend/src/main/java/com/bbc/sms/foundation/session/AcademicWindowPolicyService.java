package com.bbc.sms.foundation.session;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AcademicWindowPolicyService {
    public enum Action { GRADE_ENTRY, REVIEW, VALIDATION, PUBLICATION, CORRECTION }
    private final AcademicReportingPeriodRepository periods;
    private final AcademicTermRepository terms;
    private final AcademicSessionRepository sessions;

    public AcademicWindowPolicyService(AcademicReportingPeriodRepository periods,
                                        AcademicTermRepository terms,
                                        AcademicSessionRepository sessions) {
        this.periods = periods;
        this.terms = terms;
        this.sessions = sessions;
    }

    public void assertOpen(UUID periodId, Action action) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        Instant[] window = effectiveWindow(period, action);
        if (window[0] == null || window[1] == null) {
            throw ApiException.conflict("La fenêtre " + actionLabel(action) + " n'est pas configurée pour " + period.getLabel()
                    + ". Configurez-la dans Paramètres → Années et périodes académiques.");
        }
        Instant now = Instant.now();
        if (now.isBefore(window[0]) || now.isAfter(window[1])) {
            throw ApiException.conflict("La fenêtre " + actionLabel(action) + " est fermée pour " + period.getLabel()
                    + " (du " + window[0] + " au " + window[1] + ").");
        }
    }

    public WindowView effective(UUID periodId, Action action) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        Instant[] window = effectiveWindow(period, action);
        Instant now = Instant.now();
        return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(), window[0], window[1],
                window[0] != null && window[1] != null && !now.isBefore(window[0]) && !now.isAfter(window[1]),
                window[0] == null || window[1] == null ? "NOT_CONFIGURED" : "CONFIGURED");
    }

    public record WindowView(UUID periodId, String periodCode, String periodLabel, String action,
                             Instant opensAt, Instant closesAt, boolean open, String source) {}

    private Instant[] effectiveWindow(AcademicReportingPeriod period, Action action) {
        Instant ownOpen = open(period, action), ownClose = close(period, action);
        if (ownOpen != null || ownClose != null) return new Instant[]{ownOpen, ownClose};
        if (period.getAcademicTermId() != null) {
            AcademicTerm term = terms.findByIdAndSchoolId(period.getAcademicTermId(), TenantContext.get()).orElse(null);
            if (term != null) {
                Instant[] inherited = termWindow(term, action);
                if (inherited[0] != null || inherited[1] != null) return inherited;
            }
        }
        AcademicSession session = sessions.findByIdAndSchoolId(period.getAcademicSessionId(), TenantContext.get()).orElse(null);
        if (session == null) return new Instant[]{null, null};
        return sessionWindow(session, action);
    }

    private static Instant open(AcademicReportingPeriod p, Action a) {
        return switch (a) { case GRADE_ENTRY -> p.getGradeEntryOpensAt(); case REVIEW -> p.getReviewOpensAt(); case VALIDATION -> p.getValidationOpensAt(); case PUBLICATION -> p.getBulletinPublishOpensAt(); case CORRECTION -> p.getCorrectionOpensAt(); };
    }
    private static Instant close(AcademicReportingPeriod p, Action a) {
        return switch (a) { case GRADE_ENTRY -> p.getGradeEntryClosesAt(); case REVIEW -> p.getReviewClosesAt(); case VALIDATION -> p.getValidationClosesAt(); case PUBLICATION -> p.getBulletinPublishClosesAt(); case CORRECTION -> p.getCorrectionClosesAt(); };
    }
    private static Instant[] termWindow(AcademicTerm t, Action a) { return switch (a) { case GRADE_ENTRY -> new Instant[]{t.getGradeEntryOpensAt(), t.getGradeEntryClosesAt()}; case PUBLICATION, VALIDATION -> new Instant[]{t.getBulletinPublishOpensAt(), t.getBulletinPublishClosesAt()}; default -> new Instant[]{null, null}; }; }
    private static Instant[] sessionWindow(AcademicSession s, Action a) { return switch (a) { case GRADE_ENTRY -> new Instant[]{s.getGradeEntryOpensAt(), s.getGradeEntryClosesAt()}; case PUBLICATION, VALIDATION -> new Instant[]{s.getBulletinPublishOpensAt(), s.getBulletinPublishClosesAt()}; default -> new Instant[]{null, null}; }; }
    private static String actionLabel(Action action) { return switch (action) { case GRADE_ENTRY -> "saisie des notes"; case REVIEW -> "revue"; case VALIDATION -> "validation"; case PUBLICATION -> "publication"; case CORRECTION -> "correction"; }; }
}
