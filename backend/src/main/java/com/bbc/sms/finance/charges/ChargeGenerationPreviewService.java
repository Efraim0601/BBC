package com.bbc.sms.finance.charges;

import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.fees.FeeType;
import com.bbc.sms.finance.fees.FeeTypeRepository;
import com.bbc.sms.finance.fees.FeeTypeRevision;
import com.bbc.sms.finance.fees.FeeTypeRevisionRepository;
import com.bbc.sms.finance.plans.FeePlan;
import com.bbc.sms.finance.plans.FeePlanLine;
import com.bbc.sms.finance.plans.FeePlanLineRepository;
import com.bbc.sms.finance.plans.FeePlanRepository;
import com.bbc.sms.finance.plans.InstallmentTemplateLine;
import com.bbc.sms.finance.plans.InstallmentTemplateLineRepository;
import com.bbc.sms.finance.plans.InstallmentTemplateRepository;
import com.bbc.sms.finance.plans.StudentFeeElection;
import com.bbc.sms.finance.plans.StudentFeeElectionRepository;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.charges.ChargeDtos.*;

/** Builds a precise, non-mutating preview for charge generation. */
@Service
public class ChargeGenerationPreviewService {
    private final AcademicSessionRepository sessions;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final FeePlanRepository plans;
    private final FeePlanLineRepository planLines;
    private final FeeTypeRepository feeTypes;
    private final FeeTypeRevisionRepository revisions;
    private final StudentFeeElectionRepository elections;
    private final InstallmentTemplateRepository templates;
    private final InstallmentTemplateLineRepository templateLines;
    private final ChartOfAccountRepository accounts;
    private final StudentChargeRepository charges;
    private final AccountingPeriodService periods;

    public ChargeGenerationPreviewService(AcademicSessionRepository sessions,
                                          StudentEnrollmentRepository enrollments,
                                          StudentRepository students,
                                          FeePlanRepository plans,
                                          FeePlanLineRepository planLines,
                                          FeeTypeRepository feeTypes,
                                          FeeTypeRevisionRepository revisions,
                                          StudentFeeElectionRepository elections,
                                          InstallmentTemplateRepository templates,
                                          InstallmentTemplateLineRepository templateLines,
                                          ChartOfAccountRepository accounts,
                                          StudentChargeRepository charges,
                                          AccountingPeriodService periods) {
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.students = students;
        this.plans = plans;
        this.planLines = planLines;
        this.feeTypes = feeTypes;
        this.revisions = revisions;
        this.elections = elections;
        this.templates = templates;
        this.templateLines = templateLines;
        this.accounts = accounts;
        this.charges = charges;
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public GenerationPreview preview(GenerationRequest request) {
        UUID schoolId = TenantContext.get();
        AcademicSession session = sessions.findByIdAndSchoolId(request.academicSessionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        String policy = normalizePolicy(request.prorationPolicy());
        String transferPolicy = normalizeTransferPolicy(request.transferPolicy());
        List<BlockerView> blockers = new ArrayList<>();
        if (periods.findOpenForDate(request.chargeDate(), request.academicSessionId()).isEmpty()) {
            blockers.add(new BlockerView("ACCOUNTING_PERIOD", null, "POSTING_PERIOD_CLOSED",
                    "Aucune période comptable ouverte de la session sélectionnée ne couvre cette date.",
                    "/finance/accounting/periods"));
        }

        List<StudentEnrollment> candidates = findEnrollments(schoolId, request);
        List<PreviewRow> rows = new ArrayList<>();
        Set<UUID> covered = new HashSet<>();
        int lineCount = 0;
        int installmentCount = 0;
        int optionalPending = 0;
        int transfers = 0;
        int already = 0;
        long total = 0;

        for (StudentEnrollment enrollment : candidates) {
            Student student = students.findByIdAndSchoolId(enrollment.getStudentId(), schoolId).orElse(null);
            FeePlan plan = resolvePlan(schoolId, enrollment, request.chargeDate());
            boolean transfer = enrollment.getPreviousEnrollmentId() != null;
            if (transfer) transfers++;
            if (plan == null) {
                String message = "Aucun plan actif ne couvre le niveau/sous-système ou la classe historique de cette inscription.";
                rows.add(new PreviewRow(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                        student == null ? null : student.getMatricule(), null, 0, null,
                        enrollment.getClassNameSnapshot(), null, null, null, 0, 0, 0, false,
                        null, transfer, policy, null, "BLOCKED", "NO_ACTIVE_FEE_PLAN", message,
                        "/finance/plans"));
                blockers.add(blocker("ENROLLMENT", enrollment.getId(), "NO_ACTIVE_FEE_PLAN", message,
                        "/finance/plans"));
                continue;
            }
            covered.add(enrollment.getId());
            List<FeePlanLine> lines = planLines.findBySchoolIdAndFeePlanIdOrderByLineOrder(schoolId, plan.getId());
            if (lines.isEmpty()) {
                String message = "Le plan actif ne contient aucune ligne de frais.";
                blockers.add(blocker("FEE_PLAN", plan.getId(), "PLAN_HAS_NO_LINES", message, "/finance/plans"));
                continue;
            }
            for (FeePlanLine line : lines) {
                FeeType type = feeTypes.findByIdAndSchoolId(line.getFeeTypeId(), schoolId).orElse(null);
                FeeTypeRevision revision = revisions.findByIdAndSchoolId(line.getFeeTypeRevisionId(), schoolId).orElse(null);
                String code = type == null ? null : type.getCode();
                String name = revision == null ? code : revision.getNameEn();
                boolean optional = !line.isMandatory();
                String electionStatus = null;
                if (optional) {
                    StudentFeeElection election = elections.findBySchoolIdAndStudentEnrollmentIdAndFeePlanLineId(
                            schoolId, enrollment.getId(), line.getId()).orElse(null);
                    electionStatus = election == null ? "PENDING" : election.getStatus();
                    if (!"ACCEPTED".equals(electionStatus)) {
                        optionalPending++;
                        rows.add(new PreviewRow(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                                student == null ? null : student.getMatricule(), plan.getId(), plan.getPlanVersionNo(),
                                plan.getScopeType(), enrollment.getClassNameSnapshot(), line.getId(), code, name,
                                line.getAmountMinor(), 0, 0, true, electionStatus, transfer, policy, null,
                                "OPTIONAL_NOT_ACCEPTED", null, "Optional fee is not accepted for this enrollment.",
                                "/finance/plans/" + plan.getId() + "/elections"));
                        continue;
                    }
                }
                lineCount++;
                String linePolicy = policyOverride(policy, line.getProrationPolicy());
                String formula = null;
                long adjusted = line.getAmountMinor();
                if (revision == null || type == null) {
                    String message = "La ligne référence une révision ou un type de frais introuvable dans cet établissement.";
                    rows.add(blockedRow(enrollment, student, plan, line, transfer, linePolicy, code, name,
                            "FEE_TYPE_REVISION_MISSING", message));
                    blockers.add(blocker("FEE_PLAN_LINE", line.getId(), "FEE_TYPE_REVISION_MISSING", message,
                            "/finance/fee-types"));
                    continue;
                }
                if (!"ACTIVE".equals(revision.getRevisionStatus())
                        || !effective(revision.getEffectiveFrom(), revision.getEffectiveTo(), request.chargeDate())) {
                    String message = "La révision du type de frais n'est pas active à la date de génération.";
                    rows.add(blockedRow(enrollment, student, plan, line, transfer, linePolicy, code, name,
                            "FEE_REVISION_NOT_EFFECTIVE", message));
                    blockers.add(blocker("FEE_TYPE_REVISION", revision.getId(), "FEE_REVISION_NOT_EFFECTIVE", message,
                            "/finance/fee-types/" + type.getId()));
                    continue;
                }
                if (!validPostingAccount(schoolId, revision.getReceivableAccountId(), request.chargeDate(), "ASSET", revision.getDefaultCurrency())
                        || !validPostingAccount(schoolId, revision.getRevenueAccountId(), request.chargeDate(), "REVENUE", revision.getDefaultCurrency())) {
                    String message = "La révision active doit avoir deux comptes de mouvement actifs (créance et produit).";
                    rows.add(blockedRow(enrollment, student, plan, line, transfer, linePolicy, code, name,
                            "ACCOUNT_MAPPING_MISSING", message));
                    blockers.add(blocker("FEE_TYPE_REVISION", revision.getId(), "ACCOUNT_MAPPING_MISSING", message,
                            "/finance/fee-types/" + type.getId()));
                    continue;
                }
                if (!"NONE".equals(linePolicy)) {
                    Proration p = prorate(line.getAmountMinor(), session, enrollment, linePolicy);
                    adjusted = p.amount();
                    formula = p.formula();
                }
                if (transfer && "INCREMENTAL_ONLY".equals(transferPolicy)) {
                    long previousAmount = charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(
                                    schoolId, enrollment.getPreviousEnrollmentId()).stream()
                            .filter(previous -> code.equals(previous.getFeeTypeCode()))
                            .mapToLong(StudentCharge::getAdjustedAmountMinor).sum();
                    long targetAmount = adjusted;
                    adjusted = Math.max(0, adjusted - previousAmount);
                    String transferFormula = "INCREMENTAL_ONLY(" + targetAmount + " - " + previousAmount + ") = " + adjusted + " XAF";
                    formula = formula == null ? transferFormula : formula + "; " + transferFormula;
                }
                if (adjusted == 0 && transfer && "INCREMENTAL_ONLY".equals(transferPolicy)) {
                    already++;
                    rows.add(new PreviewRow(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                            student == null ? null : student.getMatricule(), plan.getId(), plan.getPlanVersionNo(),
                            plan.getScopeType(), enrollment.getClassNameSnapshot(), line.getId(), code, revision.getNameEn(),
                            line.getAmountMinor(), 0, 0, optional, electionStatus, true, linePolicy, formula,
                            "ALREADY_EXISTS", "TRANSFER_ALREADY_COVERED",
                            "The previous enrollment already covers this fee type; no incremental charge is created.", null));
                    continue;
                }
                Schedule schedule = schedule(line, adjusted, session, request.chargeDate(), schoolId);
                if (!schedule.blockers().isEmpty()) {
                    String message = String.join(" ", schedule.blockers());
                    rows.add(blockedRow(enrollment, student, plan, line, transfer, linePolicy, code, name,
                            "INSTALLMENT_SCHEDULE_INVALID", message));
                    blockers.add(blocker("FEE_PLAN_LINE", line.getId(), "INSTALLMENT_SCHEDULE_INVALID", message,
                            "/finance/plans/" + plan.getId() + "/installments-preview"));
                    continue;
                }
                String generationKey = generationKey(enrollment, plan, line);
                boolean exists = charges.findBySchoolIdAndGenerationKey(schoolId, generationKey).isPresent();
                if (exists) already++;
                total += adjusted;
                installmentCount += schedule.lines().size();
                rows.add(new PreviewRow(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                        student == null ? null : student.getMatricule(), plan.getId(), plan.getPlanVersionNo(),
                        plan.getScopeType(), enrollment.getClassNameSnapshot(), line.getId(), code, revision.getNameEn(),
                        line.getAmountMinor(), adjusted, schedule.lines().size(), optional,
                        electionStatus, transfer, linePolicy, formula,
                        exists ? "ALREADY_EXISTS" : "READY", null, null, null));
            }
        }
        return new GenerationPreview(request.academicSessionId(), request.schoolClassId(), request.level(), request.subsystem(),
                request.chargeDate(), policy, transferPolicy, candidates.size(), covered.size(),
                Math.max(0, candidates.size() - covered.size()), lineCount, installmentCount, optionalPending,
                transfers, already, total, "XAF", rows, blockers);
    }

    public List<StudentEnrollment> findEnrollments(UUID schoolId, GenerationRequest request) {
        if (request.schoolClassId() != null) {
            return enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                    schoolId, request.academicSessionId(), request.schoolClassId(), "ACTIVE");
        }
        if (request.level() != null && !request.level().isBlank()
                && request.subsystem() != null && !request.subsystem().isBlank()) {
            return enrollments.findBySchoolIdAndAcademicSessionIdAndLevelSnapshotAndSubsystemSnapshotAndStatusOrderByClassNameSnapshotAsc(
                    schoolId, request.academicSessionId(), request.level().trim(), request.subsystem().trim(), "ACTIVE");
        }
        return enrollments.findBySchoolIdAndAcademicSessionIdAndStatusOrderByClassNameSnapshotAsc(
                schoolId, request.academicSessionId(), "ACTIVE");
    }

    FeePlan resolvePlan(UUID schoolId, StudentEnrollment enrollment, LocalDate date) {
        FeePlan classPlan = active(plans.findForScope(schoolId, enrollment.getAcademicSessionId(), "CLASS",
                safe(enrollment.getLevelSnapshot()), safe(enrollment.getSubsystemSnapshot()), enrollment.getSchoolClassId(), "ACTIVE"), date);
        if (classPlan != null) return classPlan;
        return active(plans.findForScope(schoolId, enrollment.getAcademicSessionId(), "LEVEL",
                safe(enrollment.getLevelSnapshot()), safe(enrollment.getSubsystemSnapshot()), null, "ACTIVE"), date);
    }

    public Schedule schedule(FeePlanLine line, long amount, AcademicSession session,
                             LocalDate chargeDate, UUID schoolId) {
        if (line.getInstallmentTemplateId() == null) {
            return new Schedule(List.of(new ScheduleLine(1, "Frais", "Fee", amount, chargeDate)), List.of());
        }
        if (templates.findByIdAndSchoolId(line.getInstallmentTemplateId(), schoolId).isEmpty()) {
            return new Schedule(List.of(), List.of("Le modèle d'échéancier n'existe plus dans cet établissement."));
        }
        List<InstallmentTemplateLine> template = templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(
                schoolId, line.getInstallmentTemplateId());
        if (template.isEmpty()) return new Schedule(List.of(), List.of("Le modèle d'échéancier ne contient aucune ligne."));
        template = template.stream().sorted(Comparator.comparingInt(InstallmentTemplateLine::getLineOrder)).toList();
        String allocation = template.get(0).getAllocationType();
        if (template.stream().anyMatch(t -> !allocation.equals(t.getAllocationType()))) {
            return new Schedule(List.of(), List.of("Un échéancier ne peut pas mélanger montants fixes et pourcentages."));
        }
        List<String> errors = new ArrayList<>();
        List<ScheduleLine> result = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < template.size(); i++) {
            InstallmentTemplateLine t = template.get(i);
            long value;
            if ("FIXED".equals(allocation)) {
                value = t.getAmountMinor() == null ? 0 : t.getAmountMinor();
            } else {
                int basis = t.getPercentageBasisPoints() == null ? 0 : t.getPercentageBasisPoints();
                value = i == template.size() - 1
                        ? amount - allocated
                        : BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(basis))
                        .divide(BigDecimal.valueOf(10000), 0, RoundingMode.DOWN).longValue();
            }
            if (value < 0) errors.add("La ligne " + t.getLineOrder() + " dépasse le montant total après arrondi.");
            LocalDate due = dueDate(t, session, chargeDate);
            if (due == null) errors.add("La date d'échéance de la ligne " + t.getLineOrder() + " est incomplète.");
            result.add(new ScheduleLine(t.getLineOrder(), t.getLabelFr(), t.getLabelEn(), value, due));
            allocated += value;
        }
        if ("PERCENTAGE".equals(allocation)) {
            int totalBasis = template.stream().mapToInt(t -> t.getPercentageBasisPoints() == null ? 0 : t.getPercentageBasisPoints()).sum();
            if (totalBasis != 10000) errors.add("Les pourcentages doivent totaliser 10000 points de base (100%).");
        }
        if (allocated != amount) errors.add("Les échéances totalisent " + allocated + " XAF au lieu de " + amount + " XAF.");
        return new Schedule(errors.isEmpty() ? result : result, errors);
    }

    private LocalDate dueDate(InstallmentTemplateLine line, AcademicSession session, LocalDate chargeDate) {
        if ("ABSOLUTE_DATE".equals(line.getDueRuleType())) return line.getAbsoluteDueDate();
        int offset = line.getDueOffsetDays() == null ? 0 : line.getDueOffsetDays();
        if ("TERM_END_OFFSET".equals(line.getDueRuleType())) return session.getEndDate().plusDays(offset);
        return session.getStartDate().plusDays(offset);
    }

    private Proration prorate(long amount, AcademicSession session, StudentEnrollment enrollment, String policy) {
        LocalDate start = max(session.getStartDate(), enrollment.getEnrolledOn());
        LocalDate end = min(session.getEndDate(), enrollment.getExitedOn() == null ? session.getEndDate() : enrollment.getExitedOn());
        if (end.isBefore(start)) return new Proration(0, policy + ": no active days");
        long active;
        long total;
        if ("MONTHLY".equals(policy)) {
            YearMonth from = YearMonth.from(session.getStartDate());
            YearMonth to = YearMonth.from(session.getEndDate());
            YearMonth activeFrom = YearMonth.from(start);
            YearMonth activeTo = YearMonth.from(end);
            total = ChronoUnit.MONTHS.between(from, to) + 1;
            active = Math.max(0, ChronoUnit.MONTHS.between(activeFrom, activeTo) + 1);
        } else {
            total = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
            active = ChronoUnit.DAYS.between(start, end) + 1;
        }
        long result = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(active))
                .divide(BigDecimal.valueOf(Math.max(1, total)), 0, RoundingMode.HALF_UP).longValueExact();
        return new Proration(result, policy + "(" + amount + " x " + active + "/" + total + ") = " + result + " XAF");
    }

    private boolean validPostingAccount(UUID schoolId, UUID accountId, LocalDate date, String expectedType, String expectedCurrency) {
        if (accountId == null) return false;
        ChartOfAccount a = accounts.findByIdAndSchoolId(accountId, schoolId).orElse(null);
        return a != null && a.isActive() && a.isPostingAllowed()
                && expectedType.equals(a.getAccountType())
                && (a.getCurrency() == null || a.getCurrency().equalsIgnoreCase(expectedCurrency == null ? "XAF" : expectedCurrency))
                && (a.getEffectiveFrom() == null || !date.isBefore(a.getEffectiveFrom()))
                && (a.getEffectiveTo() == null || !date.isAfter(a.getEffectiveTo()));
    }

    private FeePlan active(List<FeePlan> candidates, LocalDate date) {
        return candidates.stream().filter(p -> effective(p.getEffectiveFrom(), p.getEffectiveTo(), date))
                .max(Comparator.comparingInt(FeePlan::getPlanVersionNo)).orElse(null);
    }

    private boolean effective(LocalDate from, LocalDate to, LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private PreviewRow blockedRow(StudentEnrollment enrollment, Student student, FeePlan plan, FeePlanLine line,
                                  boolean transfer, String policy, String code, String name,
                                  String blockerCode, String message) {
        return new PreviewRow(enrollment.getId(), enrollment.getStudentId(), studentName(student),
                student == null ? null : student.getMatricule(), plan.getId(), plan.getPlanVersionNo(),
                plan.getScopeType(), enrollment.getClassNameSnapshot(), line.getId(), code, name,
                line.getAmountMinor(), 0, 0, !line.isMandatory(), null, transfer, policy, null,
                "BLOCKED", blockerCode, message, "/finance/plans/" + plan.getId());
    }

    private BlockerView blocker(String type, UUID id, String code, String message, String action) {
        return new BlockerView(type, id == null ? null : id.toString(), code, message, action);
    }

    private String generationKey(StudentEnrollment enrollment, FeePlan plan, FeePlanLine line) {
        return "CHARGE:" + enrollment.getId() + ":" + plan.getId() + ":" + line.getId();
    }

    static String stableGenerationKey(StudentEnrollment enrollment, FeePlan plan, FeePlanLine line) {
        return "CHARGE:" + enrollment.getId() + ":" + plan.getId() + ":" + line.getId();
    }

    private String policyOverride(String requestPolicy, String linePolicy) {
        return "NONE".equals(requestPolicy) && (linePolicy == null || linePolicy.isBlank())
                ? "NONE" : ("NONE".equals(requestPolicy) ? safePolicy(linePolicy) : requestPolicy);
    }

    static String normalizePolicy(String policy) {
        String value = policy == null || policy.isBlank() ? "NONE" : policy.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NONE", "DAILY", "MONTHLY").contains(value)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PRORATION_POLICY",
                    "La politique de prorata doit être NONE, DAILY ou MONTHLY.",
                    java.util.Map.of("prorationPolicy", "Choisissez NONE, DAILY ou MONTHLY."), List.of());
        }
        return value;
    }

    static String normalizeTransferPolicy(String policy) {
        String value = policy == null || policy.isBlank() ? "INCREMENTAL_ONLY" : policy.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INCREMENTAL_ONLY", "FULL_REASSESSMENT").contains(value)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_POLICY",
                    "La politique de transfert est invalide.",
                    java.util.Map.of("transferPolicy", "Choisissez INCREMENTAL_ONLY ou FULL_REASSESSMENT."), List.of());
        }
        return value;
    }

    private String safePolicy(String value) { return value == null || value.isBlank() ? "NONE" : normalizePolicy(value); }
    private String safe(String value) { return value == null ? "" : value; }
    private String studentName(Student student) { return student == null ? "Étudiant introuvable" : (student.getFirstName() + " " + student.getLastName()).trim(); }
    private LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
    private LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

    public record Schedule(List<ScheduleLine> lines, List<String> blockers) {}
    public record ScheduleLine(int order, String labelFr, String labelEn, long amountMinor, LocalDate dueDate) {}
    private record Proration(long amount, String formula) {}
}
