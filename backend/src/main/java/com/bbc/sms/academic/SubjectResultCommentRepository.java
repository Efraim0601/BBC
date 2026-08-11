package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectResultCommentRepository extends JpaRepository<SubjectResultComment, UUID> {
    Optional<SubjectResultComment> findBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCode(UUID schoolId, UUID studentId, UUID periodId, String subjectCode);
    Optional<SubjectResultComment> findTopBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCodeOrderByUpdatedAtDesc(UUID schoolId, UUID studentId, UUID periodId, String subjectCode);
    Optional<SubjectResultComment> findByPacketIdAndStudentIdAndReportingPeriodIdAndSubjectCode(UUID packetId, UUID studentId, UUID periodId, String subjectCode);
    List<SubjectResultComment> findBySchoolIdAndReportingPeriodIdAndStudentIdInAndSubjectCode(
            UUID schoolId, UUID periodId, List<UUID> studentIds, String subjectCode);
    List<SubjectResultComment> findByPacketIdAndStudentIdInAndSubjectCode(
            UUID packetId, List<UUID> studentIds, String subjectCode);
}
