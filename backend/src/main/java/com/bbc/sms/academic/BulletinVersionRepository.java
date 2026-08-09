package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BulletinVersionRepository extends JpaRepository<BulletinVersion, UUID> {
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(UUID schoolId, UUID studentId, UUID periodId);
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(UUID schoolId, UUID studentId, UUID periodId, String state);
    List<BulletinVersion> findBySchoolIdAndStudentIdAndReportingPeriodIdAndState(UUID schoolId, UUID studentId, UUID periodId, String state);
    Optional<BulletinVersion> findFirstBySchoolIdAndStudentIdAndStateOrderByPublishedAtDesc(UUID schoolId, UUID studentId, String state);
    List<BulletinVersion> findBySchoolIdAndReportingPeriodIdAndStateOrderByAverageDesc(UUID schoolId, UUID periodId, String state);
    Optional<BulletinVersion> findByIdAndSchoolId(UUID id, UUID schoolId);
}
