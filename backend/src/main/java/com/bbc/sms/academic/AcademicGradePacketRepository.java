package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface AcademicGradePacketRepository extends JpaRepository<AcademicGradePacket, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AcademicGradePacket p where p.schoolId=?1 and p.reportingPeriodId=?2 and p.classId=?3 and p.subjectCode=?4")
    Optional<AcademicGradePacket> findForUpdate(UUID schoolId, UUID reportingPeriodId, UUID classId, String subjectCode);

    Optional<AcademicGradePacket> findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(
            UUID schoolId, UUID reportingPeriodId, UUID classId, String subjectCode);
}

