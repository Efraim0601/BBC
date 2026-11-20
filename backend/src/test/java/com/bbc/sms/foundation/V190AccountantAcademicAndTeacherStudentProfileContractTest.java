package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V190AccountantAcademicAndTeacherStudentProfileContractTest {
    @Test
    void grantsAccountantAcademicReadsWithoutGradeEntryAndTeachersOnlyHomeroomProfileEdits() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V190__accountant_academic_read_and_teacher_profile_updates.sql")) {
            assertThat(stream).as("V190 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("'accountant', 'academic', 'read'")
                .contains("'ACADEMIC_CLASS_RESULTS_VIEW'")
                .contains("'ACADEMIC_REPORT_CARD_VIEW'")
                .contains("'ACADEMIC_COUNCIL_INPUT_VIEW'")
                .contains("'ACADEMIC_STRUCTURE_VIEW'")
                .doesNotContain("'ACADEMIC_SUBJECT_GRADE_EDIT'")
                .doesNotContain("'ACADEMIC_SUBJECT_GRADE_VIEW'")
                .doesNotContain("'GRADE_SUBMIT'")
                .contains("'teacher',           'Primary and Kindergarten titulaire updates own student profiles'")
                .contains("'secondary_teacher', 'Secondary titulaire updates own student profiles'")
                .contains("'STUDENT_PROFILE_EDIT', 'ALLOW', 'TITULAIRE_CLASSES'")
                .doesNotContain("STUDENT_PROFILE_DEACTIVATE")
                .doesNotContain("ENROLLMENT_TRANSFER");
    }
}
