package com.bbc.sms.finance.charges;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentChargeRepository extends JpaRepository<StudentCharge, UUID> {
    Optional<StudentCharge> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<StudentCharge> findBySchoolIdAndGenerationKey(UUID schoolId, String generationKey);
    List<StudentCharge> findBySchoolIdAndStudentEnrollmentIdOrderByChargeDateAscCreatedAtAsc(UUID schoolId, UUID enrollmentId);
    List<StudentCharge> findBySchoolIdAndStudentIdOrderByChargeDateAscCreatedAtAsc(UUID schoolId, UUID studentId);
    List<StudentCharge> findBySchoolIdAndAcademicSessionIdOrderByChargeDateAscCreatedAtAsc(UUID schoolId, UUID sessionId);
    List<StudentCharge> findBySchoolIdOrderByChargeDateAscCreatedAtAsc(UUID schoolId);
    List<StudentCharge> findBySchoolIdAndStatusOrderByChargeDateAscCreatedAtAsc(UUID schoolId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StudentCharge c where c.schoolId = :schoolId and c.id = :id")
    Optional<StudentCharge> findForUpdateByIdAndSchoolId(UUID id, UUID schoolId);
}
