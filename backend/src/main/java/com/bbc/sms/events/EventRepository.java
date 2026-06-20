package com.bbc.sms.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<SchoolEvent, UUID> {
    List<SchoolEvent> findBySchoolIdOrderByEventDateDesc(UUID schoolId);
    Optional<SchoolEvent> findByIdAndSchoolId(UUID id, UUID schoolId);
}
