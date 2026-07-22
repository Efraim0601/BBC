package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectClassCoefRepository extends JpaRepository<SubjectClassCoef, UUID> {
    List<SubjectClassCoef> findBySchoolId(UUID schoolId);
    List<SubjectClassCoef> findBySchoolIdAndClassId(UUID schoolId, UUID classId);
    Optional<SubjectClassCoef> findBySchoolIdAndSubjectIdAndClassId(UUID schoolId, UUID subjectId, UUID classId);
}
