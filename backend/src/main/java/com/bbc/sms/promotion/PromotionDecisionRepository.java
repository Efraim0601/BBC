package com.bbc.sms.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionDecisionRepository extends JpaRepository<PromotionDecision, UUID> {

    Optional<PromotionDecision> findBySchoolIdAndStudentIdAndAcademicYear(
            UUID schoolId, UUID studentId, String academicYear);

    List<PromotionDecision> findByBatchId(UUID batchId);
}
