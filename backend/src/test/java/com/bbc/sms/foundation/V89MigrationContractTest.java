package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V89MigrationContractTest {
    @Test
    void v89IntroducesImmutableVersionsAndExplicitLegacyExceptions() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V89__immutable_curriculum_versions_and_row_safe_grades.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("CREATE TABLE academic_curriculum_version")
                .contains("DRAFT','PUBLISHED','SUPERSEDED")
                .contains("curriculum_version_id")
                .contains("canonical_content_hash")
                .contains("trg_curriculum_subject_immutability")
                .contains("CREATE TABLE legacy_grade_migration_exception")
                .contains("AMBIGUOUS_MAPPING")
                .contains("NO_UNAMBIGUOUS_MAPPING")
                .contains("academic_grade_save_request")
                .contains("academic_grade_save_result")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }
}
