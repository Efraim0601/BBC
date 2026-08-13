package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentTemplateRepository extends JpaRepository<InstallmentTemplate, UUID> {
    Optional<InstallmentTemplate> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<InstallmentTemplate> findBySchoolIdOrderByCode(UUID schoolId);
    Optional<InstallmentTemplate> findBySchoolIdAndCode(UUID schoolId, String code);
}
