package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayslipJobResultRepository extends JpaRepository<PayslipJobResult, UUID> {
    List<PayslipJobResult> findBySchoolIdAndJobIdOrderByCreatedAtAsc(UUID schoolId, UUID jobId);
}
