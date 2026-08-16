package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostingRuleRepository extends JpaRepository<PostingRule, UUID> {
    List<PostingRule> findBySchoolIdOrderByEventTypeAscSideAscPriorityDesc(UUID schoolId);
    List<PostingRule> findBySchoolIdAndEventTypeAndEnabledTrueOrderByPriorityDescEffectiveFromDesc(
            UUID schoolId, String eventType);
    Optional<PostingRule> findByIdAndSchoolId(UUID id, UUID schoolId);
}
