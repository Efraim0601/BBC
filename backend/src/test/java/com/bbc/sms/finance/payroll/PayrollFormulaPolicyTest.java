package com.bbc.sms.finance.payroll;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayrollFormulaPolicyTest {
    @Test
    void monthlyNoneKeepsIntegerSalaryAndDailyProratesInclusiveDates() {
        assertThat(PayrollFormulaPolicy.monthly(300_000, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 2, 1), null, "NONE").amountMinor()).isEqualTo(300_000);
        var prorated = PayrollFormulaPolicy.monthly(300_000, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 2, 15), null, "DAILY");
        assertThat(prorated.amountMinor()).isEqualTo(150_000);
        assertThat(prorated.quantity()).isEqualTo(14);
        assertThat(prorated.formula()).contains("14/28");
    }

    @Test
    void hourlyUsesExactIntegerMultiplication() {
        var result = PayrollFormulaPolicy.hourly(2_500, 37);
        assertThat(result.amountMinor()).isEqualTo(92_500);
        assertThat(result.quantity()).isEqualTo(37);
    }

    @Test
    void invalidSalaryRateAndHoursAreRejected() {
        assertThatThrownBy(() -> PayrollFormulaPolicy.monthly(0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, "NONE"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PayrollFormulaPolicy.hourly(1_000, 0)).isInstanceOf(RuntimeException.class);
    }
}
