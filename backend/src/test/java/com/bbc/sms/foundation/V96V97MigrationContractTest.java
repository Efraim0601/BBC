package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V96V97MigrationContractTest {
    @Test
    void provisionsStandardFamiliesAtRuntimeWithoutMigrationTimeSchoolSeeding() throws Exception {
        String sql = read("/db/migration/V96__bay36_standard_report_card_template_provisioning.sql");
        assertThat(sql).contains("standard_key", "effective_from", "effective_to",
                        "uq_document_template_standard_version", "idx_document_template_effective_selection")
                .doesNotContain("INSERT INTO document_template", "FROM school");
    }

    @Test
    void freezesRenderEvidenceAndPublishedDesignContent() throws Exception {
        String sql = read("/db/migration/V97__bay36_frozen_render_evidence.sql");
        assertThat(sql).contains("template_hash", "template_config_json", "branding_version",
                        "resolved_asset_hash", "snapshot_hash", "idx_generated_document_render_evidence",
                        "reject_published_document_template_mutation",
                        "reject_published_document_branding_mutation",
                        "enforce_bulletin_snapshot_immutable", "RETURN OLD");
    }

    private String read(String resource) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
