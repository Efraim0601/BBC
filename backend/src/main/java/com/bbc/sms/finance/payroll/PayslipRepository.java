package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    Optional<Payslip> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<Payslip> findBySchoolIdAndSourceEventKey(UUID schoolId, String key);
    List<Payslip> findBySchoolIdAndEmployeePayrollIdOrderByVersionNoDesc(UUID schoolId, UUID employeePayrollId);
    List<Payslip> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
