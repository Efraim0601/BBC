package com.bbc.sms.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
    List<DocumentTemplate> findBySchoolIdAndActiveTrueOrderByTypeAscLocaleAscTemplateVersionDesc(UUID schoolId);
    Optional<DocumentTemplate> findFirstBySchoolIdAndTypeAndLocaleAndActiveTrueOrderByTemplateVersionDesc(UUID schoolId, String type, String locale);
    Optional<DocumentTemplate> findByIdAndSchoolId(UUID id, UUID schoolId);
}
