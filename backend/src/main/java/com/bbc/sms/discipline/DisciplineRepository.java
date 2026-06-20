package com.bbc.sms.discipline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisciplineRepository extends JpaRepository<DisciplineIncident, UUID> {
    List<DisciplineIncident> findBySchoolIdOrderByIncidentDateDesc(UUID schoolId);
    Optional<DisciplineIncident> findByIdAndSchoolId(UUID id, UUID schoolId);
}
