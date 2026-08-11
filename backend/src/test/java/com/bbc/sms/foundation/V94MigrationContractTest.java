package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V94MigrationContractTest {
    @Test
    void addsTenantScopedAuthoritativeSnapshotMetadataAndPhotoDimensions() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V94__authoritative_bulletin_snapshot_contract.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("snapshot_contract_version")
                    .contains("canonical_snapshot_hash")
                    .contains("generation_actor_id")
                    .contains("width_px")
                    .contains("height_px")
                    .contains("idx_bulletin_version_contract_scope")
                    .contains("school_id");
        }
    }
}
