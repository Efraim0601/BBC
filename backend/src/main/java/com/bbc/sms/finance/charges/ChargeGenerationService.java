package com.bbc.sms.finance.charges;

import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.fees.FeeType;
import com.bbc.sms.finance.fees.FeeTypeRepository;
import com.bbc.sms.finance.fees.FeeTypeRevision;
import com.bbc.sms.finance.fees.FeeTypeRevisionRepository;
import com.bbc.sms.finance.plans.FeePlan;
import com.bbc.sms.finance.plans.FeePlanLine;
import com.bbc.sms.finance.plans.FeePlanLineRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import static com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import static com.bbc.sms.finance.charges.ChargeDtos.*;

/** Generates immutable charge snapshots and posts their accrual journals. */
@Service
public class ChargeGenerationService {
    private final ChargeGenerationPreviewService previewService;
    private final ChargeGenerationJobRepository jobs;
    private final ChargeGenerationResultRepository results;
    private final StudentChargeRepository charges;
    private final ChargeInstallmentRepository installments;
    private final StudentEnrollmentRepository enrollments;
    private final AcademicSessionRepository sessions;
    private final FeePlanLineRepository planLines;
    private final FeeTypeRepository feeTypes;
    private final FeeTypeRevisionRepository revisions;
    private final AccountingPeriodService periods;
    private final LedgerPostingService ledger;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public ChargeGenerationService(ChargeGenerationPreviewService previewService,
                                   ChargeGenerationJobRepository jobs,
                                   ChargeGenerationResultRepository results,
                                   StudentChargeRepository charges,
                                   ChargeInstallmentRepository installments,
                                   StudentEnrollmentRepository enrollments,
                                   AcademicSessionRepository sessions,
                                   FeePlanLineRepository planLines,
                                   FeeTypeRepository feeTypes,
                                   FeeTypeRevisionRepository revisions,
                                   AccountingPeriodService periods,
                                   LedgerPostingService ledger,
                                   IdempotencyService idempotency,
                                   AuditService audit) {
        this.previewService = previewService;
        this.jobs = jobs;
        this.results = results;
        this.charges = charges;
        this.installments = installments;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.planLines = planLines;
        this.feeTypes = feeTypes;
        this.revisions = revisions;
        this.periods = periods;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public GenerationPreview preview(GenerationRequest request) {
        return previewService.preview(request);
    }

    @Transactional
    public GenerationJobView generate(GenerationRequest request, String idempotencyKey) {
        return idempotency.execute("finance-v2/charges/generate", idempotencyKey, request,
                GenerationJobView.class, () -> generateNow(request, idempotencyKey));
    }

    @Transactional
    public GenerationJobView retry(UUID jobId, String idempotencyKey) {
        ChargeGenerationJob job = requireJob(jobId);
        GenerationRequest request = new GenerationRequest(job.getAcademicSessionId(), job.getSchoolClassId(),
                job.getLevel(), job.getSubsystem(), job.getChargeDate(), job.getProrationPolicy(), job.getTransferPolicy());
        return generate(request, idempotencyKey == null ? "RETRY:" + jobId + ":" + Instant.now().toEpochMilli() : idempotencyKey);
    }

    @Transactional(readOnly = true)
    public GenerationJobView job(UUID id) { return view(requireJob(id)); }

    @Transactional(readOnly = true)
    public List<GenerationJobView> jobs() {
        return jobs.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<GenerationResultView> results(UUID jobId) {
        requireJob(jobId);
        return results.findBySchoolIdAndJobIdOrderByCreatedAtAsc(TenantContext.get(), jobId)
                .stream().map(this::resultView).toList();
    }

    private GenerationJobView generateNow(GenerationRequest request, String idempotencyKey) {
        UUID schoolId = TenantContext.get();
        GenerationPreview preview = previewService.preview(request);
        ChargeGenerationJob job = new ChargeGenerationJob();
        job.setSchoolId(schoolId);
        job.setAcademicSessionId(request.academicSessionId());
        job.setSchoolClassId(request.schoolClassId());
        job.setLevel(blankToNull(request.level()));
        job.setSubsystem(blankToNull(request.subsystem()));
        job.setChargeDate(request.chargeDate());
        job.setProrationPolicy(preview.prorationPolicy());
        job.setTransferPolicy(preview.transferPolicy());
        job.setStatus("RUNNING");
        job.setIdempotencyKey(blankToNull(idempotencyKey));
        job.setEnrollmentCount(preview.enrollmentCount());
        job.setCurrency(preview.currency());
        job.setRequestedBy(currentUserId());
        job.setStartedAt(Instant.now());
        job = jobs.saveAndFlush(job);

        int generated = 0;
        int already = 0;
        int blocked = 0;
        int failed = 0;
        long total = 0;
        boolean periodBlocked = preview.blockers().stream().anyMatch(b -> "POSTING_PERIOD_CLOSED".equals(b.code()));
        AcademicSession session = sessions.findByIdAndSchoolId(request.academicSessionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Session académique"));

        for (PreviewRow row : preview.rows()) {
            if ("OPTIONAL_NOT_ACCEPTED".equals(row.resultStatus())) {
                continue;
            }
            if (!"READY".equals(row.resultStatus())) {
                ChargeGenerationResult result = result(job, row, row.resultStatus(), row.blockerCode(),
                        row.blockerMessage(), row.actionLink(), null, null);
                results.save(result);
                if ("ALREADY_EXISTS".equals(row.resultStatus())) already++; else blocked++;
                continue;
            }
            if (periodBlocked) {
                ChargeGenerationResult result = result(job, row, "BLOCKED", "POSTING_PERIOD_CLOSED",
                        "La date de génération appartient à une période comptable fermée ou non configurée.",
                        "/finance/accounting/periods", null, null);
                results.save(result);
                blocked++;
                continue;
            }
            String key = "CHARGE:" + row.enrollmentId() + ":" + row.planId() + ":" + row.planLineId();
            StudentCharge existing = charges.findBySchoolIdAndGenerationKey(schoolId, key).orElse(null);
            if (existing != null) {
                results.save(result(job, row, "ALREADY_EXISTS", null, null, null, existing.getId(), null));
                already++;
                continue;
            }
            try {
                StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(row.enrollmentId(), schoolId)
                        .orElseThrow(() -> ApiException.notFound("Inscription"));
                FeePlan plan = previewService.resolvePlan(schoolId, enrollment, request.chargeDate());
                FeePlanLine line = previewServiceLine(schoolId, row.planLineId());
                FeeType type = feeTypes.findByIdAndSchoolId(line.getFeeTypeId(), schoolId)
                        .orElseThrow(() -> ApiException.notFound("Type de frais"));
                FeeTypeRevision revision = revisions.findByIdAndSchoolId(line.getFeeTypeRevisionId(), schoolId)
                        .orElseThrow(() -> ApiException.notFound("Révision du type de frais"));
                StudentCharge charge = new StudentCharge();
                charge.setSchoolId(schoolId);
                charge.setStudentEnrollmentId(enrollment.getId());
                charge.setStudentId(enrollment.getStudentId());
                charge.setAcademicSessionId(enrollment.getAcademicSessionId());
                charge.setFeePlanId(plan.getId());
                charge.setFeePlanLineId(line.getId());
                charge.setFeeTypeId(type.getId());
                charge.setFeeTypeRevisionId(revision.getId());
                charge.setFeePlanVersionNo(plan.getPlanVersionNo());
                charge.setFeeTypeCode(type.getCode());
                charge.setFeeTypeNameFr(revision.getNameFr());
                charge.setFeeTypeNameEn(revision.getNameEn());
                charge.setFeeTypeCategory(revision.getCategory());
                charge.setScopeType(plan.getScopeType());
                charge.setLevelSnapshot(enrollment.getLevelSnapshot());
                charge.setSubsystemSnapshot(enrollment.getSubsystemSnapshot());
                charge.setSchoolClassIdSnapshot(enrollment.getSchoolClassId());
                charge.setClassNameSnapshot(enrollment.getClassNameSnapshot());
                charge.setReceivableAccountId(revision.getReceivableAccountId());
                charge.setRevenueAccountId(revision.getRevenueAccountId());
                charge.setOriginalAmountMinor(row.originalAmountMinor());
                charge.setAdjustedAmountMinor(row.adjustedAmountMinor());
                charge.setCurrency(row.adjustedAmountMinor() == 0 ? "XAF" : "XAF");
                charge.setChargeDate(request.chargeDate());
                charge.setProrationPolicy(row.prorationPolicy());
                charge.setProrationFormula(row.prorationFormula());
                charge.setGenerationKey(key);
                charge.setTransferFromEnrollmentId(enrollment.getPreviousEnrollmentId());
                charge.setTransferPolicy(preview.transferPolicy());
                charge.setStatus("DRAFT");
                charge.setOutstandingMinor(row.adjustedAmountMinor());
                charge.setCreatedBy(currentUserId());
                charge = charges.saveAndFlush(charge);

                ChargeGenerationPreviewService.Schedule schedule = previewService.schedule(line, row.adjustedAmountMinor(),
                        session, request.chargeDate(), schoolId);
                for (ChargeGenerationPreviewService.ScheduleLine scheduleLine : schedule.lines()) {
                    ChargeInstallment installment = new ChargeInstallment();
                    installment.setSchoolId(schoolId);
                    installment.setChargeId(charge.getId());
                    installment.setInstallmentNo(scheduleLine.order());
                    installment.setLabelFr(scheduleLine.labelFr());
                    installment.setLabelEn(scheduleLine.labelEn());
                    installment.setDueDate(scheduleLine.dueDate());
                    installment.setAmountMinor(scheduleLine.amountMinor());
                    installment.setOutstandingMinor(scheduleLine.amountMinor());
                    installment.setGenerationKey(key + ":INSTALLMENT:" + scheduleLine.order());
                    installments.saveAndFlush(installment);
                }
                AccountingPeriod period = periods.requireOpenForDate(request.chargeDate(), request.academicSessionId());
                var journal = ledger.createDraft(new JournalUpsert(request.chargeDate(),
                        "Charge " + type.getCode() + " - " + enrollment.getId(), "XAF", period.getId(),
                        "STUDENT_CHARGE", charge.getId().toString(), "CHARGE:" + charge.getId(), List.of(
                        new JournalLineInput(revision.getReceivableAccountId(), row.adjustedAmountMinor(), 0,
                                enrollment.getStudentId(), enrollment.getId(), null, enrollment.getSchoolClassId(), type.getCode(),
                                "Créance " + revision.getNameFr()),
                        new JournalLineInput(revision.getRevenueAccountId(), 0, row.adjustedAmountMinor(),
                                enrollment.getStudentId(), enrollment.getId(), null, enrollment.getSchoolClassId(), type.getCode(),
                                "Produit " + revision.getNameFr())), null));
                ledger.post(journal.id(), "CHARGE-JOURNAL:" + charge.getId());
                charge.setJournalEntryId(journal.id());
                charge.setStatus("POSTED");
                charge = charges.saveAndFlush(charge);
                results.save(result(job, row, "GENERATED", null, null, null, charge.getId(), row.adjustedAmountMinor()));
                audit.record("CHARGE_GENERATED", "StudentCharge", charge.getId().toString(), null,
                        new ChargeResultAudit(charge.getGenerationKey(), charge.getAdjustedAmountMinor(), charge.getCurrency()), null);
                generated++;
                total += row.adjustedAmountMinor();
            } catch (DataIntegrityViolationException ex) {
                StudentCharge concurrent = charges.findBySchoolIdAndGenerationKey(schoolId, key).orElse(null);
                if (concurrent != null) {
                    results.save(result(job, row, "ALREADY_EXISTS", null, null, null, concurrent.getId(), null));
                    already++;
                } else {
                    results.save(result(job, row, "FAILED", "CHARGE_WRITE_FAILED",
                            "La charge n'a pas pu être enregistrée; réessayez cette ligne.",
                            "/finance/charges?tab=generate", null, null));
                    failed++;
                }
            } catch (ApiException ex) {
                results.save(result(job, row, "FAILED", ex.getCode(), ex.getMessage(),
                        "/finance/charges?tab=generate", null, null));
                failed++;
            }
        }
        job.setGeneratedCount(generated);
        job.setAlreadyExistsCount(already);
        job.setBlockedCount(blocked);
        job.setFailedCount(failed);
        job.setTotalAmountMinor(total);
        job.setStatus(blocked > 0 || failed > 0 ? "COMPLETED_WITH_BLOCKERS" : "COMPLETED");
        job.setCompletedAt(Instant.now());
        job = jobs.saveAndFlush(job);
        audit.record("CHARGE_GENERATION_COMPLETED", "ChargeGenerationJob", job.getId().toString(), null, view(job), null);
        return view(job);
    }

    private FeePlanLine previewServiceLine(UUID schoolId, UUID id) {
        // The preview has already validated this tenant-scoped line. Keeping
        // this lookup in the write path prevents a stale preview from crossing tenants.
        return planLines.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Ligne de plan"));
    }

    private ChargeGenerationResult result(ChargeGenerationJob job, PreviewRow row, String status,
                                          String blockerCode, String blockerMessage, String action,
                                          UUID chargeId, Long amount) {
        ChargeGenerationResult result = new ChargeGenerationResult();
        result.setSchoolId(job.getSchoolId());
        result.setJobId(job.getId());
        result.setStudentEnrollmentId(row.enrollmentId());
        result.setStudentId(row.studentId());
        result.setFeePlanId(row.planId());
        result.setFeePlanLineId(row.planLineId());
        result.setStudentChargeId(chargeId);
        result.setClassNameSnapshot(row.className());
        result.setResultStatus(status);
        result.setAmountMinor(amount == null ? row.adjustedAmountMinor() : amount);
        result.setCurrency("XAF");
        result.setBlockerCode(blockerCode);
        result.setBlockerMessage(blockerMessage);
        result.setActionLink(action);
        return result;
    }

    private GenerationJobView view(ChargeGenerationJob job) {
        return new GenerationJobView(job.getId(), job.getAcademicSessionId(), job.getSchoolClassId(), job.getLevel(),
                job.getSubsystem(), job.getChargeDate(), job.getProrationPolicy(), job.getTransferPolicy(), job.getStatus(),
                job.getEnrollmentCount(), job.getGeneratedCount(), job.getAlreadyExistsCount(), job.getBlockedCount(),
                job.getFailedCount(), job.getTotalAmountMinor(), job.getCurrency(), job.getLastError(), job.getVersion());
    }

    private GenerationResultView resultView(ChargeGenerationResult result) {
        return new GenerationResultView(result.getId(), result.getJobId(), result.getStudentEnrollmentId(), result.getStudentId(),
                result.getFeePlanId(), result.getFeePlanLineId(), result.getStudentChargeId(), result.getSchoolClassId(),
                result.getClassNameSnapshot(), result.getResultStatus(), result.getAmountMinor(), result.getCurrency(),
                result.getBlockerCode(), result.getBlockerMessage(), result.getActionLink(), result.getErrorDetail());
    }

    private ChargeGenerationJob requireJob(UUID id) {
        return jobs.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Job de génération"));
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record ChargeResultAudit(String generationKey, long amountMinor, String currency) {}
}
