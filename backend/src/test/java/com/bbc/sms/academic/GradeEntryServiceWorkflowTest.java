package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.GradeEntryReviewRequest;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import com.bbc.sms.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
        when(packets().findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(
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
            when(row.getInt(3)).thenReturn(1);
            when(row.getBoolean(4)).thenReturn(false);
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
    void pairedProgrammeGradeUsesTheSharedCohortEnrollment() {
        TenantContext.set(schoolId);
        UUID studentId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        AcademicCohortResolver cohorts = mock(AcademicCohortResolver.class);
        when(cohorts.enrollmentIdForClass(sessionId, classId, studentId, "ACTIVE", periodStart))
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
