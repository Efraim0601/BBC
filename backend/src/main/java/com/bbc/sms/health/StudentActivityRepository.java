package com.bbc.sms.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentActivityRepository extends JpaRepository<StudentActivity, UUID> {

    List<StudentActivity> findBySchoolIdAndStudentIdOrderByNameAsc(UUID schoolId, UUID studentId);

    Optional<StudentActivity> findByIdAndSchoolId(UUID id, UUID schoolId);
}
