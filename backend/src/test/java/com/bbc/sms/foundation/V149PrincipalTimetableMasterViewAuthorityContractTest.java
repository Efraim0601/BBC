package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V149PrincipalTimetableMasterViewAuthorityContractTest {
    @Test
    void grantsDirectionOnlyPublishedMasterTimetableRead() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V149__principal_timetable_master_view_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_action_grant")
                .contains("permission_role_action")
                .contains("permission_role_template_rule")
                .contains("'principal'")
                .contains("'principal_legacy_compat'")
                .contains("'principal_oversight'")
                .contains("'TIMETABLE_MASTER_VIEW'")
                .contains("'SCHOOL_ALL'")
                .doesNotContain("TIMETABLE_DRAFT")
                .doesNotContain("TIMETABLE_PUBLISH")
                .doesNotContain("TIMETABLE_REOPEN")
                .doesNotContain("TIMETABLE_ARCHIVE")
                .doesNotContain("TIMETABLE_RESOURCE_VIEW")
                .doesNotContain("TIMETABLE_EXPORT");
    }
}
