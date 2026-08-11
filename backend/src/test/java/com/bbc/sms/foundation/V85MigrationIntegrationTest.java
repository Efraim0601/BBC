package com.bbc.sms.foundation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class V85MigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v85_test").withUsername("bbc").withPassword("bbc");

    @Test
    void v85BackfillsUnionEnvelopesWithoutImportingOverridesOrDroppingLegacyData() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        migrate("84");
            UUID school = UUID.randomUUID();
            UUID session = UUID.randomUUID();
            UUID noRulesSession = UUID.randomUUID();
            UUID t1 = UUID.randomUUID(), t2 = UUID.randomUUID(), t3 = UUID.randomUUID();
            UUID noRuleTerm = UUID.randomUUID();
            UUID s1 = UUID.randomUUID(), s3 = UUID.randomUUID(), annual = UUID.randomUUID();

            jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", school, "V85-" + school.toString().substring(0, 6), "V85 test");
            insertSession(jdbc, session, school, "source");
            insertSession(jdbc, noRulesSession, school, "no-rules");
            insertTerm(jdbc, t1, school, session, "T1", 1, "2026-09-01", "2026-11-30");
            insertTerm(jdbc, t2, school, session, "T2", 2, "2026-12-01", "2027-02-28");
            insertTerm(jdbc, t3, school, session, "T3", 3, "2027-03-01", "2027-07-31");
            insertTerm(jdbc, noRuleTerm, school, noRulesSession, "T1", 1, "2026-09-01", "2026-11-30");
            insertPeriod(jdbc, s1, school, session, t1, "S1", "SEQUENCE", 1, "2026-09-01", "2026-11-30");
            insertPeriod(jdbc, s3, school, session, t2, "S3", "SEQUENCE", 2, "2026-12-01", "2027-02-28");
            insertPeriod(jdbc, annual, school, session, null, "ANNUAL", "ANNUAL_RESULT", 3, "2026-09-01", "2027-07-31");

            Instant sessionOpen = Instant.parse("2026-09-02T08:00:00Z");
            Instant sessionClose = Instant.parse("2027-07-15T18:00:00Z");
            rule(jdbc, school, session, "SESSION", null, null, "REVIEW", sessionOpen, sessionClose);
            rule(jdbc, school, session, "TERM", t1, null, "PUBLICATION",
                    Instant.parse("2026-09-05T08:00:00Z"), Instant.parse("2026-12-20T18:00:00Z"));
            rule(jdbc, school, session, "PERIOD", t1, s1, "VALIDATION", null, Instant.parse("2026-12-25T18:00:00Z"));
            rule(jdbc, school, session, "PERIOD", null, annual, "PUBLICATION",
                    Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2027-07-31T18:00:00Z"));
            jdbc.update("""
                    INSERT INTO academic_window_override(id,school_id,academic_session_id,reporting_period_id,action,scope,reason,opens_at,expires_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), school, session, s1, "PUBLICATION", "T1", "historical exception",
                    Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")), Timestamp.from(Instant.parse("2026-09-03T00:00:00Z")));

        migrate("85");

            assertWindow(jdbc, session, "T1", true, null, "2027-07-15T18:00:00Z");
            assertWindow(jdbc, session, "T2", true, "2026-09-02T08:00:00Z", "2027-07-15T18:00:00Z");
            assertWindow(jdbc, session, "T3", true, "2026-09-02T08:00:00Z", "2027-07-31T18:00:00Z");
            assertWindow(jdbc, noRulesSession, "T1", false, null, null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_window_override", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT to_regclass('academic_workflow_window_rule') IS NOT NULL", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='academic_term' AND column_name IN ('grade_entry_opens_at','teacher_submission_opens_at')", Integer.class)).isEqualTo(2);

            String before = jdbc.queryForObject("SELECT management_window_limited || ':' || coalesce(management_opens_at::text,'') || ':' || coalesce(management_closes_at::text,'') FROM academic_term WHERE academic_session_id=? AND code='T3'", String.class, session);
            migrate("85");
            assertThat(jdbc.queryForObject("SELECT management_window_limited || ':' || coalesce(management_opens_at::text,'') || ':' || coalesce(management_closes_at::text,'') FROM academic_term WHERE academic_session_id=? AND code='T3'", String.class, session)).isEqualTo(before);
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }

    private void insertSession(JdbcTemplate jdbc, UUID id, UUID school, String code) {
        jdbc.update("INSERT INTO academic_session(id,school_id,code,label,start_date,end_date,status,is_current,timezone) VALUES (?,?,?,?,'2026-09-01','2027-07-31','OPEN',false,'Africa/Douala')",
                id, school, code + "-" + id.toString().substring(0, 6), code);
    }

    private void insertTerm(JdbcTemplate jdbc, UUID id, UUID school, UUID session, String code, int sequence,
                            String start, String end) {
        jdbc.update("INSERT INTO academic_term(id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone) VALUES (?,?,?,?,?,?,?,?,'Africa/Douala')",
                id, school, session, code, code, sequence, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    private void insertPeriod(JdbcTemplate jdbc, UUID id, UUID school, UUID session, UUID term, String code, String type,
                              int order, String start, String end) {
        jdbc.update("INSERT INTO academic_reporting_period(id,school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,status) VALUES (?,?,?,?,?,?,?, ?,?,?, 'DRAFT')",
                id, school, session, term, code, code, type, order, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    private void rule(JdbcTemplate jdbc, UUID school, UUID session, String scope, UUID term, UUID period,
                      String action, Instant open, Instant close) {
        jdbc.update("INSERT INTO academic_workflow_window_rule(school_id,academic_session_id,scope_type,academic_term_id,reporting_period_id,action,mode,opens_at,closes_at,timezone) VALUES (?,?,?,?,?,?, 'LIMITED',?,?, 'Africa/Douala')",
                school, session, scope, term, period, action,
                open == null ? null : Timestamp.from(open), close == null ? null : Timestamp.from(close));
    }

    private void assertWindow(JdbcTemplate jdbc, UUID session, String code, boolean limited, String open, String close) {
        var row = jdbc.queryForMap("SELECT management_window_limited,management_opens_at,management_closes_at FROM academic_term WHERE academic_session_id=? AND code=?", session, code);
        assertThat(row.get("management_window_limited")).isEqualTo(limited);
        assertThat(row.get("management_opens_at")).isEqualTo(open == null ? null : java.sql.Timestamp.from(Instant.parse(open)));
        assertThat(row.get("management_closes_at")).isEqualTo(close == null ? null : java.sql.Timestamp.from(Instant.parse(close)));
    }
}
