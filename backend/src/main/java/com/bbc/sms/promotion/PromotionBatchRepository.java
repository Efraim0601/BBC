package com.bbc.sms.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromotionBatchRepository extends JpaRepository<PromotionBatch, UUID> {
    List<PromotionBatch> findTop50BySchoolIdOrderByAppliedAtDesc(UUID schoolId);
}
