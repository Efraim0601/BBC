package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    Optional<RefundRequest> findByIdAndSchoolId(UUID id, UUID schoolId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.schoolId = :schoolId and r.id = :id")
    Optional<RefundRequest> findForUpdateByIdAndSchoolId(UUID id, UUID schoolId);
    List<RefundRequest> findBySchoolIdAndPaymentIdOrderByRequestedAtDesc(UUID schoolId, UUID paymentId);
}
