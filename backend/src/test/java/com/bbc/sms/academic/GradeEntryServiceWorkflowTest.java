package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.GradeEntryCellUpsert;
import com.bbc.sms.academic.dto.AcademicDtos.GradeEntryReviewRequest;
import com.bbc.sms.academic.dto.AcademicDtos.GradeEntrySaveRequest;
import com.bbc.sms.academic.dto.AcademicDtos.GradeEntryStudentUpsert;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import com.bbc.sms.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GradeEntryServiceWorkflowTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID subjectTeacherId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 9, 1);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void reviewActionDoesNotRequireSubjectTeacherViewPermission() {
        TenantContext.set(schoolId);

        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId);
        period.setAcademicSessionId(sessionId);
        period.setPeriodType("SEQUENCE");
        period.setStartDate(periodStart);
        when(periods().findByIdAndSchoolId(periodId, schoolId)).thenReturn(Optional.of(period));

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName("6ème A");
        schoolClass.setLevel("secondary");
        when(classes().findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));

        AcademicGradePacket packet = new AcademicGradePacket();
        packet.setId(UUID.randomUUID());
        packet.setStatus("SUBMITTED");
        when(packets().findForUpdate(
                schoolId, periodId, classId, "FRANCAIS")).thenReturn(Optional.of(packet));

        AcademicAccessPolicyService access = mock(AcademicAccessPolicyService.class);
        when(access.can(any(AcademicAccessPolicyService.Capability.class), any(UUID.class), any(UUID.class),
                anyString(), isNull(), any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0) == AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW);

        AcademicWindowPolicyService windows = mock(AcademicWindowPolicyService.class);
        doThrow(new ReviewWindowReached()).when(windows).assertOpen(any(UUID.class), eq(AcademicWindowPolicyService.Action.REVIEW));

        TeachingAssignmentResolver assignments = mock(TeachingAssignmentResolver.class);
        when(assignments.resolve(sessionId, classId, "FRANCAIS", periodStart))
                .thenReturn(new TeachingAssignmentResolver.Resolution("FRANCAIS", subjectTeacherId,
                        "Subject teacher", "T-1", UUID.randomUUID(), 1L, "RESPONSIBLE", "RESOLVED",
                        "ASSIGNMENT_RESOLVED", "Assigned", "Assigned", true));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString(1)).thenReturn("FRANCAIS");
            when(row.getString(2)).thenReturn("Français");
            when(row.getString(3)).thenReturn("French");
            when(row.getInt(4)).thenReturn(1);
            when(row.getBoolean(5)).thenReturn(false);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        GradeEntryService service = new GradeEntryService(
                periods(), mock(AcademicAssessmentRepository.class), mock(AcademicGradeRepository.class),
                mock(SubjectResultCommentRepository.class), packets(), mock(StudentEnrollmentRepository.class),
                mock(StudentRepository.class), mock(SubjectRepository.class), classes(), windows, access,
                assignments, jdbc);

        assertThatThrownBy(() -> service.submit(new GradeEntryReviewRequest(
                periodId, classId, "FRANCAIS", "ACCEPT", "Reviewed", packet.getVersion())))
                .isInstanceOf(ReviewWindowReached.class);

        verify(access).require(eq(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW),
                eq(sessionId), eq(classId), eq("FRANCAIS"), isNull(), eq(periodStart));
        verify(access, never()).require(eq(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW),
                any(UUID.class), any(UUID.class), anyString(), isNull(), any(LocalDate.class));
    }

    @Test
    void submitterCannotReviewTheirOwnGradePacket() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> GradeEntryService.requireIndependentReviewer(userId, userId))
                .isInstanceOf(com.bbc.sms.platform.common.ApiException.class)
                .extracting("code").isEqualTo("GRADE_PACKET_SELF_REVIEW_DENIED");
    }

    @Test
    void savedOrSubmittedSheetsRequireAnExplicitReviewerReturn() {
        for (String state : List.of("SUBMITTED", "ACCEPTED", "LOCKED", "UNKNOWN")) {
            assertThatThrownBy(() -> GradeEntryService.requireEditablePacket(state))
                    .isInstanceOf(com.bbc.sms.platform.common.ApiException.class)
                    .hasMessageContaining("verrouillée");
        }
        GradeEntryService.requireEditablePacket("DRAFT");
        GradeEntryService.requireEditablePacket("RETURNED");
        GradeEntryService.requireEditablePacket(null);
    }

    @Test
    void teacherCanReopenOnlyTheirOwnSavedSheet() {
        TenantContext.set(schoolId);
        UUID authorId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(authorId, schoolId, "teacher", "teacher", "Teacher", "TT"), null));

        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId);
        period.setAcademicSessionId(sessionId);
        period.setPeriodType("SEQUENCE");
        period.setStartDate(periodStart);
        when(periods().findByIdAndSchoolId(periodId, schoolId)).thenReturn(Optional.of(period));

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName("6ème A");
        schoolClass.setLevel("secondary");
        when(classes().findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));

        AcademicGradePacket packet = new AcademicGradePacket();
        packet.setId(UUID.randomUUID());
        packet.setStatus("ACCEPTED");
        packet.setSubmittedBy(authorId);
        when(packets().findForUpdate(schoolId, periodId, classId, "MATH")).thenReturn(Optional.of(packet));

        TeachingAssignmentResolver assignments = mock(TeachingAssignmentResolver.class);
        when(assignments.resolve(sessionId, classId, "MATH", periodStart)).thenReturn(
                new TeachingAssignmentResolver.Resolution("MATH", subjectTeacherId, "Math teacher", "T-1",
                        UUID.randomUUID(), 1L, "RESPONSIBLE", "RESOLVED", "ASSIGNMENT_RESOLVED",
                        "Assigned", "Assigned", true));
        AcademicAccessPolicyService access = mock(AcademicAccessPolicyService.class);
        when(access.can(eq(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW), eq(sessionId), eq(classId),
                eq("MATH"), isNull(), eq(periodStart))).thenReturn(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString(1)).thenReturn("MATH");
            when(row.getString(2)).thenReturn("Mathematics");
            when(row.getString(3)).thenReturn("Mathematics");
            when(row.getInt(4)).thenReturn(1);
            when(row.getBoolean(5)).thenReturn(false);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        AcademicWindowPolicyService windows = mock(AcademicWindowPolicyService.class);
        GradeEntryService raw = new GradeEntryService(periods(), mock(AcademicAssessmentRepository.class),
                mock(AcademicGradeRepository.class), mock(SubjectResultCommentRepository.class), packets(),
                mock(StudentEnrollmentRepository.class), mock(StudentRepository.class), mock(SubjectRepository.class),
                classes(), windows, access, assignments, jdbc);
        GradeEntryService service = spy(raw);
        doReturn(null).when(service).view(periodId, classId, "MATH");

        service.reopenOwn(new com.bbc.sms.academic.dto.AcademicDtos.GradeEntryReopenRequest(
                periodId, classId, "math", null));

        assertThat(packet.getStatus()).isEqualTo("RETURNED");
        verify(windows).assertOpen(periodId, AcademicWindowPolicyService.Action.GRADE_ENTRY);
        verify(access).require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                sessionId, classId, "MATH", null, periodStart);
        verify(packets()).saveAndFlush(packet);
        verify(jdbc).update(contains("UPDATE academic_grade g SET workflow_status=?"), eq("RETURNED"),
                eq(schoolId), eq(periodId), eq("MATH"), eq(classId));
    }

    @Test
    void teacherCannotReopenAnotherTeachersSavedSheet() {
        TenantContext.set(schoolId);
        UUID currentUser = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(currentUser, schoolId, "teacher", "teacher", "Teacher", "TT"), null));

        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId);
        period.setAcademicSessionId(sessionId);
        period.setPeriodType("SEQUENCE");
        period.setStartDate(periodStart);
        when(periods().findByIdAndSchoolId(periodId, schoolId)).thenReturn(Optional.of(period));
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName("6ème A");
        when(classes().findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));

        AcademicGradePacket packet = new AcademicGradePacket();
        packet.setId(UUID.randomUUID());
        packet.setStatus("ACCEPTED");
        packet.setSubmittedBy(UUID.randomUUID());
        when(packets().findForUpdate(schoolId, periodId, classId, "MATH")).thenReturn(Optional.of(packet));

        TeachingAssignmentResolver assignments = mock(TeachingAssignmentResolver.class);
        when(assignments.resolve(sessionId, classId, "MATH", periodStart)).thenReturn(
                new TeachingAssignmentResolver.Resolution("MATH", subjectTeacherId, "Math teacher", "T-1",
                        UUID.randomUUID(), 1L, "RESPONSIBLE", "RESOLVED", "ASSIGNMENT_RESOLVED",
                        "Assigned", "Assigned", true));
        AcademicAccessPolicyService access = mock(AcademicAccessPolicyService.class);
        when(access.can(eq(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW), eq(sessionId), eq(classId),
                eq("MATH"), isNull(), eq(periodStart))).thenReturn(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString(1)).thenReturn("MATH");
            when(row.getString(2)).thenReturn("Mathematics");
            when(row.getString(3)).thenReturn("Mathematics");
            when(row.getInt(4)).thenReturn(1);
            when(row.getBoolean(5)).thenReturn(false);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        GradeEntryService service = new GradeEntryService(periods(), mock(AcademicAssessmentRepository.class),
                mock(AcademicGradeRepository.class), mock(SubjectResultCommentRepository.class), packets(),
                mock(StudentEnrollmentRepository.class), mock(StudentRepository.class), mock(SubjectRepository.class),
                classes(), mock(AcademicWindowPolicyService.class), access, assignments, jdbc);

        assertThatThrownBy(() -> service.reopenOwn(new com.bbc.sms.academic.dto.AcademicDtos.GradeEntryReopenRequest(
                periodId, classId, "math", null)))
                .isInstanceOf(com.bbc.sms.platform.common.ApiException.class)
                .extracting("code").isEqualTo("GRADE_PACKET_REOPEN_DENIED");
        assertThat(packet.getStatus()).isEqualTo("ACCEPTED");
        verify(packets(), never()).saveAndFlush(any());
    }

    @Test
    void saveMakesThePacketAndItsMarksImmediatelyReadyForReportCards() throws Exception {
        TenantContext.set(schoolId);
        UUID authorId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(authorId, schoolId, "teacher", "teacher", "Teacher", "TT"), null));

        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setId(periodId);
        period.setAcademicSessionId(sessionId);
        period.setPeriodType("SEQUENCE");
        period.setStartDate(periodStart);
        when(periods().findByIdAndSchoolId(periodId, schoolId)).thenReturn(Optional.of(period));

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setName("6ème A");
        schoolClass.setLevel("secondary");
        when(classes().findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));

        UUID assessmentId = UUID.randomUUID();
        AcademicAssessment assessment = new AcademicAssessment();
        assessment.setId(assessmentId);
        assessment.setCode("E1");
        assessment.setLabel("Évaluation 1");
        assessment.setMaxScore(BigDecimal.valueOf(20));
        assessment.setWeight(BigDecimal.ONE);
        assessment.setMandatory(true);
        AcademicAssessmentRepository assessmentRepository = mock(AcademicAssessmentRepository.class);
        when(assessmentRepository.findApplicable(schoolId, periodId, classId, "MATH"))
                .thenReturn(List.of(assessment));
        AcademicGradeRepository gradeRepository = mock(AcademicGradeRepository.class);
        SubjectResultCommentRepository commentRepository = mock(SubjectResultCommentRepository.class);
        when(gradeRepository.findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(commentRepository.findBySchoolIdAndStudentIdAndReportingPeriodIdAndProgrammeClassIdAndSubjectCode(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setId(enrollmentId);
        enrollment.setSchoolClassId(classId);
        StudentEnrollmentRepository enrollmentRepository = mock(StudentEnrollmentRepository.class);
        when(enrollmentRepository.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                schoolId, studentId, sessionId, "ACTIVE")).thenReturn(Optional.of(enrollment));

        AcademicGradePacket packet = new AcademicGradePacket();
        packet.setId(UUID.randomUUID());
        packet.setStatus("DRAFT");
        when(packets().findForUpdate(schoolId, periodId, classId, "MATH")).thenReturn(Optional.of(packet));

        TeachingAssignmentResolver assignments = mock(TeachingAssignmentResolver.class);
        when(assignments.resolve(sessionId, classId, "MATH", periodStart)).thenReturn(
                new TeachingAssignmentResolver.Resolution("MATH", subjectTeacherId, "Math teacher", "T-1",
                        UUID.randomUUID(), 1L, "RESPONSIBLE", "RESOLVED", "ASSIGNMENT_RESOLVED",
                        "Assigned", "Assigned", true));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<?> mapper = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            if (sql.contains("academic_curriculum_subject")) {
                when(row.getString(1)).thenReturn("MATH");
                when(row.getString(2)).thenReturn("Mathematics");
                when(row.getString(3)).thenReturn("Mathematics");
                when(row.getInt(4)).thenReturn(1);
                when(row.getBoolean(5)).thenReturn(false);
                return List.of(mapper.mapRow(row, 0));
            }
            if (sql.contains("FROM student_enrollment")) {
                when(row.getObject(1, UUID.class)).thenReturn(studentId);
                when(row.getString(2)).thenReturn("BBC-001");
                when(row.getString(3)).thenReturn("Student One");
                return List.of(mapper.mapRow(row, 0));
            }
            return List.of();
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        AcademicAccessPolicyService access = mock(AcademicAccessPolicyService.class);
        when(access.can(any(AcademicAccessPolicyService.Capability.class), eq(sessionId), eq(classId),
                eq("MATH"), isNull(), eq(periodStart))).thenReturn(true);
        GradeEntryService raw = new GradeEntryService(periods(), assessmentRepository, gradeRepository,
                commentRepository, packets(), enrollmentRepository, mock(StudentRepository.class),
                mock(SubjectRepository.class), classes(), mock(AcademicWindowPolicyService.class), access,
                assignments, jdbc);
        GradeEntryService service = spy(raw);
        // The assertions below concern persistence. The separately tested view mapper is
        // deliberately bypassed so this test is not coupled to its read-only queries.
        doReturn(null).when(service).view(periodId, classId, "MATH");

        service.save(new GradeEntrySaveRequest(periodId, classId, "math", List.of(
                new GradeEntryStudentUpsert(studentId, List.of(
                        new GradeEntryCellUpsert(assessmentId, BigDecimal.valueOf(14), "SCORED", null)), "Good work")),
                null));

        assertThat(packet.getStatus()).isEqualTo("ACCEPTED");
        assertThat(packet.getSubmittedBy()).isEqualTo(authorId);
        assertThat(packet.getReviewReason()).isNull();
        verify(packets()).saveAndFlush(packet);
        ArgumentCaptor<AcademicGrade> savedGrade = ArgumentCaptor.forClass(AcademicGrade.class);
        verify(gradeRepository).save(savedGrade.capture());
        assertThat(savedGrade.getValue().getWorkflowStatus()).isEqualTo("ACCEPTED");
        ArgumentCaptor<SubjectResultComment> savedComment = ArgumentCaptor.forClass(SubjectResultComment.class);
        verify(commentRepository).save(savedComment.capture());
        assertThat(savedComment.getValue().getWorkflowStatus()).isEqualTo("ACCEPTED");
        verify(gradeRepository).flush();
        verify(commentRepository).flush();
        verify(jdbc).update(contains("UPDATE academic_grade g SET workflow_status=?"), eq("ACCEPTED"),
                eq(schoolId), eq(periodId), eq("MATH"), eq(classId));
        verify(jdbc).update(contains("UPDATE subject_result_comment SET workflow_status=?"), eq("ACCEPTED"),
                eq(schoolId), eq(periodId), eq(classId), eq("MATH"));
    }

    @Test
    void incompleteSavePayloadStaysDraftUntilEveryRequiredValueIsPresent() {
        UUID studentId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        AcademicAssessment assessment = new AcademicAssessment();
        assessment.setId(assessmentId);
        assessment.setMandatory(true);

        GradeEntryStudentUpsert missing = new GradeEntryStudentUpsert(studentId,
                List.of(new GradeEntryCellUpsert(assessmentId, null, "MISSING", null)), null);
        GradeEntryStudentUpsert scored = new GradeEntryStudentUpsert(studentId,
                List.of(new GradeEntryCellUpsert(assessmentId, BigDecimal.valueOf(14), "SCORED", null)), null);

        assertThat(GradeEntryService.completeSavePayload(List.of(assessment), Set.of(studentId), List.of(missing), false)).isFalse();
        assertThat(GradeEntryService.completeSavePayload(List.of(assessment), Set.of(studentId), List.of(scored), false)).isTrue();
    }

    @Test
    void pairedProgrammeGradeUsesTheSharedCohortEnrollmentFromTheDisplayedActiveRoster() {
        TenantContext.set(schoolId);
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        AcademicCohortResolver cohorts = mock(AcademicCohortResolver.class);
        when(cohorts.enrollmentIdForClass(sessionId, classId, studentId, "ACTIVE", null))
                .thenReturn(enrollmentId);

        GradeEntryService service = new GradeEntryService(
                periods(), mock(AcademicAssessmentRepository.class), mock(AcademicGradeRepository.class),
                mock(SubjectResultCommentRepository.class), packets(), mock(StudentEnrollmentRepository.class),
                mock(StudentRepository.class), mock(SubjectRepository.class), classes(),
                mock(AcademicWindowPolicyService.class), mock(AcademicAccessPolicyService.class),
                mock(TeachingAssignmentResolver.class), mock(JdbcTemplate.class), cohorts);

        assertThat(service.resolveEnrollmentId(sessionId, classId, studentId, periodStart))
                .isEqualTo(enrollmentId);
    }

    private AcademicReportingPeriodRepository periods() {
        return periodsMock;
    }

    private SchoolClassRepository classes() {
        return classesMock;
    }

    private AcademicGradePacketRepository packets() {
        return packetsMock;
    }

    private final AcademicReportingPeriodRepository periodsMock = mock(AcademicReportingPeriodRepository.class);
    private final SchoolClassRepository classesMock = mock(SchoolClassRepository.class);
    private final AcademicGradePacketRepository packetsMock = mock(AcademicGradePacketRepository.class);

    private static final class ReviewWindowReached extends RuntimeException {}
}
