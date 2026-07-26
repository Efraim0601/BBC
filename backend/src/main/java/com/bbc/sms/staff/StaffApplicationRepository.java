package com.bbc.sms.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffApplicationRepository extends JpaRepository<StaffApplication, UUID> {
    List<StaffApplication> findBySchoolIdOrderBySubmittedAtDesc(UUID schoolId);
    List<StaffApplication> findBySchoolIdAndStatusOrderBySubmittedAtDesc(UUID schoolId, String status);
    Optional<StaffApplication> findByIdAndSchoolId(UUID id, UUID schoolId);
    boolean existsBySchoolIdAndEmailIgnoreCaseAndStatusIn(UUID schoolId, String email, List<String> statuses);
}
