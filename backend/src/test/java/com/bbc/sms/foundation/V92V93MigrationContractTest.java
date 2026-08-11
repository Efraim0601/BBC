package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V92V93MigrationContractTest {
    @Test
    void packetWorkflowMigrationIsAdditiveAndTenantScoped() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V92__bay34_packet_workflow_revisions.sql"));
        assertThat(sql).contains("IN_REVIEW", "revision_number", "packet_id", "affected_rows", "school_id");
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE");
    }

    @Test
    void commentPolicyMigrationHasAppendOnlyHistoryAndNoLegacyEdit() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V93__bay34_comment_policy_history.sql"));
        assertThat(sql).contains("subject_result_comment_history", "immutable", "appreciation_code", "school_id");
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE");
    }
}
