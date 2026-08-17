package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V142ParentMessageAckMigrationContractTest {
    @Test
    void bindsAcknowledgementToTheLiveParentRoleWithoutStaffAuthorities() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V142__parent_message_ack_role_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("permission_role_action")
                .contains("'parent', 'PARENT_MESSAGES_ACK', 'ALLOW', 'LINKED_CHILDREN'")
                .contains("code='parent'")
                .contains("code='PARENT_MESSAGES_ACK'")
                .doesNotContain("parent_portal")
                .doesNotContain("SCHOOL_ALL")
                .doesNotContain("finance")
                .doesNotContain("staff");
    }
}
