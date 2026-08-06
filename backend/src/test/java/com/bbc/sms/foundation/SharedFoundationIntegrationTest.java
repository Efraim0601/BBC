package com.bbc.sms.foundation;

import com.bbc.sms.documents.OfficialDocumentDtos.GenerateRequest;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.foundation.session.SessionDtos.SessionUpsert;
import com.bbc.sms.foundation.session.SessionDtos.TermUpsert;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
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
                "document_template","generated_document","permission_action_grant"}) {
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
}
