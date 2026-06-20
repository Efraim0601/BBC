package com.bbc.sms.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findBySchoolIdOrderByName(UUID schoolId);
    Optional<SchoolClass> findBySchoolIdAndName(UUID schoolId, String name);
}
