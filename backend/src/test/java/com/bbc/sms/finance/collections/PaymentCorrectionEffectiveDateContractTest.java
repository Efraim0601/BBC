package com.bbc.sms.finance.collections;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCorrectionEffectiveDateContractTest {
    @Test
    void correctionsUsePersistedPaymentDateForAcademicAndPostingScope() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/finance/collections/PaymentCorrectionService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("LocalDate date = payment.getPaymentDate()")
                .contains("requirePayment(\"REFUND_APPROVE\", refund.getPaymentId(), payment.getPaymentDate())")
                .doesNotContain("LocalDate date = LocalDate.now()")
                .doesNotContain("requirePayment(\"REFUND_APPROVE\", refund.getPaymentId(), LocalDate.now())");
    }
}
