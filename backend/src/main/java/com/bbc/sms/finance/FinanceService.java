package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.*;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceService {

    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final RealtimeService realtime;

    public FinanceService(PaymentRepository payments, ExpenseRepository expenses, RealtimeService realtime) {
        this.payments = payments;
        this.expenses = expenses;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> listPayments() {
        UUID schoolId = TenantContext.get();
        return payments.findBySchoolIdOrderByPaidOnDesc(schoolId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> listExpenses() {
        UUID schoolId = TenantContext.get();
        return expenses.findBySchoolIdOrderBySpentOnDesc(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public PaymentView recordPayment(PaymentRequest in) {
        UUID schoolId = TenantContext.get();
        Payment p = new Payment();
        p.setSchoolId(schoolId);
        p.setReceiptNo("RCT-2026-" + String.format("%04d", 1000 + payments.countBySchoolId(schoolId)));
        p.setStudentId(in.studentId());
        p.setAmount(in.amount());
        p.setMethod(in.method());
        p.setTranche(in.tranche());
        p.setPaidOn(in.paidOn() == null ? LocalDate.now() : in.paidOn());
        PaymentView view = toView(payments.save(p));
        realtime.broadcast(schoolId, "payments", view);
        return view;
    }

    @Transactional
    public ExpenseView addExpense(ExpenseRequest in) {
        UUID schoolId = TenantContext.get();
        Expense e = new Expense();
        e.setSchoolId(schoolId);
        e.setSpentOn(in.spentOn());
        e.setCategory(in.category());
        e.setLabel(in.label());
        e.setAmount(in.amount());
        return toView(expenses.save(e));
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary() {
        UUID schoolId = TenantContext.get();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);   // 30-day window inclusive of today

        List<Payment> recentPayments = payments.findBySchoolIdAndPaidOnBetween(schoolId, from, to);

        Map<LocalDate, Long> byDay = new HashMap<>();
        long totalRevenue30d = 0;
        for (Payment p : recentPayments) {
            byDay.merge(p.getPaidOn(), p.getAmount(), Long::sum);
            totalRevenue30d += p.getAmount();
        }

        List<RevenuePoint> revenueSeries = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate day = from.plusDays(i);
            revenueSeries.add(new RevenuePoint(day, byDay.getOrDefault(day, 0L)));
        }

        long totalExpense30d = expenses.findBySchoolIdOrderBySpentOnDesc(schoolId).stream()
                .filter(e -> !e.getSpentOn().isBefore(from) && !e.getSpentOn().isAfter(to))
                .mapToLong(Expense::getAmount)
                .sum();

        long balance30d = totalRevenue30d - totalExpense30d;
        int paymentsCount = recentPayments.size();

        return new FinanceSummary(totalRevenue30d, totalExpense30d, balance30d, paymentsCount, revenueSeries);
    }

    private PaymentView toView(Payment p) {
        return new PaymentView(p.getId(), p.getReceiptNo(), p.getStudentId(), p.getAmount(),
                p.getMethod(), p.getTranche(), p.getPaidOn());
    }

    private ExpenseView toView(Expense e) {
        return new ExpenseView(e.getId(), e.getSpentOn(), e.getCategory(), e.getLabel(), e.getAmount());
    }
}
