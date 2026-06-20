package com.bbc.sms.parentportal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuggestionRepository extends JpaRepository<ParentSuggestion, UUID> {
    List<ParentSuggestion> findBySchoolIdAndParentUserIdOrderByCreatedAtDesc(UUID schoolId, UUID parentUserId);
    List<ParentSuggestion> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
