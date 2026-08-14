package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, UUID> {
    Optional<PayrollPayment> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PayrollPayment> findBySchoolIdAndSourceEventKey(UUID schoolId, String sourceEventKey);
    List<PayrollPayment> findBySchoolIdAndEmployeePayrollIdOrderByCreatedAtDesc(UUID schoolId, UUID employeePayrollId);
    boolean existsBySchoolIdAndEmployeePayrollIdAndStatus(UUID schoolId, UUID employeePayrollId, String status);
}
