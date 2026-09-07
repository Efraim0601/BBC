package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.PaymentView;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FinanceHistoryTest {
    private final UUID school = UUID.randomUUID(), own = UUID.randomUUID(), foreign = UUID.randomUUID();
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final TeacherScopeService scope = mock(TeacherScopeService.class);
    private final PaymentChannelRepository channels = mock(PaymentChannelRepository.class);
    private List<PaymentView> collections = List.of();
    private List<FinanceService.CashEvent> events = List.of();
    private long ledgerExpense;
    private FinanceService service;

    @BeforeEach
    void setup() {
        TenantContext.set(school);
        when(scope.allowedStudentIds()).thenReturn(null);
        when(channels.findBySchoolIdAndCode(any(), anyString())).thenReturn(Optional.empty());
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("queryForObject")) return ledgerExpense;
            if (!invocation.getMethod().getName().equals("query")) return RETURNS_DEFAULTS.answer(invocation);
            String sql = invocation.getArgument(0);
            if (sql.contains("movements WHERE")) return events;
            if (sql.contains("FROM finance_payment p")) return collections;
            return List.of();
        });
        service = new FinanceService(payments, expenses, null, null, null, null, null, channels,
                mock(AuthorizationPolicyService.class), jdbc, scope, null, null, null, null, null, null, null);
    }

    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void mergesLegacyAndScheduledPaymentsAndRetainsCorrectionStatus() {
        Payment legacy = new Payment();
        legacy.setId(UUID.randomUUID()); legacy.setSchoolId(school); legacy.setStudentId(own);
        legacy.setReceiptNo("OLD-1"); legacy.setMethod("CASH"); legacy.setPaidOn(LocalDate.now().minusDays(1));
        legacy.setAmount(5000);
        when(payments.findBySchoolIdOrderByPaidOnDesc(school)).thenReturn(List.of(legacy));
        collections = List.of(payment(own, "REVERSED", 0), payment(own, "PARTIALLY_REFUNDED", 2000));
        var history = service.listPayments();
        assertThat(history).hasSize(3);
        assertThat(history).extracting(PaymentView::source).containsExactly("COLLECTION", "COLLECTION", "LEGACY_PAYMENT");
        assertThat(history).extracting(PaymentView::status).contains("REVERSED", "PARTIALLY_REFUNDED", "POSTED");
        assertThat(history).extracting(PaymentView::refundedAmount).contains(2000L);
    }

    @Test void collectionHistoryCannotExpandThePrincipalsStudentScope() {
        when(scope.allowedStudentIds()).thenReturn(Set.of(own));
        collections = List.of(payment(own, "POSTED", 0), payment(foreign, "POSTED", 0));
        assertThat(service.listPayments()).extracting(PaymentView::studentId).containsExactly(own);
    }

    @Test void cashflowCountsRefundsAndReversalsOnTheirActualDates() {
        LocalDate today = LocalDate.now();
        events = List.of(new FinanceService.CashEvent(own, today.minusDays(2), 30000),
                new FinanceService.CashEvent(own, today.minusDays(1), 10000),
                new FinanceService.CashEvent(own, today, -5000),
                new FinanceService.CashEvent(own, today, -10000));
        var summary = service.summary();
        assertThat(summary.totalRevenue30d()).isEqualTo(25000);
        assertThat(summary.paymentsCount()).isEqualTo(2);
        assertThat(summary.revenueSeries()).hasSize(30);
        assertThat(summary.revenueSeries().getLast().amount()).isEqualTo(-15000);
    }

    @Test void scopedCashflowExcludesForeignStudentsAndSchoolwideExpenses() {
        when(scope.allowedStudentIds()).thenReturn(Set.of(own));
        events = List.of(new FinanceService.CashEvent(own, LocalDate.now(), 10000),
                new FinanceService.CashEvent(foreign, LocalDate.now(), 90000));
        var summary = service.summary();
        assertThat(summary.totalRevenue30d()).isEqualTo(10000);
        assertThat(summary.totalExpense30d()).isZero();
        assertThat(summary.paymentsCount()).isEqualTo(1);
        verifyNoInteractions(expenses);
    }

    @Test void summaryIncludesExpensesPostedByPayrollAndOtherLedgerWorkflows() {
        ledgerExpense=4000;
        events=List.of(new FinanceService.CashEvent(own,LocalDate.now(),10000));
        var result=service.summary();
        assertThat(result.totalExpense30d()).isEqualTo(4000);
        assertThat(result.balance30d()).isEqualTo(6000);
    }

    private PaymentView payment(UUID student, String status, long refunded) {
        UUID id = UUID.randomUUID();
        return new PaymentView(id, "V2-" + id, student, null, null, null, 10000, "CASH", "Espèces", "Cash",
                null, null, LocalDate.now(), null, null, null, "COLLECTION", status, refunded);
    }
}
