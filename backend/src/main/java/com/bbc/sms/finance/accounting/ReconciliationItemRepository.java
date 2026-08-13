package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, UUID> {
    List<ReconciliationItem> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    List<ReconciliationItem> findBySchoolIdAndStateOrderByCreatedAtDesc(UUID schoolId, String state);
    Optional<ReconciliationItem> findByIdAndSchoolId(UUID id, UUID schoolId);
}
