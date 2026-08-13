package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinanceReceiptLineRepository extends JpaRepository<FinanceReceiptLine, UUID> {
    List<FinanceReceiptLine> findBySchoolIdAndReceiptIdOrderByIdAsc(UUID schoolId, UUID receiptId);
}
