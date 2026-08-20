package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {
    Optional<DocumentSequence> findBySchoolIdAndDocumentTypeAndPeriodKey(UUID schoolId, String documentType, String periodKey);
}
