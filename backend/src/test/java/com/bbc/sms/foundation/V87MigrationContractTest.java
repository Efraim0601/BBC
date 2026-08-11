package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V87MigrationContractTest {
    @Test
    void v87AddsStructuredBatchEvidenceWithoutRewritingArtifacts() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V87__bulletin_batch_diagnostics_and_artifacts.sql")) {
            assertThat(stream).as("V87 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS policy")
                .contains("ADD COLUMN IF NOT EXISTS scope_fingerprint")
                .contains("ADD COLUMN IF NOT EXISTS diagnostic_storage_key")
                .contains("ADD COLUMN IF NOT EXISTS result_code")
                .contains("ADD COLUMN IF NOT EXISTS result_details")
                .contains("ADD COLUMN IF NOT EXISTS snapshot_published_at")
                .contains("REPORT_NOT_PUBLISHED_LEGACY")
                .contains("WHERE result_code IS NULL")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }
}
