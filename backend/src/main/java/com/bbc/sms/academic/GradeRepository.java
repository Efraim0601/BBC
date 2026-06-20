package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRepository extends JpaRepository<Grade, UUID> {
    List<Grade> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
    Optional<Grade> findBySchoolIdAndStudentIdAndSubjectCodeAndSequence(UUID schoolId, UUID studentId, String subjectCode, int sequence);
}
