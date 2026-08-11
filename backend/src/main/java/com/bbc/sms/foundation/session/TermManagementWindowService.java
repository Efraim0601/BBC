package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.TermManagementWindowUpsert;
import static com.bbc.sms.foundation.session.SessionDtos.TermManagementWindowView;

/**
 * V85's authoritative configuration and mapping service.  Legacy action-window
 * tables are intentionally absent from this service: they remain history only.
 */
@Service
public class TermManagementWindowService {
    private final AcademicSessionRepository sessions;
    private final AcademicTermRepository terms;
    private final AcademicReportingPeriodRepository periods;
    private final AuditService audit;
    private final Clock clock;

    public TermManagementWindowService(AcademicSessionRepository sessions,
                                       AcademicTermRepository terms,
                                       AcademicReportingPeriodRepository periods,
                                       AuditService audit,
                                       Clock clock) {
        this.sessions = sessions;
        this.terms = terms;
        this.periods = periods;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TermManagementWindowView> list(UUID sessionId) {
        requireSession(sessionId);
        UUID schoolId = TenantContext.get();
        return terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(schoolId, sessionId)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public TermManagementWindowView getForTerm(UUID termId) {
        return view(requireTerm(termId));
    }

    @Transactional
    public TermManagementWindowView update(UUID sessionId, UUID termId, TermManagementWindowUpsert input) {
        requireSession(sessionId);
        AcademicTerm term = requireTerm(termId);
        if (!sessionId.equals(term.getAcademicSessionId())) {
            throw ApiException.notFound("Trimestre");
        }
        var session = sessions.findByIdAndSchoolId(sessionId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        if (List.of("CLOSED", "ARCHIVED").contains(session.getStatus())) {
            throw ApiException.conflict("Cette session est en lecture seule");
        }

        if (input == null) {
            throw ApiException.badRequest("La configuration du trimestre est obligatoire.");
        }
        if (input.version() == null) {
            throw ApiException.field(HttpStatus.CONFLICT, "STALE_VERSION",
                    "Rechargez le trimestre avant d'enregistrer sa limite d'accès.",
                    "version", "Rechargez la valeur actuelle avant de modifier ce trimestre.");
        }
        if (input.version() != term.getVersion()) {
            throw ApiException.staleVersion(
                    "La configuration du trimestre a changé depuis son chargement. Rechargez-la avant de continuer.",
                    term.getVersion(), input.version(), "version");
        }
        validate(input);

        TermManagementWindowView before = view(term);
        term.setManagementWindowLimited(input.limited());
        term.setManagementOpensAt(input.limited() ? input.opensAt() : null);
        term.setManagementClosesAt(input.limited() ? input.closesAt() : null);
        try {
            term = terms.saveAndFlush(term);
        } catch (OptimisticLockingFailureException | StaleObjectStateException ex) {
            throw ApiException.staleVersion(
                    "La configuration du trimestre a changé depuis son chargement. Rechargez-la avant de continuer.",
                    term.getVersion() + 1, input.version(), "version");
        }
        TermManagementWindowView after = view(term);
        audit.record("TERM_MANAGEMENT_WINDOW_UPDATED", "AcademicTerm", term.getId().toString(),
                auditPayload(before), auditPayload(after), null);
        return after;
    }

    @Transactional(readOnly = true)
    public AcademicTerm resolveForPeriod(UUID periodId) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        if (period.getAcademicTermId() != null) {
            AcademicTerm term = terms.findByIdAndSchoolId(period.getAcademicTermId(), TenantContext.get()).orElse(null);
            if (term != null && period.getAcademicSessionId().equals(term.getAcademicSessionId())) return term;
            throw mappingMissing(period, "le trimestre lié à " + period.getCode() + " n'appartient pas à sa session");
        }

        List<AcademicTerm> sessionTerms = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                TenantContext.get(), period.getAcademicSessionId());
        List<AcademicTerm> bySequence = sessionTerms.stream().filter(t -> t.getSequenceNo() == 3).toList();
        if (bySequence.size() == 1) return bySequence.getFirst();
        List<AcademicTerm> byCode = sessionTerms.stream().filter(t -> "T3".equalsIgnoreCase(t.getCode())).toList();
        if (byCode.size() == 1) return byCode.getFirst();
        throw mappingMissing(period, "le résultat annuel ne peut pas identifier un trimestre T3 unique");
    }

    @Transactional(readOnly = true)
    public TermManagementWindowView viewForPeriod(UUID periodId) {
        return view(resolveForPeriod(periodId));
    }

    public WindowState state(AcademicTerm term, Instant now) {
        if (!term.isManagementWindowLimited()) return new WindowState(true, "OPEN", null);
        Instant opensAt = term.getManagementOpensAt();
        Instant closesAt = term.getManagementClosesAt();
        // A missing opening and closing date is the explicit unrestricted mode,
        // including legacy rows whose limited flag was left on.
        if (opensAt == null && closesAt == null) return new WindowState(true, "OPEN", null);
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt)) {
            return new WindowState(false, "INVALID", null);
        }
        if (opensAt != null && now.isBefore(opensAt)) return new WindowState(false, "SCHEDULED", opensAt);
        if (closesAt != null && now.isAfter(closesAt)) return new WindowState(false, "CLOSED", null);
        return new WindowState(true, "OPEN", closesAt);
    }

    public Instant now() {
        return clock.instant();
    }

    public record WindowState(boolean open, String state, Instant nextTransition) {}

    private TermManagementWindowView view(AcademicTerm term) {
        WindowState state = state(term, clock.instant());
        return new TermManagementWindowView(term.getAcademicSessionId(), term.getId(), term.getCode(), term.getLabel(),
                term.getSequenceNo(), term.getStartDate(), term.getEndDate(), term.isManagementWindowLimited(),
                term.getManagementOpensAt(), term.getManagementClosesAt(), timezone(term.getTimezone()),
                governedPeriodCodes(term), state.state(), state.nextTransition(), term.getVersion());
    }

    private Map<String, Object> auditPayload(TermManagementWindowView value) {
        return Map.of(
                "limited", value.limited(),
                "opensAt", value.opensAt() == null ? "" : value.opensAt(),
                "closesAt", value.closesAt() == null ? "" : value.closesAt(),
                "timezone", value.timezone(),
                "academicSessionId", value.academicSessionId(),
                "termId", value.termId(),
                "governedPeriodCodes", value.governedPeriodCodes());
    }

    private List<String> governedPeriodCodes(AcademicTerm term) {
        return switch (term.getSequenceNo()) {
            case 1 -> List.of("S1", "S2", "T1_RESULT");
            case 2 -> List.of("S3", "S4", "T2_RESULT");
            case 3 -> List.of("S5", "S6", "T3_RESULT", "ANNUAL");
            default -> {
                String code = term.getCode() == null ? "" : term.getCode().trim().toUpperCase(Locale.ROOT);
                yield switch (code) {
                    case "T1" -> List.of("S1", "S2", "T1_RESULT");
                    case "T2" -> List.of("S3", "S4", "T2_RESULT");
                    case "T3" -> List.of("S5", "S6", "T3_RESULT", "ANNUAL");
                    default -> List.of();
                };
            }
        };
    }

    private void validate(TermManagementWindowUpsert input) {
        if (!input.limited()) {
            if (input.opensAt() != null || input.closesAt() != null) {
                throw ApiException.field(HttpStatus.BAD_REQUEST, "TERM_WINDOW_DATES_NOT_ALLOWED",
                        "Désactivez la limite ou retirez les dates avant d'enregistrer.",
                        "limited", "Une fenêtre non limitée ne peut pas contenir de dates.");
            }
            return;
        }
        if (input.opensAt() != null && input.closesAt() != null
                && !input.closesAt().isAfter(input.opensAt())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "TERM_WINDOW_RANGE_INVALID",
                    "La fermeture doit être postérieure à l'ouverture.", "closesAt",
                    "La fermeture doit être postérieure à l'ouverture.");
        }
    }

    private AcademicSession requireSession(UUID sessionId) {
        return sessions.findByIdAndSchoolId(sessionId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Session académique"));
    }

    private AcademicTerm requireTerm(UUID termId) {
        return terms.findByIdAndSchoolId(termId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Trimestre"));
    }

    private ApiException mappingMissing(AcademicReportingPeriod period, String detail) {
        return ApiException.coded(HttpStatus.CONFLICT, "TERM_MAPPING_MISSING",
                "Le jalon " + period.getCode() + " ne peut pas être relié à un trimestre valide (" + detail
                        + "). Ouvrez Paramètres → Années & périodes → Assistant de configuration académique.");
    }

    private static String timezone(String value) {
        return value == null || value.isBlank() ? "Africa/Douala" : value;
    }
}
