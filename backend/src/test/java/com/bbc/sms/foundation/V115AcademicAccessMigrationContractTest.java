package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V115AcademicAccessMigrationContractTest {
    @Test
    void declaresScopedAuditedDelegationsWithoutASecondAssignmentRegistry() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V115__academic_teacher_access_control.sql")) {
            assertThat(stream).as("V115 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS academic_access_delegation")
                .contains("academic_session_id")
                .contains("class_id")
                .contains("subject_code")
                .contains("capability_code")
                .contains("effective_from")
                .contains("effective_to")
                .contains("requested_by")
                .contains("approved_by")
                .contains("trg_academic_access_delegation_overlap")
                .contains("ACADEMIC_ACCESS_DELEGATE")
                .doesNotContain("CREATE TABLE IF NOT EXISTS academic_class_subject_teacher")
                .doesNotContain("CREATE TABLE IF NOT EXISTS class_teacher_assignment");
    }
}
