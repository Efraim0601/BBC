package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V91MigrationContractTest {
    @Test
    void compatibilityInsertResolutionIsAdditiveAndKeepsPublishedProtection() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V91__compatibility_curriculum_insert_resolution.sql"));
        assertThat(sql).contains("CREATE OR REPLACE FUNCTION reject_published_curriculum_mutation")
                .contains("academic_curriculum_version")
                .contains("state='PUBLISHED'")
                .contains("Published curriculum versions are immutable")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }
}
