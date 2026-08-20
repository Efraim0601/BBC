package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeOverrideRepository extends JpaRepository<StudentFeeOverride, UUID> {
    Optional<StudentFeeOverride> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<StudentFeeOverride> findBySchoolIdAndStudentEnrollmentIdOrderByEffectiveFromDescCreatedAtDesc(
            UUID schoolId, UUID enrollmentId);
    long countBySchoolIdAndFeePlanLineId(UUID schoolId, UUID lineId);
}
