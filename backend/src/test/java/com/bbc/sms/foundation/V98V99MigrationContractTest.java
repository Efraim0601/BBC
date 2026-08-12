package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V98V99MigrationContractTest {
    @Test
    void reservesOnlyTheBay67AdditiveMigrationsAndKeepsWorkflowHistoryAppendOnly() throws Exception {
        String v98 = migration("/db/migration/V98__bay67_attendance_evidence_workflows.sql");
        String v99 = migration("/db/migration/V99__bay67_immutable_attendance_snapshots.sql");

        assertThat(v98).contains("ADD COLUMN IF NOT EXISTS cancelled")
                .contains("LOCKED_BY_PUBLICATION")
                .contains("attendance_period_adjustment_history")
                .contains("student_period_conduct_history")
                .contains("reject_bay67_history_mutation")
                .contains("COUNCIL_OVERRIDE")
                .contains("conduct_recommendation_policy");
        assertThat(v99).contains("CREATE TABLE IF NOT EXISTS attendance_official_snapshot")
                .contains("source_roll_call_ids")
                .contains("source_snapshot_ids")
                .contains("supersedes_snapshot_id")
                .contains("reject_attendance_official_snapshot_mutation")
                .contains("official attendance snapshots are immutable");
    }

    private String migration(String resource) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
