package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the V2 catalog scope used by the school-wide finance document register. */
class V138FinanceDocumentViewScopeContractTest {
    @Test
    void financeDocumentRegisterScopeIsAlignedWithV137SchoolRules() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V138__finance_document_view_school_scope.sql"));
        assertTrue(migration.contains("code = 'FINANCE_DOCUMENT_VIEW'"));
        assertTrue(migration.contains("scope_type = 'SCHOOL'"));
        assertTrue(migration.contains("scope_type = 'STUDENT'"));
    }
}
