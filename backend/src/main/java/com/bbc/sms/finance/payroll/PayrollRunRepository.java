package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
    List<PayrollRun> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    Optional<PayrollRun> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PayrollRun> findBySchoolIdAndSourceEventKey(UUID schoolId, String sourceEventKey);
    Optional<PayrollRun> findBySchoolIdAndPayrollPeriodIdAndStatusNot(UUID schoolId, UUID periodId, String status);
}
