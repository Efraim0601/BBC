package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import static com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class AccountingFoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bbc_accounting_test").withUsername("bbc").withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerPostingService ledger;
    @Autowired DocumentSequenceService sequences;

    private UUID schoolId;
    private UUID periodId;
    private UUID debitAccountId;
    private UUID creditAccountId;

    @BeforeEach
    void tenantAndFoundation() {
        schoolId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        debitAccountId = UUID.randomUUID();
        creditAccountId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId,
                "A" + schoolId.toString().substring(0, 8), "Accounting test school");
        TenantContext.set(schoolId);
        jdbc.update("""
                INSERT INTO chart_of_account(id,school_id,code,name_fr,name_en,account_type,normal_side,currency)
                VALUES (?,?,'1100','Caisse','Cash','ASSET','DEBIT','XAF'),
                       (?,?,'4000','Produits','Revenue','REVENUE','CREDIT','XAF')
                """, debitAccountId, schoolId, creditAccountId, schoolId);
        jdbc.update("""
                INSERT INTO accounting_period(id,school_id,code,name_fr,name_en,start_date,end_date,status)
                VALUES (?,?,'2026-01','Janvier 2026','January 2026','2026-01-01','2026-01-31','OPEN')
                """, periodId, schoolId);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void postsBalancedJournalOnceAndKeepsTrialBalanceEqualOnReplay() {
        assertThat(sequences.allocateJournalNumber("2026-01")).isEqualTo("JRN/2026-01/000001");
        var draft = ledger.createDraftInternal(journal("EVENT-1", 1000, null));
        var posted = ledger.postNowInternal(draft.id());
        var replay = ledger.postNowInternal(draft.id());

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.number()).isEqualTo("JRN/2026-01/000002");
        assertThat(replay.id()).isEqualTo(posted.id());
        assertThat(replay.status()).isEqualTo("POSTED");

        var trial = ledger.trialBalanceInternal(LocalDate.of(2026, 1, 31), false);
        assertThat(trial.balanced()).isTrue();
        assertThat(trial.totalDebitMinor()).isEqualTo(1000);
        assertThat(trial.totalCreditMinor()).isEqualTo(1000);
    }

    @Test
    void rejectsDuplicateSourceEventAndPostingToClosedPeriod() {
        ledger.createDraftInternal(journal("EVENT-2", 500, null));
        assertThatThrownBy(() -> ledger.createDraftInternal(journal("EVENT-2", 500, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("SOURCE_EVENT_DUPLICATE"));

        var closedDraft = ledger.createDraftInternal(journal(null, 200, null));
        jdbc.update("UPDATE accounting_period SET status='CLOSED' WHERE id=? AND school_id=?", periodId, schoolId);
        assertThatThrownBy(() -> ledger.postNowInternal(closedDraft.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("POSTING_PERIOD_CLOSED"));
    }

    @Test
    void postedJournalCannotBeEditedAndOtherTenantCannotReadIt() {
        var posted = ledger.postNowInternal(ledger.createDraftInternal(journal(null, 300, null)).id());
        JournalUpsert edit = journal(null, 301, posted.version());

        assertThatThrownBy(() -> ledger.updateDraftInternal(posted.id(), edit))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.update("UPDATE journal_entry SET description='tampered' WHERE id=?", posted.id()))
                .hasMessageContaining("immutable");

        UUID otherSchool = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", otherSchool,
                "B" + otherSchool.toString().substring(0, 8), "Other school");
        TenantContext.set(otherSchool);
        assertThatThrownBy(() -> ledger.detailInternal(posted.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("NOT_FOUND"));
    }

    private JournalUpsert journal(String sourceEventKey, long amount, Long version) {
        return new JournalUpsert(LocalDate.of(2026, 1, 15), "Accounting test", "XAF", periodId,
                "MANUAL", sourceEventKey == null ? null : "SRC-" + sourceEventKey, sourceEventKey,
                List.of(new JournalLineInput(debitAccountId, amount, 0, null, null, null, null, null, "Debit"),
                        new JournalLineInput(creditAccountId, 0, amount, null, null, null, null, null, "Credit")), version);
    }
}
