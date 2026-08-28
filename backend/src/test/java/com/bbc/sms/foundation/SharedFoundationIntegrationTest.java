package com.bbc.sms.foundation;

import com.bbc.sms.attendance.AttendanceWorkflowService;
import com.bbc.sms.attendance.dto.AttendanceDtos.BulkMarkRequest;
import com.bbc.sms.attendance.dto.AttendanceDtos.MarkInput;
import com.bbc.sms.attendance.dto.AttendanceDtos.ActionRequest;
import com.bbc.sms.documents.OfficialDocumentDtos.GenerateRequest;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicConfigurationCopyService;
import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.foundation.session.SessionDtos.ConfigurationCopyPreviewRequest;
import com.bbc.sms.foundation.session.SessionDtos.CopyScopeSelection;
import com.bbc.sms.foundation.session.SessionDtos.SessionUpsert;
import com.bbc.sms.foundation.session.SessionDtos.TermUpsert;
import com.bbc.sms.journey.JourneyPromotionService;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.ProgressionPathUpsert;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.PromotionCommitRequest;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.PromotionOverrideRequest;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.PromotionPreviewRequest;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.PromotionRuleUpsert;
import com.bbc.sms.journey.dto.JourneyPromotionDtos.PromotionActivationRequest;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentService;
import com.bbc.sms.timetable.TimetableVersionService;
import com.bbc.sms.timetable.dto.TimetableVersionDtos.TimetableVersionActionRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class SharedFoundationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bbc_test").withUsername("bbc").withPassword("bbc");

    private static final Path DOCUMENTS = Path.of("target", "foundation-test-documents").toAbsolutePath();
    private static UUID schoolId;
    private UUID actorUserId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("bbc.documents.storage-path", DOCUMENTS::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicSessionService sessionService;
    @Autowired AcademicConfigurationCopyService configurationCopy;
    @Autowired IdempotencyService idempotency;
    @Autowired OfficialDocumentService documents;
    @Autowired AttendanceWorkflowService attendance;
    @Autowired JourneyPromotionService promotions;
    @Autowired StudentService students;
    @Autowired TimetableVersionService timetables;

    @BeforeEach
    void tenant() {
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId, "T" + schoolId.toString().substring(0, 6), "Test school");
        actorUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO role(code,label_fr,label_en,builtin) VALUES ('principal','Principal','Principal',true) ON CONFLICT (code) DO NOTHING");
        jdbc.update("""
            INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,active)
            VALUES (?,?,'foundation-test','test','Foundation test','FT','principal',true)
            """, actorUserId, schoolId);
        jdbc.update("""
            INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason)
            VALUES (?,?,'principal',true,'Foundation integration fixture')
            ON CONFLICT DO NOTHING
            """, schoolId, actorUserId);
        for (String action : new String[]{
                "ACADEMIC_ROSTER_VIEW", "STUDENT_DIRECTORY_VIEW", "ENROLLMENT_VIEW", "ATTENDANCE_POLICY_MANAGE", "ATTENDANCE_ROSTER_VIEW",
                "ATTENDANCE_MARK", "ATTENDANCE_FINALIZE", "ATTENDANCE_REOPEN",
                "ATTENDANCE_ANALYTICS_VIEW", "ATTENDANCE_RECONCILE", "TIMETABLE_PUBLISH"}) {
            jdbc.update("""
                INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
                VALUES (?,? ,?,'ALLOW','SCHOOL_ALL',true,'Foundation integration fixture')
                ON CONFLICT DO NOTHING
                """, schoolId, "principal", action);
        }
        TenantContext.set(schoolId);
        AppUserPrincipal principal = new AppUserPrincipal(actorUserId, schoolId,
                "foundation-test", "principal", "Foundation test", "FT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach void clearTenant() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void flywayCreatesEveryFoundationTableAndSessionTermsCannotOverlap() {
        for (String table : new String[]{"academic_session","academic_term","student_enrollment",
                "school_calendar_day","expected_school_session","audit_event","idempotency_key",
                "document_template","generated_document","permission_action_grant",
                "attendance_policy","attendance_session","attendance_mark","attendance_session_event",
                "attendance_notification","secondary_competency_model","secondary_competency",
                "secondary_competency_mark","bulletin_batch_artifact"}) {
            assertThat(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table)).isTrue();
        }
        var session = sessionService.create(new SessionUpsert("2026-2027", "Session 2026-2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 31), "DRAFT", true,
                null, null, null, null, null));
        sessionService.addTerm(session.id(), new TermUpsert("T1", "Trimestre 1", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 20), null, null, null, null, null));
        assertThatThrownBy(() -> sessionService.addTerm(session.id(), new TermUpsert("T2", "Overlap", 2,
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 20), null, null, null, null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("chevaucher");
        assertThat(sessionService.current().id()).isEqualTo(session.id());
    }

    @Test
    void attendanceRosterUsesExpectedSessionsAndAuditsFinalizationAndReopening() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        String sectionId = "p" + schoolId.toString().substring(0, 8);
        LocalDate date = LocalDate.of(2026, 9, 1);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','primary')",
                sectionId, schoolId, "Primary");
        jdbc.update("""
            INSERT INTO school_class(id,school_id,section_id,name,subsystem,level)
            VALUES (?,?,?,'CP Test','FR','primary')
            """, classId, schoolId, sectionId);
        jdbc.update("""
            INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current)
            VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)
            """, academicId, schoolId);
        jdbc.update("""
            INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level)
            VALUES (?,?, 'TEST-1','Ada','Lovelace',?,'CP Test','FR','primary')
            """, studentId, schoolId, classId);
        jdbc.update("""
            INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,
                class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source)
            VALUES (?,?,?,?,'CP Test','primary','FR','ACTIVE','2026-09-01','TEST')
            """, schoolId, studentId, academicId, classId);

        var preview = attendance.generate(date, date, true);
        assertThat(preview.expectedSessions()).isEqualTo(1);
        assertThat(preview.synchronizedSessions()).isZero();

        var roster = attendance.roster(classId, date, null);
        assertThat(roster.marks()).hasSize(1);
        assertThat(roster.marks().getFirst().status()).isEqualTo("unmarked");
        var saved = attendance.save(new BulkMarkRequest(roster.session().id(), roster.session().version(),
                java.util.List.of(new MarkInput(studentId, "absent", null, null, 0))));
        assertThat(saved.marks().getFirst().status()).isEqualTo("absent");
        assertThat(saved.marks().getFirst().reason()).isNull();
        assertThatThrownBy(() -> attendance.save(new BulkMarkRequest(roster.session().id(), roster.session().version(),
                java.util.List.of(new MarkInput(studentId, "present", null, "Late stale edit", 0)))))
                .isInstanceOf(ApiException.class).hasMessageContaining("modifié");

        var finalized = attendance.finalizeSession(saved.session().id(), new ActionRequest(saved.session().version(), null));
        assertThat(finalized.session().status()).isEqualTo("FINALIZED");
        var reopened = attendance.reopen(finalized.session().id(),
                new ActionRequest(finalized.session().version(), "Correction approved"));
        assertThat(reopened.session().status()).isEqualTo("REOPENED");
        assertThat(reopened.events()).extracting(e -> e.action())
                .contains("SAVED", "FINALIZED", "REOPENED");

        var analytics = attendance.analytics(date, date, classId);
        assertThat(analytics.expected()).isEqualTo(1);
        assertThat(analytics.absent()).isEqualTo(1);
        assertThat(analytics.attendancePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void attendanceGenerationUsesOnlyThePublishedTimetableVersion() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID oldVersionId = UUID.randomUUID();
        UUID publishedVersionId = UUID.randomUUID();
        String sectionId = "v" + schoolId.toString().substring(0, 8);
        LocalDate date = LocalDate.of(2026, 9, 1);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,'Secondary','FR','secondary')",
                sectionId, schoolId);
        jdbc.update("""
            INSERT INTO school_class(id,school_id,section_id,name,subsystem,level)
            VALUES (?,?,?,'Versioned 4E','FR','secondary')
            """, classId, schoolId, sectionId);
        jdbc.update("""
            INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current)
            VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)
            """, academicId, schoolId);
        jdbc.update("""
            INSERT INTO timetable_class_config(school_id,academic_session_id,class_id,model,status)
            VALUES (?,?,?,'DEPARTMENTAL','PUBLISHED')
            """, schoolId, academicId, classId);
        jdbc.update("""
            INSERT INTO timetable_version(id,school_id,academic_session_id,version_no,status,effective_from,effective_to,published_at)
            VALUES (?,?,?,1,'ARCHIVED','2026-09-01','2027-07-31',NULL),
                   (?,?,?,2,'PUBLISHED','2026-09-01','2027-07-31',now())
            """, oldVersionId, schoolId, academicId, publishedVersionId, schoolId, academicId);
        jdbc.update("""
            INSERT INTO timetable_slot(id,school_id,class_id,academic_session_id,timetable_version_id,day_idx,slot_idx,subject_code)
            VALUES (gen_random_uuid(),?,?,?,?,1,0,'FRANCAIS'),
                   (gen_random_uuid(),?,?,?,?,1,0,'FRANCAIS')
            """, schoolId, classId, academicId, oldVersionId,
                schoolId, classId, academicId, publishedVersionId);

        var preview = attendance.generate(date, date, true);

        assertThat(preview.expectedSessions()).isEqualTo(1);
        assertThat(preview.synchronizedSessions()).isZero();
    }

    @Test
    void attendanceGenerationExcludesConfiguredHolidays() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        String sectionId = "h" + schoolId.toString().substring(0, 8);
        LocalDate date = LocalDate.of(2026, 9, 2);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,'Holiday','FR','primary')",
                sectionId, schoolId);
        jdbc.update("""
            INSERT INTO school_class(id,school_id,section_id,name,subsystem,level)
            VALUES (?,?,?,'Holiday CP','FR','primary')
            """, classId, schoolId, sectionId);
        jdbc.update("""
            INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current)
            VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)
            """, academicId, schoolId);
        jdbc.update("INSERT INTO school_holiday(id,school_id,holiday_date,label) VALUES (gen_random_uuid(),?,?,?)",
                schoolId, date, "Gate 8 holiday regression");

        var preview = attendance.generate(date, date, true);

        assertThat(preview.expectedSessions()).isZero();
        assertThat(preview.synchronizedSessions()).isZero();
    }

    @Test
    void attendanceGenerationSkipsSecondaryClassesWithoutPublishedConfiguration() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        String sectionId = "n" + schoolId.toString().substring(0, 8);
        LocalDate date = LocalDate.of(2026, 8, 14);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,'No timetable','FR','secondary')",
                sectionId, schoolId);
        jdbc.update("""
            INSERT INTO school_class(id,school_id,section_id,name,subsystem,level)
            VALUES (?,?,?,'No timetable 6E','FR','secondary')
            """, classId, schoolId, sectionId);
        jdbc.update("""
            INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current)
            VALUES (?,?, 'G8-NO-TIMETABLE','G8 no timetable','2026-08-14','2026-08-14','OPEN',false)
            """, academicId, schoolId);

        var result = attendance.generate(date, date, false);

        assertThat(result.expectedSessions()).isZero();
        assertThat(result.synchronizedSessions()).isZero();
    }

    @Test
    void academicRosterUsesActiveEnrollmentForTheRequestedSessionAndClass() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID enrolledStudent = UUID.randomUUID();
        UUID legacyOnlyStudent = UUID.randomUUID();
        String sectionId = "r" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','primary')",
                sectionId, schoolId, "Roster");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'CE1','FR','primary')",
                classId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?, 'ROSTER-1','Enrolled','Student',?,'CE1','FR','primary')",
                enrolledStudent, schoolId, classId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?, 'ROSTER-2','Legacy','Student',?,'CE1','FR','primary')",
                legacyOnlyStudent, schoolId, classId);
        jdbc.update("INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) VALUES (?,?,?,?,'CE1','primary','FR','ACTIVE','2026-09-01','TEST')",
                schoolId, enrolledStudent, academicId, classId);

        var roster = students.roster(academicId, classId);

        assertThat(roster).extracting(v -> v.id()).containsExactly(enrolledStudent);
        assertThat(roster.getFirst().className()).isEqualTo("CE1");
    }

    @Test
    void academicStudentRosterDoesNotRequireEnrollmentHistoryAuthority() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID teacherUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        String sectionId = "rd" + schoolId.toString().substring(0, 8);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')",
                sectionId, schoolId, "Roster boundary");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'4E Roster','FR','secondary')",
                classId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,'Roster Teacher','Permanent',true,'secondary')",
                employeeId, schoolId, "RD-" + schoolId.toString().substring(0, 8));
        jdbc.update("INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,employee_id,active) VALUES (?,?,?,'test','Roster Teacher','RT','form_teacher',?,true)",
                teacherUserId, schoolId, "roster-teacher-" + schoolId.toString().substring(0, 8), employeeId);
        jdbc.update("INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason) VALUES (?,?, 'form_teacher',true,'Roster policy boundary regression') ON CONFLICT DO NOTHING",
                schoolId, teacherUserId);
        for (String action : new String[]{"ACADEMIC_ROSTER_VIEW", "STUDENT_DIRECTORY_VIEW"}) {
            jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,?,?,'ALLOW','ASSIGNED_CLASSES',true,'Roster policy boundary regression') ON CONFLICT DO NOTHING",
                    schoolId, "form_teacher", action);
        }
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,'{\"fr\":\"Français\",\"en\":\"French\"}'::jsonb,1,'FR')",
                subjectId, schoolId, "RD-FR");
        jdbc.update("INSERT INTO academic_class_subject_teacher(school_id,academic_session_id,class_id,subject_id,employee_id,role,effective_from,source,active) VALUES (?,?,?,?,?,'RESPONSIBLE','2026-09-01','ACADEMIC_SETUP',true)",
                schoolId, academicId, classId, subjectId, employeeId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?, 'RD-1','Active','Roster',?,'4E Roster','FR','secondary')",
                studentId, schoolId, classId);
        jdbc.update("INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) VALUES (?,?,?,?,'4E Roster','secondary','FR','ACTIVE','2026-09-01','TEST')",
                schoolId, studentId, academicId, classId);

        AppUserPrincipal teacher = new AppUserPrincipal(teacherUserId, schoolId,
                "roster-teacher", "form_teacher", "Roster Teacher", "RT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null, teacher.getAuthorities()));

        assertThat(students.roster(academicId, classId)).extracting(v -> v.id()).containsExactly(studentId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_role_action WHERE school_id=? AND role_code='form_teacher' AND action_code='ENROLLMENT_VIEW'",
                Integer.class, schoolId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM permission_role_template_rule
                 WHERE template_code='form_teacher'
                   AND action_code='STUDENT_DIRECTORY_VIEW'
                   AND effect='ALLOW' AND scope_mode='ASSIGNED_CLASSES'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM permission_role_action
                 WHERE school_id=? AND role_code='form_teacher'
                   AND action_code='STUDENT_DIRECTORY_VIEW'
                   AND effect='ALLOW' AND scope_mode='ASSIGNED_CLASSES'
                """, Integer.class, schoolId)).isEqualTo(1);
    }

    @Test
    void teacherStudentDirectoryOrdersDistinctEnrollmentRowsWithPostgresCompatibleSql() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID teacherUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        String sectionId = "sd" + schoolId.toString().substring(0, 8);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')",
                sectionId, schoolId, "Directory");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'6eme Directory','FR','secondary')",
                classId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,'Directory Teacher','Permanent',true,'secondary')",
                employeeId, schoolId, "SD-" + schoolId.toString().substring(0, 8));
        jdbc.update("INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,employee_id,active,parcours_scope_mode) VALUES (?,?,?,'test','Directory Teacher','DT','teacher',?,true,'ASSIGNMENT_DERIVED')",
                teacherUserId, schoolId, "directory-teacher-" + schoolId.toString().substring(0, 8), employeeId);
        jdbc.update("INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason) VALUES (?,?, 'teacher',true,'Student directory regression') ON CONFLICT DO NOTHING",
                schoolId, teacherUserId);
        jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,?,?,'ALLOW','ASSIGNED_CLASSES',true,'Student directory regression') ON CONFLICT DO NOTHING",
                schoolId, "teacher", "ACADEMIC_ROSTER_VIEW");
        jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,?,?,'ALLOW','ASSIGNED_CLASSES',true,'Student directory regression') ON CONFLICT DO NOTHING",
                schoolId, "teacher", "STUDENT_DIRECTORY_VIEW");
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,'{\"fr\":\"Math\",\"en\":\"Math\"}'::jsonb,1,'FR')",
                subjectId, schoolId, "SDM");
        jdbc.update("INSERT INTO academic_class_subject_teacher(school_id,academic_session_id,class_id,subject_id,employee_id,role,effective_from,source,active) VALUES (?,?,?,?,?,'RESPONSIBLE','2026-09-01','MANUAL',true)",
                schoolId, academicId, classId, subjectId, employeeId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?, 'SD-1','Ada','Directory',?,'6eme Directory','FR','secondary')",
                studentId, schoolId, classId);
        jdbc.update("INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) VALUES (?,?,?,?,'6eme Directory','secondary','FR','ACTIVE','2026-09-01','TEST')",
                schoolId, studentId, academicId, classId);

        AppUserPrincipal teacher = new AppUserPrincipal(teacherUserId, schoolId, "directory-teacher", "teacher",
                "Directory Teacher", "DT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacher, null, teacher.getAuthorities()));

        var rows = students.list(null);

        assertThat(rows).extracting(v -> v.id()).containsExactly(studentId);
        assertThat(rows.getFirst().className()).isEqualTo("6eme Directory");
        assertThat(students.classOptions()).extracting(v -> v.id()).containsExactly(classId);
    }

    @Test
    void primaryAndKindergartenHomeroomsGrantStudentAccessWithoutTimetableOrCourseDistribution() {
        UUID academicId = UUID.randomUUID();
        String sectionPrefix = "hr" + schoolId.toString().substring(0, 7);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        for (String action : new String[]{"ACADEMIC_ROSTER_VIEW", "STUDENT_DIRECTORY_VIEW", "STUDENT_PROFILE_VIEW"}) {
            jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,?,?,'ALLOW','ASSIGNED_CLASSES',true,'Homeroom roster regression') ON CONFLICT DO NOTHING",
                    schoolId, "teacher", action);
        }

        UUID primaryStudent = insertHomeroomOnlyRosterFixture(academicId, sectionPrefix + "p",
                "primary", "CE1 Homeroom", "Primary Homeroom Teacher", "PH", "HR-P");
        UUID kindergartenStudent = insertHomeroomOnlyRosterFixture(academicId, sectionPrefix + "m",
                "maternelle", "Nursery Homeroom", "Kindergarten Homeroom Teacher", "KH", "HR-M");

        authenticateTeacherForStudent(primaryStudent);
        assertThat(students.list(null)).extracting(v -> v.id()).containsExactly(primaryStudent);
        assertThat(students.get(primaryStudent)).isNotNull();

        authenticateTeacherForStudent(kindergartenStudent);
        assertThat(students.list(null)).extracting(v -> v.id()).containsExactly(kindergartenStudent);
        assertThat(students.get(kindergartenStudent)).isNotNull();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_class_config WHERE school_id=? AND academic_session_id=?",
                Integer.class, schoolId, academicId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot WHERE school_id=? AND academic_session_id=?",
                Integer.class, schoolId, academicId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teacher_class tc JOIN employee e ON e.id=tc.employee_id WHERE e.school_id=? AND e.code IN ('HR-P','HR-M')",
                Integer.class, schoolId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_class_subject_teacher WHERE school_id=? AND academic_session_id=?",
                Integer.class, schoolId, academicId)).isZero();
    }

    @Test
    void secondaryHomeroomGrantsClassRosterAccessWithoutResponsibleSubjectAssignment() {
        UUID academicId = UUID.randomUUID();
        String sectionId = "hs" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        for (String action : new String[]{"ACADEMIC_ROSTER_VIEW", "STUDENT_DIRECTORY_VIEW", "STUDENT_PROFILE_VIEW"}) {
            jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,?,?,'ALLOW','ASSIGNED_CLASSES',true,'Secondary roster regression') ON CONFLICT DO NOTHING",
                    schoolId, "teacher", action);
        }
        UUID studentId = insertHomeroomOnlyRosterFixture(academicId, sectionId,
                "secondary", "Form 1 Homeroom", "Secondary Form Teacher", "SF", "HR-S");

        authenticateTeacherForStudent(studentId);

        assertThat(students.list(null)).extracting(v -> v.id()).containsExactly(studentId);
        assertThat(students.get(studentId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_class_subject_teacher WHERE school_id=? AND academic_session_id=?",
                Integer.class, schoolId, academicId)).isZero();
    }

    private UUID insertHomeroomOnlyRosterFixture(UUID academicId, String sectionId, String level,
                                                  String className, String teacherName,
                                                  String initials, String employeeCode) {
        UUID classId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID teacherUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR',?)",
                sectionId, schoolId, className, level);
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,?,'FR',?)",
                classId, schoolId, sectionId, className, level);
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,?, 'Permanent',true,?)",
                employeeId, schoolId, employeeCode, teacherName, level);
        jdbc.update("INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,employee_id,active,parcours_scope_mode) VALUES (?,?,?,'test',?,?,'teacher',?,true,'ASSIGNMENT_DERIVED')",
                teacherUserId, schoolId, "homeroom-" + employeeCode.toLowerCase(), teacherName, initials, employeeId);
        jdbc.update("INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason) VALUES (?,?, 'teacher',true,'Homeroom roster regression') ON CONFLICT DO NOTHING",
                schoolId, teacherUserId);
        jdbc.update("INSERT INTO class_teacher_assignment(school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source) VALUES (?,?,?,?,'HOMEROOM','2026-09-01','ACTIVE','ACADEMIC_SETUP')",
                schoolId, academicId, classId, employeeId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?,?,'Test',?, ?,?,'FR',?)",
                studentId, schoolId, "ST-" + employeeCode, className, classId, className, level);
        jdbc.update("INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) VALUES (?,?,?,?,?,?,'FR','ACTIVE','2026-09-01','TEST')",
                schoolId, studentId, academicId, classId, className, level);
        return studentId;
    }

    private void authenticateTeacherForStudent(UUID studentId) {
        Map<String, Object> teacher = jdbc.queryForMap("""
                SELECT u.id,u.username,u.display_name,u.initials
                  FROM app_user u
                  JOIN employee e ON e.id=u.employee_id
                  JOIN class_teacher_assignment a ON a.employee_id=e.id AND a.school_id=e.school_id
                  JOIN student_enrollment se ON se.school_class_id=a.class_id
                 WHERE u.school_id=? AND se.student_id=?
                """, schoolId, studentId);
        AppUserPrincipal principal = new AppUserPrincipal((UUID) teacher.get("id"), schoolId,
                (String) teacher.get("username"), "teacher", (String) teacher.get("display_name"),
                (String) teacher.get("initials"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void timetablePublicationPersistsCanonicalPrimaryHomeroomAndPublishedSnapshot() {
        UUID academicId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID unrelatedClassId = UUID.randomUUID();
        String sectionId = "t" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','primary')",
                sectionId, schoolId, "Timetable");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'CE1 Timetable','FR','primary')",
                classId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,'Primary Homeroom','Permanent',true,'primary')",
                teacherId, schoolId, "TH-" + schoolId.toString().substring(0, 8));
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,'{\"fr\":\"Anglais\",\"en\":\"English\"}'::jsonb,1,'FR')",
                subjectId, schoolId, "EN");
        jdbc.update("INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), schoolId, academicId, classId, subjectId);
        jdbc.update("INSERT INTO class_teacher_assignment(id,school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source) VALUES (?,?,?,?,?,'HOMEROOM','2026-09-01','ACTIVE','ACADEMIC_SETUP')",
                assignmentId, schoolId, academicId, classId, teacherId);
        jdbc.update("INSERT INTO timetable_class_config(id,school_id,academic_session_id,class_id,model,status) VALUES (?,?,?,?,'HOMEROOM','DRAFT')",
                UUID.randomUUID(), schoolId, academicId, classId);
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'CP Unrelated','FR','primary')",
                unrelatedClassId, schoolId, sectionId);
        jdbc.update("INSERT INTO timetable_class_config(id,school_id,academic_session_id,class_id,model,status) VALUES (?,?,?,?,'HOMEROOM','DRAFT')",
                UUID.randomUUID(), schoolId, academicId, unrelatedClassId);
        jdbc.update("INSERT INTO timetable_version(id,school_id,academic_session_id,version_no,status,effective_from,effective_to) VALUES (?,?,?,1,'DRAFT','2026-09-01','2027-07-31')",
                versionId, schoolId, academicId);
        jdbc.update("INSERT INTO timetable_slot(id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,timetable_version_id) VALUES (?,?,?,?,0,0,'EN',?)",
                slotId, schoolId, classId, academicId, versionId);

        var published = timetables.publish(versionId, new TimetableVersionActionRequest("Canonical publication", 0L));

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT homeroom_teacher_id FROM timetable_class_config WHERE id IS NOT NULL AND school_id=? AND academic_session_id=? AND class_id=?", UUID.class,
                schoolId, academicId, classId)).isEqualTo(teacherId);
        assertThat(jdbc.queryForObject("SELECT published_teacher_id FROM timetable_slot WHERE id=?", UUID.class, slotId)).isEqualTo(teacherId);
        assertThat(jdbc.queryForObject("SELECT published_assignment_id FROM timetable_slot WHERE id=?", UUID.class, slotId)).isEqualTo(assignmentId);
        assertThat(jdbc.queryForObject("SELECT status FROM timetable_class_config WHERE school_id=? AND academic_session_id=? AND class_id=?", String.class,
                schoolId, academicId, unrelatedClassId)).isEqualTo("DRAFT");
    }

    @Test
    void classTimetablePublicationIgnoresIncompleteClassesAndPreservesTheirDraft() {
        UUID academicId = UUID.randomUUID();
        UUID ce1Id = UUID.randomUUID();
        UUID incompleteClassId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String sectionId = "p" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','primary')",
                sectionId, schoolId, "Primary timetable");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'CE1 Publish','FR','primary')",
                ce1Id, schoolId, sectionId);
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'Class Without Teacher','FR','primary')",
                incompleteClassId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','OPEN',true)",
                academicId, schoolId);
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,'CE1 Homeroom','Permanent',true,'primary')",
                teacherId, schoolId, "CE1-" + schoolId.toString().substring(0, 8));
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,'{\"fr\":\"Français\",\"en\":\"French\"}'::jsonb,1,'FR')",
                subjectId, schoolId, "FR-" + schoolId.toString().substring(0, 6));
        jdbc.update("INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), schoolId, academicId, ce1Id, subjectId);
        jdbc.update("INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), schoolId, academicId, incompleteClassId, subjectId);
        jdbc.update("INSERT INTO class_teacher_assignment(id,school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source) VALUES (?,?,?,?,?,'HOMEROOM','2026-09-01','ACTIVE','ACADEMIC_SETUP')",
                assignmentId, schoolId, academicId, ce1Id, teacherId);
        jdbc.update("INSERT INTO timetable_class_config(id,school_id,academic_session_id,class_id,model,status) VALUES (?,?,?,?,'HOMEROOM','DRAFT')",
                UUID.randomUUID(), schoolId, academicId, ce1Id);
        jdbc.update("INSERT INTO timetable_class_config(id,school_id,academic_session_id,class_id,model,status) VALUES (?,?,?,?,'HOMEROOM','DRAFT')",
                UUID.randomUUID(), schoolId, academicId, incompleteClassId);
        jdbc.update("INSERT INTO timetable_version(id,school_id,academic_session_id,version_no,status,effective_from,effective_to) VALUES (?,?,?,1,'DRAFT','2026-09-01','2027-07-31')",
                versionId, schoolId, academicId);
        String subjectCode = jdbc.queryForObject("SELECT code FROM subject WHERE id=?", String.class, subjectId);
        jdbc.update("INSERT INTO timetable_slot(id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,timetable_version_id) VALUES (?,?,?,?,0,0,?,?)",
                UUID.randomUUID(), schoolId, ce1Id, academicId, subjectCode, versionId);
        jdbc.update("INSERT INTO timetable_slot(id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,timetable_version_id) VALUES (?,?,?,?,0,1,?,?)",
                UUID.randomUUID(), schoolId, incompleteClassId, academicId, subjectCode, versionId);

        var published = timetables.publishClass(academicId, ce1Id, 0L, "Publish CE1 only");

        assertThat(published.id()).isEqualTo(versionId);
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.classCount()).isEqualTo(1);
        assertThat(published.slotCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM timetable_class_config WHERE class_id=?", String.class, ce1Id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM timetable_class_config WHERE class_id=?", String.class, incompleteClassId)).isEqualTo("DRAFT");
        UUID successorDraftId = timetables.currentDraftVersion(academicId);
        assertThat(successorDraftId).isNotNull().isNotEqualTo(versionId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot WHERE timetable_version_id=?", Integer.class, successorDraftId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot WHERE timetable_version_id=? AND class_id=?", Integer.class, versionId, incompleteClassId)).isZero();
        assertThat(timetables.versionForClass(academicId, ce1Id)).isEqualTo(versionId);
        assertThat(timetables.versionForClass(academicId, incompleteClassId)).isEqualTo(successorDraftId);
        assertThat(jdbc.queryForObject("SELECT published_teacher_id FROM timetable_slot WHERE timetable_version_id=? AND class_id=?", UUID.class, versionId, ce1Id)).isEqualTo(teacherId);

        timetables.reopenClass(academicId, ce1Id, 1L, "Correct CE1 timetable");

        assertThat(jdbc.queryForObject("SELECT status FROM timetable_class_config WHERE class_id=?", String.class, ce1Id)).isEqualTo("DRAFT");
        assertThat(timetables.versionForClass(academicId, ce1Id)).isEqualTo(successorDraftId);

        var republished = timetables.publishClass(academicId, ce1Id, 2L,
                "Republish CE1 while the other class is still incomplete");

        assertThat(republished.id()).isEqualTo(successorDraftId);
        assertThat(republished.status()).isEqualTo("PUBLISHED");
        assertThat(republished.classCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM timetable_version WHERE id=?", String.class, versionId))
                .isEqualTo("ARCHIVED");
        UUID nextDraftId = timetables.currentDraftVersion(academicId);
        assertThat(nextDraftId).isNotNull().isNotEqualTo(successorDraftId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot WHERE timetable_version_id=? AND class_id=?",
                Integer.class, nextDraftId, incompleteClassId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot WHERE timetable_version_id=? AND class_id=?",
                Integer.class, successorDraftId, incompleteClassId)).isZero();
        assertThat(timetables.versionForClass(academicId, ce1Id)).isEqualTo(successorDraftId);
        assertThat(timetables.versionForClass(academicId, incompleteClassId)).isEqualTo(nextDraftId);
    }

    @Test
    void idempotencyReturnsStoredResponseAndRejectsChangedPayload() {
        AtomicInteger calls = new AtomicInteger();
        String first = idempotency.execute("test", "same-key", Map.of("amount", 1), String.class,
                () -> "result-" + calls.incrementAndGet());
        String replay = idempotency.execute("test", "same-key", Map.of("amount", 1), String.class,
                () -> "result-" + calls.incrementAndGet());
        assertThat(first).isEqualTo("result-1");
        assertThat(replay).isEqualTo(first);
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> idempotency.execute("test", "same-key", Map.of("amount", 2), String.class, () -> "bad"))
                .isInstanceOf(ApiException.class).hasMessageContaining("autre requête");
    }

    @Test
    void officialDocumentIsDeterministicOnRetryAndProducesVerifiedPdf() throws Exception {
        UUID templateId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO document_template(id,school_id,type,locale,name,body_template)
            VALUES (?,?, 'GENERIC','fr','Test','Bonjour {{studentName}}')
            """, templateId, schoolId);
        GenerateRequest request = new GenerateRequest("GENERIC", templateId, "Student", UUID.randomUUID().toString(),
                "1", "fr", "Document test", "STAFF", Map.of("studentName", "Ada"));
        var first = documents.generate(request, "doc-key");
        var replay = documents.generate(request, "doc-key");
        assertThat(replay.id()).isEqualTo(first.id());
        byte[] content = documents.content(first.id());
        assertThat(content).startsWith("%PDF".getBytes());
        assertThat(content).hasSizeGreaterThan(500);
        assertThat(Files.exists(DOCUMENTS.resolve(schoolId.toString()).resolve(first.id() + ".pdf"))).isTrue();
    }

    @Test
    void auditTableIsAppendOnlyAtDatabaseLevel() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_event(id,school_id,action,aggregate_type) VALUES (?,?,?,?)", id, schoolId, "TEST", "Test");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id=?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void promotionPreviewExplainsRecommendationAndManualOverrideCommitsNextEnrollment() {
        UUID sourceSession = UUID.randomUUID(), targetSession = UUID.randomUUID();
        UUID sourceClass = UUID.randomUUID(), targetClass = UUID.randomUUID(), student = UUID.randomUUID();
        String section = "j" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')", section, schoolId, "Secondary");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'6e Test','FR','secondary')", sourceClass, schoolId, section);
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'5e Test','FR','secondary')", targetClass, schoolId, section);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2025-2026','2025-2026','2025-09-01','2026-07-31','OPEN',false)", sourceSession, schoolId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','2026-2027','2026-09-01','2027-07-31','DRAFT',false)", targetSession, schoolId);
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?, 'PROMO-1','Awa','Test',?,'6e Test','FR','secondary')", student, schoolId, sourceClass);
        jdbc.update("INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) VALUES (?,?,?,?,'6e Test','secondary','FR','ACTIVE','2025-09-01','TEST')", schoolId, student, sourceSession, sourceClass);
        UUID annualPeriod = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO academic_reporting_period
                (id,school_id,academic_session_id,code,label,period_type,display_order,start_date,end_date,status)
            VALUES (?,?,?,'ANNUAL','Annual 2025-2026','ANNUAL_RESULT',10,'2025-09-01','2026-07-31','PUBLISHED')
            """, annualPeriod, schoolId, sourceSession);
        jdbc.update("""
            INSERT INTO bulletin_version
                (id,school_id,academic_session_id,reporting_period_id,student_id,state,snapshot_json,
                 snapshot_hash,average,class_size,published_at)
            VALUES (?,?,?,?,?,'PUBLISHED','{\"conduct\":{\"status\":\"APPROVED\",\"decisionCode\":\"PROMOTE\"}}'::jsonb,'test-annual',12.50,1,now())
            """, UUID.randomUUID(), schoolId, sourceSession, annualPeriod, student);

        promotions.savePath(new ProgressionPathUpsert(sourceSession, sourceClass, targetSession, targetClass, false, null));
        promotions.savePath(new ProgressionPathUpsert(sourceSession, targetClass, targetSession, null, true, null));
        promotions.saveRule(new PromotionRuleUpsert(sourceSession, null, null,
                new java.math.BigDecimal("10"), new java.math.BigDecimal("8"), true, null));
        var graphDraft = promotions.graphVersions(sourceSession, targetSession).stream()
                .filter(g -> "DRAFT".equals(g.status())).findFirst().orElseThrow();
        promotions.publishGraph(graphDraft.id(), graphDraft.version());
        var ruleDraft = promotions.ruleSets(sourceSession).stream()
                .filter(r -> "DRAFT".equals(r.status())).findFirst().orElseThrow();
        promotions.publishRuleSet(ruleDraft.id(), ruleDraft.version());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion_batch WHERE school_id=?", Integer.class, schoolId)).isZero();
        var readOnlyPreview = promotions.previewReadOnly(new PromotionPreviewRequest(sourceSession, targetSession,
                "Read-only preview", java.util.List.of(sourceClass), null));
        assertThat(readOnlyPreview.candidates()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion_batch WHERE school_id=?", Integer.class, schoolId)).isZero();
        var preview = promotions.preview(new PromotionPreviewRequest(sourceSession, targetSession, "Promotion test", java.util.List.of(sourceClass), "promo-key"));
        assertThat(preview.candidates()).singleElement().satisfies(c -> {
            assertThat(c.recommendation()).isEqualTo("PROMOTE");
            assertThat(c.targetClassId()).isEqualTo(targetClass);
            assertThat(c.explanation()).contains("annuelle").contains("PROMOTE");
        });

        var candidate = preview.candidates().getFirst();
        promotions.override(candidate.id(), new PromotionOverrideRequest("HOLD", sourceClass, "Décision du conseil", candidate.version()));
        var refreshed = promotions.batch(preview.id());
        var committed = promotions.commit(preview.id(), new PromotionCommitRequest("Conseil validé", refreshed.version()));
        assertThat(committed.status()).isEqualTo("COMMITTED");
        assertThat(committed.repeatCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND school_class_id=? AND status='PLANNED'", Integer.class, schoolId, student, targetSession, sourceClass)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND school_class_id=? AND status='ACTIVE'", Integer.class, schoolId, student, targetSession, sourceClass)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'", Integer.class, schoolId, student, sourceSession)).isEqualTo(1);
        UUID planned = jdbc.queryForObject("SELECT id FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='PLANNED'", UUID.class, schoolId, student, targetSession);
        var activated = promotions.activatePlanned(planned, new PromotionActivationRequest("Rentrée confirmée"));
        assertThat(activated.status()).isEqualTo("ACTIVE");
        long transitionCount = jdbc.queryForObject("SELECT count(*) FROM promotion_transition_event WHERE target_enrollment_id=?", Long.class, planned);
        var activationReplay = promotions.activatePlanned(planned, new PromotionActivationRequest("Retry sans doublon"));
        assertThat(activationReplay.status()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion_transition_event WHERE target_enrollment_id=?", Long.class, planned))
                .isEqualTo(transitionCount);
        assertThat(jdbc.queryForObject("SELECT status FROM student_enrollment WHERE id=?", String.class, planned)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='COMPLETED'", String.class, schoolId, student, sourceSession)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT final_decision FROM journey_entry WHERE school_id=? AND student_id=? AND academic_year='2025-2026'", String.class, schoolId, student)).isEqualTo("HOLD");
    }

    @Test
    void trimesterRolloverUsesThreeSequenceRowsAndPreservesLocalWindowTime() {
        UUID sourceSession = UUID.randomUUID(), targetSession = UUID.randomUUID();
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?,?,'Source','2025-09-01','2026-07-31','OPEN',false)",
                sourceSession, schoolId, "SRC-" + sourceSession.toString().substring(0, 6));
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?,?,'Target','2026-09-01','2027-07-31','DRAFT',false)",
                targetSession, schoolId, "TGT-" + targetSession.toString().substring(0, 6));
        insertRolloverTerm(sourceSession, 1, "T1", "2025-09-01", "2025-11-30", true,
                java.time.Instant.parse("2025-09-05T08:15:00Z"), null);
        insertRolloverTerm(sourceSession, 2, "T2", "2025-12-01", "2026-02-28", false, null, null);
        insertRolloverTerm(sourceSession, 3, "T3", "2026-03-01", "2026-07-31", true,
                java.time.Instant.parse("2026-03-05T17:45:00Z"), java.time.Instant.parse("2026-07-31T18:00:00Z"));

        var scope = new CopyScopeSelection(true, false, false, true);
        var request = new ConfigurationCopyPreviewRequest(sourceSession, "SHIFT_FROM_SESSION_START", "UPDATE_ALL", scope, java.util.List.of(), java.util.List.of());
        var preview = configurationCopy.preview(targetSession, request);

        assertThat(preview.termManagementWindows()).hasSize(3);
        assertThat(preview.termManagementWindows()).extracting(r -> r.key())
                .containsExactly("TERM_WINDOW:1:T1", "TERM_WINDOW:2:T2", "TERM_WINDOW:3:T3");
        assertThat(preview.termManagementWindows()).allMatch(r -> "CREATE".equals(r.status()));
        assertThat(preview.termManagementWindows().get(0).proposed().get("opensAt"))
                .isEqualTo(java.time.Instant.parse("2026-09-05T08:15:00Z"));
        assertThat(preview.termManagementWindows().get(1).proposed().get("limited")).isEqualTo(false);

        var applied = configurationCopy.apply(targetSession, new com.bbc.sms.foundation.session.SessionDtos.ConfigurationCopyApplyRequest(
                sourceSession, "SHIFT_FROM_SESSION_START", "UPDATE_ALL", scope, java.util.List.of(), java.util.List.of(),
                "Rollover trimester access", preview.fingerprint()), "rollover-key");
        assertThat(applied.termManagementWindows()).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT management_window_limited FROM academic_term WHERE school_id=? AND academic_session_id=? AND code='T1'", Boolean.class,
                schoolId, targetSession)).isTrue();
        assertThat(jdbc.queryForObject("SELECT management_opens_at FROM academic_term WHERE school_id=? AND academic_session_id=? AND code='T1'", java.time.Instant.class,
                schoolId, targetSession)).isEqualTo(java.time.Instant.parse("2026-09-05T08:15:00Z"));
        assertThat(jdbc.queryForObject("SELECT management_window_limited FROM academic_term WHERE school_id=? AND academic_session_id=? AND code='T2'", Boolean.class,
                schoolId, targetSession)).isFalse();
    }

    private void insertRolloverTerm(UUID sessionId, int sequenceNo, String code, String start, String end,
                                    boolean limited, java.time.Instant opensAt, java.time.Instant closesAt) {
        jdbc.update("""
                INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone,
                    management_window_limited,management_opens_at,management_closes_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), schoolId, sessionId, code, "Trimester " + sequenceNo, sequenceNo,
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), "Africa/Douala", limited,
                opensAt == null ? null : java.sql.Timestamp.from(opensAt),
                closesAt == null ? null : java.sql.Timestamp.from(closesAt));
    }

    @Test
    void committedPromotionRetryRepairsMissingRegisterAndSerializesConcurrentRetries() throws Exception {
        UUID sourceSession = UUID.randomUUID(), targetSession = UUID.randomUUID(), batchId = UUID.randomUUID();
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2025-2026','Source','2025-09-01','2026-07-31','OPEN',false)",
                sourceSession, schoolId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?, '2026-2027','Target','2026-09-01','2027-07-31','DRAFT',false)",
                targetSession, schoolId);
        jdbc.update("INSERT INTO promotion_batch(id,school_id,source_session_id,target_session_id,name,status,committed_at) VALUES (?,?,?,?,?,'COMMITTED',now())",
                batchId, schoolId, sourceSession, targetSession, "Recovery batch");

        var repaired = promotions.commit(batchId, new PromotionCommitRequest("Recover register", 0L));
        assertThat(repaired.status()).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion_register WHERE school_id=? AND batch_id=?", Integer.class, schoolId, batchId)).isEqualTo(1);

        jdbc.update("DELETE FROM promotion_register WHERE school_id=? AND batch_id=?", schoolId, batchId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                TenantContext.set(schoolId);
                try { promotions.commit(batchId, new PromotionCommitRequest("Concurrent retry A", 0L)); }
                finally { TenantContext.clear(); }
            });
            Future<?> second = pool.submit(() -> {
                TenantContext.set(schoolId);
                try { promotions.commit(batchId, new PromotionCommitRequest("Concurrent retry B", 0L)); }
                finally { TenantContext.clear(); }
            });
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotion_register WHERE school_id=? AND batch_id=?", Integer.class, schoolId, batchId)).isEqualTo(1);
    }
}
