package com.bbc.sms.finance.charges;

import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import static com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import static com.bbc.sms.finance.charges.ChargeDtos.*;

/** Requests and approves non-cash reductions against a posted charge. */
@Service
public class ChargeAdjustmentService {
    private final StudentChargeRepository charges;
    private final ChargeInstallmentRepository installments;
    private final ChargeAdjustmentRepository adjustments;
    private final ChartOfAccountRepository accounts;
    private final AccountingPeriodService periods;
    private final LedgerPostingService ledger;
    private final AuditService audit;

    public ChargeAdjustmentService(StudentChargeRepository charges,
                                   ChargeInstallmentRepository installments,
                                   ChargeAdjustmentRepository adjustments,
                                   ChartOfAccountRepository accounts,
                                   AccountingPeriodService periods,
                                   LedgerPostingService ledger,
                                   AuditService audit) {
        this.charges = charges;
        this.installments = installments;
        this.adjustments = adjustments;
        this.accounts = accounts;
        this.periods = periods;
        this.ledger = ledger;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdjustmentImpact impact(UUID chargeId, AdjustmentRequest request) {
        StudentCharge charge = requireCharge(chargeId);
        List<String> blockers = new ArrayList<>();
        if (!List.of("WAIVER", "ADJUSTMENT").contains(normalize(request.adjustmentType()))) {
            blockers.add("Le type doit être WAIVER ou ADJUSTMENT.");
        }
        if (request.amountMinor() <= 0) blockers.add("Le montant doit être strictement positif.");
        if (request.amountMinor() > charge.getOutstandingMinor()) blockers.add("Le montant dépasse le solde restant dû.");
        if (request.installmentId() != null) {
            ChargeInstallment installment = installments.findByIdAndSchoolId(request.installmentId(), TenantContext.get()).orElse(null);
            if (installment == null || !installment.getChargeId().equals(chargeId)) blockers.add("L'échéance sélectionnée n'appartient pas à cette charge.");
            else if (request.amountMinor() > installment.getOutstandingMinor()) blockers.add("Le montant dépasse le solde de l'échéance.");
        }
        if (!validAccount(request.contraAccountId())) blockers.add("Le compte de contrepartie doit être actif et autorisé à recevoir des écritures.");
        return new AdjustmentImpact(chargeId, request.installmentId(), charge.getOutstandingMinor(), request.amountMinor(),
                Math.max(0, charge.getOutstandingMinor() - request.amountMinor()), blockers.isEmpty(), blockers);
    }

    @Transactional
    public AdjustmentView request(UUID chargeId, AdjustmentRequest request) {
        UUID schoolId = TenantContext.get();
        StudentCharge charge = charges.findForUpdateByIdAndSchoolId(chargeId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Charge"));
        if (request.version() != null && request.version() != charge.getVersion()) conflict("charge");
        AdjustmentImpact impact = impact(chargeId, request);
        if (!impact.allowed()) throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                "CHARGE_ADJUSTMENT_INVALID", "La demande d'ajustement ne peut pas être enregistrée.",
                Map.of("amountMinor", String.join(" ", impact.blockers())), List.of());
        ChargeAdjustment adjustment = new ChargeAdjustment();
        adjustment.setSchoolId(schoolId);
        adjustment.setChargeId(chargeId);
        adjustment.setInstallmentId(request.installmentId());
        adjustment.setAdjustmentType(normalize(request.adjustmentType()));
        adjustment.setAmountMinor(request.amountMinor());
        adjustment.setCurrency(charge.getCurrency());
        adjustment.setReason(request.reason().trim());
        adjustment.setEvidenceReference(blankToNull(request.evidenceReference()));
        adjustment.setContraAccountId(request.contraAccountId());
        adjustment.setEffectiveDate(request.effectiveDate());
        adjustment.setRequestedBy(currentUserId());
        adjustment = adjustments.saveAndFlush(adjustment);
        audit.record("CHARGE_ADJUSTMENT_REQUESTED", "ChargeAdjustment", adjustment.getId().toString(), null, view(adjustment), request.reason());
        return view(adjustment);
    }

    @Transactional
    public AdjustmentView decide(UUID id, AdjustmentDecisionRequest request) {
        UUID schoolId = TenantContext.get();
        ChargeAdjustment adjustment = adjustments.findForUpdateByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Demande d'ajustement"));
        if (request.version() != adjustment.getVersion()) conflict("demande d'ajustement");
        if (!"REQUESTED".equals(adjustment.getStatus())) throw ApiException.conflict("Cette demande a déjà été décidée et est immuable.");
        if (!request.approve()) {
            adjustment.setStatus("REJECTED");
            adjustment.setApprovedBy(currentUserId());
            adjustment.setApprovedAt(Instant.now());
            adjustment.setDecisionReason(request.decisionReason().trim());
            adjustment = adjustments.saveAndFlush(adjustment);
            audit.record("CHARGE_ADJUSTMENT_REJECTED", "ChargeAdjustment", id.toString(), null, view(adjustment), request.decisionReason());
            return view(adjustment);
        }
        StudentCharge charge = charges.findForUpdateByIdAndSchoolId(adjustment.getChargeId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Charge"));
        if (adjustment.getAmountMinor() > charge.getOutstandingMinor()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "CHARGE_OUTSTANDING_CHANGED",
                    "Le solde de la charge a changé; la demande dépasse maintenant le solde disponible.",
                    Map.of("amountMinor", "Rechargez le solde avant approbation."), List.of());
        }
        if (adjustment.getInstallmentId() != null) {
            ChargeInstallment installment = installments.findByIdAndSchoolId(adjustment.getInstallmentId(), schoolId)
                    .orElseThrow(() -> ApiException.notFound("Échéance"));
            if (!installment.getChargeId().equals(charge.getId()) || adjustment.getAmountMinor() > installment.getOutstandingMinor()) {
                throw ApiException.conflict("Le solde de l'échéance a changé; rechargez la demande.");
            }
        }
        AccountingPeriod period = periods.requireOpenForDate(adjustment.getEffectiveDate(), charge.getAcademicSessionId());
        var journal = ledger.createDraft(new JournalUpsert(adjustment.getEffectiveDate(),
                "Ajustement charge " + charge.getFeeTypeCode(), charge.getCurrency(), period.getId(),
                "STUDENT_CHARGE_ADJUSTMENT", adjustment.getId().toString(), "CHARGE_ADJUSTMENT:" + adjustment.getId(), List.of(
                new JournalLineInput(adjustment.getContraAccountId(), adjustment.getAmountMinor(), 0,
                        charge.getStudentId(), charge.getStudentEnrollmentId(), null, charge.getSchoolClassIdSnapshot(), charge.getFeeTypeCode(),
                        adjustment.getAdjustmentType()),
                new JournalLineInput(charge.getReceivableAccountId(), 0, adjustment.getAmountMinor(),
                        charge.getStudentId(), charge.getStudentEnrollmentId(), null, charge.getSchoolClassIdSnapshot(), charge.getFeeTypeCode(),
                        "Réduction créance")), null));
        ledger.post(journal.id(), "CHARGE-ADJUSTMENT-JOURNAL:" + adjustment.getId());
        adjustment.setStatus("POSTED");
        adjustment.setApprovedBy(currentUserId());
        adjustment.setApprovedAt(Instant.now());
        adjustment.setDecisionReason(request.decisionReason().trim());
        adjustment.setJournalEntryId(journal.id());
        adjustment = adjustments.saveAndFlush(adjustment);
        applyReduction(charge, adjustment);
        charges.saveAndFlush(charge);
        audit.record("CHARGE_ADJUSTMENT_APPROVED", "ChargeAdjustment", id.toString(), null, view(adjustment), request.decisionReason());
        return view(adjustment);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentView> list(UUID chargeId) {
        requireCharge(chargeId);
        return adjustments.findBySchoolIdAndChargeIdOrderByCreatedAtAsc(TenantContext.get(), chargeId).stream().map(this::view).toList();
    }

    private void applyReduction(StudentCharge charge, ChargeAdjustment adjustment) {
        long remaining = adjustment.getAmountMinor();
        List<ChargeInstallment> target = adjustment.getInstallmentId() == null
                ? installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(TenantContext.get(), charge.getId())
                : installments.findByIdAndSchoolId(adjustment.getInstallmentId(), TenantContext.get()).stream().toList();
        for (ChargeInstallment installment : target) {
            if (remaining <= 0) break;
            long reduction = Math.min(remaining, installment.getOutstandingMinor());
            installment.setWaivedMinor(Math.addExact(installment.getWaivedMinor(), reduction));
            installment.setOutstandingMinor(Math.subtractExact(installment.getOutstandingMinor(), reduction));
            installment.setStatus(installment.getOutstandingMinor() == 0 ? "WAIVED" : "PARTIAL");
            installments.saveAndFlush(installment);
            remaining -= reduction;
        }
        if (remaining > 0) throw ApiException.conflict("La réduction ne peut pas rendre le solde négatif.");
        charge.setWaivedMinor(Math.addExact(charge.getWaivedMinor(), adjustment.getAmountMinor()));
        charge.setOutstandingMinor(Math.subtractExact(charge.getOutstandingMinor(), adjustment.getAmountMinor()));
        charge.setStatus(charge.getOutstandingMinor() == 0 ? "WAIVED" : "PARTIAL");
    }

    private StudentCharge requireCharge(UUID id) { return charges.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Charge")); }
    private boolean validAccount(UUID id) { return id != null && accounts.findByIdAndSchoolId(id, TenantContext.get()).map(a -> a.isActive() && a.isPostingAllowed()).orElse(false); }
    private AdjustmentView view(ChargeAdjustment a) { return new AdjustmentView(a.getId(), a.getChargeId(), a.getInstallmentId(), a.getAdjustmentType(), a.getAmountMinor(), a.getCurrency(), a.getReason(), a.getEvidenceReference(), a.getContraAccountId(), a.getEffectiveDate(), a.getStatus(), a.getRequestedBy(), a.getApprovedBy(), a.getDecisionReason(), a.getJournalEntryId(), a.getVersion()); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
    private void conflict(String label) { throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "OPTIMISTIC_CONFLICT", "Le " + label + " a changé ailleurs. Rechargez avant de réessayer.", Map.of("version", "Rechargez la donnée avant de continuer."), List.of()); }
}
