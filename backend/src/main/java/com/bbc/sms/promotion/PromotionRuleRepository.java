package com.bbc.sms.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRuleRepository extends JpaRepository<PromotionRule, UUID> {
    List<PromotionRule> findBySchoolId(UUID schoolId);
    Optional<PromotionRule> findByIdAndSchoolId(UUID id, UUID schoolId);
}
