package com.bbc.sms.finance.charges;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeAdjustmentEffectiveDateContractTest {
    @Test
    void waiverApprovalUsesTheAdjustmentEffectiveDateForPolicyScope() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/finance/charges/ChargeAdjustmentService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("requireCharge(\"FEE_WAIVE_APPROVE\", adjustment.getChargeId(), adjustment.getEffectiveDate())")
                .doesNotContain("requireCharge(\"FEE_WAIVE_APPROVE\", adjustment.getChargeId(), LocalDate.now())");
    }
}
