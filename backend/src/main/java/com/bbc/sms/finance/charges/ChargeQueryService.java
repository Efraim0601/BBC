package com.bbc.sms.finance.charges;

import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.charges.ChargeDtos.*;

/** Tenant-scoped charge, account and ageing read model. */
@Service
public class ChargeQueryService {
    private final StudentChargeRepository charges;
    private final ChargeInstallmentRepository installments;
    private final ChargeAdjustmentRepository adjustments;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final FinancePolicyService financePolicy;

    public ChargeQueryService(StudentChargeRepository charges,
                              ChargeInstallmentRepository installments,
                              ChargeAdjustmentRepository adjustments,
                              StudentEnrollmentRepository enrollments,
                              StudentRepository students,
                              FinancePolicyService financePolicy) {
        this.charges = charges;
        this.installments = installments;
        this.adjustments = adjustments;
        this.enrollments = enrollments;
        this.students = students;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public List<ChargeView> list(ChargeListFilters filters) {
        financePolicy.requireSchool("CHARGE_PREVIEW");
        UUID schoolId = TenantContext.get();
        List<StudentCharge> source = filters != null && filters.studentId() != null
                ? charges.findBySchoolIdAndStudentIdOrderByChargeDateAscCreatedAtAsc(schoolId, filters.studentId())
                : filters != null && filters.academicSessionId() != null
                ? charges.findBySchoolIdAndAcademicSessionIdOrderByChargeDateAscCreatedAtAsc(schoolId, filters.academicSessionId())
                : charges.findBySchoolIdOrderByChargeDateAscCreatedAtAsc(schoolId);
        String query = filters == null || filters.query() == null ? "" : filters.query().trim().toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(c -> filters == null || filters.status() == null || filters.status().isBlank()
                        || c.getStatus().equalsIgnoreCase(filters.status().trim()))
                .filter(c -> filters == null || filters.schoolClassId() == null
                        || filters.schoolClassId().equals(c.getSchoolClassIdSnapshot()))
                .filter(c -> filters == null || filters.feeTypeCode() == null || filters.feeTypeCode().isBlank()
                        || c.getFeeTypeCode().equalsIgnoreCase(filters.feeTypeCode().trim()))
                .filter(c -> filters == null || filters.minAmountMinor() == null || c.getAdjustedAmountMinor() >= filters.minAmountMinor())
                .filter(c -> filters == null || filters.maxAmountMinor() == null || c.getAdjustedAmountMinor() <= filters.maxAmountMinor())
                .filter(c -> query.isBlank() || c.getFeeTypeCode().toLowerCase(Locale.ROOT).contains(query)
                        || c.getFeeTypeNameEn().toLowerCase(Locale.ROOT).contains(query)
                        || c.getClassNameSnapshot().toLowerCase(Locale.ROOT).contains(query)
                        || studentName(c.getStudentId()).toLowerCase(Locale.ROOT).contains(query))
                .filter(c -> dueDateMatches(c, filters))
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ChargeView detail(UUID id) {
        financePolicy.requireSchool("CHARGE_PREVIEW");
        return view(charges.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Charge")));
    }

    @Transactional(readOnly = true)
    public List<StudentContextOption> studentOptions(String query, UUID sessionId) {
        financePolicy.requireSchool("CHARGE_PREVIEW");
        UUID schoolId = TenantContext.get();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return enrollments.findBySchoolIdAndAcademicSessionIdAndStatusOrderByClassNameSnapshotAsc(schoolId, sessionId, "ACTIVE")
                .stream().map(e -> new StudentContextOption(e.getId(), e.getStudentId(), studentName(e.getStudentId()),
                        studentMatricule(e.getStudentId()), e.getAcademicSessionId(), e.getClassNameSnapshot(),
                        e.getLevelSnapshot(), e.getSubsystemSnapshot()))
                .filter(v -> needle.isBlank() || v.studentName().toLowerCase(Locale.ROOT).contains(needle)
                        || (v.matricule() != null && v.matricule().toLowerCase(Locale.ROOT).contains(needle)))
                .limit(100).toList();
    }

    @Transactional(readOnly = true)
    public StudentAccountView account(UUID enrollmentId) {
        UUID schoolId = TenantContext.get();
        StudentEnrollment enrollment = enrollments.findByIdAndSchoolId(enrollmentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        // An account is a persisted finance resource. Use its enrollment start
        // as the policy date so a configured future-session account can be read
        // before the wall clock reaches the session, while payment posting still
        // validates the requested payment date separately.
        financePolicy.requireEnrollment("CHARGE_PREVIEW", enrollmentId, enrollment.getEnrolledOn());
        List<StudentCharge> chargeList = charges.findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(schoolId, enrollmentId);
        List<LedgerEntryView> ledger = new ArrayList<>();
        long running = 0;
        long charged = 0, paid = 0, waived = 0;
        for (StudentCharge charge : chargeList) {
            charged += charge.getAdjustedAmountMinor();
            paid += charge.getPaidMinor();
            waived += charge.getWaivedMinor();
            running += charge.getAdjustedAmountMinor();
            ledger.add(new LedgerEntryView("CHARGE", charge.getId(), null, null, charge.getChargeDate(),
                    charge.getFeeTypeNameEn(), charge.getAdjustedAmountMinor(), 0, running, charge.getStatus()));
            for (ChargeInstallment installment : installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(schoolId, charge.getId())) {
                ledger.add(new LedgerEntryView("INSTALLMENT", charge.getId(), installment.getId(), null,
                        installment.getDueDate(), installment.getLabelEn(), 0, 0, running, installment.getStatus()));
            }
            for (ChargeAdjustment adjustment : adjustments.findBySchoolIdAndChargeIdOrderByCreatedAtAsc(schoolId, charge.getId())) {
                if ("APPROVED".equals(adjustment.getStatus()) || "POSTED".equals(adjustment.getStatus())) {
                    running -= adjustment.getAmountMinor();
                }
                ledger.add(new LedgerEntryView("ADJUSTMENT", charge.getId(), adjustment.getInstallmentId(),
                        adjustment.getId(), adjustment.getEffectiveDate(), adjustment.getReason(), 0,
                        adjustment.getAmountMinor(), running, adjustment.getStatus()));
            }
        }
        ledger.sort(Comparator.comparing(LedgerEntryView::entryDate).thenComparing(LedgerEntryView::entryType));
        AgeingTotals ageing = ageingFor(chargeList, LocalDate.now(), schoolId);
        Student student = students.findByIdAndSchoolId(enrollment.getStudentId(), schoolId).orElse(null);
        return new StudentAccountView(enrollment.getStudentId(), studentName(student), student == null ? null : student.getMatricule(),
                enrollment.getId(), enrollment.getClassNameSnapshot(), enrollment.getLevelSnapshot(), enrollment.getSubsystemSnapshot(),
                enrollment.getAcademicSessionId(), charged, paid, waived, Math.max(0, charged - paid - waived),
                ageing.current(), ageing.days1To30(), ageing.days31To60(), ageing.days61To90(), ageing.over90(), ledger,
                List.of("Allocations et paiements seront alimentés par BAY-47.",
                        "Factures, reçus et documents sont réservés à BAY-48."));
    }

    @Transactional(readOnly = true)
    public AgeingView ageing(LocalDate asOfDate, UUID academicSessionId, UUID schoolClassId) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        UUID schoolId = TenantContext.get();
        LocalDate date = asOfDate == null ? LocalDate.now() : asOfDate;
        List<StudentCharge> source = academicSessionId == null
                ? charges.findBySchoolIdOrderByChargeDateAscCreatedAtAsc(schoolId)
                : charges.findBySchoolIdAndAcademicSessionIdOrderByChargeDateAscCreatedAtAsc(schoolId, academicSessionId);
        Map<UUID, AgeingAccumulator> grouped = new LinkedHashMap<>();
        for (StudentCharge charge : source) {
            if (schoolClassId != null && !schoolClassId.equals(charge.getSchoolClassIdSnapshot())) continue;
            for (ChargeInstallment installment : installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(schoolId, charge.getId())) {
                if (installment.getOutstandingMinor() <= 0) continue;
                AgeingAccumulator acc = grouped.computeIfAbsent(charge.getStudentId(), ignored -> new AgeingAccumulator(charge.getStudentId(), charge.getStudentEnrollmentId(), charge.getClassNameSnapshot()));
                acc.add(installment.getDueDate(), installment.getOutstandingMinor(), date);
            }
        }
        List<AgeingRow> rows = grouped.values().stream().map(a -> {
            Student s = students.findByIdAndSchoolId(a.studentId, schoolId).orElse(null);
            return new AgeingRow(a.studentId, studentName(s), s == null ? null : s.getMatricule(), a.enrollmentId,
                    a.className, a.current, a.days1To30, a.days31To60, a.days61To90, a.over90, a.total());
        }).sorted(Comparator.comparing(AgeingRow::studentName)).toList();
        return new AgeingView(date, "XAF", sum(rows, 0), sum(rows, 1), sum(rows, 2), sum(rows, 3), sum(rows, 4), rows);
    }

    private ChargeView view(StudentCharge c) {
        UUID schoolId = TenantContext.get();
        return new ChargeView(c.getId(), c.getStudentEnrollmentId(), c.getStudentId(), c.getAcademicSessionId(),
                c.getFeePlanId(), c.getFeePlanLineId(), c.getFeeTypeId(), c.getFeeTypeRevisionId(), c.getFeePlanVersionNo(),
                c.getFeeTypeCode(), c.getFeeTypeNameFr(), c.getFeeTypeNameEn(), c.getFeeTypeCategory(), c.getScopeType(),
                c.getLevelSnapshot(), c.getSubsystemSnapshot(), c.getSchoolClassIdSnapshot(), c.getClassNameSnapshot(),
                c.getOriginalAmountMinor(), c.getAdjustedAmountMinor(), c.getPaidMinor(), c.getWaivedMinor(),
                c.getOutstandingMinor(), c.getCurrency(), c.getChargeDate(), c.getProrationPolicy(), c.getProrationFormula(),
                c.getTransferFromEnrollmentId(), c.getTransferPolicy(), c.getStatus(), c.getJournalEntryId(), c.getVersion(),
                installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(schoolId, c.getId()).stream().map(i ->
                        new ChargeInstallmentView(i.getId(), i.getInstallmentNo(), i.getLabelFr(), i.getLabelEn(), i.getDueDate(),
                                i.getAmountMinor(), i.getPaidMinor(), i.getWaivedMinor(), i.getOutstandingMinor(), i.getStatus(), i.getVersion())).toList(),
                adjustments.findBySchoolIdAndChargeIdOrderByCreatedAtAsc(schoolId, c.getId()).stream().map(a ->
                        new AdjustmentView(a.getId(), a.getChargeId(), a.getInstallmentId(), a.getAdjustmentType(), a.getAmountMinor(),
                                a.getCurrency(), a.getReason(), a.getEvidenceReference(), a.getContraAccountId(), a.getEffectiveDate(),
                                a.getStatus(), a.getRequestedBy(), a.getApprovedBy(), a.getDecisionReason(), a.getJournalEntryId(), a.getVersion())).toList());
    }

    private boolean dueDateMatches(StudentCharge c, ChargeListFilters filters) {
        if (filters == null || (filters.dueFrom() == null && filters.dueTo() == null)) return true;
        return installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(TenantContext.get(), c.getId()).stream()
                .anyMatch(i -> (filters.dueFrom() == null || !i.getDueDate().isBefore(filters.dueFrom()))
                        && (filters.dueTo() == null || !i.getDueDate().isAfter(filters.dueTo())));
    }

    private AgeingTotals ageingFor(List<StudentCharge> list, LocalDate date, UUID schoolId) {
        AgeingAccumulator a = new AgeingAccumulator(null, null, null);
        for (StudentCharge c : list) for (ChargeInstallment i : installments.findBySchoolIdAndChargeIdOrderByInstallmentNo(schoolId, c.getId()))
            if (i.getOutstandingMinor() > 0) a.add(i.getDueDate(), i.getOutstandingMinor(), date);
        return new AgeingTotals(a.current, a.days1To30, a.days31To60, a.days61To90, a.over90);
    }

    private long sum(List<AgeingRow> rows, int bucket) {
        return rows.stream().mapToLong(r -> switch (bucket) {
            case 0 -> r.currentMinor(); case 1 -> r.days1To30Minor(); case 2 -> r.days31To60Minor();
            case 3 -> r.days61To90Minor(); default -> r.over90Minor();
        }).sum();
    }

    private String studentName(UUID id) { return students.findByIdAndSchoolId(id, TenantContext.get()).map(this::studentName).orElse("Étudiant introuvable"); }
    private String studentName(Student s) { return s == null ? "Étudiant introuvable" : (s.getFirstName() + " " + s.getLastName()).trim(); }
    private String studentMatricule(UUID id) { return students.findByIdAndSchoolId(id, TenantContext.get()).map(Student::getMatricule).orElse(null); }

    public record StudentContextOption(UUID enrollmentId, UUID studentId, String studentName, String matricule,
                                      UUID academicSessionId, String className, String level, String subsystem) {}
    private record AgeingTotals(long current, long days1To30, long days31To60, long days61To90, long over90) {}
    private static final class AgeingAccumulator {
        private final UUID studentId; private final UUID enrollmentId; private final String className;
        private long current, days1To30, days31To60, days61To90, over90;
        private AgeingAccumulator(UUID studentId, UUID enrollmentId, String className) { this.studentId = studentId; this.enrollmentId = enrollmentId; this.className = className; }
        private void add(LocalDate due, long amount, LocalDate asOf) {
            long days = ChronoUnit.DAYS.between(due, asOf);
            if (days <= 0) current += amount; else if (days <= 30) days1To30 += amount;
            else if (days <= 60) days31To60 += amount; else if (days <= 90) days61To90 += amount; else over90 += amount;
        }
        private long total() { return current + days1To30 + days31To60 + days61To90 + over90; }
    }
}
