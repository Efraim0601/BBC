package com.bbc.sms.finance.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PayslipJobRepository extends JpaRepository<PayslipJob, UUID> {
    Optional<PayslipJob> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PayslipJob> findBySchoolIdAndIdempotencyKey(UUID schoolId, String key);
}
