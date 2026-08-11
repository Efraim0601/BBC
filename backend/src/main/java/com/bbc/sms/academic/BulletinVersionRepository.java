package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BulletinVersionRepository extends JpaRepository<BulletinVersion, UUID> {
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(UUID schoolId, UUID studentId, UUID periodId);
    List<BulletinVersion> findBySchoolIdAndStudentIdAndReportingPeriodIdAndStateInOrderByCreatedAtDesc(
            UUID schoolId, UUID studentId, UUID periodId, List<String> states);
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(UUID schoolId, UUID studentId, UUID periodId, String state);
    List<BulletinVersion> findBySchoolIdAndStudentIdAndReportingPeriodIdAndState(UUID schoolId, UUID studentId, UUID periodId, String state);
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndStateOrderByPublishedAtDesc(UUID schoolId, UUID studentId, String state);
    List<BulletinVersion> findBySchoolIdAndReportingPeriodIdAndStateOrderByAverageDesc(UUID schoolId, UUID periodId, String state);
    Optional<BulletinVersion> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from BulletinVersion v where v.id = :id and v.schoolId = :schoolId")
    Optional<BulletinVersion> findByIdAndSchoolIdForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
