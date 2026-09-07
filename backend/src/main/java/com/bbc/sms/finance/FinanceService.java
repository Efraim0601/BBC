package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.*;
import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.JournalEntryRepository;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import com.bbc.sms.finance.accounting.AccountingDtos.ReverseRequest;
import com.bbc.sms.finance.treasury.TreasuryService;
import com.bbc.sms.platform.common.ApiException;
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
import java.util.Set;
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
    private final AccountingPeriodService accountingPeriods;
    private final ChartOfAccountRepository chartAccounts;
    private final JournalEntryRepository journals;
    private final LedgerPostingService ledger;
    private final TreasuryService treasury;
    private final DocumentSequenceService sequences;
    private final IdempotencyService idempotency;

    private record FinanceStudent(UUID id, String matricule, String firstName,
                                  String lastName, String className, String level,
                                  String subsystem) {}

    public FinanceService(PaymentRepository payments, ExpenseRepository expenses, RealtimeService realtime,
                          StudentFeeRepository studentFees, FeeConfigRepository feeConfigs,
                          StudentRepository students, FeeService fees, PaymentChannelRepository channels,
                          AuthorizationPolicyService policy, JdbcTemplate jdbc,
                          TeacherScopeService teacherScope, AccountingPeriodService accountingPeriods,
                          ChartOfAccountRepository chartAccounts, JournalEntryRepository journals,
                          LedgerPostingService ledger, TreasuryService treasury,
                          DocumentSequenceService sequences, IdempotencyService idempotency) {
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
        this.accountingPeriods = accountingPeriods;
        this.chartAccounts = chartAccounts;
        this.journals = journals;
        this.ledger = ledger;
        this.treasury = treasury;
        this.sequences = sequences;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> listPayments() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        List<Payment> rows = payments.findBySchoolIdOrderByPaidOnDesc(schoolId);
        List<PaymentView> collections = collectionRows(schoolId);
        Set<UUID> studentIds = rows.stream().map(Payment::getStudentId).collect(Collectors.toSet());
        collections.forEach(p -> studentIds.add(p.studentId()));
        // Resolve student identities in one pass — a per-row lookup would be N+1.
        Map<UUID, FinanceStudent> byId = financeStudents(schoolId, studentIds);
        Set<UUID> allowedStudents = teacherScope.allowedStudentIds();
        List<PaymentView> history = new ArrayList<>(rows.stream()
                .filter(p -> allowedStudents == null || allowedStudents.contains(p.getStudentId()))
                .map(p -> toView(p, byId.get(p.getStudentId()))).toList());
        for (PaymentView p : collections) {
            if (allowedStudents != null && !allowedStudents.contains(p.studentId())) continue;
            FinanceStudent s = byId.get(p.studentId());
            history.add(new PaymentView(p.id(), p.receiptNo(), p.studentId(), s == null ? null : (s.lastName()+" "+s.firstName()).trim(),
                    s == null ? null : s.matricule(), s == null ? null : s.className(), p.amount(), p.method(),
                    p.methodLabelFr(), p.methodLabelEn(), p.reference(), p.tranche(), p.paidOn(), p.treasuryAccountId(),
                    p.treasuryAccountName(), p.journalEntryId(), p.source(), p.status(), p.refundedAmount()));
        }
        history.sort(java.util.Comparator.comparing(PaymentView::paidOn).reversed().thenComparing(PaymentView::receiptNo, java.util.Comparator.reverseOrder()));
        return history;
    }

    private List<PaymentView> collectionRows(UUID schoolId) {
        return jdbc.query("""
                SELECT p.id,p.receipt_no,p.student_id,p.amount_minor,p.channel_code_snapshot,
                       coalesce(ch.label_fr,p.channel_code_snapshot),coalesce(ch.label_en,p.channel_code_snapshot),
                       p.reference,p.payment_date,p.treasury_account_id,a.display_name,p.journal_entry_id,p.status,
                       coalesce((SELECT sum(r.amount_minor) FROM refund_transaction r WHERE r.school_id=p.school_id AND r.payment_id=p.id),0)
                  FROM finance_payment p
                  LEFT JOIN payment_channel ch ON ch.school_id=p.school_id AND ch.code=p.channel_code_snapshot
                  LEFT JOIN treasury_account a ON a.school_id=p.school_id AND a.id=p.treasury_account_id
                 WHERE p.school_id=?
                """, (rs,n) -> new PaymentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),
                        null,null,null,rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),null,
                        rs.getObject(9,LocalDate.class),rs.getObject(10,UUID.class),rs.getString(11),rs.getObject(12,UUID.class),
                        "COLLECTION",rs.getString(13),rs.getLong(14)), schoolId);
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
        if (ParcoursContext.get() != null || teacherScope.adminSection() != null) return List.of();
        return expenses.findBySchoolIdOrderBySpentOnDesc(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public PaymentView recordPayment(PaymentRequest in) {
        return recordPayment(in, null);
    }

    @Transactional
    public PaymentView recordPayment(PaymentRequest in, String idempotencyKey) {
        // On n'encaisse que pour un élève de son périmètre.
        teacherScope.assertSectionStudent(in.studentId());
        LocalDate paidOn = in.paidOn() == null ? LocalDate.now() : in.paidOn();
        // Always re-authorize, including when returning a cached retry response.
        requireStudent("PAYMENT_COLLECT", in.studentId(), paidOn);
        return idempotency.execute("finance/payments", idempotencyKey, in, PaymentView.class,
                () -> recordPaymentInternal(in, paidOn));
    }

    private PaymentView recordPaymentInternal(PaymentRequest in, LocalDate paidOn) {
        UUID schoolId = TenantContext.get();
        // Serialize collection for this student before reading the running total.
        // Two cashiers must not both spend the same remaining fee balance.
        jdbc.queryForObject("SELECT id FROM student WHERE school_id=? AND id=? FOR UPDATE",
                UUID.class, schoolId, in.studentId());
        if (fees.hasV2Charges(schoolId, in.studentId())) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "USE_CHARGE_COLLECTION",
                    "Cet élève est géré par les charges et échéances. Utilisez Encaissements & comptes élèves pour éviter une double facturation.");
        }

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
        ChartOfAccount treasuryAccount = requireTreasuryChartAccount(in.treasuryAccountId(), paidOn);

        Payment p = new Payment();
        p.setSchoolId(schoolId);
        String periodKey = String.valueOf(paidOn.getYear());
        p.setReceiptNo(sequences.allocate("RECEIPT", periodKey, "RCT/" + periodKey + "/", 6));
        p.setStudentId(in.studentId());
        p.setAmount(in.amount());
        p.setMethod(channel.getCode());
        p.setReference(reference);
        p.setTranche(in.tranche());
        p.setPaidOn(paidOn);
        p.setTreasuryAccountId(in.treasuryAccountId());
        p.setCreatedBy(currentUserId());
        p = payments.saveAndFlush(p);

        AccountingPeriod period = accountingPeriods.requireOpenForDate(paidOn);
        ChartOfAccount revenue = requirePostingAccount("4000", paidOn, "Produits de scolarité", "REVENUE");
        var journal = ledger.createDraftInternal(new JournalUpsert(paidOn,
                "Encaissement " + p.getReceiptNo(), "XAF", period.getId(),
                "LEGACY_PAYMENT", p.getId().toString(), "LEGACY_PAYMENT:" + p.getId(),
                List.of(
                        new JournalLineInput(treasuryAccount.getId(), in.amount(), 0,
                                in.studentId(), null, null, null, null, p.getReceiptNo()),
                        new JournalLineInput(revenue.getId(), 0, in.amount(),
                                in.studentId(), null, null, null, null, p.getReceiptNo())), null));
        var posted = ledger.postNowInternal(journal.id());
        p.setJournalEntryId(posted.id());
        p = payments.saveAndFlush(p);
        PaymentView view = toView(p, financeStudent(schoolId, in.studentId()));

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
        ChartOfAccount treasuryAccount = requireTreasuryChartAccount(in.treasuryAccountId(), in.spentOn());
        Expense e = new Expense();
        e.setSchoolId(schoolId);
        e.setSpentOn(in.spentOn());
        e.setCategory(in.category());
        e.setLabel(in.label());
        e.setAmount(in.amount());
        e.setTreasuryAccountId(in.treasuryAccountId());
        e.setCreatedBy(currentUserId());
        e.setStatus("POSTED");
        e = expenses.saveAndFlush(e);

        AccountingPeriod period = accountingPeriods.requireOpenForDate(in.spentOn());
        ChartOfAccount expenseAccount = requirePostingAccount("6900", in.spentOn(), "Compte de contrôle des dépenses", "EXPENSE");
        var journal = ledger.createDraftInternal(new JournalUpsert(in.spentOn(),
                "Dépense " + in.label().trim(), "XAF", period.getId(),
                "LEGACY_EXPENSE", e.getId().toString(), "LEGACY_EXPENSE:" + e.getId(),
                List.of(
                        new JournalLineInput(expenseAccount.getId(), in.amount(), 0,
                                null, null, null, null, null, in.label().trim()),
                        new JournalLineInput(treasuryAccount.getId(), 0, in.amount(),
                                null, null, null, null, null, in.label().trim())), null));
        var posted = ledger.postNowInternal(journal.id());
        e.setJournalEntryId(posted.id());
        return toView(expenses.saveAndFlush(e));
    }

    @Transactional
    public void deleteExpense(UUID id) {
        requireSchool("FINANCE_EXPENSE_DELETE");
        UUID schoolId = TenantContext.get();
        Expense e = expenses.findById(id)
                .filter(x -> x.getSchoolId().equals(schoolId))   // never cross the tenant boundary
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable"));
        if (!"POSTED".equals(e.getStatus()) || e.getJournalEntryId() == null) {
            throw ApiException.conflict("Cette dépense ne peut pas être renversée car son écriture comptable est introuvable ou déjà renversée.");
        }
        var journal = journals.findByIdAndSchoolId(e.getJournalEntryId(), schoolId)
                .orElseThrow(() -> ApiException.conflict("L'écriture comptable de cette dépense est introuvable."));
        ledger.reverseNowInternal(journal.getId(), new ReverseRequest(LocalDate.now(),
                "Annulation de la dépense " + e.getLabel(), journal.getVersion()));
        e.setStatus("REVERSED");
        expenses.saveAndFlush(e);
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);   // 30-day window inclusive of today

        Set<UUID> allowedStudents = teacherScope.allowedStudentIds();
        List<CashEvent> events = cashEvents(schoolId, from, to).stream()
                .filter(p -> allowedStudents == null || allowedStudents.contains(p.studentId())).toList();

        Map<LocalDate, Long> byDay = new HashMap<>();
        long totalRevenue30d = 0;
        for (CashEvent event : events) {
            byDay.merge(event.date(), event.amount(), Long::sum);
            totalRevenue30d += event.amount();
        }

        List<RevenuePoint> revenueSeries = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate day = from.plusDays(i);
            revenueSeries.add(new RevenuePoint(day, byDay.getOrDefault(day, 0L)));
        }

        // Dépenses hors périmètre d'un admin de cycle : à zéro, et le champ
        // `section` dit à l'écran pourquoi le solde n'est pas celui de l'école.
        long totalExpense30d = allowedStudents != null ? 0L
                : java.util.Objects.requireNonNullElse(jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount),0) FROM (" + FinanceCashflowSql.EXPENSE_MOVEMENTS
                                + ") movements WHERE movement_date BETWEEN ? AND ?",
                        Long.class, schoolId, schoolId, from, to), 0L);

        long balance30d = totalRevenue30d - totalExpense30d;
        int paymentsCount = (int)events.stream().filter(e -> e.amount() > 0).count();

        return new FinanceSummary(totalRevenue30d, totalExpense30d, balance30d, paymentsCount,
                revenueSeries, ParcoursContext.effectiveLevel());
    }

    record CashEvent(UUID studentId, LocalDate date, long amount) {}

    /** Count money on its actual posting date, including later refunds/reversals. */
    private List<CashEvent> cashEvents(UUID schoolId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT student_id,movement_date,amount FROM (" + FinanceCashflowSql.MOVEMENTS
                        + ") movements WHERE movement_date BETWEEN ? AND ?",
                (rs,n) -> new CashEvent(rs.getObject(1,UUID.class),rs.getObject(2,LocalDate.class),rs.getLong(3)),
                schoolId,schoolId,schoolId,schoolId,from,to);
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
                p.getReference(), p.getTranche(), p.getPaidOn(), p.getTreasuryAccountId(),
                p.getTreasuryAccountId() == null ? null : treasury.displayNameForWorkflow(p.getTreasuryAccountId()),
                p.getJournalEntryId(), "LEGACY_PAYMENT", "POSTED", 0);
    }

    private ExpenseView toView(Expense e) {
        return new ExpenseView(e.getId(), e.getSpentOn(), e.getCategory(), e.getLabel(), e.getAmount(),
                e.getTreasuryAccountId(), e.getTreasuryAccountId() == null ? null
                        : treasury.displayNameForWorkflow(e.getTreasuryAccountId()),
                e.getJournalEntryId(), e.getStatus());
    }

    private ChartOfAccount requireTreasuryChartAccount(UUID treasuryAccountId, LocalDate date) {
        if (treasuryAccountId == null) {
            throw ApiException.badRequest("Sélectionnez le compte de trésorerie crédité ou débité.");
        }
        TreasuryService.TreasuryRecord record = treasury.requireActiveRecord(treasuryAccountId);
        return requirePostingAccount(record.chartAccountId(), date, "Compte de trésorerie", "ASSET");
    }

    private ChartOfAccount requirePostingAccount(String code, LocalDate date, String label, String expectedType) {
        return chartAccounts.findBySchoolIdAndCode(TenantContext.get(), code)
                .map(a -> requirePostingAccount(a, date, label, expectedType))
                .orElseThrow(() -> ApiException.conflict("Le " + label + " n'est pas configuré."));
    }

    private ChartOfAccount requirePostingAccount(UUID id, LocalDate date, String label, String expectedType) {
        return chartAccounts.findByIdAndSchoolId(id, TenantContext.get())
                .map(a -> requirePostingAccount(a, date, label, expectedType))
                .orElseThrow(() -> ApiException.notFound(label));
    }

    private ChartOfAccount requirePostingAccount(ChartOfAccount account, LocalDate date,
                                                  String label, String expectedType) {
        boolean effective = (account.getEffectiveFrom() == null || !date.isBefore(account.getEffectiveFrom()))
                && (account.getEffectiveTo() == null || !date.isAfter(account.getEffectiveTo()));
        if (!account.isActive() || !account.isPostingAllowed() || !effective
                || !expectedType.equals(account.getAccountType())
                || (account.getCurrency() != null && !"XAF".equals(account.getCurrency()))) {
            throw ApiException.conflict("Le " + label + " n'est pas postable pour cette date et cette devise.");
        }
        return account;
    }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p
                ? p.userId() : null;
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
