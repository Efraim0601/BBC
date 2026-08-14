package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmployeePayrollLineRepository extends JpaRepository<EmployeePayrollLine, UUID> {
    List<EmployeePayrollLine> findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(UUID schoolId, UUID employeePayrollId);
    void deleteBySchoolIdAndEmployeePayrollId(UUID schoolId, UUID employeePayrollId);
}
