package com.bbc.sms.finance.charges;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeAccountEffectiveDateContractTest {
    @Test
    void accountReadsUseEnrollmentStartForFutureSessionResources() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/finance/charges/ChargeQueryService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(enrollmentId, schoolId)")
                .contains("financePolicy.requireEnrollment(\"CHARGE_PREVIEW\", enrollmentId, enrollment.getEnrolledOn())")
                .doesNotContain("financePolicy.requireEnrollment(\"CHARGE_PREVIEW\", enrollmentId, LocalDate.now())");
    }
}
