package com.bbc.sms.setup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, String> {
    List<Section> findBySchoolIdOrderByLabel(UUID schoolId);
    Optional<Section> findByIdAndSchoolId(String id, UUID schoolId);
    boolean existsByIdAndSchoolId(String id, UUID schoolId);
}
