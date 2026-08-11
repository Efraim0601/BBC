package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V95MigrationContractTest {
    @Test
    void createsAppendOnlyTenantScopedSnapshotSourceIndex() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V95__bulletin_snapshot_source_versions.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS bulletin_snapshot_source_version")
                    .contains("bulletin_version_id")
                    .contains("source_hash")
                    .contains("ON CONFLICT DO NOTHING")
                    .contains("trg_bulletin_snapshot_source_immutable")
                    .contains("school_id");
        }
    }
}
