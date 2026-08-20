package com.bbc.sms.finance.payroll;

import com.bbc.sms.platform.common.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure integer-XAF payroll formula policy; statutory/tax formulas are not embedded. */
public final class PayrollFormulaPolicy {
    private PayrollFormulaPolicy() {}

    public static Calculation monthly(long salaryMinor, LocalDate periodStart, LocalDate periodEnd,
                                      LocalDate hiredOn, LocalDate exitedOn, String prorationMode) {
        if (salaryMinor <= 0) throw ApiException.badRequest("Le salaire mensuel doit être supérieur à zéro.");
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (!"DAILY".equalsIgnoreCase(prorationMode)) {
            return new Calculation(salaryMinor, 0, "Salaire mensuel = " + salaryMinor + " XAF");
        }
        LocalDate from = hiredOn == null || hiredOn.isBefore(periodStart) ? periodStart : hiredOn;
        LocalDate to = exitedOn == null || exitedOn.isAfter(periodEnd) ? periodEnd : exitedOn;
        long eligibleDays = to.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, to) + 1;
        long amount = BigDecimal.valueOf(salaryMinor)
                .multiply(BigDecimal.valueOf(eligibleDays))
                .divide(BigDecimal.valueOf(totalDays), 0, RoundingMode.HALF_UP).longValueExact();
        return new Calculation(amount, eligibleDays, "Salaire mensuel " + salaryMinor + " × " + eligibleDays + "/" + totalDays + " jours, arrondi XAF");
    }

    public static Calculation hourly(long rateMinor, int hours) {
        if (rateMinor <= 0) throw ApiException.badRequest("Le taux horaire doit être supérieur à zéro.");
        if (hours <= 0) throw ApiException.badRequest("Les heures approuvées doivent être supérieures à zéro.");
        try {
            return new Calculation(Math.multiplyExact(rateMinor, hours), hours,
                    "Taux horaire " + rateMinor + " × " + hours + " heures");
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("Le montant horaire dépasse la capacité autorisée.");
        }
    }

    public record Calculation(long amountMinor, long quantity, String formula) {}
}
