package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstallmentTemplateLineRepository extends JpaRepository<InstallmentTemplateLine, UUID> {
    List<InstallmentTemplateLine> findBySchoolIdAndTemplateIdOrderByLineOrder(UUID schoolId, UUID templateId);
    void deleteBySchoolIdAndTemplateId(UUID schoolId, UUID templateId);
}
