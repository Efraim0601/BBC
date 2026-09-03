package com.bbc.sms.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the pre-session bilingual-cohort access regression found in live QA. */
class TeacherScopeEffectiveDateContractTest {

    @Test
    void studentScopeUsesTheBoundedCurrentSessionDateInsteadOfWallClockSql() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/platform/security/TeacherScopeService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("LocalDate effectiveDate = currentEffectiveDate();")
                .contains("e.enrolled_on<=?")
                .contains("e.exited_on IS NULL OR e.exited_on>=?")
                .doesNotContain("e.enrolled_on<=CURRENT_DATE")
                .doesNotContain("e.exited_on>=CURRENT_DATE");
    }
}
