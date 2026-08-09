package com.bbc.sms.foundation.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicReportingPeriodRepository extends JpaRepository<AcademicReportingPeriod, UUID> {
    List<AcademicReportingPeriod> findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(UUID schoolId, UUID sessionId);
    Optional<AcademicReportingPeriod> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<AcademicReportingPeriod> findBySchoolIdAndAcademicSessionIdAndCode(UUID schoolId, UUID sessionId, String code);
}
