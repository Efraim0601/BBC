package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeePlanLineRepository extends JpaRepository<FeePlanLine, UUID> {
    List<FeePlanLine> findBySchoolIdAndFeePlanIdOrderByLineOrder(UUID schoolId, UUID feePlanId);
    Optional<FeePlanLine> findByIdAndSchoolId(UUID id, UUID schoolId);
    boolean existsBySchoolIdAndFeePlanIdAndFeeTypeRevisionId(UUID schoolId, UUID feePlanId, UUID revisionId);
    long countBySchoolIdAndFeePlanIdAndMandatoryFalse(UUID schoolId, UUID feePlanId);
    long countBySchoolIdAndInstallmentTemplateId(UUID schoolId, UUID installmentTemplateId);
}
