package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V176OptionalAttendanceReasonsContractTest {
    @Test
    void makesLegacyAttendanceReasonPolicyOptional() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V176__optional_attendance_absence_reasons.sql")) {
            assertThat(stream).as("V176 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("UPDATE attendance_policy")
                .contains("require_absence_reason = false")
                .contains("Legacy compatibility flag")
                .contains("reasons are optional");
    }
}
