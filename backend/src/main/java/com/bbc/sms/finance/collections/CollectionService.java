package com.bbc.sms.finance.collections;

import com.bbc.sms.finance.PaymentChannel;
import com.bbc.sms.finance.PaymentChannelRepository;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import com.bbc.sms.finance.charges.ChargeInstallment;
import com.bbc.sms.finance.charges.ChargeInstallmentRepository;
import com.bbc.sms.finance.charges.StudentCharge;
import com.bbc.sms.finance.charges.StudentChargeRepository;
import com.bbc.sms.finance.documents.FinanceReceipt;
import com.bbc.sms.finance.documents.FinanceReceiptRepository;
import com.bbc.sms.finance.documents.FinanceDocumentService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;
import com.bbc.sms.finance.treasury.TreasuryService;
import com.bbc.sms.finance.treasury.TreasuryDtos;

/** Quote and post boundary for allocation-aware collections. */
@Service
public class CollectionService {
    private static final String CURRENCY = "XAF";
    private record FinancePayer(UUID studentId, String studentName, String matricule,
                                UUID enrollmentId, UUID academicSessionId, String className,
                                LocalDate enrolledOn, LocalDate exitedOn) {}

    private final FinancePaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final StudentCreditLedgerRepository credits;
    private final CashierSessionRepository cashiers;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AcademicSessionRepository sessions;
    private final PaymentChannelRepository channels;
    private final StudentChargeRepository charges;
    private final ChargeInstallmentRepository installments;
    private final AccountingPeriodService periods;
    private final ChartOfAccountRepository accounts;
    private final LedgerPostingService ledger;
    private final DocumentSequenceService sequences;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final FinanceReceiptRepository receipts;
    private final FinanceDocumentService financeDocuments;
    private final FinancePolicyService financePolicy;
    private final TreasuryService treasury;

    public CollectionService(FinancePaymentRepository payments,
                             PaymentAllocationRepository allocations,
                             StudentCreditLedgerRepository credits,
                             CashierSessionRepository cashiers,
                             StudentEnrollmentRepository enrollments,
                             StudentRepository students,
                             AcademicSessionRepository sessions,
                             PaymentChannelRepository channels,
                             StudentChargeRepository charges,
                             ChargeInstallmentRepository installments,
                             AccountingPeriodService periods,
                             ChartOfAccountRepository accounts,
                             LedgerPostingService ledger,
                             DocumentSequenceService sequences,
                              IdempotencyService idempotency,
                              AuditService audit,
                              JdbcTemplate jdbc,
                              FinanceReceiptRepository receipts,
                              FinanceDocumentService financeDocuments,
                              FinancePolicyService financePolicy,
                              TreasuryService treasury) {
        this.payments = payments;
        this.allocations = allocations;
        this.credits = credits;
        this.cashiers = cashiers;
        this.enrollments = enrollments;
        this.students = students;
        this.sessions = sessions;
        this.channels = channels;
        this.charges = charges;
        this.installments = installments;
        this.periods = periods;
        this.accounts = accounts;
        this.ledger = ledger;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
        this.receipts = receipts;
        this.financeDocuments = financeDocuments;
        this.financePolicy = financePolicy;
        this.treasury = treasury;
    }

    @Transactional(readOnly = true)
    public List<StudentSearchView> search(String query, UUID sessionId) {
        financePolicy.requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        String needle = lower(query);
        return financePayers(schoolId, sessionId).stream().map(p -> searchView(p, schoolId))
                .filter(v -> needle.isBlank() || contains(v, needle))
                .limit(100)
                .toList();
    }

    /** Minimal payer projection; guardian credentials/contact never enter finance DTO construction. */
    private List<FinancePayer> financePayers(UUID schoolId, UUID sessionId) {
        return jdbc.query("""
                SELECT s.id,s.first_name,s.last_name,s.matricule,e.id,e.academic_session_id,
                       COALESCE(c.name,e.class_name_snapshot),e.enrolled_on,e.exited_on
                  FROM student_enrollment e
                  JOIN student s ON s.id=e.student_id AND s.school_id=e.school_id AND s.active=true
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.status='ACTIVE'
                   AND (CAST(? AS uuid) IS NULL OR e.academic_session_id=?)
                   AND e.enrolled_on<=CURRENT_DATE
                   AND (e.exited_on IS NULL OR e.exited_on>=CURRENT_DATE)
                 ORDER BY COALESCE(c.name,e.class_name_snapshot),s.last_name,s.first_name
                """, (rs, n) -> new FinancePayer(rs.getObject(1, UUID.class),
                        (rs.getString(3) + " " + rs.getString(2)).trim(), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getString(7),
                        rs.getObject(8, LocalDate.class), rs.getObject(9, LocalDate.class)),
                schoolId, sessionId, sessionId);
    }

    @Transactional(readOnly = true)
    public PaymentQuoteView quote(QuoteRequest request) {
        StudentEnrollment enrollment = requireEnrollment(request.enrollmentId());
        financePolicy.requireEnrollment("PAYMENT_VIEW", request.enrollmentId(), request.paymentDate());
        Student student = requireStudent(enrollment.getStudentId());
        AcademicSession session = sessions.findByIdAndSchoolId(enrollment.getAcademicSessionId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        List<OpenInstallment> open = openInstallments(enrollment.getId());
        long openTotal = open.stream().mapToLong(i -> i.installment().getOutstandingMinor()).sum();
        long proposed = Math.min(request.amountMinor(), openTotal);
        long credit = availableCredit(student.getId());
        List<InstallmentProposal> proposals = new ArrayList<>();
        long remaining = proposed;
        for (OpenInstallment item : open) {
            long amount = Math.min(remaining, item.installment().getOutstandingMinor());
            remaining -= amount;
            proposals.add(new InstallmentProposal(item.installment().getId(), item.charge().getId(),
                    item.charge().getFeeTypeCode(), item.installment().getLabelEn(), item.installment().getDueDate(),
                    item.installment().getOutstandingMinor(), amount, item.installment().getStatus()));
        }
        List<BlockerView> blockers = new ArrayList<>();
        String periodCode = periods.findOpenForDate(request.paymentDate(), session.getId())
                .map(AccountingPeriod::getCode).orElse(null);
        if (periodCode == null) {
            blockers.add(new BlockerView("POSTING_PERIOD_CLOSED",
                    "Aucune période comptable ouverte de la session sélectionnée ne couvre cette date.",
                    "OPEN_ACCOUNTING_PERIOD"));
        }
        if (request.paymentDate().isBefore(enrollment.getEnrolledOn())
                || (enrollment.getExitedOn() != null && request.paymentDate().isAfter(enrollment.getExitedOn()))) {
            String end = enrollment.getExitedOn() == null ? "the active enrollment end" : enrollment.getExitedOn().toString();
            blockers.add(new BlockerView("PAYMENT_DATE_OUTSIDE_ENROLLMENT",
                    "Payment date must be within the student's enrollment window: "
                            + enrollment.getEnrolledOn() + " to " + end + ".",
                    "#payment-date"));
        }
        List<ChannelView> channelViews = channels.findBySchoolIdAndEnabledTrueOrderBySortOrderAscLabelFrAsc(TenantContext.get())
                .stream().map(this::channelView).toList();
        if (channelViews.isEmpty()) {
            blockers.add(new BlockerView("PAYMENT_CHANNEL_MISSING", "Aucun canal de paiement actif n'est configuré.",
                    "OPEN_PAYMENT_CHANNELS"));
        }
        if (student == null) blockers.add(new BlockerView("STUDENT_NOT_FOUND", "Le compte élève est introuvable.", "OPEN_STUDENTS"));
        return new PaymentQuoteView(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                session.getId(), enrollment.getClassNameSnapshot(), request.amountMinor(), credit,
                proposed, Math.max(0, request.amountMinor() - proposed), Math.max(0, openTotal - proposed), CURRENCY,
                blockers.stream().noneMatch(b -> "POSTING_PERIOD_CLOSED".equals(b.code())), periodCode,
                proposals, channelViews, blockers, treasury.listAccountsForWorkflow().stream()
                        .filter(TreasuryDtos.TreasuryAccountView::active)
                        .map(a -> new TreasuryAccountOption(a.id(), a.chartAccountId(), a.displayName(), a.kind(), a.currency(), a.balanceMinor()))
                        .toList());
    }

    @Transactional
    public PaymentView post(PaymentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED", "Une clé d'idempotence est obligatoire pour publier un encaissement.",
                    Map.of("idempotencyKey", "Fournissez l'en-tête Idempotency-Key."), List.of());
        }
        return idempotency.execute("finance-v2/collections/post", idempotencyKey.trim(), request,
                PaymentView.class, () -> postNow(request, idempotencyKey.trim()));
    }

    @Transactional
    public PaymentView postNow(PaymentRequest request, String idempotencyKey) {
        UUID schoolId = TenantContext.get();
        StudentEnrollment enrollment = requireEnrollment(request.enrollmentId());
        financePolicy.requireEnrollment("PAYMENT_COLLECT", request.enrollmentId(), request.paymentDate());
        Student student = requireStudent(enrollment.getStudentId());
        AcademicSession session = sessions.findByIdAndSchoolId(enrollment.getAcademicSessionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        if (!"ACTIVE".equalsIgnoreCase(enrollment.getStatus())) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE",
                    "L'inscription sélectionnée n'est plus active.", Map.of("enrollmentId", "Sélectionnez une inscription active."), List.of());
        }
        AccountingPeriod period = periods.requireOpenForDate(request.paymentDate(), session.getId());
        PaymentChannel channel = channels.findById(request.paymentChannelId())
                .filter(c -> schoolId.equals(c.getSchoolId()))
                .orElseThrow(() -> ApiException.notFound("Canal de paiement"));
        validateChannel(channel, request.reference());
        CashierSession cashier = null;
        if ("CASH".equalsIgnoreCase(channel.getCode())) {
            UUID userId = currentUserId();
            cashier = userId == null ? null : cashiers.findBySchoolIdAndCashierUserIdAndStatus(schoolId, userId, "OPEN").orElse(null);
            if (cashier == null) {
                throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "CASHIER_SESSION_REQUIRED",
                        "Un tiroir de caisse ouvert est requis pour un encaissement en espèces.",
                        Map.of("paymentChannel", "Ouvrez une session de caisse avant de continuer."), List.of());
            }
        }
        if (request.amountMinor() <= 0) throw fieldError("amountMinor", "Le montant doit être supérieur à zéro.");
        if (request.paymentDate().isBefore(enrollment.getEnrolledOn())
                || (enrollment.getExitedOn() != null && request.paymentDate().isAfter(enrollment.getExitedOn()))) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYMENT_DATE_OUTSIDE_ENROLLMENT",
                    "La date d'encaissement doit appartenir à la période de l'inscription.",
                    Map.of("paymentDate", "Choisissez une date comprise dans l'inscription."), List.of());
        }
        String reference = trim(request.reference());
        if (reference != null && jdbc.queryForObject("SELECT count(*) FROM finance_payment WHERE school_id=? AND channel_code_snapshot=? AND reference=?",
                Integer.class, schoolId, channel.getCode(), reference) > 0) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "PAYMENT_REFERENCE_DUPLICATE",
                    "Cette référence de paiement existe déjà pour ce canal.", Map.of("reference", "Utilisez une référence unique."), List.of());
        }

        List<OpenInstallment> open = openInstallments(enrollment.getId());
        Map<UUID, OpenInstallment> byInstallment = new LinkedHashMap<>();
        open.forEach(i -> byInstallment.put(i.installment().getId(), i));
        List<AllocationDecision> selected = requestedAllocations(request, open, byInstallment);
        long allocatedMinor = selected.stream().mapToLong(AllocationDecision::amountMinor).sum();
        if (allocatedMinor > request.amountMinor()) {
            throw fieldError("allocations", "Les allocations ne peuvent pas dépasser le montant reçu.");
        }
        long creditMinor = request.amountMinor() - allocatedMinor;
        TreasuryService.TreasuryRecord selectedTreasury = request.treasuryAccountId() == null
                ? treasury.findForChartAccountForWorkflow(channel.getDebitAccountId()) : treasury.requireActiveRecord(request.treasuryAccountId());
        ChartOfAccount debit = selectedTreasury != null
                ? requirePostingAccount(selectedTreasury.chartAccountId(), request.paymentDate(), "Compte de trésorerie", "ASSET")
                : requirePaymentDebitAccount(channel, request.paymentDate());
        ChartOfAccount receivable = requirePostingAccount("1100", request.paymentDate(), "Créances élèves", "ASSET");
        ChartOfAccount creditAccount = creditMinor > 0
                ? requirePostingAccount("2100", request.paymentDate(), "Crédits élèves", "LIABILITY")
                : null;

        String periodKey = String.valueOf(request.paymentDate().getYear());
        String receiptNo = sequences.allocate("RECEIPT", periodKey, "RCT/" + periodKey + "/", 6);
        FinancePayment payment = new FinancePayment();
        payment.setSchoolId(schoolId);
        payment.setStudentId(student.getId());
        payment.setStudentEnrollmentId(enrollment.getId());
        payment.setAcademicSessionId(session.getId());
        payment.setPaymentChannelId(channel.getId());
        payment.setTreasuryAccountId(selectedTreasury == null ? null : selectedTreasury.id());
        payment.setChannelCodeSnapshot(channel.getCode().trim().toUpperCase(Locale.ROOT));
        payment.setAmountMinor(request.amountMinor());
        payment.setCurrency(CURRENCY);
        payment.setPaymentDate(request.paymentDate());
        payment.setReference(reference);
        payment.setPayerName(trim(request.payerName()));
        payment.setNote(trim(request.note()));
        payment.setStatus("DRAFT");
        payment.setReceiptNo(receiptNo);
        payment.setLegacyReceiptNo(trim(request.legacyReceiptNo()));
        payment.setCashierSessionId(cashier == null ? null : cashier.getId());
        payment.setSourceEventKey("COLLECTION:" + idempotencyKey);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCreatedBy(currentUserId());
        payment = payments.saveAndFlush(payment);

        List<PaymentAllocation> savedAllocations = new ArrayList<>();
        for (AllocationDecision decision : selected) {
            OpenInstallment requested = byInstallment.get(decision.installmentId());
            ChargeInstallment installment = installments.findForUpdateByIdAndSchoolId(decision.installmentId(), schoolId)
                    .orElseThrow(() -> ApiException.notFound("Échéance"));
            if (requested == null || !requested.charge().getId().equals(installmentChargeId(requested))
                    || installment.getOutstandingMinor() < decision.amountMinor()) {
                throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "INSTALLMENT_CHANGED",
                        "Une échéance a changé avant la publication de l'encaissement.", Map.of("allocations", "Actualisez le devis."), List.of());
            }
            StudentCharge charge = charges.findForUpdateByIdAndSchoolId(requested.charge().getId(), schoolId)
                    .orElseThrow(() -> ApiException.notFound("Charge"));
            installment.setPaidMinor(installment.getPaidMinor() + decision.amountMinor());
            installment.setOutstandingMinor(installment.getOutstandingMinor() - decision.amountMinor());
            installment.setStatus(installment.getOutstandingMinor() == 0 ? "PAID" : "PARTIAL");
            installments.saveAndFlush(installment);
            charge.setPaidMinor(charge.getPaidMinor() + decision.amountMinor());
            charge.setOutstandingMinor(Math.max(0, charge.getOutstandingMinor() - decision.amountMinor()));
            charge.setStatus(charge.getOutstandingMinor() == 0 ? "PAID" : "PARTIAL");
            charges.saveAndFlush(charge);
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setSchoolId(schoolId);
            allocation.setPaymentId(payment.getId());
            allocation.setChargeInstallmentId(installment.getId());
            allocation.setStudentId(student.getId());
            allocation.setAllocatedMinor(decision.amountMinor());
            allocation.setCurrency(CURRENCY);
            savedAllocations.add(allocations.saveAndFlush(allocation));
        }
        financeDocuments.refreshInvoiceBalancesForInstallments(
                savedAllocations.stream().map(PaymentAllocation::getChargeInstallmentId).toList());

        List<JournalLineInput> lines = new ArrayList<>();
        if (allocatedMinor > 0) lines.add(new JournalLineInput(debit.getId(), allocatedMinor, 0,
                student.getId(), enrollment.getId(), null, enrollment.getSchoolClassId(), null,
                "Encaissement affecté " + receiptNo));
        if (creditMinor > 0) lines.add(new JournalLineInput(debit.getId(), creditMinor, 0,
                student.getId(), enrollment.getId(), null, enrollment.getSchoolClassId(), null,
                "Encaissement en crédit " + receiptNo));
        if (allocatedMinor > 0) lines.add(new JournalLineInput(receivable.getId(), 0, allocatedMinor,
                student.getId(), enrollment.getId(), null, enrollment.getSchoolClassId(), null,
                "Règlement de créances " + receiptNo));
        if (creditMinor > 0) lines.add(new JournalLineInput(creditAccount.getId(), 0, creditMinor,
                student.getId(), enrollment.getId(), null, enrollment.getSchoolClassId(), null,
                "Crédit élève " + receiptNo));
        var journal = ledger.createDraftInternal(new JournalUpsert(request.paymentDate(),
                "Encaissement " + receiptNo + " — " + studentName(student), CURRENCY, period.getId(),
                "PAYMENT", payment.getId().toString(), "PAYMENT:" + payment.getId(), lines, null));
        var postedJournal = ledger.postNowInternal(journal.id());
        if (creditMinor > 0) {
            StudentCreditLedger credit = new StudentCreditLedger();
            credit.setSchoolId(schoolId);
            credit.setStudentId(student.getId());
            credit.setStudentEnrollmentId(enrollment.getId());
            credit.setPaymentId(payment.getId());
            credit.setEntryType("CREATED");
            credit.setAmountMinor(creditMinor);
            credit.setCurrency(CURRENCY);
            credit.setSourceEventKey("CREDIT:" + payment.getId());
            credit.setEntryDate(request.paymentDate());
            credit.setReason("Excédent de l'encaissement " + receiptNo);
            credit.setCreatedBy(currentUserId());
            credits.saveAndFlush(credit);
        }
        payment.setJournalEntryId(postedJournal.id());
        payment.setStatus("POSTED");
        payment.setPostedAt(java.time.Instant.now());
        payment.setPostedBy(currentUserId());
        payment = payments.saveAndFlush(payment);
        if (cashier != null) refreshCashierExpected(cashier);
        financeDocuments.createReceiptForPayment(payment.getId());
        PaymentView result = view(payment, savedAllocations, allocatedMinor, creditMinor);
        audit.record("PAYMENT_POSTED", "FinancePayment", payment.getId().toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(PaymentListFilters filters) {
        financePolicy.requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        List<FinancePayment> source = filters != null && filters.studentId() != null
                ? payments.findBySchoolIdAndStudentIdOrderByPaymentDateDesc(schoolId, filters.studentId())
                : payments.findBySchoolIdOrderByPaymentDateDesc(schoolId);
        return source.stream()
                .filter(p -> filters == null || filters.status() == null || filters.status().isBlank()
                        || p.getStatus().equalsIgnoreCase(filters.status()))
                .filter(p -> filters == null || filters.channelCode() == null || filters.channelCode().isBlank()
                        || p.getChannelCodeSnapshot().equalsIgnoreCase(filters.channelCode()))
                .filter(p -> filters == null || filters.academicSessionId() == null
                        || filters.academicSessionId().equals(p.getAcademicSessionId()))
                .filter(p -> filters == null || filters.fromDate() == null || !p.getPaymentDate().isBefore(filters.fromDate()))
                .filter(p -> filters == null || filters.toDate() == null || !p.getPaymentDate().isAfter(filters.toDate()))
                .filter(p -> filters == null || filters.cashierSessionId() == null
                        || filters.cashierSessionId().equals(p.getCashierSessionId()))
                .filter(p -> filters == null || filters.reference() == null || filters.reference().isBlank()
                        || (p.getReference() != null && p.getReference().toLowerCase(Locale.ROOT).contains(filters.reference().toLowerCase(Locale.ROOT))))
                .map(p -> view(p, allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(schoolId, p.getId()),
                        allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(schoolId, p.getId()).stream().mapToLong(PaymentAllocation::getAllocatedMinor).sum(),
                        creditForPayment(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentView detail(UUID id) {
        UUID schoolId = TenantContext.get();
        FinancePayment payment = payments.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> ApiException.notFound("Encaissement"));
        financePolicy.requirePayment("PAYMENT_VIEW", id, payment.getPaymentDate());
        List<PaymentAllocation> rows = allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(schoolId, id);
        long allocated = rows.stream().mapToLong(PaymentAllocation::getAllocatedMinor).sum();
        return view(payment, rows, allocated, creditForPayment(id));
    }

    private List<AllocationDecision> requestedAllocations(PaymentRequest request, List<OpenInstallment> open,
                                                            Map<UUID, OpenInstallment> byInstallment) {
        if (request.allocations() == null || request.allocations().isEmpty()) {
            return CollectionAllocationPolicy.oldestDue(request.amountMinor(), open.stream()
                            .map(item -> new CollectionAllocationPolicy.DueInstallment(item.installment().getId(), item.installment().getOutstandingMinor()))
                            .toList()).allocations().stream()
                    .map(item -> new AllocationDecision(item.installmentId(), item.amountMinor())).toList();
        }
        Map<UUID, AllocationDecision> unique = new LinkedHashMap<>();
        for (AllocationInput input : request.allocations()) {
            if (input == null || input.installmentId() == null || input.amountMinor() <= 0) {
                throw fieldError("allocations", "Chaque allocation doit avoir une échéance et un montant positif.");
            }
            OpenInstallment item = byInstallment.get(input.installmentId());
            if (item == null) throw fieldError("allocations", "L'échéance sélectionnée n'appartient pas à ce compte.");
            if (input.amountMinor() > item.installment().getOutstandingMinor()) {
                throw fieldError("allocations", "Une allocation dépasse le solde de son échéance.");
            }
            if (unique.put(input.installmentId(), new AllocationDecision(input.installmentId(), input.amountMinor())) != null) {
                throw fieldError("allocations", "Une échéance ne peut apparaître qu'une seule fois.");
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<OpenInstallment> openInstallments(UUID enrollmentId) {
        UUID schoolId = TenantContext.get();
        Map<UUID, StudentCharge> chargeById = new HashMap<>();
        List<OpenInstallment> result = new ArrayList<>();
        for (StudentCharge charge : charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(schoolId, enrollmentId)) {
            chargeById.put(charge.getId(), charge);
            for (ChargeInstallment installment : installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(schoolId, charge.getId())) {
                if (installment.getOutstandingMinor() > 0) result.add(new OpenInstallment(charge, installment));
            }
        }
        result.sort(Comparator.comparing(i -> i.installment().getDueDate()));
        return result;
    }

    private ChannelView channelView(PaymentChannel channel) {
        ChartOfAccount account = channel.getDebitAccountId() == null ? null
                : accounts.findByIdAndSchoolId(channel.getDebitAccountId(), TenantContext.get()).orElse(null);
        return new ChannelView(channel.getId(), channel.getCode(), channel.getLabelFr(), channel.getLabelEn(),
                channel.isRequiresReference(), channel.isEnabled(), "CASH".equalsIgnoreCase(channel.getCode()),
                channel.getDebitAccountId(), account == null ? null : account.getCode(),
                account == null ? null : account.getNameFr(), CURRENCY);
    }

    private StudentSearchView searchView(FinancePayer payer, UUID schoolId) {
        long outstanding = 0, overdue = 0;
        LocalDate today = LocalDate.now();
        for (OpenInstallment item : openInstallments(payer.enrollmentId())) {
            outstanding += item.installment().getOutstandingMinor();
            if (item.installment().getDueDate().isBefore(today)) overdue += item.installment().getOutstandingMinor();
        }
        return new StudentSearchView(payer.studentId(), payer.studentName(), payer.matricule(),
                payer.enrollmentId(), payer.academicSessionId(), payer.className(),
                payer.enrolledOn(), payer.exitedOn(),
                outstanding, overdue);
    }

    private boolean contains(StudentSearchView v, String needle) {
        return contains(v.studentName(), needle) || contains(v.matricule(), needle)
                || contains(v.className(), needle);
    }

    private boolean contains(String value, String needle) { return value != null && value.toLowerCase(Locale.ROOT).contains(needle); }

    private StudentEnrollment requireEnrollment(UUID id) {
        if (id == null) throw fieldError("enrollmentId", "L'inscription est obligatoire.");
        return enrollments.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Inscription"));
    }

    private Student requireStudent(UUID id) {
        return students.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
    }

    private void validateChannel(PaymentChannel channel, String reference) {
        if (!channel.isEnabled()) throw ApiException.conflict("Ce canal de paiement est désactivé.");
        if (channel.isRequiresReference() && trim(reference) == null) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYMENT_REFERENCE_REQUIRED",
                    "Une référence est obligatoire pour ce canal.", Map.of("reference", "Saisissez la référence opérateur."), List.of());
        }
    }

    private ChartOfAccount requirePaymentDebitAccount(PaymentChannel channel, LocalDate date) {
        if (channel.getDebitAccountId() == null) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "PAYMENT_CHANNEL_MAPPING_MISSING",
                    "Le canal de paiement n'est pas relié à un compte comptable.", Map.of("paymentChannel", "Configurez le compte débité."), List.of());
        }
        return requirePostingAccount(channel.getDebitAccountId(), date, "Compte du canal de paiement", "ASSET");
    }

    private ChartOfAccount requirePostingAccount(String code, LocalDate date, String label) {
        return requirePostingAccount(code, date, label, "ASSET");
    }

    private ChartOfAccount requirePostingAccount(String code, LocalDate date, String label, String expectedType) {
        return accounts.findBySchoolIdAndCode(TenantContext.get(), code)
                .map(a -> requirePostingAccount(a, date, label, expectedType)).orElseThrow(() -> ApiException.structured(
                        org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_MAPPING_MISSING",
                        "Le compte comptable " + label + " n'est pas configuré.", Map.of(), List.of()));
    }

    private ChartOfAccount requirePostingAccount(UUID id, LocalDate date, String label) {
        return requirePostingAccount(id, date, label, "ASSET");
    }

    private ChartOfAccount requirePostingAccount(UUID id, LocalDate date, String label, String expectedType) {
        return accounts.findByIdAndSchoolId(id, TenantContext.get())
                .map(a -> requirePostingAccount(a, date, label, expectedType)).orElseThrow(() -> ApiException.notFound("Compte comptable"));
    }

    private ChartOfAccount requirePostingAccount(ChartOfAccount account, LocalDate date, String label) {
        return requirePostingAccount(account, date, label, "ASSET");
    }

    private ChartOfAccount requirePostingAccount(ChartOfAccount account, LocalDate date, String label, String expectedType) {
        boolean effective = (account.getEffectiveFrom() == null || !date.isBefore(account.getEffectiveFrom()))
                && (account.getEffectiveTo() == null || !date.isAfter(account.getEffectiveTo()));
        if (!account.isActive() || !account.isPostingAllowed() || !effective
                || !expectedType.equals(account.getAccountType()) || (account.getCurrency() != null && !CURRENCY.equals(account.getCurrency()))) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_NOT_POSTABLE",
                    "Le compte " + label + " n'est pas postable pour cette date et cette devise.", Map.of(), List.of());
        }
        return account;
    }

    private PaymentView view(FinancePayment payment, List<PaymentAllocation> rows, long allocated, long credit) {
        List<AllocationView> views = rows.stream().map(a -> new AllocationView(a.getId(), a.getChargeInstallmentId(),
                a.getAllocatedMinor(), a.getCurrency(), a.getStatus())).toList();
        long outstanding = charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(
                        TenantContext.get(), payment.getStudentEnrollmentId()).stream()
                .mapToLong(StudentCharge::getOutstandingMinor).sum();
        FinanceReceipt receipt = receipts.findBySchoolIdAndFinancePaymentId(TenantContext.get(), payment.getId()).orElse(null);
        return new PaymentView(payment.getId(), payment.getStudentId(), payment.getStudentEnrollmentId(),
                payment.getAcademicSessionId(), payment.getAmountMinor(), payment.getCurrency(), payment.getPaymentDate(),
                payment.getChannelCodeSnapshot(), payment.getReference(), payment.getStatus(), payment.getReceiptNo(),
                payment.getLegacyReceiptNo(), payment.getJournalEntryId(), allocated, credit, outstanding,
                payment.getVersion(), views, receipt == null ? null : receipt.getGeneratedDocumentId(),
                receipt == null ? null : receipt.getReceiptNumber(), receipt == null ? null : receipt.getStatus(),
                receipt == null ? null : receipt.getGenerationError(), payment.getTreasuryAccountId(),
                payment.getTreasuryAccountId() == null ? null : treasury.displayNameForWorkflow(payment.getTreasuryAccountId()));
    }

    private long creditForPayment(UUID paymentId) {
        return credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(),
                        payments.findByIdAndSchoolId(paymentId, TenantContext.get()).map(FinancePayment::getStudentId).orElse(null))
                .stream().filter(c -> paymentId.equals(c.getPaymentId())).mapToLong(c -> signed(c.getEntryType(), c.getAmountMinor())).sum();
    }

    private long availableCredit(UUID studentId) {
        return Math.max(0, credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(), studentId)
                .stream().mapToLong(c -> signed(c.getEntryType(), c.getAmountMinor())).sum());
    }

    private long signed(String type, long amount) { return "CREATED".equals(type) ? amount : -amount; }

    private void refreshCashierExpected(CashierSession cashier) {
        UUID schoolId = TenantContext.get();
        // Payment posting can run concurrently for one open drawer. Lock the
        // row before recomputing its denormalized expected cash, then update it
        // through JDBC so a stale managed @Version entity cannot leak a 500.
        jdbc.queryForObject("SELECT id FROM cashier_session WHERE school_id=? AND id=? FOR UPDATE",
                UUID.class, schoolId, cashier.getId());
        Long incoming = jdbc.queryForObject("SELECT coalesce(sum(amount_minor),0) FROM finance_payment WHERE school_id=? AND cashier_session_id=? AND status='POSTED' AND channel_code_snapshot='CASH'",
                Long.class, schoolId, cashier.getId());
        Long outgoing = jdbc.queryForObject("SELECT coalesce(sum(rt.amount_minor),0) FROM refund_transaction rt JOIN finance_payment p ON p.school_id=rt.school_id AND p.id=rt.payment_id WHERE rt.school_id=? AND p.cashier_session_id=? AND rt.channel_code='CASH'",
                Long.class, schoolId, cashier.getId());
        Long opening = jdbc.queryForObject("SELECT opening_cash_minor FROM cashier_session WHERE school_id=? AND id=?",
                Long.class, schoolId, cashier.getId());
        long expected = (opening == null ? 0 : opening) + (incoming == null ? 0 : incoming) - (outgoing == null ? 0 : outgoing);
        jdbc.update("UPDATE cashier_session SET expected_cash_minor=?, version=version+1, updated_at=now() WHERE school_id=? AND id=?",
                expected, schoolId, cashier.getId());
    }

    private static ApiException fieldError(String field, String message) {
        return ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                Map.of(field, message), List.of());
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private static String studentName(Student student) {
        return student == null ? "Élève" : (firstNonBlank(student.getFirstName(), "") + " " + firstNonBlank(student.getLastName(), "")).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String lower(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static UUID installmentChargeId(OpenInstallment item) { return item.charge().getId(); }

    private record OpenInstallment(StudentCharge charge, ChargeInstallment installment) {}
    private record AllocationDecision(UUID installmentId, long amountMinor) {}
}
