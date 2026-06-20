package com.bbc.sms.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeRepository extends JpaRepository<StudentFee, UUID> {
    List<StudentFee> findBySchoolId(UUID schoolId);
    Optional<StudentFee> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
}
