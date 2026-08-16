package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinanceInvoiceLineRepository extends JpaRepository<FinanceInvoiceLine, UUID> {
    List<FinanceInvoiceLine> findBySchoolIdAndInvoiceIdOrderByLineNo(UUID schoolId, UUID invoiceId);
}
