package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V136PrincipalCouncilInputViewMigrationContractTest {
    @Test
    void keepsDirectionCouncilQueueReadOnlyAcrossPolicyLayers() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V136__principal_council_input_view_authority.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("'principal', 'ACADEMIC_COUNCIL_INPUT_VIEW'")
                .contains("'principal_oversight','ACADEMIC_COUNCIL_INPUT_VIEW','ALLOW','SCHOOL_ALL'")
                .contains("'principal_legacy_compat', 'ACADEMIC_COUNCIL_INPUT_VIEW'")
                .doesNotContain("ACADEMIC_COUNCIL_INPUT_EDIT");
    }
}
