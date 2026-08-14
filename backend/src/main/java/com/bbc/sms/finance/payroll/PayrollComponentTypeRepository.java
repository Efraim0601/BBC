package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollComponentTypeRepository extends JpaRepository<PayrollComponentType, UUID> {
    List<PayrollComponentType> findBySchoolIdOrderByCodeAsc(UUID schoolId);
    List<PayrollComponentType> findBySchoolIdAndActiveTrueOrderByCodeAsc(UUID schoolId);
    Optional<PayrollComponentType> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PayrollComponentType> findBySchoolIdAndCode(UUID schoolId, String code);
}
