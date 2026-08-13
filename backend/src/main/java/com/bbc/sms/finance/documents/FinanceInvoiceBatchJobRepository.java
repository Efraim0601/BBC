package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceInvoiceBatchJobRepository extends JpaRepository<FinanceInvoiceBatchJob, UUID> {
    Optional<FinanceInvoiceBatchJob> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FinanceInvoiceBatchJob> findBySchoolIdAndIdempotencyKey(UUID schoolId, String key);
    List<FinanceInvoiceBatchJob> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
