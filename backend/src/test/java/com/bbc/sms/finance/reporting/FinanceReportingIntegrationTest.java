package com.bbc.sms.finance.reporting;

import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import static com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import static com.bbc.sms.finance.reporting.FinanceReportingDtos.ReportFilters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class FinanceReportingIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bbc_finance_reporting_test").withUsername("bbc").withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceReportingService reporting;
    @Autowired LedgerPostingService ledger;
    @MockBean com.bbc.sms.finance.FinancePolicyService financePolicy;

    private UUID schoolId;
    private UUID sessionId;
    private UUID classId;
    private UUID studentId;
    private UUID enrollmentId;
    private UUID channelId;
    private UUID chargeId;
    private UUID installmentId;
    private UUID debitAccountId;
    private UUID creditAccountId;
    private UUID periodId;

    @BeforeEach
    void seedTenantAndPostedSources() {
        schoolId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        classId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        chargeId = UUID.randomUUID();
        installmentId = UUID.randomUUID();
        debitAccountId = UUID.randomUUID();
        creditAccountId = UUID.randomUUID();
        periodId = UUID.randomUUID();

        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId,
                "R" + schoolId.toString().substring(0, 8), "Reporting test school");
        TenantContext.set(schoolId);

        String sectionId = "sec" + schoolId.toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,?,?)",
                sectionId, schoolId, "Secondary FR", "FR", "secondary");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,?,?,?)",
                classId, schoolId, sectionId, "Form 1", "FR", "secondary");
        jdbc.update("""
                INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current)
                VALUES (?,?, '2026-S1','First session','2026-01-01','2026-03-31','OPEN',true)
                """, sessionId, schoolId);
        jdbc.update("""
                INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level,active)
                VALUES (?,?,?,?,?,?,?,?,?,true)
                """, studentId, schoolId, "ST-" + studentId.toString().substring(0, 8), "Ada", "Test",
                classId, "Form 1", "FR", "secondary");
        jdbc.update("""
                INSERT INTO student_enrollment(id,school_id,student_id,academic_session_id,school_class_id,
                                               class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source)
                VALUES (?,?,?,?,?,?,?,?, 'ACTIVE','2026-01-01','TEST')
                """, enrollmentId, schoolId, studentId, sessionId, classId, "Form 1", "secondary", "FR");

        jdbc.update("""
                INSERT INTO chart_of_account(id,school_id,code,name_fr,name_en,account_type,normal_side,currency)
                VALUES (?,?,'1100','Caisse','Cash','ASSET','DEBIT','XAF'),
                       (?,?,'4000','Scolarité','Tuition','REVENUE','CREDIT','XAF')
                """, debitAccountId, schoolId, creditAccountId, schoolId);
        jdbc.update("""
                INSERT INTO accounting_period(id,school_id,code,name_fr,name_en,start_date,end_date,academic_session_id,status)
                VALUES (?,?, '2026-01','Janvier 2026','January 2026','2026-01-01','2026-03-31',?,'OPEN')
                """, periodId, schoolId, sessionId);
        jdbc.update("""
                INSERT INTO payment_channel(id,school_id,code,label_fr,label_en,enabled,sort_order)
                VALUES (?,?,'CASH','Espèces','Cash',true,1)
                """, channelId, schoolId);

        UUID feeTypeId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID planLineId = UUID.randomUUID();
        jdbc.update("INSERT INTO fee_type(id,school_id,code,lifecycle,current_revision_no) VALUES (?,?, 'TUITION','ACTIVE',1)",
                feeTypeId, schoolId);
        jdbc.update("""
                INSERT INTO fee_type_revision(id,school_id,fee_type_id,revision_no,revision_status,name_fr,name_en,
                                              category,default_amount_minor,default_currency,frequency,mandatory,refundable)
                VALUES (?,?,?,1,'ACTIVE','Scolarité','Tuition','TUITION',100000,'XAF','ONCE',true,false)
                """, revisionId, schoolId, feeTypeId);
        jdbc.update("""
                INSERT INTO fee_plan(id,school_id,academic_session_id,scope_type,level,subsystem,school_class_id,
                                     plan_version_no,lifecycle,effective_from,currency)
                VALUES (?,?,?,'CLASS','secondary','FR',?,1,'DRAFT','2026-01-01','XAF')
                """, planId, schoolId, sessionId, classId);
        jdbc.update("""
                INSERT INTO fee_plan_line(id,school_id,fee_plan_id,line_order,fee_type_id,fee_type_revision_id,
                                          amount_minor,currency,mandatory,refundable)
                VALUES (?,?,?,1,?,?,100000,'XAF',true,false)
                """, planLineId, schoolId, planId, feeTypeId, revisionId);
        jdbc.update("""
                INSERT INTO student_charge(id,school_id,student_enrollment_id,student_id,academic_session_id,
                    fee_plan_id,fee_plan_line_id,fee_type_id,fee_type_revision_id,fee_plan_version_no,fee_type_code,
                    fee_type_name_fr,fee_type_name_en,fee_type_category,scope_type,level_snapshot,subsystem_snapshot,
                    school_class_id_snapshot,class_name_snapshot,receivable_account_id,revenue_account_id,
                    original_amount_minor,adjusted_amount_minor,currency,charge_date,proration_policy,generation_key,
                    status,paid_minor,waived_minor,outstanding_minor,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,1,'TUITION','Scolarité','Tuition','TUITION','CLASS','secondary','FR',?,?,?, ?,
                        100000,100000,'XAF','2026-01-10','NONE',?,'POSTED',0,0,100000,?,?)
                """, chargeId, schoolId, enrollmentId, studentId, sessionId, planId, planLineId, feeTypeId, revisionId,
                classId, "Form 1", debitAccountId, creditAccountId, "CHARGE-" + chargeId, at("2026-01-10T09:00:00Z"), at("2026-01-10T09:00:00Z"));
        jdbc.update("""
                INSERT INTO charge_installment(id,school_id,charge_id,installment_no,label_fr,label_en,due_date,
                    amount_minor,paid_minor,waived_minor,outstanding_minor,status,generation_key,created_at,updated_at)
                VALUES (?,?,?,1,'Tranche 1','Installment 1','2026-01-31',100000,0,0,100000,'OPEN',?,?,?)
                """, installmentId, schoolId, chargeId, "INSTALLMENT-" + installmentId, at("2026-01-10T09:00:00Z"), at("2026-01-10T09:00:00Z"));

        insertPayment(UUID.randomUUID(), 30000, LocalDate.of(2026, 1, 15), "R-001", "PAY-001", "IDEM-001",
                at("2026-01-15T10:00:00Z"));
        UUID creditPaymentId = UUID.randomUUID();
        insertPayment(creditPaymentId, 5000, LocalDate.of(2026, 1, 25), "R-002", "PAY-002", "IDEM-002",
                at("2026-01-25T10:00:00Z"));
        jdbc.update("""
                INSERT INTO student_credit_ledger(id,school_id,student_id,student_enrollment_id,payment_id,
                    entry_type,amount_minor,currency,source_event_key,entry_date,reason,created_at)
                VALUES (?,?,?,?,?,'CREATED',5000,'XAF',?,'2026-01-25','Overpayment',?)
                """, UUID.randomUUID(), schoolId, studentId, enrollmentId, creditPaymentId,
                "CREDIT-" + creditPaymentId, at("2026-01-25T10:00:00Z"));

        UUID allocationPaymentId = jdbc.queryForObject("SELECT id FROM finance_payment WHERE school_id=? AND receipt_no='R-001'",
                UUID.class, schoolId);
        jdbc.update("""
                INSERT INTO payment_allocation(id,school_id,payment_id,charge_installment_id,student_id,allocated_minor,
                                               currency,status,created_at,updated_at)
                VALUES (?,?,?,?,?,30000,'XAF','ACTIVE',?,?)
                """, UUID.randomUUID(), schoolId, allocationPaymentId, installmentId, studentId,
                at("2026-01-15T10:00:00Z"), at("2026-01-15T10:00:00Z"));
        jdbc.update("UPDATE student_charge SET paid_minor=30000,outstanding_minor=70000 WHERE school_id=? AND id=?",
                schoolId, chargeId);
        jdbc.update("UPDATE charge_installment SET paid_minor=30000,outstanding_minor=70000,status='PARTIAL' WHERE school_id=? AND id=?",
                schoolId, installmentId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void legacySummaryCombinesPaymentSystemsWithoutResurrectingReversedExpenses() {
        jdbc.update("INSERT INTO payment(id,school_id,receipt_no,student_id,amount,method,paid_on) VALUES (?,?, 'LEGACY-1',?,5000,'CASH','2026-01-15')",
                UUID.randomUUID(),schoolId,studentId);
        jdbc.update("INSERT INTO expense(id,school_id,spent_on,category,label,amount,status) VALUES (?,?,'2026-01-15','QA','Reversed test expense',7000,'REVERSED')",
                UUID.randomUUID(),schoolId);
        var report=reporting.legacyFinance();
        assertThat(report.totalRevenue()).isEqualTo(40000);
        assertThat(report.totalExpense()).isZero();
        assertThat(report.recoveryRate()).isEqualTo(30);
    }

    @Test
    void fullyWaivedChargesDoNotFallBackToLegacyFeesInReports() {
        jdbc.update("INSERT INTO student_fee(id,school_id,student_id,total,paid,balance,status) VALUES (?,?,?,150000,30000,120000,'partial')",
                UUID.randomUUID(),schoolId,studentId);
        jdbc.update("UPDATE student_charge SET status='WAIVED',paid_minor=0,waived_minor=100000,outstanding_minor=0 WHERE id=?",chargeId);
        assertThat(reporting.legacyFinance().recoveryRate()).isZero();
    }

    @Test
    void validatesContextAndTenantBeforeReadingReports() {
        assertCode("REPORT_CONTEXT_REQUIRED", () -> reporting.collections(
                new ReportFilters(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 1, 31), null, null, null, null, null, 10, 0)));
        assertCode("REPORT_DATE_OUTSIDE_SESSION", () -> reporting.collections(
                new ReportFilters(sessionId, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 1, 31), null, null, null, null, null, 10, 0)));

        UUID otherSchool = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", otherSchool,
                "O" + otherSchool.toString().substring(0, 8), "Other reporting school");
        TenantContext.set(otherSchool);
        assertCode("REPORT_SESSION_NOT_FOUND", () -> reporting.collections(filters(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), 10)));
    }

    @Test
    void collectionTotalsIgnorePaginationAndRespectAsOfSessionAndTenant() {
        var full = reporting.collections(filters(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31), 1));
        assertThat(full.data().rows()).hasSize(1);
        assertThat(full.data().paymentTotalMinor()).isEqualTo(35000);
        assertThat(full.data().allocatedMinor()).isEqualTo(30000);
        assertThat(full.data().remainingCreditMinor()).isEqualTo(5000);
        assertThat(full.data().mismatchMinor()).isZero();
        assertThat(full.data().balanced()).isTrue();
        assertThat(full.meta().dataThrough().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 31));

        var historical = reporting.collections(filters(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 20), 10));
        assertThat(historical.data().paymentTotalMinor()).isEqualTo(30000);
        assertThat(historical.data().allocatedMinor()).isEqualTo(30000);
        assertThat(historical.data().remainingCreditMinor()).isZero();
        assertThat(historical.data().rows()).hasSize(1);
        assertThat(historical.meta().dataThrough().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void receivablesRebuildHistoricalAllocationAndExposePaymentMismatches() {
        var historical = reporting.receivables(filters(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 20), 10));
        assertThat(historical.data().billedMinor()).isEqualTo(100000);
        assertThat(historical.data().collectedMinor()).isEqualTo(30000);
        assertThat(historical.data().outstandingMinor()).isEqualTo(70000);
        assertThat(historical.data().mismatchMinor()).isZero();
        assertThat(historical.data().balanced()).isTrue();
        assertThat(historical.data().rows()).singleElement().satisfies(row -> {
            assertThat(row.collectedMinor()).isEqualTo(30000);
            assertThat(row.outstandingMinor()).isEqualTo(70000);
        });

        insertPayment(UUID.randomUUID(), 7000, LocalDate.of(2026, 1, 18), "R-003", "PAY-003", "IDEM-003",
                at("2026-01-18T10:00:00Z"));
        var mismatch = reporting.collections(filters(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31), 10));
        assertThat(mismatch.data().mismatchMinor()).isEqualTo(7000);
        assertThat(mismatch.data().mismatchCount()).isEqualTo(1);
        assertThat(mismatch.data().balanced()).isFalse();
        assertThat(mismatch.data().exceptions()).extracting(e -> e.code()).contains("PAYMENT_BALANCE_MISMATCH");
    }

    @Test
    void trialBalanceAndZeroDataAreExplicitlyBalanced() {
        var draft = ledger.createDraftInternal(new JournalUpsert(LocalDate.of(2026, 1, 16), "Reporting journal", "XAF",
                periodId, "TEST", "REPORTING-1", "REPORTING-1",
                List.of(new JournalLineInput(debitAccountId, 15000, 0, null, null, null, null, null, "Cash"),
                        new JournalLineInput(creditAccountId, 0, 15000, null, null, null, null, null, "Revenue")), null));
        ledger.postNowInternal(draft.id());

        var accounting = reporting.accounting(filters(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31), 100));
        assertThat(accounting.data().trialBalance().debitMinor()).isEqualTo(15000);
        assertThat(accounting.data().trialBalance().creditMinor()).isEqualTo(15000);
        assertThat(accounting.data().trialBalance().balanced()).isTrue();
        assertThat(accounting.data().exceptions()).isEmpty();

        var empty = reporting.receivables(new ReportFilters(sessionId, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 2), null, null, null, null, null, 10, 0));
        assertThat(empty.data().billedMinor()).isZero();
        assertThat(empty.data().collectedMinor()).isZero();
        assertThat(empty.data().recoveryPercentage()).isZero();
        assertThat(empty.data().balanced()).isTrue();
    }

    private ReportFilters filters(LocalDate from, LocalDate to, LocalDate asOf, int limit) {
        return new ReportFilters(sessionId, from, to, asOf, classId, "secondary", "TUITION", null, null, limit, 0);
    }

    @Test void reversedJournalsRemainInTrialBalancesAndBothGeneralLedgers() {
        var draft=ledger.createDraftInternal(new JournalUpsert(LocalDate.of(2026,1,16),"QA reversal","XAF",periodId,"TEST","QA-REV","QA-REV",
                List.of(new JournalLineInput(debitAccountId,15000,0,null,null,null,null,null,"Cash"),
                        new JournalLineInput(creditAccountId,0,15000,null,null,null,null,null,"Revenue")),null));
        var posted=ledger.postNowInternal(draft.id());
        ledger.reverseNowInternal(posted.id(),new com.bbc.sms.finance.accounting.AccountingDtos.ReverseRequest(LocalDate.of(2026,1,20),"QA reverse",posted.version()));
        var before=ledger.trialBalanceInternal(LocalDate.of(2026,1,18),false);
        assertThat(before.rows()).filteredOn(r -> r.accountId().equals(debitAccountId)).singleElement()
                .satisfies(r -> assertThat(r.balanceMinor()).isEqualTo(15000));
        var after=ledger.trialBalanceInternal(LocalDate.of(2026,1,31),false);
        assertThat(after.rows()).allSatisfy(r -> assertThat(r.balanceMinor()).isZero());
        var general=ledger.generalLedger(debitAccountId,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31));
        assertThat(general.lines()).hasSize(2);
        assertThat(general.totalDebitMinor()).isEqualTo(general.totalCreditMinor());
        var report=reporting.accounting(filters(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),LocalDate.of(2026,1,31),100)).data();
        assertThat(report.trialBalance().rows()).allSatisfy(r -> assertThat(r.balanceMinor()).isZero());
        assertThat(report.incomeStatement().revenueMinor()).isZero();
        assertThat(report.ledger()).hasSize(4).extracting(r -> r.status()).contains("REVERSED","POSTED");
    }

    @Test void expenseOriginalAndReversalOffsetWithoutLosingHistoricalAmounts() {
        UUID expense=UUID.randomUUID();
        jdbc.update("INSERT INTO chart_of_account(id,school_id,code,name_fr,name_en,account_type,normal_side,currency) VALUES (?,?,'6500','Charges','Expenses','EXPENSE','DEBIT','XAF')",expense,schoolId);
        var draft=ledger.createDraftInternal(new JournalUpsert(LocalDate.of(2026,1,16),"QA expense","XAF",periodId,"TEST","QA-EXP","QA-EXP",
                List.of(new JournalLineInput(expense,3000,0,null,null,null,null,null,"Expense"),
                        new JournalLineInput(debitAccountId,0,3000,null,null,null,null,null,"Cash")),null));
        var posted=ledger.postNowInternal(draft.id());
        ledger.reverseNowInternal(posted.id(),new com.bbc.sms.finance.accounting.AccountingDtos.ReverseRequest(LocalDate.of(2026,1,20),"QA undo",posted.version()));
        assertThat(reporting.legacyFinance().totalExpense()).isZero();
        assertThat(reporting.expenses(filters(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),LocalDate.of(2026,1,18),100)).data().postedExpenseMinor()).isEqualTo(3000);
        assertThat(reporting.expenses(filters(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),LocalDate.of(2026,1,31),100)).data().postedExpenseMinor()).isZero();
        jdbc.update("INSERT INTO expense(id,school_id,spent_on,category,label,amount,status) VALUES (?,?,'2026-01-21','QA','Unlinked legacy',500,'POSTED')",UUID.randomUUID(),schoolId);
        assertThat(reporting.legacyFinance().totalExpense()).isEqualTo(500);
    }

    private void insertPayment(UUID id, long amount, LocalDate paymentDate, String receipt, String sourceEvent,
                               String idempotencyKey, OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO finance_payment(id,school_id,student_id,student_enrollment_id,academic_session_id,
                    payment_channel_id,channel_code_snapshot,amount_minor,currency,payment_date,reference,status,
                    receipt_no,source_event_key,idempotency_key,posted_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?, 'CASH',?,'XAF',?,?,'POSTED',?,?,?, ?,?,?)
                """, id, schoolId, studentId, enrollmentId, sessionId, channelId, amount, paymentDate,
                "REF-" + receipt, receipt, sourceEvent, idempotencyKey, createdAt, createdAt, createdAt);
    }

    private static OffsetDateTime at(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static void assertCode(String code, ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo(code));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
