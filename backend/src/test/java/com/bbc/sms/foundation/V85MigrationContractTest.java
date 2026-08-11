package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V85MigrationContractTest {
    @Test
    void v85DeclaresTheTrimesterGateAndTheSafeLegacyUnionBackfill() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V85__simplify_trimester_management_windows.sql")) {
            assertThat(stream).as("V85 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS management_window_limited")
                .contains("ADD COLUMN IF NOT EXISTS management_opens_at")
                .contains("ADD COLUMN IF NOT EXISTS management_closes_at")
                .contains("chk_academic_term_management_window")
                .contains("scope_type = 'SESSION'")
                .contains("scope_type = 'TERM'")
                .contains("scope_type = 'PERIOD'")
                .contains("mode = 'LIMITED'")
                .contains("upper(t.code) = 'T3'")
                .contains("min(c.opens_at)")
                .contains("max(c.closes_at)")
                .contains("bool_or(c.opens_at IS NULL)")
                .contains("bool_or(c.closes_at IS NULL")
                .contains("management_window_limited = CASE");

        String backfill = sql.substring(sql.indexOf("WITH candidates"));
        assertThat(backfill).doesNotContain("academic_window_override")
                .contains("p.period_type = 'ANNUAL_RESULT'")
                .contains("t.sequence_no = 3 OR upper(t.code) = 'T3'");
    }
}
