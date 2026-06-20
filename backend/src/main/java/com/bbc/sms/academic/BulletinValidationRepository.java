package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BulletinValidationRepository extends JpaRepository<BulletinValidation, UUID> {
    Optional<BulletinValidation> findBySchoolIdAndStudentIdAndSequence(UUID schoolId, UUID studentId, int sequence);
}
