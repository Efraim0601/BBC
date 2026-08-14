package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findBySchoolIdAndPaymentIdOrderByCreatedAtAsc(UUID schoolId, UUID paymentId);
    List<PaymentAllocation> findBySchoolIdAndChargeInstallmentIdAndStatus(UUID schoolId, UUID installmentId, String status);
}
