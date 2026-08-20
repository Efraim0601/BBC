package com.bbc.sms.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
    Optional<GeneratedDocument> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<GeneratedDocument> findBySchoolIdAndDocumentNumber(UUID schoolId, String number);
    Optional<GeneratedDocument> findFirstByDocumentNumberIgnoreCase(String number);
    List<GeneratedDocument> findBySchoolIdAndAggregateTypeAndAggregateIdOrderByGeneratedAtDesc(UUID schoolId, String type, String id);
    Optional<GeneratedDocument> findFirstBySchoolIdAndDocumentTypeAndAggregateTypeAndAggregateIdAndAggregateVersionAndLocale(
            UUID schoolId, String documentType, String aggregateType, String aggregateId, String aggregateVersion, String locale);
}
