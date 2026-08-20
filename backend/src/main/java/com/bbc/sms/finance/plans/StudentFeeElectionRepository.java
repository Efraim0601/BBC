package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeElectionRepository extends JpaRepository<StudentFeeElection, UUID> {
    List<StudentFeeElection> findBySchoolIdAndStudentEnrollmentIdOrderByCreatedAtDesc(UUID schoolId, UUID enrollmentId);
    Optional<StudentFeeElection> findBySchoolIdAndStudentEnrollmentIdAndFeePlanLineId(
            UUID schoolId, UUID enrollmentId, UUID lineId);
}
