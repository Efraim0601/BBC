package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinanceInvoiceBatchResultRepository extends JpaRepository<FinanceInvoiceBatchResult, UUID> {
    List<FinanceInvoiceBatchResult> findBySchoolIdAndJobIdOrderByCreatedAtAsc(UUID schoolId, UUID jobId);
    List<FinanceInvoiceBatchResult> findBySchoolIdAndJobIdAndResultStatus(UUID schoolId, UUID jobId, String status);
}
