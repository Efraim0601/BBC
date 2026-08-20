package com.bbc.sms.foundation.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findBySchoolIdAndAggregateTypeAndAggregateIdOrderByOccurredAtDesc(
            UUID schoolId, String aggregateType, String aggregateId, Pageable pageable);
}
