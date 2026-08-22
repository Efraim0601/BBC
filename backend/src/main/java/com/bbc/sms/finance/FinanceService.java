package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.*;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final FeeService fees;
    private final PaymentChannelRepository channels;
    private final AuthorizationPolicyService policy;
    private final JdbcTemplate jdbc;
    private final TeacherScopeService teacherScope;

    private record FinanceStudent(UUID id, String matricule, String firstName,
                                  String lastName, String className, String level,
                                  String subsystem) {}

    public FinanceService(PaymentRepository payments, ExpenseRepository expenses, RealtimeService realtime,
                          StudentFeeRepository studentFees, FeeConfigRepository feeConfigs,
                          StudentRepository students, FeeService fees, PaymentChannelRepository channels,
                          AuthorizationPolicyService policy, JdbcTemplate jdbc,
                          TeacherScopeService teacherScope) {
        this.payments = payments;
        this.expenses = expenses;
        this.realtime = realtime;
        this.studentFees = studentFees;
        this.feeConfigs = feeConfigs;
        this.students = students;
        this.fees = fees;
        this.channels = channels;
        this.policy = policy;
        this.jdbc = jdbc;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> listPayments() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        List<Payment> rows = payments.findBySchoolIdOrderByPaidOnDesc(schoolId);
        // Resolve student identities in one pass — a per-row lookup would be N+1.
        Map<UUID, FinanceStudent> byId = financeStudents(schoolId,
                rows.stream().map(Payment::getStudentId).collect(Collectors.toSet()));
        String section = teacherScope.adminSection();
        return rows.stream()
                .filter(p -> inSection(byId.get(p.getStudentId()), section))
                .map(p -> toView(p, byId.get(p.getStudentId()))).toList();
    }

    /**
     * L'encaissement relève-t-il du cycle de l'administrateur courant ?
     *
     * <p>Un règlement dont l'élève a disparu des effectifs n'est rattachable à
     * aucune section : il reste au seul admin principal, qui répond des comptes
     * de l'établissement. Le masquer à l'admin de cycle vaut mieux que le lui
     * imputer à tort.
     */
    private static boolean inSection(Student s, String section) {
        if (section == null) return true;
        return s != null && section.equals(s.getLevel());
    }

    /** Même règle sur la projection réduite servie par {@link #financeStudents}. */
    private static boolean inSection(FinanceStudent s, String section) {
        if (section == null) return true;
        return s != null && section.equals(s.level());
    }

    /**
     * Les dépenses n'ont pas de section : un loyer, un carburant, un salaire
     * n'appartiennent pas à un cycle. Plutôt que de les répartir au jugé, on les
     * réserve à l'administrateur principal — seul comptable du solde de l'école.
     */
    @Transactional(readOnly = true)
    public List<ExpenseView> listExpenses() {
        requireSchool("FINANCE_EXPENSE_VIEW");
        UUID schoolId = TenantContext.get();
        if (teacherScope.adminSection() != null) return List.of();
        return expenses.findBySchoolIdOrderBySpentOnDesc(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public PaymentView recordPayment(PaymentRequest in) {
        UUID schoolId = TenantContext.get();
        // On n'encaisse que pour un élève de son périmètre.
        teacherScope.assertSectionStudent(in.studentId());
        LocalDate paidOn = in.paidOn() == null ? LocalDate.now() : in.paidOn();
        requireStudent("PAYMENT_COLLECT", in.studentId(), paidOn);

        long configuredTotal = expectedTotal(schoolId, in.studentId());
        if (configuredTotal <= 0) {
            throw com.bbc.sms.platform.common.ApiException.badRequest(
                    "Aucune grille de frais ne couvre cet élève. Configurez ses frais avant d'enregistrer un paiement.");
        }
        long alreadyReceived = payments.findBySchoolIdAndStudentIdOrderByPaidOnAsc(schoolId, in.studentId())
                .stream().mapToLong(Payment::getAmount).sum();
        requireCollectibleAmount(configuredTotal, alreadyReceived, in.amount());

        // Le canal doit exister et être actif ; ceux qui l'exigent (mobile money, carte,
        // virement) imposent la référence de transaction, seule preuve opposable au parent.
        PaymentChannel channel = fees.requireEnabledChannel(schoolId, in.method());
        String reference = in.reference() == null || in.reference().isBlank() ? null : in.reference().trim();
        if (channel.isRequiresReference() && reference == null) {
            throw com.bbc.sms.platform.common.ApiException.badRequest(
                    "Le moyen de paiement « " + channel.getLabelFr() + " » exige une référence de transaction.");
        }

        Payment p = new Payment();
        p.setSchoolId(schoolId);
        p.setReceiptNo("RCT-2026-" + String.format("%04d", 1000 + payments.countBySchoolId(schoolId)));
        p.setStudentId(in.studentId());
        p.setAmount(in.amount());
        p.setMethod(channel.getCode());
        p.setReference(reference);
        p.setTranche(in.tranche());
        p.setPaidOn(paidOn);
        PaymentView view = toView(payments.save(p), financeStudent(schoolId, in.studentId()));

        // Reconcile the student's running balance — without this, recording a payment
        // never reduced what the student owes (the dashboard/debtor figures went stale).
        reconcileStudentFee(schoolId, in.studentId());

        realtime.broadcast(schoolId, "payments", view);
        return view;
    }

    static long requireCollectibleAmount(long configuredTotal, long alreadyReceived, long requested) {
        long remaining = Math.max(0, configuredTotal - Math.min(configuredTotal, alreadyReceived));
        if (remaining == 0) {
            throw com.bbc.sms.platform.common.ApiException.conflict(
                    "Les frais de cet élève sont déjà entièrement réglés. Aucun nouveau paiement n'est attendu.");
        }
        if (requested > remaining) {
            throw com.bbc.sms.platform.common.ApiException.badRequest(
                    "Le montant saisi (" + requested + " FCFA) dépasse le solde restant de "
                            + remaining + " FCFA.");
        }
        return remaining;
    }

    /** Apply a payment to the student's {@code student_fee} row, creating it from the fee grid if absent. */
    private void reconcileStudentFee(UUID schoolId, UUID studentId) {
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

        long total = expectedTotal(schoolId, studentId);
        long received = payments.findBySchoolIdAndStudentIdOrderByPaidOnAsc(schoolId, studentId)
                .stream().mapToLong(Payment::getAmount).sum();
        long paid = Math.min(total, received);
        long balance = Math.max(0, total - paid);

        fee.setTotal(total);
        fee.setPaid(paid);
        fee.setBalance(balance);
        fee.setTranchesPaid(tranchesCovered(schoolId, studentId, paid, fee.getTranchesPaid()));
        fee.setStatus(balance <= 0 ? "paid" : (paid > 0 ? "partial" : "unpaid"));
        studentFees.save(fee);
    }

    /** Montant attendu selon la grille applicable à l'élève (classe, sinon niveau), 0 si aucune. */
    private long expectedTotal(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
        return fees.resolveGrid(schoolId, s).map(FeeConfig::getTotal).orElse(0L);
    }

    /** Nombre de tranches entièrement couvertes par le total versé (valeur actuelle à défaut de grille). */
    private int tranchesCovered(UUID schoolId, UUID studentId, long paid, int current) {
        Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
        FeeConfig grid = fees.resolveGrid(schoolId, s).orElse(null);
        if (grid == null) return current;
        var tranches = FeeService.fromJson(grid.getTranches());
        if (tranches.isEmpty()) return current;

        long cumulative = 0;
        int covered = 0;
        for (var t : tranches) {
            cumulative += t.amount();
            if (paid >= cumulative) covered++;
            else break;
        }
        return covered;
    }

    @Transactional
    public ExpenseView addExpense(ExpenseRequest in) {
        requireSchool("FINANCE_EXPENSE_CREATE");
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
        requireSchool("FINANCE_EXPENSE_DELETE");
        UUID schoolId = TenantContext.get();
        Expense e = expenses.findById(id)
                .filter(x -> x.getSchoolId().equals(schoolId))   // never cross the tenant boundary
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable"));
        expenses.delete(e);
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);   // 30-day window inclusive of today

        String section = teacherScope.adminSection();
        List<Payment> recentPayments = payments.findBySchoolIdAndPaidOnBetween(schoolId, from, to);
        if (section != null) {
            Map<UUID, Student> byId = students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId).stream()
                    .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
            recentPayments = recentPayments.stream()
                    .filter(p -> inSection(byId.get(p.getStudentId()), section)).toList();
        }

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

        // Dépenses hors périmètre d'un admin de cycle : à zéro, et le champ
        // `section` dit à l'écran pourquoi le solde n'est pas celui de l'école.
        long totalExpense30d = section != null ? 0L
                : expenses.findBySchoolIdOrderBySpentOnDesc(schoolId).stream()
                        .filter(e -> !e.getSpentOn().isBefore(from) && !e.getSpentOn().isAfter(to))
                        .mapToLong(Expense::getAmount)
                        .sum();

        long balance30d = totalRevenue30d - totalExpense30d;
        int paymentsCount = recentPayments.size();

        return new FinanceSummary(totalRevenue30d, totalExpense30d, balance30d, paymentsCount,
                revenueSeries, section);
    }

    private PaymentView toView(Payment p) {
        return toView(p, financeStudent(p.getSchoolId(), p.getStudentId()));
    }

    /** {@code student} may be null — a payment can outlive the student record it points at. */
    private PaymentView toView(Payment p, FinanceStudent s) {
        String name = s == null ? null : (s.lastName() + " " + s.firstName()).trim();
        PaymentChannel ch = channels.findBySchoolIdAndCode(p.getSchoolId(), p.getMethod()).orElse(null);
        return new PaymentView(p.getId(), p.getReceiptNo(), p.getStudentId(),
                name, s == null ? null : s.matricule(), s == null ? null : s.className(),
                p.getAmount(), p.getMethod(),
                ch == null ? p.getMethod() : ch.getLabelFr(),
                ch == null ? p.getMethod() : ch.getLabelEn(),
                p.getReference(), p.getTranche(), p.getPaidOn());
    }

    private ExpenseView toView(Expense e) {
        return new ExpenseView(e.getId(), e.getSpentOn(), e.getCategory(), e.getLabel(), e.getAmount());
    }

    private FinanceStudent financeStudent(UUID schoolId, UUID studentId) {
        return jdbc.query("""
                SELECT s.id,s.matricule,s.first_name,s.last_name,
                       c.name,c.level,c.subsystem
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.school_class_id
                         FROM student_enrollment e
                         JOIN academic_session a ON a.id=e.academic_session_id
                                               AND a.school_id=e.school_id
                        WHERE e.school_id=? AND e.student_id=s.id AND e.status='ACTIVE'
                          AND a.is_current=true AND e.enrolled_on<=CURRENT_DATE
                          AND (e.exited_on IS NULL OR e.exited_on>=CURRENT_DATE)
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) current_enrollment ON true
                  LEFT JOIN school_class c ON c.id=current_enrollment.school_class_id
                                           AND c.school_id=s.school_id
                 WHERE s.school_id=? AND s.id=? AND s.active=true
                """, rs -> rs.next() ? new FinanceStudent(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)) : null,
                schoolId, schoolId, studentId);
    }

    private Map<UUID, FinanceStudent> financeStudents(UUID schoolId, java.util.Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        args.add(schoolId);
        args.addAll(ids);
        return jdbc.query("""
                SELECT s.id,s.matricule,s.first_name,s.last_name,
                       c.name,c.level,c.subsystem
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.school_class_id
                         FROM student_enrollment e
                         JOIN academic_session a ON a.id=e.academic_session_id
                                               AND a.school_id=e.school_id
                        WHERE e.school_id=? AND e.student_id=s.id AND e.status='ACTIVE'
                          AND a.is_current=true AND e.enrolled_on<=CURRENT_DATE
                          AND (e.exited_on IS NULL OR e.exited_on>=CURRENT_DATE)
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) current_enrollment ON true
                  LEFT JOIN school_class c ON c.id=current_enrollment.school_class_id
                                           AND c.school_id=s.school_id
                 WHERE s.school_id=? AND s.active=true AND s.id IN (%s)
                """.formatted(placeholders), (rs, n) -> new FinanceStudent(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)), args.toArray())
                .stream().collect(Collectors.toMap(FinanceStudent::id, x -> x));
    }

    private void requireSchool(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    private void requireStudent(String action, UUID studentId, LocalDate date) {
        UUID schoolId = TenantContext.get();
        Map<String, Object> scope = jdbc.query("""
                SELECT e.academic_session_id,e.school_class_id,c.level,c.subsystem
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.academic_session_id,e.school_class_id
                         FROM student_enrollment e
                         JOIN academic_session a ON a.id=e.academic_session_id
                                               AND a.school_id=e.school_id
                        WHERE e.school_id=s.school_id AND e.student_id=s.id AND e.status='ACTIVE'
                          AND a.is_current=true AND e.enrolled_on<=?
                          AND (e.exited_on IS NULL OR e.exited_on>=?)
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) e ON true
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=s.school_id
                 WHERE s.school_id=? AND s.id=? AND s.active=true
                """, rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> result = new HashMap<>();
                    result.put("session", rs.getObject(1, UUID.class));
                    result.put("class", rs.getObject(2, UUID.class));
                    result.put("level", rs.getString(3));
                    result.put("subsystem", rs.getString(4));
                    return result;
                },
                date, date, schoolId, studentId);
        if (scope == null) {
            throw com.bbc.sms.platform.common.ApiException.coded(
                    org.springframework.http.HttpStatus.FORBIDDEN, "STUDENT_SCOPE_DENIED",
                    "Cet élève actif n'appartient pas à l'établissement courant.");
        }
        ParcoursContext.Scope parcours = scope.get("level") == null ? null
                : new ParcoursContext.Scope(String.valueOf(scope.get("level")),
                String.valueOf(scope.get("subsystem")));
        policy.require(action, new PolicyResourceContext(schoolId,
                (UUID) scope.get("session"), date, parcours, (UUID) scope.get("class"),
                null, studentId, null, null, null, null, null));
    }
}
