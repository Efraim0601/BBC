package com.bbc.sms.guardian;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianAccountServiceTenantContractTest {
    @Test
    void publicInvitationAcceptanceBindsTokenSchoolForAuditAndRestoresContext() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/guardian/GuardianAccountService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("UUID previousTenant = TenantContext.isSet() ? TenantContext.get() : null;")
                .contains("TenantContext.set(g.schoolId());")
                .contains("audit.record(\"GUARDIAN_INVITE_ACCEPTED\"")
                .contains("if (previousTenant == null) TenantContext.clear();")
                .contains("else TenantContext.set(previousTenant);");
    }
}
