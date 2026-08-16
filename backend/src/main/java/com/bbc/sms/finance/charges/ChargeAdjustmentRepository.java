package com.bbc.sms.finance.charges;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeAdjustmentRepository extends JpaRepository<ChargeAdjustment, UUID> {
    Optional<ChargeAdjustment> findByIdAndSchoolId(UUID id, UUID schoolId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChargeAdjustment a where a.id = :id and a.schoolId = :schoolId")
    Optional<ChargeAdjustment> findForUpdateByIdAndSchoolId(UUID id, UUID schoolId);
    List<ChargeAdjustment> findBySchoolIdAndChargeIdOrderByCreatedAtAsc(UUID schoolId, UUID chargeId);
}
