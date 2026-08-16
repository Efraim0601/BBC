package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceReceiptRepository extends JpaRepository<FinanceReceipt, UUID> {
    Optional<FinanceReceipt> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FinanceReceipt> findBySchoolIdAndFinancePaymentId(UUID schoolId, UUID paymentId);
    Optional<FinanceReceipt> findBySchoolIdAndIdempotencyKey(UUID schoolId, String key);
    List<FinanceReceipt> findBySchoolIdAndStudentIdOrderByIssueDateDescCreatedAtDesc(UUID schoolId, UUID studentId);
    List<FinanceReceipt> findBySchoolIdOrderByIssueDateDescCreatedAtDesc(UUID schoolId);
}
