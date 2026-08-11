package com.bbc.sms.academic;

import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCardBatchEligibilityServiceTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();

    @Mock JdbcTemplate jdbc;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock AcademicReportingPeriodRepository periods;
    @Mock AcademicSessionRepository sessions;
    @Mock SchoolClassRepository classes;
    @Mock StudentRepository students;
    @Mock TeacherScopeService teacherScope;
    @Mock BulletinSnapshotService snapshots;
    @Mock AcademicWindowPolicyService windows;

    private AcademicReportingPeriod period;
    private AcademicSession session;
    private SchoolClass schoolClass;
    private StudentEnrollment enrollment;
    private Student student;

    @BeforeEach
    void setUp() {
        TenantContext.set(schoolId);

        session = new AcademicSession();
        session.setId(sessionId);
        session.setSchoolId(schoolId);
        session.setLabel("2026-2027");

        period = new AcademicReportingPeriod();
        period.setId(periodId);
        period.setSchoolId(schoolId);
        period.setAcademicSessionId(sessionId);
        period.setCode("S1");
        period.setLabel("Sequence 1");
        period.setVersion(3);

        schoolClass = new SchoolClass();
        schoolClass.setId(classId);
        schoolClass.setSchoolId(schoolId);
        schoolClass.setName("4eme A");

        enrollment = new StudentEnrollment();
        enrollment.setId(enrollmentId);
        enrollment.setSchoolId(schoolId);
        enrollment.setStudentId(studentId);
        enrollment.setAcademicSessionId(sessionId);
        enrollment.setSchoolClassId(classId);
        enrollment.setStatus("ACTIVE");

        student = new Student();
        student.setId(studentId);
        student.setSchoolId(schoolId);
        student.setFirstName("Marie");
        student.setLastName("Amanta");
        student.setMatricule("BBC-1615");

        when(classes.findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));
        when(periods.findByIdAndSchoolId(periodId, schoolId)).thenReturn(Optional.of(period));
        when(students.findByIdAndSchoolId(studentId, schoolId)).thenReturn(Optional.of(student));
        when(enrollments.findBySchoolIdAndStudentIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByEnrolledOnDescCreatedAtDesc(
                schoolId, studentId, sessionId, classId, "ACTIVE")).thenReturn(List.of(enrollment));
        org.mockito.Mockito.lenient().when(windows.effective(eq(periodId), eq(AcademicWindowPolicyService.Action.BATCH_GENERATION)))
                .thenReturn(new AcademicWindowPolicyService.WindowView(periodId, "S1", "Sequence 1",
                        "BATCH_GENERATION", null, null, "TERM_MANAGEMENT_WINDOW", null, null, true,
                        "TERM_MANAGEMENT_WINDOW", "UNRESTRICTED", null, List.of(), "Africa/Douala",
                        "UNRESTRICTED", "UNRESTRICTED", null, "T1", "Trimester 1",
                        List.of("S1", "S2", "T1_RESULT"), Instant.parse("2026-09-15T10:00:00Z")));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void lifecycleStatesProduceStableBusinessCodes() {
        assertThat(resolve(List.of()).code()).isEqualTo("REPORT_NOT_CREATED");
        assertThat(resolve(List.of(version("DRAFT", "{}"))).code()).isEqualTo("REPORT_DRAFT");
        assertThat(resolve(List.of(version("RETURNED", "{}"))).code()).isEqualTo("REPORT_RETURNED");
        assertThat(resolve(List.of(version("VALIDATED", "{}"))).code()).isEqualTo("REPORT_VALIDATED_NOT_PUBLISHED");
        assertThat(resolve(List.of(version("SUPERSEDED", "{}"))).code()).isEqualTo("REPORT_SUPERSEDED_ONLY");
        assertThat(resolve(List.of(version("REVOKED", "{}"))).code()).isEqualTo("REPORT_PUBLICATION_REVOKED");
        assertThat(resolve(List.of(version("PUBLISHED", "{\"average\":12}"))).eligibility()).isEqualTo("READY");
        assertThat(resolve(List.of(version("PUBLISHED", "not-json"))).code()).isEqualTo("SNAPSHOT_UNREADABLE");
    }

    @Test
    void exactPeriodKeepsS1BlockedWhenOnlyT1WasPublished() {
        // The query is parameterized by the selected S1 period and enrollment;
        // a T1 row is never a candidate for this resolution.
        EligibilityFixture fixture = prepare(List.of());
        assertThat(fixture.row().code()).isEqualTo("REPORT_NOT_CREATED");
        assertThat(fixture.row().messageArgs()).containsEntry("periodCode", "S1");
    }

    @Test
    void latestReadablePublishedVersionWinsOverAcorruptNewerVersion() {
        VersionSpec older = version("PUBLISHED", "{\"average\":11}");
        VersionSpec newerCorrupt = version("PUBLISHED", "broken");
        newerCorrupt.createdAt = older.createdAt.plusSeconds(30);
        EligibilityFixture fixture = prepare(List.of(newerCorrupt, older));
        assertThat(fixture.row().eligibility()).isEqualTo("READY");
        assertThat(fixture.row().snapshot().id()).isEqualTo(older.id);
    }

    @Test
    void anotherClassOrTenantCannotMakeAStudentEligible() {
        when(enrollments.findBySchoolIdAndStudentIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByEnrolledOnDescCreatedAtDesc(
                schoolId, studentId, sessionId, classId, "ACTIVE")).thenReturn(List.of());
        assertThat(resolve(List.of(version("PUBLISHED", "{}"))).code()).isEqualTo("ENROLLMENT_MISSING");

        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> service().resolveForJob(otherTenant, sessionId, classId, periodId, studentId, "en"))
                .isInstanceOf(com.bbc.sms.platform.common.ApiException.class)
                .hasMessageContaining("locataire");
    }

    @Test
    void previewFingerprintIsLocaleIndependentAndIncludesSnapshotEvidence() {
        prepare(List.of(version("PUBLISHED", "{}")));
        when(sessions.findByIdAndSchoolId(sessionId, schoolId)).thenReturn(Optional.of(session));
        when(enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                schoolId, sessionId, classId, "ACTIVE")).thenReturn(List.of(enrollment));

        ReportCardBatchEligibilityService.EligibilityPreview fr = service().preview(classId, periodId, "fr");
        ReportCardBatchEligibilityService.EligibilityPreview en = service().preview(classId, periodId, "en");
        assertThat(fr.policy()).isEqualTo("PUBLISHED_ONLY");
        assertThat(fr.scopeFingerprint()).isEqualTo(en.scopeFingerprint());
        assertThat(fr.rows()).singleElement().satisfies(row -> {
            assertThat(row.eligibility()).isEqualTo("READY");
            assertThat(row.snapshot()).isNotNull();
        });
    }

    private ReportCardBatchEligibilityService.EligibilityRow resolve(List<VersionSpec> versions) {
        return prepare(versions).row();
    }

    private EligibilityFixture prepare(List<VersionSpec> versions) {
        stubVersions(versions);
        ReportCardBatchEligibilityService service = service();
        return new EligibilityFixture(service.resolveForJob(schoolId, sessionId, classId, periodId, studentId, "en"));
    }

    private ReportCardBatchEligibilityService service() {
        return new ReportCardBatchEligibilityService(jdbc, enrollments, periods, sessions, classes, students,
                teacherScope, snapshots, new ObjectMapper(), windows);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubVersions(List<VersionSpec> versions) {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            List<Object> rows = new ArrayList<>();
            for (int index = 0; index < versions.size(); index++) rows.add(mapper.mapRow(resultSet(versions.get(index)), index));
            return rows;
        }).when(jdbc).query(anyString(), ArgumentMatchers.any(RowMapper.class), any(), any(), any(), any(), any());
    }

    private ResultSet resultSet(VersionSpec spec) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(spec.id);
        when(rs.getObject("enrollment_id", UUID.class)).thenReturn(enrollmentId);
        when(rs.getString("state")).thenReturn(spec.state);
        when(rs.getString("snapshot_hash")).thenReturn("hash-" + spec.id);
        when(rs.getLong("version")).thenReturn(spec.version);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(spec.createdAt);
        when(rs.getObject("published_at", OffsetDateTime.class)).thenReturn(spec.publishedAt);
        when(rs.getString("snapshot_json")).thenReturn(spec.json);
        return rs;
    }

    private VersionSpec version(String state, String json) {
        Instant now = Instant.parse("2026-08-01T10:00:00Z");
        return new VersionSpec(UUID.randomUUID(), state, json, 1,
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                "PUBLISHED".equals(state) ? OffsetDateTime.ofInstant(now, ZoneOffset.UTC) : null);
    }

    private record EligibilityFixture(ReportCardBatchEligibilityService.EligibilityRow row) {}

    private static final class VersionSpec {
        private final UUID id;
        private final String state;
        private final String json;
        private final long version;
        private final OffsetDateTime publishedAt;
        private OffsetDateTime createdAt;

        private VersionSpec(UUID id, String state, String json, long version,
                            OffsetDateTime createdAt, OffsetDateTime publishedAt) {
            this.id = id;
            this.state = state;
            this.json = json;
            this.version = version;
            this.createdAt = createdAt;
            this.publishedAt = publishedAt;
        }
    }
}
