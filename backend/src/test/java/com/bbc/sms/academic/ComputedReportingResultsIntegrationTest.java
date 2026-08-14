package com.bbc.sms.academic;

import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.platform.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the live dependency graph against PostgreSQL, including the
 * sequence packet boundary, recursive term calculation, annual calculation,
 * and the class-scoped coefficient authority.
 */
@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class ComputedReportingResultsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("computed_results_test")
            .withUsername("bbc")
            .withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BulletinSnapshotService snapshots;

    private UUID schoolId;
    private UUID sessionId;
    private UUID classId;
    private UUID studentId;
    private UUID subjectId;
    private UUID testUserId;
    private final Map<String, UUID> periods = new LinkedHashMap<>();
    private final Map<String, UUID> terms = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        classId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)",
                schoolId, "CMP-" + schoolId.toString().substring(0, 8), "Computed results test");
        String sectionId = "cmp-" + schoolId.toString().substring(0, 8);
        jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,'FR','secondary')",
                sectionId, schoolId, "Computed secondary");
        jdbc.update("""
                INSERT INTO school_class(id,school_id,section_id,name,subsystem,level)
                VALUES (?,?,?,'4eme Computed','FR','secondary')
                """, classId, schoolId, sectionId);
        jdbc.update("""
                INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current,timezone)
                VALUES (?,?,?,'Computed session','2026-09-01','2027-07-31','OPEN',true,'Africa/Douala')
                """, sessionId, schoolId, "CMP-" + sessionId.toString().substring(0, 8));

        insertTerm("T1", 1, "2026-09-01", "2026-11-30");
        insertTerm("T2", 2, "2026-12-01", "2027-02-28");
        insertTerm("T3", 3, "2027-03-01", "2027-07-31");

        insertPeriod("S1", "SEQUENCE", 1, "2026-09-01", "2026-10-15", terms.get("T1"));
        insertPeriod("S2", "SEQUENCE", 2, "2026-10-16", "2026-11-30", terms.get("T1"));
        insertPeriod("T1_RESULT", "TERM_RESULT", 3, "2026-09-01", "2026-11-30", terms.get("T1"));
        insertPeriod("S3", "SEQUENCE", 4, "2026-12-01", "2027-01-15", terms.get("T2"));
        insertPeriod("S4", "SEQUENCE", 5, "2027-01-16", "2027-02-28", terms.get("T2"));
        insertPeriod("T2_RESULT", "TERM_RESULT", 6, "2026-12-01", "2027-02-28", terms.get("T2"));
        insertPeriod("S5", "SEQUENCE", 7, "2027-03-01", "2027-05-15", terms.get("T3"));
        insertPeriod("S6", "SEQUENCE", 8, "2027-05-16", "2027-07-31", terms.get("T3"));
        insertPeriod("T3_RESULT", "TERM_RESULT", 9, "2027-03-01", "2027-07-31", terms.get("T3"));
        insertPeriod("ANNUAL", "ANNUAL_RESULT", 10, "2026-09-01", "2027-07-31", null);

        dependency("T1_RESULT", "S1", "0.5", false, 1);
        dependency("T1_RESULT", "S2", "0.5", false, 2);
        dependency("T2_RESULT", "S3", "0.5", false, 1);
        dependency("T2_RESULT", "S4", "0.5", false, 2);
        dependency("T3_RESULT", "S5", "0.5", false, 1);
        dependency("T3_RESULT", "S6", "0.5", false, 2);
        dependency("ANNUAL", "T1_RESULT", "0.3333333333333333", false, 1);
        dependency("ANNUAL", "T2_RESULT", "0.3333333333333333", false, 2);
        dependency("ANNUAL", "T3_RESULT", "0.3333333333333334", false, 3);

        jdbc.update("""
                INSERT INTO subject(id,school_id,code,label,coef,subsystem)
                VALUES (?,?, 'MATH','{"fr":"Mathématiques","en":"Mathematics"}'::jsonb,1,'FR')
                """, subjectId, schoolId);
        jdbc.update("""
                INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id,
                    display_order,coefficient,max_score,mandatory,pass_threshold)
                VALUES (?,?,?,?,?,1,3,20,true,10)
                """, UUID.randomUUID(), schoolId, sessionId, classId, subjectId);
        jdbc.update("""
                INSERT INTO student(id,school_id,matricule,first_name,last_name,class_id,class_name,subsystem,level)
                VALUES (?,?, 'CMP-1615','Marie','Amanta',?,'4eme Computed','FR','secondary')
                """, studentId, schoolId, classId);
        jdbc.update("""
                INSERT INTO student_enrollment(school_id,student_id,academic_session_id,school_class_id,
                    class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source)
                VALUES (?,?,?,?,'4eme Computed','secondary','FR','ACTIVE','2026-09-01','TEST')
                """, schoolId, studentId, sessionId, classId);

        testUserId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO role(code,label_fr,label_en,builtin)
                VALUES ('principal','Principal','Principal',true)
                ON CONFLICT (code) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO permission_action_grant(school_id,role_code,action_code,allowed)
                VALUES (?, 'principal', 'ACADEMIC_REPORT_CARD_VIEW', true)
                ON CONFLICT (school_id,role_code,action_code) DO UPDATE SET allowed=true
                """, schoolId);
        jdbc.update("""
                INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,active)
                VALUES (?,?,'computed-test','test','Computed test','CT','principal',true)
                """, testUserId, schoolId);

        Map<String, BigDecimal> marks = Map.of(
                "S1", new BigDecimal("12"), "S2", new BigDecimal("14"),
                "S3", new BigDecimal("10"), "S4", new BigDecimal("16"),
                "S5", new BigDecimal("8"), "S6", new BigDecimal("12"));
        marks.forEach(this::insertSequenceInput);
        TenantContext.set(schoolId);
        AppUserPrincipal principal = new AppUserPrincipal(testUserId, schoolId,
                "computed-test", "principal", "Computed test", "CT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void computesTermAndAnnualFromAcceptedLiveDependenciesWithoutSnapshots() {
        var term = snapshots.preview(studentId, periods.get("T1_RESULT"));
        assertThat(term.product()).isEqualTo("TERM");
        assertThat(term.reportingPeriodType()).isEqualTo("TERM_RESULT");
        assertThat(term.average()).isEqualByComparingTo("13");
        assertThat(term.lines()).singleElement().satisfies(line -> {
            assertThat(line.coefficient()).isEqualTo(3);
            assertThat(line.mark()).isEqualByComparingTo("13");
            assertThat(line.periodMarks()).extracting(p -> p.periodCode())
                    .containsExactly("S1", "S2");
        });
        assertThat(term.evidence().dependencySources()).extracting(d -> d.childPeriodCode())
                .containsExactly("S1", "S2");
        assertThat(term.workflowMeta().inputReadiness()).isEqualTo("READY");
        assertThat(term.workflowMeta().dependencies()).allMatch(d -> d.acceptedPacketCount() == 1);
        assertThat(term.blockers()).isEmpty();

        var annual = snapshots.preview(studentId, periods.get("ANNUAL"));
        assertThat(annual.product()).isEqualTo("ANNUAL");
        assertThat(annual.reportingPeriodType()).isEqualTo("ANNUAL_RESULT");
        assertThat(annual.average()).isEqualByComparingTo("12");
        assertThat(annual.lines()).singleElement().satisfies(line -> {
            assertThat(line.coefficient()).isEqualTo(3);
            assertThat(line.mark()).isEqualByComparingTo("12");
            assertThat(line.periodMarks()).extracting(p -> p.periodCode())
                    .containsExactly("T1_RESULT", "T2_RESULT", "T3_RESULT");
        });
        assertThat(annual.evidence().dependencySources()).extracting(d -> d.childPeriodCode())
                .containsExactly("T1_RESULT", "T2_RESULT", "T3_RESULT");
        assertThat(annual.evidence().dependencySources()).allMatch(d -> !d.packetTraces().isEmpty());
        assertThat(annual.blockers()).isEmpty();
    }

    private void insertTerm(String code, int sequence, String start, String end) {
        UUID id = UUID.randomUUID();
        terms.put(code, id);
        jdbc.update("""
                INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone)
                VALUES (?,?,?,?,?,?,?,?, 'Africa/Douala')
                """, id, schoolId, sessionId, code, code, sequence, Date.valueOf(start), Date.valueOf(end));
    }

    private void insertPeriod(String code, String type, int displayOrder, String start, String end, UUID termId) {
        UUID id = UUID.randomUUID();
        periods.put(code, id);
        jdbc.update("""
                INSERT INTO academic_reporting_period(id,school_id,academic_session_id,academic_term_id,
                    code,label,period_type,display_order,start_date,end_date,status)
                VALUES (?,?,?,?,?,?,?, ?,?,?, 'OPEN')
                """, id, schoolId, sessionId, termId, code, code, type, displayOrder,
                Date.valueOf(start), Date.valueOf(end));
    }

    private void dependency(String parent, String child, String weight, boolean optional, int displayOrder) {
        jdbc.update("""
                INSERT INTO academic_reporting_period_dependency
                    (id,school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), schoolId, sessionId, periods.get(parent), periods.get(child),
                new BigDecimal(weight), optional, displayOrder);
    }

    private void insertSequenceInput(String periodCode, BigDecimal mark) {
        UUID periodId = periods.get(periodCode);
        UUID assessmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academic_assessment(id,school_id,academic_session_id,reporting_period_id,
                    subject_code,class_id,code,label,assessment_type,max_score,weight,mandatory,display_order,
                    lifecycle_status,component_type)
                VALUES (?,?,?,?,?,?,?,'Evaluation', 'EVALUATION',20,1,true,1,'PUBLISHED','SEQUENCE')
                """, assessmentId, schoolId, sessionId, periodId, "MATH", classId,
                "EVAL_" + periodCode);
        jdbc.update("""
                INSERT INTO academic_grade(id,school_id,academic_session_id,reporting_period_id,assessment_id,
                    student_id,subject_code,mark,value_status,workflow_status)
                VALUES (?,?,?,?,?,?,?,?,'SCORED','ACCEPTED')
                """, UUID.randomUUID(), schoolId, sessionId, periodId, assessmentId,
                studentId, "MATH", mark);
        jdbc.update("""
                INSERT INTO academic_grade_packet(id,school_id,academic_session_id,reporting_period_id,class_id,
                    subject_code,status,submitted_at,reviewed_at)
                VALUES (?,?,?,?,?,'MATH','ACCEPTED',now(),now())
                """, UUID.randomUUID(), schoolId, sessionId, periodId, classId);
    }
}
