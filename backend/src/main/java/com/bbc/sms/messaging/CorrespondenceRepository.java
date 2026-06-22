package com.bbc.sms.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorrespondenceRepository extends JpaRepository<Correspondence, UUID> {

    List<Correspondence> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    List<Correspondence> findBySchoolIdAndStudentIdOrderByCreatedAtDesc(UUID schoolId, UUID studentId);

    Optional<Correspondence> findByIdAndSchoolId(UUID id, UUID schoolId);
}
