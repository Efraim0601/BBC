package com.bbc.sms.finance.collections;

import com.bbc.sms.finance.PaymentChannel;
import com.bbc.sms.finance.PaymentChannelRepository;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.accounting.JournalEntry;
import com.bbc.sms.finance.accounting.JournalEntryRepository;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalView;
import com.bbc.sms.finance.accounting.AccountingDtos.ReverseRequest;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.finance.documents.FinanceDocumentService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

/** Maker-checker corrections for immutable posted collections. */
@Service
public class PaymentCorrectionService {
    private final FinancePaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final StudentCreditLedgerRepository credits;
    private final PaymentReversalRequestRepository reversals;
    private final RefundRequestRepository refundRequests;
    private final RefundTransactionRepository refundTransactions;
    private final com.bbc.sms.finance.charges.ChargeInstallmentRepository installments;
    private final com.bbc.sms.finance.charges.StudentChargeRepository charges;
    private final PaymentChannelRepository channels;
    private final ChartOfAccountRepository accounts;
    private final JournalEntryRepository journals;
    private final AccountingPeriodService periods;
    private final LedgerPostingService ledger;
    private final DocumentSequenceService sequences;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final FinanceDocumentService financeDocuments;
    private final FinancePolicyService financePolicy;

    public PaymentCorrectionService(FinancePaymentRepository payments,
                                    PaymentAllocationRepository allocations,
                                    StudentCreditLedgerRepository credits,
                                    PaymentReversalRequestRepository reversals,
                                    RefundRequestRepository refundRequests,
                                    RefundTransactionRepository refundTransactions,
                                    com.bbc.sms.finance.charges.ChargeInstallmentRepository installments,
                                    com.bbc.sms.finance.charges.StudentChargeRepository charges,
                                    PaymentChannelRepository channels,
                                    ChartOfAccountRepository accounts,
                                    JournalEntryRepository journals,
                                    AccountingPeriodService periods,
                                    LedgerPostingService ledger,
                                    DocumentSequenceService sequences,
                                    IdempotencyService idempotency,
                                    AuditService audit,
                                    FinanceDocumentService financeDocuments,
                                    FinancePolicyService financePolicy) {
        this.payments = payments;
        this.allocations = allocations;
        this.credits = credits;
        this.reversals = reversals;
        this.refundRequests = refundRequests;
        this.refundTransactions = refundTransactions;
        this.installments = installments;
        this.charges = charges;
        this.channels = channels;
        this.accounts = accounts;
        this.journals = journals;
        this.periods = periods;
        this.ledger = ledger;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.audit = audit;
        this.financeDocuments = financeDocuments;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public ReversalPreview reversalPreview(UUID paymentId) {
        FinancePayment payment = requirePayment(paymentId);
        financePolicy.requirePayment("PAYMENT_REVERSE", paymentId, payment.getPaymentDate());
        List<PaymentAllocation> active = allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(TenantContext.get(), paymentId)
                .stream().filter(a -> "ACTIVE".equals(a.getStatus())).toList();
        long allocated = active.stream().mapToLong(PaymentAllocation::getAllocatedMinor).sum();
        long credit = availableCreditForPayment(paymentId);
        List<BlockerView> blockers = new ArrayList<>();
        if (!"POSTED".equals(payment.getStatus()) && !"PARTIALLY_REFUNDED".equals(payment.getStatus())) {
            blockers.add(new BlockerView("PAYMENT_NOT_POSTED", "Cet encaissement n'est pas dans un état réversible.", "OPEN_PAYMENT"));
        }
        if (!refundTransactions.findBySchoolIdAndPaymentId(TenantContext.get(), paymentId).isEmpty()) {
            blockers.add(new BlockerView("REFUND_ALREADY_POSTED", "Un remboursement existe déjà; utilisez le flux de correction du remboursement.", "OPEN_REFUNDS"));
        }
        if (consumedCreditForPayment(paymentId) > 0) {
            blockers.add(new BlockerView("CREDIT_ALREADY_CONSUMED", "Un crédit issu de cet encaissement a déjà été consommé.", "OPEN_STUDENT_ACCOUNT"));
        }
        return new ReversalPreview(paymentId, payment.getReceiptNo(), payment.getAmountMinor(), allocated, credit,
                blockers.isEmpty(), blockers);
    }

    @Transactional
    public PaymentView reverse(UUID paymentId, ReversalRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Une clé d'idempotence est obligatoire pour une correction.");
        }
        return idempotency.execute("finance-v2/collections/reverse", idempotencyKey, new Object[]{paymentId, request},
                PaymentView.class, () -> reverseNow(paymentId, request));
    }

    @Transactional
    public PaymentView reverseNow(UUID paymentId, ReversalRequest request) {
        UUID schoolId = TenantContext.get();
        FinancePayment payment = payments.findForUpdateByIdAndSchoolId(paymentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Encaissement"));
        financePolicy.requirePayment("PAYMENT_REVERSE", paymentId, payment.getPaymentDate());
        UUID actor = currentUserId();
        if (payment.getCreatedBy() != null && payment.getCreatedBy().equals(actor)) {
            throw ApiException.forbidden("La personne qui a enregistré l'encaissement ne peut pas le renverser.");
        }
        AccountVersion.require(request.version(), payment.getVersion(), "encaissement");
        ReversalPreview preview = reversalPreview(paymentId);
        if (!preview.allowed()) throw blocked("PAYMENT_REVERSAL_BLOCKED", "Cet encaissement ne peut pas être renversé.", preview.blockers());
        if (payment.getJournalEntryId() == null) throw ApiException.conflict("Le journal de l'encaissement est introuvable.");
        JournalEntry original = journals.findByIdAndSchoolId(payment.getJournalEntryId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Journal de l'encaissement"));
        // A correction belongs to the payment's academic/accounting context.
        // Using the server's current date rejects valid future-session payment
        // corrections before the open posting period can be resolved.
        LocalDate date = payment.getPaymentDate();
        AccountingPeriod period = periods.requireOpenForDate(date, payment.getAcademicSessionId());
        JournalView reversalJournal = ledger.reverseNowInternal(original.getId(),
                new ReverseRequest(date, request.reason().trim(), original.getVersion()));
        List<UUID> affectedInstallments = new ArrayList<>();
        for (PaymentAllocation allocation : allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(schoolId, paymentId)) {
            if (!"ACTIVE".equals(allocation.getStatus())) continue;
            affectedInstallments.add(allocation.getChargeInstallmentId());
            var installment = installments.findForUpdateByIdAndSchoolId(allocation.getChargeInstallmentId(), schoolId)
                    .orElseThrow(() -> ApiException.notFound("Échéance"));
            installment.setPaidMinor(Math.max(0, installment.getPaidMinor() - allocation.getAllocatedMinor()));
            installment.setOutstandingMinor(installment.getOutstandingMinor() + allocation.getAllocatedMinor());
            installment.setStatus(installment.getPaidMinor() == 0 ? "OPEN" : "PARTIAL");
            installments.saveAndFlush(installment);
            var charge = charges.findByIdAndSchoolId(findChargeId(allocation.getChargeInstallmentId()), schoolId).orElse(null);
            if (charge != null) {
                charge.setPaidMinor(Math.max(0, charge.getPaidMinor() - allocation.getAllocatedMinor()));
                charge.setOutstandingMinor(charge.getOutstandingMinor() + allocation.getAllocatedMinor());
                charge.setStatus(charge.getPaidMinor() == 0 ? "POSTED" : "PARTIAL");
                charges.saveAndFlush(charge);
            }
            allocation.setStatus("REVERSED");
            allocations.saveAndFlush(allocation);
        }
        financeDocuments.refreshInvoiceBalancesForInstallments(affectedInstallments);
        reverseRemainingCredit(paymentId, payment.getStudentId(), payment.getStudentEnrollmentId(), date);
        PaymentReversalRequest reversal = new PaymentReversalRequest();
        reversal.setSchoolId(schoolId);
        reversal.setPaymentId(paymentId);
        reversal.setReversalNo(sequences.allocate("PAYMENT_REVERSAL", String.valueOf(date.getYear()), "REV/" + date.getYear() + "/", 6));
        reversal.setStatus("POSTED");
        reversal.setReason(request.reason().trim());
        reversal.setRequestedBy(currentUserId());
        reversal.setApprovedBy(currentUserId());
        reversal.setApprovedAt(Instant.now());
        reversal.setDecisionReason(request.reason().trim());
        reversal.setJournalEntryId(reversalJournal.id());
        reversal.setPostedAt(Instant.now());
        reversals.saveAndFlush(reversal);
        payment.setStatus("REVERSED");
        payment.setJournalEntryId(payment.getJournalEntryId());
        payment = payments.saveAndFlush(payment);
        PaymentView result = new CollectionServiceViewAdapter(payments, allocations, charges).view(payment);
        audit.record("PAYMENT_REVERSED", "FinancePayment", paymentId.toString(), null, result, request.reason());
        return result;
    }

    @Transactional
    public RefundView requestRefund(UUID paymentId, RefundCreateRequest request) {
        FinancePayment payment = requirePayment(paymentId);
        financePolicy.requirePayment("REFUND_REQUEST", paymentId, payment.getPaymentDate());
        if (!"POSTED".equals(payment.getStatus()) && !"PARTIALLY_REFUNDED".equals(payment.getStatus())) {
            throw ApiException.conflict("Seul un encaissement posté peut faire l'objet d'un remboursement.");
        }
        long available = availableCreditForPayment(paymentId);
        if (request.amountMinor() > available) {
            throw blocked("REFUND_NOT_AVAILABLE", "Le montant disponible à rembourser est de " + available + " XAF. Les montants affectés nécessitent d'abord un renversement.",
                    List.of(new BlockerView("REFUND_NOT_AVAILABLE", "Crédit disponible insuffisant", "REVERSE_OR_ADJUST_PAYMENT")));
        }
        String channelCode = request.channelCode().trim().toUpperCase();
        PaymentChannel channel = channels.findBySchoolIdAndCode(TenantContext.get(), channelCode)
                .orElseThrow(() -> ApiException.notFound("Canal de remboursement"));
        if (!channel.isEnabled()) throw ApiException.conflict("Le canal de remboursement est désactivé.");
        if (channel.isRequiresReference() && trim(request.reference()) == null) throw ApiException.structured(
                org.springframework.http.HttpStatus.BAD_REQUEST, "REFUND_REFERENCE_REQUIRED", "Une référence est obligatoire pour ce remboursement.",
                Map.of("reference", "Saisissez la référence opérateur."), List.of());
        RefundRequest refund = new RefundRequest();
        refund.setSchoolId(TenantContext.get());
        refund.setPaymentId(paymentId);
        refund.setAmountMinor(request.amountMinor());
        refund.setCurrency(payment.getCurrency());
        refund.setChannelCode(channelCode);
        refund.setReference(trim(request.reference()));
        refund.setReason(request.reason().trim());
        refund.setRequestedBy(currentUserId());
        refund.setStatus("REQUESTED");
        refund = refundRequests.saveAndFlush(refund);
        RefundView result = view(refund);
        audit.record("REFUND_REQUESTED", "RefundRequest", refund.getId().toString(), null, result, request.reason());
        return result;
    }

    @Transactional
    public RefundView decideRefund(UUID refundId, RefundDecisionRequest request) {
        RefundRequest refund = refundRequests.findForUpdateByIdAndSchoolId(refundId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Demande de remboursement"));
        FinancePayment payment = payments.findForUpdateByIdAndSchoolId(refund.getPaymentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Encaissement"));
        financePolicy.requirePayment("REFUND_APPROVE", refund.getPaymentId(), payment.getPaymentDate());
        AccountVersion.require(request.version(), refund.getVersion(), "demande de remboursement");
        if (!"REQUESTED".equals(refund.getStatus())) return view(refund);
        UUID actor = currentUserId();
        if (request.approve() && actor != null && actor.equals(refund.getRequestedBy())) {
            throw ApiException.forbidden("La personne qui demande un remboursement ne peut pas l'approuver.");
        }
        refund.setDecisionReason(request.decisionReason().trim());
        refund.setApprovedBy(actor);
        refund.setApprovedAt(Instant.now());
        if (!request.approve()) {
            refund.setStatus("REJECTED");
            refund = refundRequests.saveAndFlush(refund);
            RefundView result = view(refund);
            audit.record("REFUND_REJECTED", "RefundRequest", refundId.toString(), null, result, request.decisionReason());
            return result;
        }
        if (refund.getAmountMinor() > availableCreditForPayment(payment.getId())) {
            throw ApiException.conflict("Le crédit disponible a changé; le remboursement ne peut plus être approuvé.");
        }
        LocalDate date = payment.getPaymentDate();
        AccountingPeriod period = periods.requireOpenForDate(date, payment.getAcademicSessionId());
        PaymentChannel channel = channels.findBySchoolIdAndCode(TenantContext.get(), refund.getChannelCode())
                .orElseThrow(() -> ApiException.notFound("Canal de remboursement"));
        ChartOfAccount credit = requireAccount("2100", date, "LIABILITY");
        ChartOfAccount debit = channel.getDebitAccountId() == null ? null : accounts.findByIdAndSchoolId(channel.getDebitAccountId(), TenantContext.get()).orElse(null);
        if (debit == null || !isPostable(debit, date, "ASSET")) throw ApiException.conflict("Le compte du canal de remboursement n'est pas postable.");
        refund.setRefundNo(sequences.allocate("REFUND", String.valueOf(date.getYear()), "RFN/" + date.getYear() + "/", 6));
        refund = refundRequests.saveAndFlush(refund);
        var journal = ledger.createDraftInternal(new com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert(date,
                "Remboursement " + refund.getRefundNo(), payment.getCurrency(), period.getId(), "REFUND",
                refund.getId().toString(), "REFUND:" + refund.getId(), List.of(
                new JournalLineInput(credit.getId(), refund.getAmountMinor(), 0, payment.getStudentId(), payment.getStudentEnrollmentId(), null, null, null, "Crédit élève"),
                new JournalLineInput(debit.getId(), 0, refund.getAmountMinor(), payment.getStudentId(), payment.getStudentEnrollmentId(), null, null, null, "Sortie remboursement")), null));
        var posted = ledger.postNowInternal(journal.id());
        RefundTransaction transaction = new RefundTransaction();
        transaction.setSchoolId(TenantContext.get());
        transaction.setRefundRequestId(refund.getId());
        transaction.setPaymentId(payment.getId());
        transaction.setRefundNo(refund.getRefundNo());
        transaction.setAmountMinor(refund.getAmountMinor());
        transaction.setCurrency(refund.getCurrency());
        transaction.setChannelCode(refund.getChannelCode());
        transaction.setReference(refund.getReference());
        transaction.setJournalEntryId(posted.id());
        transaction.setPostedBy(actor);
        refundTransactions.saveAndFlush(transaction);
        consumeCredit(payment, refund.getAmountMinor(), date, "REFUND:" + refund.getId(), "Remboursement " + refund.getRefundNo());
        refund.setStatus("POSTED");
        refund.setJournalEntryId(posted.id());
        refund.setPostedAt(Instant.now());
        refund = refundRequests.saveAndFlush(refund);
        long refunded = refundTransactions.findBySchoolIdAndPaymentId(TenantContext.get(), payment.getId()).stream().mapToLong(RefundTransaction::getAmountMinor).sum();
        payment.setStatus(refunded >= payment.getAmountMinor() ? "REFUNDED" : "PARTIALLY_REFUNDED");
        payments.saveAndFlush(payment);
        RefundView result = view(refund);
        audit.record("REFUND_POSTED", "RefundRequest", refundId.toString(), null, result, request.decisionReason());
        return result;
    }

    @Transactional(readOnly = true)
    public List<RefundView> refunds(UUID paymentId) {
        FinancePayment payment = requirePayment(paymentId);
        financePolicy.requirePayment("PAYMENT_VIEW", paymentId, payment.getPaymentDate());
        return refundRequests.findBySchoolIdAndPaymentIdOrderByRequestedAtDesc(TenantContext.get(), paymentId).stream().map(this::view).toList();
    }

    private long availableCreditForPayment(UUID paymentId) {
        List<StudentCreditLedger> all = credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(),
                payments.findByIdAndSchoolId(paymentId, TenantContext.get()).map(FinancePayment::getStudentId).orElse(null));
        List<UUID> sources = all.stream().filter(c -> paymentId.equals(c.getPaymentId()) && "CREATED".equals(c.getEntryType())).map(StudentCreditLedger::getId).toList();
        return Math.max(0, all.stream().filter(c -> sources.contains(c.getId()) || (c.getSourceCreditId() != null && sources.contains(c.getSourceCreditId())))
                .mapToLong(c -> "CREATED".equals(c.getEntryType()) ? c.getAmountMinor() : -c.getAmountMinor()).sum());
    }

    private long consumedCreditForPayment(UUID paymentId) {
        FinancePayment payment = requirePayment(paymentId);
        List<StudentCreditLedger> all = credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(), payment.getStudentId());
        List<UUID> sources = all.stream().filter(c -> paymentId.equals(c.getPaymentId()) && "CREATED".equals(c.getEntryType())).map(StudentCreditLedger::getId).toList();
        return all.stream().filter(c -> sources.contains(c.getSourceCreditId()) && "CONSUMED".equals(c.getEntryType())).mapToLong(StudentCreditLedger::getAmountMinor).sum();
    }

    private void reverseRemainingCredit(UUID paymentId, UUID studentId, UUID enrollmentId, LocalDate date) {
        List<StudentCreditLedger> all = credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(), studentId);
        for (StudentCreditLedger source : all.stream().filter(c -> paymentId.equals(c.getPaymentId()) && "CREATED".equals(c.getEntryType())).toList()) {
            long available = all.stream().filter(c -> source.getId().equals(c.getId()) || source.getId().equals(c.getSourceCreditId()))
                    .mapToLong(c -> c.getId().equals(source.getId()) ? c.getAmountMinor() : -c.getAmountMinor()).sum();
            if (available <= 0) continue;
            consumeCredit(paymentId, available, date, "PAYMENT_REVERSAL_CREDIT:" + paymentId, "Crédit annulé lors du renversement");
        }
    }

    private void consumeCredit(FinancePayment payment, long amount, LocalDate date, String sourceKey, String reason) {
        consumeCredit(payment.getId(), amount, date, sourceKey, reason);
    }

    private void consumeCredit(UUID paymentId, long amount, LocalDate date, String sourceKey, String reason) {
        FinancePayment payment = requirePayment(paymentId);
        StudentCreditLedger movement = new StudentCreditLedger();
        movement.setSchoolId(TenantContext.get());
        movement.setStudentId(payment.getStudentId());
        movement.setStudentEnrollmentId(payment.getStudentEnrollmentId());
        movement.setPaymentId(null);
        credits.findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(TenantContext.get(), payment.getStudentId()).stream()
                .filter(c -> paymentId.equals(c.getPaymentId()) && "CREATED".equals(c.getEntryType()))
                .findFirst().ifPresent(source -> movement.setSourceCreditId(source.getId()));
        movement.setEntryType(sourceKey.startsWith("REFUND:") ? "REFUNDED" : "REVERSED");
        movement.setAmountMinor(amount);
        movement.setCurrency(payment.getCurrency());
        movement.setSourceEventKey(sourceKey);
        movement.setEntryDate(date);
        movement.setReason(reason);
        movement.setCreatedBy(currentUserId());
        credits.saveAndFlush(movement);
    }

    private UUID findChargeId(UUID installmentId) {
        return installments.findByIdAndSchoolId(installmentId, TenantContext.get()).map(i -> {
            // ChargeInstallment intentionally stores only the tenant-safe charge id.
            return i.getChargeId();
        }).orElse(null);
    }

    private FinancePayment requirePayment(UUID id) { return payments.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Encaissement")); }
    private ChartOfAccount requireAccount(String code, LocalDate date, String expectedType) {
        return accounts.findBySchoolIdAndCode(TenantContext.get(), code).filter(a -> isPostable(a, date, expectedType))
                .orElseThrow(() -> ApiException.conflict("Le compte comptable " + code + " n'est pas postable."));
    }
    private boolean isPostable(ChartOfAccount account, LocalDate date, String expectedType) {
        boolean effective = (account.getEffectiveFrom() == null || !date.isBefore(account.getEffectiveFrom()))
                && (account.getEffectiveTo() == null || !date.isAfter(account.getEffectiveTo()));
        return account.isActive() && account.isPostingAllowed() && effective
                && expectedType.equals(account.getAccountType())
                && (account.getCurrency() == null || "XAF".equals(account.getCurrency()));
    }
    private RefundView view(RefundRequest r) { return new RefundView(r.getId(), r.getPaymentId(), r.getRefundNo(), r.getAmountMinor(), r.getCurrency(), r.getChannelCode(), r.getReference(), r.getStatus(), r.getReason(), r.getRequestedBy(), r.getApprovedBy(), r.getJournalEntryId(), r.getVersion()); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
    private static ApiException blocked(String code, String message, List<BlockerView> blockers) { return ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, code, message, Map.of(), blockers.stream().map(b -> new ApiException.Blocker("COLLECTION", b.code(), b.message(), b.actionLink())).toList()); }

    private static final class AccountVersion {
        static void require(long expected, long actual, String label) { if (expected != actual) throw ApiException.conflict("La version de " + label + " a changé. Actualisez avant de continuer."); }
    }

    /** Small read adapter keeps the correction service independent of command posting internals. */
    private static final class CollectionServiceViewAdapter {
        private final FinancePaymentRepository payments; private final PaymentAllocationRepository allocations; private final com.bbc.sms.finance.charges.StudentChargeRepository charges;
        CollectionServiceViewAdapter(FinancePaymentRepository p, PaymentAllocationRepository a, com.bbc.sms.finance.charges.StudentChargeRepository c) { payments=p; allocations=a; charges=c; }
        PaymentView view(FinancePayment payment) { var rows=allocations.findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(TenantContext.get(), payment.getId()); long allocated=rows.stream().mapToLong(PaymentAllocation::getAllocatedMinor).sum(); return new PaymentView(payment.getId(),payment.getStudentId(),payment.getStudentEnrollmentId(),payment.getAcademicSessionId(),payment.getAmountMinor(),payment.getCurrency(),payment.getPaymentDate(),payment.getChannelCodeSnapshot(),payment.getReference(),payment.getStatus(),payment.getReceiptNo(),payment.getLegacyReceiptNo(),payment.getJournalEntryId(),allocated,payment.getAmountMinor()-allocated,charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(TenantContext.get(),payment.getStudentEnrollmentId()).stream().mapToLong(com.bbc.sms.finance.charges.StudentCharge::getOutstandingMinor).sum(),payment.getVersion(),rows.stream().map(a->new AllocationView(a.getId(),a.getChargeInstallmentId(),a.getAllocatedMinor(),a.getCurrency(),a.getStatus())).toList(),null,null,null,null,payment.getTreasuryAccountId(),null); }
    }
}
