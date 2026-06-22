package com.bbc.sms.journey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JourneyRepository extends JpaRepository<JourneyEntry, UUID> {

    List<JourneyEntry> findBySchoolIdAndStudentIdOrderByAcademicYearAsc(UUID schoolId, UUID studentId);

    Optional<JourneyEntry> findByIdAndSchoolId(UUID id, UUID schoolId);

    Optional<JourneyEntry> findBySchoolIdAndStudentIdAndAcademicYear(
            UUID schoolId, UUID studentId, String academicYear);
}
