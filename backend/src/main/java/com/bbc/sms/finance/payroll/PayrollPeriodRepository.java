package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {
    List<PayrollPeriod> findBySchoolIdOrderByStartDateDesc(UUID schoolId);
    Optional<PayrollPeriod> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PayrollPeriod> findBySchoolIdAndCode(UUID schoolId, String code);
}
