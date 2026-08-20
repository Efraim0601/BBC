package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, UUID> {
    List<RefundTransaction> findBySchoolIdAndPaymentId(UUID schoolId, UUID paymentId);
}
