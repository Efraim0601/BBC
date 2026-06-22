package com.bbc.sms.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    List<LeaveRequest> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    Optional<LeaveRequest> findByIdAndSchoolId(UUID id, UUID schoolId);
}
