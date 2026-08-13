package com.bbc.sms.finance.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceInvoiceRepository extends JpaRepository<FinanceInvoice, UUID> {
    Optional<FinanceInvoice> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FinanceInvoice> findBySchoolIdAndIdempotencyKey(UUID schoolId, String key);
    Optional<FinanceInvoice> findBySchoolIdAndSourceEventKey(UUID schoolId, String key);
    List<FinanceInvoice> findBySchoolIdOrderByIssueDateDescCreatedAtDesc(UUID schoolId);
    List<FinanceInvoice> findBySchoolIdAndStudentIdOrderByIssueDateDescCreatedAtDesc(UUID schoolId, UUID studentId);
    List<FinanceInvoice> findBySchoolIdAndStudentEnrollmentIdOrderByIssueDateDescCreatedAtDesc(UUID schoolId, UUID enrollmentId);
}
