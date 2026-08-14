package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentCreditLedgerRepository extends JpaRepository<StudentCreditLedger, UUID> {
    List<StudentCreditLedger> findBySchoolIdAndStudentIdOrderByEntryDateAscCreatedAtAsc(UUID schoolId, UUID studentId);
    boolean existsBySchoolIdAndSourceEventKey(UUID schoolId, String sourceEventKey);
}
