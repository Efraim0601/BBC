package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReversalRequestRepository extends JpaRepository<PaymentReversalRequest, UUID> {
    Optional<PaymentReversalRequest> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<PaymentReversalRequest> findBySchoolIdAndPaymentIdAndStatusIn(UUID schoolId, UUID paymentId, java.util.Collection<String> statuses);
}
