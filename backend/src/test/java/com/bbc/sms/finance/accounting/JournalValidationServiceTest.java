package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JournalValidationServiceTest {
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID DEBIT_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CREDIT_ACCOUNT_ID = UUID.randomUUID();

    @Mock AccountService accounts;
    @Mock AccountingPeriodService periods;
    @Mock JdbcTemplate jdbc;

    private JournalValidationService validation;
    private AccountingPeriod period;

    @BeforeEach
    void setUp() {
        TenantContext.set(SCHOOL_ID);
        validation = new JournalValidationService(accounts, periods, jdbc);
        period = new AccountingPeriod();
        period.setId(PERIOD_ID);
        period.setCode("2026-01");
        period.setStartDate(LocalDate.of(2026, 1, 1));
        period.setEndDate(LocalDate.of(2026, 1, 31));
        period.setStatus("OPEN");
        when(periods.require(PERIOD_ID)).thenReturn(period);
        lenient().when(accounts.require(DEBIT_ACCOUNT_ID)).thenReturn(account(DEBIT_ACCOUNT_ID, "1100", "DEBIT"));
        lenient().when(accounts.require(CREDIT_ACCOUNT_ID)).thenReturn(account(CREDIT_ACCOUNT_ID, "4000", "CREDIT"));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void acceptsBalancedIntegerXafJournal() {
        JournalValidationService.Totals totals = validation.validateForPost(entry(), List.of(
                line(1, DEBIT_ACCOUNT_ID, 1000, 0), line(2, CREDIT_ACCOUNT_ID, 0, 1000)));

        assertThat(totals.debitMinor()).isEqualTo(1000);
        assertThat(totals.creditMinor()).isEqualTo(1000);
        assertThat(totals.currency()).isEqualTo("XAF");
    }

    @Test
    void rejectsImbalancedJournalWithStructuredCode() {
        assertThatThrownBy(() -> validation.validateForPost(entry(), List.of(
                line(1, DEBIT_ACCOUNT_ID, 1000, 0), line(2, CREDIT_ACCOUNT_ID, 0, 900))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("JOURNAL_NOT_BALANCED"));
    }

    @Test
    void rejectsLineWithNeitherOrBothSides() {
        assertThatThrownBy(() -> validation.validateForPost(entry(), List.of(
                line(1, DEBIT_ACCOUNT_ID, 1000, 1000), line(2, CREDIT_ACCOUNT_ID, 0, 1000))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("JOURNAL_LINE_INVALID"));
    }

    @Test
    void rejectsPostingToClosedPeriodBeforeTotalsAreComputed() {
        period.setStatus("CLOSED");

        assertThatThrownBy(() -> validation.validateForPost(entry(), List.of(
                line(1, DEBIT_ACCOUNT_ID, 1000, 0), line(2, CREDIT_ACCOUNT_ID, 0, 1000))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("POSTING_PERIOD_CLOSED"));
    }

    private JournalEntry entry() {
        JournalEntry entry = new JournalEntry();
        entry.setAccountingPeriodId(PERIOD_ID);
        entry.setEntryDate(LocalDate.of(2026, 1, 15));
        entry.setCurrency("xaf");
        entry.setDescription("Test journal");
        return entry;
    }

    private JournalLine line(int number, UUID accountId, long debit, long credit) {
        JournalLine line = new JournalLine();
        line.setLineNumber(number);
        line.setAccountId(accountId);
        line.setDebitMinor(debit);
        line.setCreditMinor(credit);
        return line;
    }

    private ChartOfAccount account(UUID id, String code, String normalSide) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setCode(code);
        account.setAccountType("ASSET");
        account.setNormalSide(normalSide);
        account.setCurrency("XAF");
        account.setActive(true);
        account.setPostingAllowed(true);
        return account;
    }
}
