package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V135PrincipalAcademicGradeViewMigrationContractTest {
    @Test
    void keepsDirectionGradePacketReadAuthorityAlignedAcrossPolicyLayers() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V135__principal_academic_grade_view_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("'principal', 'ACADEMIC_SUBJECT_GRADE_VIEW'")
                .contains("'principal_oversight','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','SCHOOL_ALL'")
                .contains("'principal_legacy_compat', 'ACADEMIC_SUBJECT_GRADE_VIEW'")
                .contains("'ALLOW', 'SCHOOL_ALL'")
                .doesNotContain("ACADEMIC_SUBJECT_GRADE_EDIT")
                .doesNotContain("GRADE_SUBMIT");
    }
}
