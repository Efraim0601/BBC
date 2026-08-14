package com.bbc.sms.foundation.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, UUID> {
    List<StudentEnrollment> findBySchoolIdAndStudentIdOrderByEnrolledOnDescCreatedAtDesc(UUID schoolId, UUID studentId);
    Optional<StudentEnrollment> findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
            UUID schoolId, UUID studentId, UUID sessionId, String status);
    Optional<StudentEnrollment> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<StudentEnrollment> findBySchoolIdAndAcademicSessionIdAndLevelSnapshotAndSubsystemSnapshotAndStatusOrderByClassNameSnapshotAsc(
            UUID schoolId, UUID sessionId, String levelSnapshot, String subsystemSnapshot, String status);
    List<StudentEnrollment> findBySchoolIdAndAcademicSessionIdAndStatusOrderByClassNameSnapshotAsc(
            UUID schoolId, UUID sessionId, String status);
    List<StudentEnrollment> findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
            UUID schoolId, UUID sessionId, UUID classId, String status);
    List<StudentEnrollment> findBySchoolIdAndStatusOrderByClassNameSnapshotAsc(
            UUID schoolId, String status);
}
