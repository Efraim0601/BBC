package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V172TeacherWorkflowAuthorityContractTest {

    @Test
    void grantsHomeroomReportingAndLevelSpecificCoursebookAuthority() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V172__teacher_titulaire_reporting_and_coursebook.sql")) {
            assertThat(stream).as("V172 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("'teacher','ACADEMIC_REPORT_CARD_VALIDATE','TITULAIRE_CLASSES'")
                .contains("'secondary_teacher','ACADEMIC_REPORT_CARD_VALIDATE','TITULAIRE_CLASSES'")
                .contains("'teacher','DOCUMENT_GENERATE','TITULAIRE_CLASSES'")
                .contains("'secondary_teacher','DOCUMENT_GENERATE','TITULAIRE_CLASSES'")
                .contains("'teacher','COURSEBOOK_MANAGE','TITULAIRE_CLASSES'")
                .contains("'secondary_teacher','COURSEBOOK_MANAGE','ASSIGNED_CLASS_SUBJECTS'")
                .doesNotContain("ACADEMIC_REPORT_CARD_PUBLISH");
    }
}
