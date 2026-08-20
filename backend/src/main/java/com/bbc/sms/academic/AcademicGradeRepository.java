package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicGradeRepository extends JpaRepository<AcademicGrade, UUID> {
    List<AcademicGrade> findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(UUID schoolId, UUID studentId, UUID periodId);
    List<AcademicGrade> findBySchoolIdAndReportingPeriodIdAndStudentIdInAndSubjectCodeOrderByStudentIdAscAssessmentIdAsc(
            UUID schoolId, UUID periodId, List<UUID> studentIds, String subjectCode);
    Optional<AcademicGrade> findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(UUID schoolId, UUID studentId, UUID assessmentId, String subjectCode);
}
