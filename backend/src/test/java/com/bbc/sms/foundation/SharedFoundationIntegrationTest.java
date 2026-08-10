package com.bbc.sms.foundation;

import com.bbc.sms.attendance.AttendanceWorkflowService;
import com.bbc.sms.attendance.dto.AttendanceDtos.BulkMarkRequest;
import com.bbc.sms.attendance.dto.AttendanceDtos.MarkInput;
import com.bbc.sms.attendance.dto.AttendanceDtos.ActionRequest;
import com.bbc.sms.documents.OfficialDocumentDtos.GenerateRequest;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicSessionService;
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
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("bbc.documents.storage-path", DOCUMENTS::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicSessionService sessionService;
    @Autowired IdempotencyService idempotency;
    @Autowired OfficialDocumentService documents;
    @Autowired AttendanceWorkflowService attendance;
    @Autowired JourneyPromotionService promotions;
    @Autowired StudentService students;

    @BeforeEach
    void tenant() {
        schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId, "T" + schoolId.toString().substring(0, 6), "Test school");
        TenantContext.set(schoolId);
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void flywayCreatesEveryFoundationTableAndSessionTermsCannotOverlap() {
        for (String table : new String[]{"academic_session","academic_term","student_enrollment",
                "school_calendar_day","expected_school_session","audit_event","idempotency_key",
                "document_template","generated_document","permission_action_grant",
                "attendance_policy","attendance_session","attendance_mark","attendance_session_event",
                "attendance_notification"}) {
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
                java.util.List.of(new MarkInput(studentId, "present", null, "On time", 0))));
        assertThat(saved.marks().getFirst().status()).isEqualTo("present");
        assertThatThrownBy(() -> attendance.save(new BulkMarkRequest(roster.session().id(), roster.session().version(),
                java.util.List.of(new MarkInput(studentId, "absent", "Sick", null, 0)))))
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
        assertThat(analytics.attendancePercent()).isEqualByComparingTo("100.00");
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
