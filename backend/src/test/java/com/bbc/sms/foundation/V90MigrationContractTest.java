package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V90MigrationContractTest {
    @Test
    void v90BackfillsTraceableAssessmentAndGradeEvidence() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V90__backfill_canonical_curriculum_evidence.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("UPDATE academic_assessment")
                .contains("UPDATE academic_grade")
                .contains("ACADEMIC_GRADE_CANONICAL_MAPPING_REQUIRED")
                .contains("legacy_grade_migration_exception")
                .contains("ON CONFLICT (school_id,source_table,source_id) DO NOTHING");
    }
}
