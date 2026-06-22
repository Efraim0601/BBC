package com.bbc.sms.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

    Optional<HealthRecord> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
}
