package com.bbc.sms.foundation.session;

import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.ReadinessSectionView;
import static com.bbc.sms.foundation.session.SessionDtos.ConfigurationCopyPreview;
import static com.bbc.sms.foundation.session.SessionDtos.ConfigurationCopyPreviewRequest;
import static com.bbc.sms.foundation.session.SessionDtos.CopyScopeSelection;
import static com.bbc.sms.foundation.session.SessionDtos.SessionReadinessView;
import static com.bbc.sms.foundation.session.SessionDtos.TermManagementWindowUpsert;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class AcademicSessionReadinessIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bbc_readiness_test").withUsername("bbc").withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicSessionService sessions;
    @Autowired AcademicConfigurationCopyService configurationCopy;
    @Autowired TermManagementWindowService termWindows;

    private UUID schoolId;

    @BeforeEach
    void setTenant() {
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId,
                "R" + schoolId.toString().substring(0, 8), "Readiness test school");
        TenantContext.set(schoolId);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void optionalDatesDoNotBlockAndMissingAssignmentsAreScopedToActionableClassSubjects() {
        Fixture fixture = fixture();

        SessionReadinessView unrestricted = sessions.readiness(fixture.sessionId());
        assertThat(unrestricted.ready()).isTrue();
        assertThat(unrestricted.phase()).isEqualTo("READY");
        assertThat(unrestricted.blockers()).doesNotContain("CURRICULUM_ASSIGNMENT_MISSING");
        assertThat(unrestricted.warnings()).containsExactly("CURRICULUM_ASSIGNMENT_MISSING");
        assertThat(section(unrestricted, "TERM_ACCESS").status()).isEqualTo("READY");

        ReadinessSectionView curriculum = section(unrestricted, "CURRICULUM");
        assertThat(curriculum.status()).isEqualTo("WARNING");
        assertThat(curriculum.ready()).isTrue();
        assertThat(curriculum.issues()).hasSize(3).allSatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("CURRICULUM_ASSIGNMENT_MISSING");
            assertThat(issue.severity()).isEqualTo("WARNING");
            assertThat(issue.scope()).startsWith("Unused A · ");
            assertThat(issue.classId()).isEqualTo(fixture.unusedClassId());
            assertThat(issue.subjectId()).isNotNull();
            assertThat(issue.repairTarget()).isEqualTo("class-subjects");
            assertThat(issue.messageFr()).contains("aucun élève actif");
            assertThat(issue.messageEn()).contains("does not block session readiness");
        });

        var t1 = termWindows.list(fixture.sessionId()).getFirst();
        var openingOnly = termWindows.update(fixture.sessionId(), t1.termId(),
                new TermManagementWindowUpsert(true, Instant.parse("2030-09-01T08:00:00Z"), null, t1.version()));
        SessionReadinessView openingOnlyReadiness = sessions.readiness(fixture.sessionId());
        assertThat(openingOnly.limited()).isTrue();
        assertThat(openingOnlyReadiness.ready()).isTrue();
        assertThat(openingOnlyReadiness.blockers()).isEmpty();
        assertThat(section(openingOnlyReadiness, "TERM_ACCESS").status()).isEqualTo("SCHEDULED");

        var closingOnly = termWindows.update(fixture.sessionId(), t1.termId(),
                new TermManagementWindowUpsert(true, null, Instant.parse("2030-12-01T17:00:00Z"), openingOnly.version()));
        SessionReadinessView closingOnlyReadiness = sessions.readiness(fixture.sessionId());
        assertThat(closingOnlyReadiness.ready()).isTrue();
        assertThat(closingOnlyReadiness.blockers()).isEmpty();

        var complete = termWindows.update(fixture.sessionId(), t1.termId(),
                new TermManagementWindowUpsert(true, Instant.parse("2030-09-01T08:00:00Z"),
                        Instant.parse("2030-12-01T17:00:00Z"), closingOnly.version()));
        SessionReadinessView completeReadiness = sessions.readiness(fixture.sessionId());
        assertThat(completeReadiness.ready()).isTrue();
        assertThat(completeReadiness.blockers()).isEmpty();

        termWindows.update(fixture.sessionId(), t1.termId(),
                new TermManagementWindowUpsert(false, null, null, complete.version()));
        assertThat(section(sessions.readiness(fixture.sessionId()), "TERM_ACCESS").status()).isEqualTo("READY");

        ConfigurationCopyPreview reuse = configurationCopy.preview(fixture.sessionId(),
                new ConfigurationCopyPreviewRequest(fixture.sourceSessionId(), "SHIFT_FROM_SESSION_START", "FILL_MISSING",
                        CopyScopeSelection.all(), List.of()));
        assertThat(reuse.blockers()).isEmpty();
        assertThat(reuse.termManagementWindows()).hasSize(3).allSatisfy(row -> {
            assertThat(row.blockers()).isEmpty();
            assertThat(row.proposed()).containsEntry("limited", false);
            assertThat(row.proposed().get("opensAt")).isNull();
            assertThat(row.proposed().get("closesAt")).isNull();
        });
        assertThat(sessions.readiness(fixture.sessionId()).ready()).isTrue();

        UUID blockerClassId = UUID.randomUUID();
        String blockerSectionId = "b-" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')",
                blockerSectionId, schoolId, "Blocker section");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'Active Empty','FR','secondary')",
                blockerClassId, schoolId, blockerSectionId);
        UUID blockerStudentId = UUID.randomUUID();
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?,?,'Empty','Student',?,'Active Empty','FR','secondary')",
                blockerStudentId, schoolId, "BE" + blockerStudentId.toString().substring(0, 8), blockerClassId);
        jdbc.update("""
                INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source)
                VALUES (?,?,?,?,'Active Empty','secondary','FR','ACTIVE','2026-08-01','TEST')
                """, schoolId, blockerStudentId, fixture.sessionId(), blockerClassId);
        SessionReadinessView missingCurriculum = sessions.readiness(fixture.sessionId());
        assertThat(missingCurriculum.ready()).isFalse();
        assertThat(missingCurriculum.blockers()).containsExactly("CURRICULUM_MISSING");
        assertThat(section(missingCurriculum, "CURRICULUM").status()).isEqualTo("BLOCKED");
        assertThat(section(missingCurriculum, "CURRICULUM").issues())
                .filteredOn(issue -> "CURRICULUM_MISSING".equals(issue.code()))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.severity()).isEqualTo("BLOCKER");
                    assertThat(issue.scope()).isEqualTo("Active Empty");
                    assertThat(issue.classId()).isEqualTo(blockerClassId);
                    assertThat(issue.messageFr()).contains("Aucune matière");
                    assertThat(issue.messageEn()).contains("No class-subject curriculum");
                });
    }

    private ReadinessSectionView section(SessionReadinessView readiness, String key) {
        return readiness.sections().stream().filter(value -> key.equals(value.key())).findFirst().orElseThrow();
    }

    private Fixture fixture() {
        UUID sessionId = UUID.randomUUID();
        UUID activeClassId = UUID.randomUUID();
        UUID unusedClassId = UUID.randomUUID();
        UUID activeSubjectId = UUID.randomUUID();
        UUID[] unusedSubjectIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        UUID employeeId = UUID.randomUUID();
        UUID sourceSessionId = UUID.randomUUID();
        String sectionId = "r-" + schoolId.toString().substring(0, 8);

        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')",
                sectionId, schoolId, "Secondary");
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'Active A','FR','secondary')",
                activeClassId, schoolId, sectionId);
        jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,'Unused A','FR','secondary')",
                unusedClassId, schoolId, sectionId);
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?,?,'Readiness session','2026-08-01','2027-07-31','OPEN',false)",
                sessionId, schoolId, "READINESS-" + sessionId.toString().substring(0, 8));
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current) VALUES (?,?,?,'Source session','2025-08-01','2026-07-31','OPEN',false)",
                sourceSessionId, schoolId, "SOURCE-" + sourceSessionId.toString().substring(0, 8));

        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                    INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone)
                    VALUES (?,?,?,?,?,?,?::date,?::date,'Africa/Douala')
                    """, UUID.randomUUID(), schoolId, sessionId, "T" + i, "Trimester " + i, i,
                    i == 1 ? "2026-08-01" : i == 2 ? "2027-01-05" : "2027-04-06",
                     i == 1 ? "2026-12-20" : i == 2 ? "2027-03-27" : "2027-07-31");
        }
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                    INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone)
                    VALUES (?,?,?,?,?,?,?::date,?::date,'Africa/Douala')
                    """, UUID.randomUUID(), schoolId, sourceSessionId, "T" + i, "Source trimester " + i, i,
                    i == 1 ? "2025-08-01" : i == 2 ? "2026-01-05" : "2026-04-06",
                    i == 1 ? "2025-12-20" : i == 2 ? "2026-03-27" : "2026-07-31");
        }
        List<UUID> termIds = jdbc.query("SELECT id FROM academic_term WHERE school_id=? AND academic_session_id=? ORDER BY sequence_no",
                (rs, rowNum) -> rs.getObject(1, UUID.class), schoolId, sessionId);
        String[] sequenceCodes = {"S1", "S2", "S3", "S4", "S5", "S6"};
        String[] sequenceStarts = {"2026-08-01", "2026-10-16", "2027-01-05", "2027-02-15", "2027-04-06", "2027-05-20"};
        String[] sequenceEnds = {"2026-10-15", "2026-12-20", "2027-02-14", "2027-03-27", "2027-05-19", "2027-07-31"};
        for (int i = 0; i < sequenceCodes.length; i++) {
            UUID termId = termIds.get(i / 2);
            jdbc.update("""
                    INSERT INTO academic_reporting_period(id,school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,status)
                    VALUES (?,?,?,?,?,?, 'SEQUENCE',?,?::date,?::date,'DRAFT')
                    """, UUID.randomUUID(), schoolId, sessionId, termId, sequenceCodes[i], sequenceCodes[i], i + 1,
                    sequenceStarts[i], sequenceEnds[i]);
        }
        String[] resultCodes = {"T1_RESULT", "T2_RESULT", "T3_RESULT"};
        String[] resultStarts = {"2026-12-01", "2027-03-01", "2027-07-01"};
        String[] resultEnds = {"2026-12-20", "2027-03-27", "2027-07-31"};
        for (int i = 0; i < resultCodes.length; i++) {
            UUID termId = termIds.get(i);
            jdbc.update("""
                    INSERT INTO academic_reporting_period(id,school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,status)
                    VALUES (?,?,?,?,?,?, 'TERM_RESULT',?,?::date,?::date,'DRAFT')
                    """, UUID.randomUUID(), schoolId, sessionId, termId, resultCodes[i], resultCodes[i], i + 7,
                    resultStarts[i], resultEnds[i]);
        }
        jdbc.update("""
                INSERT INTO academic_reporting_period(id,school_id,academic_session_id,code,label,period_type,display_order,start_date,end_date,status)
                VALUES (?,?,?,'ANNUAL','ANNUAL','ANNUAL_RESULT',10,'2026-08-01','2027-07-31','DRAFT')
                """, UUID.randomUUID(), schoolId, sessionId);

        insertSubject(activeSubjectId, "FRANC", "Français");
        for (int i = 0; i < unusedSubjectIds.length; i++) insertSubject(unusedSubjectIds[i], "UNUSED" + i, "Unused " + i);
        jdbc.update("INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id,display_order) VALUES (?,?,?,?,?,1)",
                UUID.randomUUID(), schoolId, sessionId, activeClassId, activeSubjectId);
        for (int i = 0; i < unusedSubjectIds.length; i++) {
            jdbc.update("INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id,display_order) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), schoolId, sessionId, unusedClassId, unusedSubjectIds[i], i + 1);
        }
        jdbc.update("INSERT INTO employee(id,school_id,code,name,type,active,level) VALUES (?,?,?,'Active Teacher','Permanent',true,'secondary')",
                employeeId, schoolId, "TEACHER-" + employeeId.toString().substring(0, 8));
        jdbc.update("""
                INSERT INTO academic_class_subject_teacher(id,school_id,academic_session_id,class_id,subject_id,employee_id,role,active,source)
                VALUES (?,?,?,?,?,?,'RESPONSIBLE',true,'ACADEMIC_SETUP')
                """, UUID.randomUUID(), schoolId, sessionId, activeClassId, activeSubjectId, employeeId);
        UUID studentId = UUID.randomUUID();
        jdbc.update("INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level) VALUES (?,?,?,'Active','Student',?,'Active A','FR','secondary')",
                studentId, schoolId, "RD" + studentId.toString().substring(0, 8), activeClassId);
        jdbc.update("""
                INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source)
                VALUES (?,?,?,?,'Active A','secondary','FR','ACTIVE','2026-08-01','TEST')
                """, schoolId, studentId, sessionId, activeClassId);
        return new Fixture(sessionId, unusedClassId, sourceSessionId);
    }

    private void insertSubject(UUID id, String code, String label) {
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,?::jsonb,1,'FR')",
                id, schoolId, code, "{\"fr\":\"" + label + "\",\"en\":\"" + label + "\"}");
    }

    private record Fixture(UUID sessionId, UUID unusedClassId, UUID sourceSessionId) {}
}
