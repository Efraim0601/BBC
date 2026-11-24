package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V192DirectSaveGradePacketsContractTest {
    @Test
    void promotesPendingPacketsAndLimitsAccountantsToCorrectionReopenAuthority() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V192__direct_save_grade_packets_and_accountant_reopen.sql")) {
            assertThat(stream).as("V192 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("from_status, to_status")
                .contains("'SUBMITTED', 'ACCEPTED'")
                .contains("WHERE status = 'SUBMITTED'")
                .contains("workflow_status = 'ACCEPTED'")
                .contains("'accountant', 'ACADEMIC_GRADE_PACKET_REVIEW'")
                .contains("'ALLOW', 'SCHOOL_ALL'")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT")
                .doesNotContain("ACADEMIC_REPORT_CARD_VALIDATE")
                .doesNotContain("ACADEMIC_REPORT_CARD_PUBLISH");
    }
}
