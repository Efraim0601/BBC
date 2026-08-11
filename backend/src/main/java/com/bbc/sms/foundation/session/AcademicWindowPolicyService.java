package com.bbc.sms.foundation.session;

import com.bbc.sms.academic.AcademicPeriodRules;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Compatibility adapter for existing academic mutation services.  Every
 * applicable action now evaluates the same governing trimester window.
 */
@Service
public class AcademicWindowPolicyService {
    public enum Action { GRADE_ENTRY, TEACHER_SUBMISSION, REVIEW, VALIDATION, PUBLICATION, CORRECTION }

    private final AcademicReportingPeriodRepository periods;
    private final TermManagementWindowService termWindows;
    private final Clock clock;

    public AcademicWindowPolicyService(AcademicReportingPeriodRepository periods,
                                       TermManagementWindowService termWindows,
                                       Clock clock) {
        this.periods = periods;
        this.termWindows = termWindows;
        this.clock = clock;
    }

    public void assertOpen(UUID periodId, Action action) {
        WindowView window = effective(null, periodId, action);
        if (window.open()) return;

        String milestones = String.join(", ", window.governedPeriodCodes());
        String repair = "Paramètres → Années & périodes → Accès par trimestre";
        if ("SCHEDULED".equals(window.state())) {
            throw ApiException.coded(HttpStatus.CONFLICT, "TRIMESTER_WINDOW_SCHEDULED",
                    "La gestion du trimestre " + window.governingTermCode() + " sera disponible à partir du "
                            + format(window.nextTransition(), window.timezone()) + " (" + window.timezone()
                            + "). Cette restriction concerne " + milestones + ". Ouvrez " + repair + ".");
        }
        if ("CLOSED".equals(window.state())) {
            throw ApiException.coded(HttpStatus.CONFLICT, "TRIMESTER_WINDOW_CLOSED",
                    "La fenêtre de gestion du trimestre " + window.governingTermCode() + " est fermée depuis le "
                            + format(window.closesAt(), window.timezone()) + " (" + window.timezone()
                            + "). Elle concerne " + milestones + ". Modifiez-la dans " + repair + ".");
        }
        if ("INVALID".equals(window.state())) {
            throw ApiException.coded(HttpStatus.CONFLICT, "TERM_WINDOW_INVALID",
                    "La limite d'accès du trimestre " + window.governingTermCode()
                            + " est invalide. Corrigez-la dans " + repair + ".");
        }
        throw ApiException.coded(HttpStatus.CONFLICT, "TRIMESTER_WINDOW_CLOSED",
                "La gestion du trimestre " + window.governingTermCode() + " n'est pas disponible. Consultez " + repair + ".");
    }

    public WindowView effective(UUID periodId, Action action) {
        return effective(null, periodId, action);
    }

    public WindowView effective(UUID sessionId, UUID periodId, Action action) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        if (sessionId != null && !sessionId.equals(period.getAcademicSessionId())) {
            throw ApiException.notFound("Période de résultat");
        }
        if (AcademicPeriodRules.isComputed(period) && isRawAction(action)) {
            return notApplicable(period, action);
        }

        AcademicTerm governingTerm = termWindows.resolveForPeriod(periodId);
        TermManagementWindowService.WindowState state = termWindows.state(governingTerm, clock.instant());
        String mode = governingTerm.isManagementWindowLimited() ? "LIMITED" : "UNRESTRICTED";
        List<String> governed = governedPeriodCodes(governingTerm);
        return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(),
                governingTerm.getManagementOpensAt(), governingTerm.getManagementClosesAt(), "TERM_MANAGEMENT_WINDOW",
                governingTerm.getManagementOpensAt(), governingTerm.getManagementClosesAt(), state.open(),
                "TERM_MANAGEMENT_WINDOW", state.state(), state.nextTransition(), List.of(),
                timezone(governingTerm.getTimezone()), mode, mode, null,
                governingTerm.getCode(), governingTerm.getLabel(), governed);
    }

    public record WindowView(UUID periodId, String periodCode, String periodLabel, String action,
                             Instant configuredOpensAt, Instant configuredClosesAt, String configuredSource,
                             Instant opensAt, Instant closesAt, boolean open, String source, String state,
                             Instant nextTransition, List<String> blockers, String timezone,
                             String configuredMode, String effectiveMode, String inheritedFrom,
                             String governingTermCode, String governingTermLabel,
                             List<String> governedPeriodCodes) {}

    private WindowView notApplicable(AcademicReportingPeriod period, Action action) {
        return new WindowView(period.getId(), period.getCode(), period.getLabel(), action.name(),
                null, null, "NOT_APPLICABLE", null, null, false, "NOT_APPLICABLE", "NOT_APPLICABLE",
                null, List.of("COMPUTED_RESULT_PERIOD"), timezone(period.getTimezone()),
                "NOT_APPLICABLE", "NOT_APPLICABLE", null, null, null, List.of());
    }

    private static List<String> governedPeriodCodes(AcademicTerm term) {
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

    private static boolean isRawAction(Action action) {
        return action == Action.GRADE_ENTRY || action == Action.TEACHER_SUBMISSION;
    }

    private static String timezone(String value) {
        return value == null || value.isBlank() ? "Africa/Douala" : value;
    }

    private static String format(Instant value, String timezone) {
        if (value == null) return "maintenant";
        return DateTimeFormatter.ofPattern("d MMMM uuuu à HH:mm", Locale.FRENCH)
                .withZone(ZoneId.of(timezone(timezone))).format(value);
    }
}
