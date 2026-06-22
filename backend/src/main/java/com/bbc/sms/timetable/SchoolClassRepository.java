package com.bbc.sms.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findBySchoolIdOrderByName(UUID schoolId);
    List<SchoolClass> findBySchoolIdAndSectionId(UUID schoolId, String sectionId);
    Optional<SchoolClass> findBySchoolIdAndName(UUID schoolId, String name);
    Optional<SchoolClass> findByIdAndSchoolId(UUID id, UUID schoolId);
    boolean existsBySchoolIdAndName(UUID schoolId, String name);
    boolean existsBySchoolIdAndSectionId(UUID schoolId, String sectionId);
}
