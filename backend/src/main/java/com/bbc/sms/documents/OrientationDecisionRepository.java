package com.bbc.sms.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrientationDecisionRepository extends JpaRepository<OrientationDecision, UUID> {

    List<OrientationDecision> findBySchoolIdAndStudentIdOrderByAcademicYearDesc(UUID schoolId, UUID studentId);

    Optional<OrientationDecision> findByIdAndSchoolId(UUID id, UUID schoolId);
}
