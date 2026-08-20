package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancePaymentRepository extends JpaRepository<FinancePayment, UUID> {
    Optional<FinancePayment> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FinancePayment> findBySchoolIdAndIdempotencyKey(UUID schoolId, String idempotencyKey);
    Optional<FinancePayment> findBySchoolIdAndSourceEventKey(UUID schoolId, String sourceEventKey);
    Optional<FinancePayment> findBySchoolIdAndChannelCodeSnapshotAndReference(UUID schoolId, String channelCodeSnapshot, String reference);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FinancePayment p where p.schoolId = :schoolId and p.id = :id")
    Optional<FinancePayment> findForUpdateByIdAndSchoolId(UUID id, UUID schoolId);
    List<FinancePayment> findBySchoolIdAndStudentIdOrderByPaymentDateDesc(UUID schoolId, UUID studentId);
    List<FinancePayment> findBySchoolIdOrderByPaymentDateDesc(UUID schoolId);
}
