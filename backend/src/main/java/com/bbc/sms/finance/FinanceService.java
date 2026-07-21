package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.*;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FinanceService {

    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final RealtimeService realtime;
    private final StudentFeeRepository studentFees;
    private final FeeConfigRepository feeConfigs;
    private final StudentRepository students;

    public FinanceService(PaymentRepository payments, ExpenseRepository expenses, RealtimeService realtime,
                          StudentFeeRepository studentFees, FeeConfigRepository feeConfigs,
                          StudentRepository students) {
        this.payments = payments;
        this.expenses = expenses;
        this.realtime = realtime;
        this.studentFees = studentFees;
        this.feeConfigs = feeConfigs;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> listPayments() {
        UUID schoolId = TenantContext.get();
        List<Payment> rows = payments.findBySchoolIdOrderByPaidOnDesc(schoolId);
        // Resolve student identities in one pass — a per-row lookup would be N+1.
        Map<UUID, Student> byId = students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId).stream()
                .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
        return rows.stream().map(p -> toView(p, byId.get(p.getStudentId()))).toList();
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

        // Reconcile the student's running balance — without this, recording a payment
        // never reduced what the student owes (the dashboard/debtor figures went stale).
        reconcileStudentFee(schoolId, in.studentId(), in.amount());

        realtime.broadcast(schoolId, "payments", view);
        return view;
    }

    /** Apply a payment to the student's {@code student_fee} row, creating it from the fee grid if absent. */
    private void reconcileStudentFee(UUID schoolId, UUID studentId, long amount) {
        StudentFee fee = studentFees.findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(() -> {
                    StudentFee fresh = new StudentFee();
                    fresh.setSchoolId(schoolId);
                    fresh.setStudentId(studentId);
                    fresh.setTotal(expectedTotal(schoolId, studentId));
                    fresh.setPaid(0);
                    fresh.setTranchesPaid(0);
                    return fresh;
                });

        long paid = fee.getPaid() + amount;
        long total = Math.max(fee.getTotal(), paid);   // never let balance go negative
        long balance = Math.max(0, total - paid);

        fee.setTotal(total);
        fee.setPaid(paid);
        fee.setBalance(balance);
        fee.setTranchesPaid(tranchesCovered(schoolId, studentId, paid, fee.getTranchesPaid()));
        fee.setStatus(balance <= 0 ? "paid" : (paid > 0 ? "partial" : "unpaid"));
        studentFees.save(fee);
    }

    /** Fee-grid total for the student's level/subsystem, or 0 when no grid matches. */
    private long expectedTotal(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
        if (s == null || s.getLevel() == null) return 0;
        return feeConfigs.findBySchoolIdAndLevel(schoolId, s.getLevel()).stream()
                .filter(c -> c.getSubsystem() == null || c.getSubsystem().equals(s.getSubsystem()))
                .mapToLong(FeeConfig::getTotal)
                .findFirst()
                .orElse(0L);
    }

    /** How many cumulative tranches the total paid now covers (falls back to the current count). */
    private int tranchesCovered(UUID schoolId, UUID studentId, long paid, int current) {
        Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
        if (s == null || s.getLevel() == null) return current;
        List<Long> tranches = feeConfigs.findBySchoolIdAndLevel(schoolId, s.getLevel()).stream()
                .filter(c -> c.getSubsystem() == null || c.getSubsystem().equals(s.getSubsystem()))
                .map(FeeConfig::getTranches)
                .filter(t -> t != null && !t.isEmpty())
                .findFirst()
                .orElse(null);
        if (tranches == null) return current;
        long cumulative = 0;
        int covered = 0;
        for (Long t : tranches) {
            cumulative += t;
            if (paid >= cumulative) covered++;
            else break;
        }
        return covered;
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

    @Transactional
    public void deleteExpense(UUID id) {
        UUID schoolId = TenantContext.get();
        Expense e = expenses.findById(id)
                .filter(x -> x.getSchoolId().equals(schoolId))   // never cross the tenant boundary
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable"));
        expenses.delete(e);
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
        return toView(p, students.findByIdAndSchoolId(p.getStudentId(), p.getSchoolId()).orElse(null));
    }

    /** {@code student} may be null — a payment can outlive the student record it points at. */
    private PaymentView toView(Payment p, Student s) {
        String name = s == null ? null : (s.getLastName() + " " + s.getFirstName()).trim();
        return new PaymentView(p.getId(), p.getReceiptNo(), p.getStudentId(),
                name, s == null ? null : s.getMatricule(), s == null ? null : s.getClassName(),
                p.getAmount(), p.getMethod(), p.getTranche(), p.getPaidOn());
    }

    private ExpenseView toView(Expense e) {
        return new ExpenseView(e.getId(), e.getSpentOn(), e.getCategory(), e.getLabel(), e.getAmount());
    }
}
