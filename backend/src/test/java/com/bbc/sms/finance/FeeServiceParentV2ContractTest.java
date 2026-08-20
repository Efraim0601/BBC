package com.bbc.sms.finance;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FeeServiceParentV2ContractTest {
    @Test
    void parentStatementsPreferV2ChargesAndNetRefundsWithLegacyFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/finance/FeeService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("if (hasV2Charges(schoolId, studentId)) return v2StatementForParent(schoolId, studentId);")
                .contains("FROM student_charge")
                .contains("FROM charge_installment")
                .contains("FROM finance_payment p")
                .contains("LEFT JOIN refund_transaction rt")
                .contains("p.status NOT IN ('REVERSED','VOID')")
                .contains("return statementInternal(schoolId, studentId);");
    }
}
