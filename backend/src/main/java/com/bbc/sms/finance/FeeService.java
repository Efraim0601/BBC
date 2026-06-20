package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FeeDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeeService {

    private final FeeConfigRepository feeConfigs;
    private final StudentFeeRepository studentFees;
    private final StudentRepository students;

    public FeeService(FeeConfigRepository feeConfigs, StudentFeeRepository studentFees, StudentRepository students) {
        this.feeConfigs = feeConfigs;
        this.studentFees = studentFees;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<FeeConfigView> listConfig() {
        UUID schoolId = TenantContext.get();
        return feeConfigs.findBySchoolId(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public FeeConfigView upsertConfig(FeeConfigUpdate in) {
        UUID schoolId = TenantContext.get();

        if (in.tranches() != null && !in.tranches().isEmpty()) {
            long sum = in.tranches().stream().mapToLong(Long::longValue).sum();
            if (sum != in.total()) {
                throw ApiException.badRequest("La somme des tranches (" + sum
                        + ") doit être égale au total (" + in.total() + ")");
            }
        }

        // Unique key is (school_id, level, subsystem); match on level (and subsystem when provided).
        List<FeeConfig> matches = feeConfigs.findBySchoolIdAndLevel(schoolId, in.level());
        FeeConfig cfg = matches.stream()
                .filter(c -> in.subsystem() == null || in.subsystem().equals(c.getSubsystem()))
                .findFirst()
                .orElseGet(() -> {
                    FeeConfig fresh = new FeeConfig();
                    fresh.setSchoolId(schoolId);
                    return fresh;
                });

        cfg.setLevel(in.level());
        cfg.setSubsystem(in.subsystem());
        cfg.setTotal(in.total());
        cfg.setTranches(in.tranches());
        cfg.setItems(in.items());

        return toView(feeConfigs.save(cfg));
    }

    @Transactional(readOnly = true)
    public List<SituationView> situation() {
        return buildSituation(false);
    }

    @Transactional(readOnly = true)
    public List<SituationView> debtors() {
        return buildSituation(true);
    }

    private List<SituationView> buildSituation(boolean onlyDebtors) {
        UUID schoolId = TenantContext.get();

        Map<UUID, Student> studentsById = students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        return studentFees.findBySchoolId(schoolId).stream()
                .filter(sf -> !onlyDebtors || sf.getBalance() > 0)
                .map(sf -> toSituation(sf, studentsById.get(sf.getStudentId())))
                .sorted(Comparator.comparingLong(SituationView::balance).reversed())
                .toList();
    }

    private SituationView toSituation(StudentFee sf, Student student) {
        String name = student == null ? null : (student.getFirstName() + " " + student.getLastName());
        String className = student == null ? null : student.getClassName();
        int progressPct = sf.getTotal() > 0
                ? (int) Math.round(sf.getPaid() * 100.0 / sf.getTotal())
                : 100;
        return new SituationView(sf.getStudentId(), name, className, sf.getTotal(), sf.getPaid(),
                sf.getBalance(), sf.getTranchesPaid(), sf.getStatus(), progressPct);
    }

    private FeeConfigView toView(FeeConfig c) {
        return new FeeConfigView(c.getId(), c.getLevel(), c.getSubsystem(), c.getTotal(),
                c.getTranches(), c.getItems());
    }
}
