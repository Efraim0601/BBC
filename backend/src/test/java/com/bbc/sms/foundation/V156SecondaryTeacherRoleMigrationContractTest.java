package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V156SecondaryTeacherRoleMigrationContractTest {

    @Test
    void separatesPrimaryAndSecondaryTeacherAttendanceAuthorities() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V156__separate_secondary_teacher_role.sql")) {
            assertThat(stream).as("V156 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("'secondary_teacher','Enseignant secondaire','Secondary teacher'")
                .contains("role_code='teacher'")
                .contains("role_code='secondary_teacher'")
                .contains("'TITULAIRE_CLASSES'")
                .contains("'TIMETABLE_OCCURRENCES_ASSIGNED'")
                .contains("lower(e.level)='secondary'")
                .contains("UPDATE app_user")
                .contains("UPDATE app_user_role")
                .contains("permission_role_action");
    }
}
