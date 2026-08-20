package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeePayrollRepository extends JpaRepository<EmployeePayroll, UUID> {
    List<EmployeePayroll> findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(UUID schoolId, UUID runId);
    Optional<EmployeePayroll> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<EmployeePayroll> findBySchoolIdAndEmployeeIdOrderByCreatedAtDesc(UUID schoolId, UUID employeeId);
}
