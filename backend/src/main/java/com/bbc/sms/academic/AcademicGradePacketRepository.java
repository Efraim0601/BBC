package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AcademicGradePacketRepository extends JpaRepository<AcademicGradePacket, UUID> {
    Optional<AcademicGradePacket> findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(
            UUID schoolId, UUID reportingPeriodId, UUID classId, String subjectCode);

    Optional<AcademicGradePacket> findTopBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCodeOrderByRevisionNumberDesc(
            UUID schoolId, UUID reportingPeriodId, UUID classId, String subjectCode);
}

