package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V153AdministratorPrincipalSeparationContractTest {

    @Test
    void migrationSeparatesBootstrapAdminAndMakesPrincipalScopeExplicit() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V153__separate_administrator_and_scope_principal.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("VALUES ('administrator', 'Administrateur', 'Administrator', true)")
                .contains("SET role_code='administrator', parcours_scope_mode='GLOBAL'")
                .contains("SET parcours_scope_mode='EXPLICIT'")
                .contains("CREATE TABLE IF NOT EXISTS employee_management_level")
                .contains("'PERMISSION_VIEW', 'PERMISSION_MANAGE', 'ROLE_MANAGE'")
                .contains("'Access Control is reserved for administrators'");
    }
}
