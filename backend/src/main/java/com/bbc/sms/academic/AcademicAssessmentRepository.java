package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicAssessmentRepository extends JpaRepository<AcademicAssessment, UUID> {
    List<AcademicAssessment> findBySchoolIdAndReportingPeriodIdOrderByDisplayOrder(UUID schoolId, UUID periodId);
    @Query("""
            select a from AcademicAssessment a
             where a.schoolId = :schoolId and a.reportingPeriodId = :periodId
               and (a.classId is null or a.classId = :classId)
               and (a.subjectCode is null or upper(a.subjectCode) = upper(:subjectCode))
             order by a.displayOrder, a.code
            """)
    List<AcademicAssessment> findApplicable(@Param("schoolId") UUID schoolId,
                                            @Param("periodId") UUID periodId,
                                            @Param("classId") UUID classId,
                                            @Param("subjectCode") String subjectCode);
    Optional<AcademicAssessment> findByIdAndSchoolId(UUID id, UUID schoolId);
    boolean existsBySchoolIdAndReportingPeriodIdAndCodeIgnoreCase(UUID schoolId, UUID periodId, String code);

    @Query("""
            select case when count(a) > 0 then true else false end from AcademicAssessment a
             where a.schoolId = :schoolId and a.reportingPeriodId = :periodId
               and (a.classId = :classId or (a.classId is null and :classId is null))
               and (a.subjectCode = :subjectCode or (a.subjectCode is null and :subjectCode is null))
               and upper(a.code) = upper(:code)
            """)
    boolean existsScoped(@Param("schoolId") UUID schoolId, @Param("periodId") UUID periodId,
                         @Param("classId") UUID classId, @Param("subjectCode") String subjectCode,
                         @Param("code") String code);

    @Query("""
            select a from AcademicAssessment a
             where a.schoolId = :schoolId and a.reportingPeriodId = :periodId
               and (a.classId is null or a.classId = :classId)
             order by a.displayOrder, a.code
            """)
    List<AcademicAssessment> findApplicableForClass(@Param("schoolId") UUID schoolId,
                                                    @Param("periodId") UUID periodId,
                                                    @Param("classId") UUID classId);
}
