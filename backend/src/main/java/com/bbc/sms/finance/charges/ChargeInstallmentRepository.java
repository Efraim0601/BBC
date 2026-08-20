package com.bbc.sms.finance.charges;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeInstallmentRepository extends JpaRepository<ChargeInstallment, UUID> {
    Optional<ChargeInstallment> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<ChargeInstallment> findBySchoolIdAndGenerationKey(UUID schoolId, String generationKey);
    List<ChargeInstallment> findBySchoolIdAndChargeIdOrderByInstallmentNo(UUID schoolId, UUID chargeId);
    List<ChargeInstallment> findBySchoolIdAndStatusOrderByDueDateAsc(UUID schoolId, String status);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ChargeInstallment i where i.schoolId = :schoolId and i.id = :id")
    Optional<ChargeInstallment> findForUpdateByIdAndSchoolId(UUID id, UUID schoolId);
}
