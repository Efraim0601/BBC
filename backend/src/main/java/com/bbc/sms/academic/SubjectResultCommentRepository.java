package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectResultCommentRepository extends JpaRepository<SubjectResultComment, UUID> {
    Optional<SubjectResultComment> findBySchoolIdAndStudentIdAndReportingPeriodIdAndProgrammeClassIdAndSubjectCode(
            UUID schoolId, UUID studentId, UUID periodId, UUID programmeClassId, String subjectCode);
    List<SubjectResultComment> findBySchoolIdAndReportingPeriodIdAndStudentIdInAndProgrammeClassIdAndSubjectCode(
            UUID schoolId, UUID periodId, List<UUID> studentIds, UUID programmeClassId, String subjectCode);
}
