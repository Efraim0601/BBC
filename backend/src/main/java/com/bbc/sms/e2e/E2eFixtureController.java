package com.bbc.sms.e2e;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Test infrastructure only: provisions a minimal second tenant for the
 * isolated lifecycle acceptance matrix.
 *
 * <p>The route is unavailable unless the {@code e2e} Spring profile is active,
 * the {@code prod} profile is absent, and the explicit
 * {@code bbc.e2e.fixtures.enabled=true} property is active. It is protected by
 * the normal permission-policy action and does not add any production
 * tenant-provisioning capability or ordinary-role authority.</p>
 */
@RestController
@RequestMapping("/api/e2e/fixtures")
@Profile("e2e & !prod")
@ConditionalOnProperty(prefix = "bbc.e2e.fixtures", name = "enabled", havingValue = "true")
public class E2eFixtureController {

    private static final List<String> READ_MODULES = List.of(
            "dashboard", "students", "reports", "settings", "academic",
            "presence", "finance", "timetable", "events", "discipline",
            "journey", "alerts", "messages", "coursebook", "health",
            "documents", "classkit");

    private static final List<String> READ_ACTIONS = List.of(
            "DASHBOARD_VIEW", "REPORTS_VIEW", "SCHOOL_PROFILE_VIEW",
            "STUDENT_DIRECTORY_VIEW", "STUDENT_PROFILE_VIEW");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public E2eFixtureController(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /**
     * Create or read back the deterministic second-school fixture. The caller
     * supplies the credentials because the fixture is intentionally isolated
     * from production bootstrap credentials and mail delivery.
     */
    @PostMapping("/second-school")
    @PreAuthorize("@policy.canAction('PERMISSION_MANAGE')")
    @Transactional
    public FixtureView provisionSecondSchool(@Valid @RequestBody FixtureRequest request) {
        String schoolCode = normalizeCode(request.schoolCode());
        String username = request.adminUsername().trim();

        List<FixtureView> existing = jdbc.query("""
                SELECT s.id AS school_id, ay.id AS session_id, u.id AS user_id,
                       s.code, s.name, u.username
                  FROM school s
                  JOIN academic_session ay ON ay.school_id=s.id AND ay.is_current=true
                  JOIN app_user u ON u.school_id=s.id AND u.username=?
                 WHERE s.code=?
                 LIMIT 1
                """, (rs, row) -> new FixtureView(
                rs.getObject("school_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("username")),
                username, schoolCode);
        if (!existing.isEmpty()) return existing.getFirst();

        Integer codeCount = jdbc.queryForObject(
                "SELECT count(*) FROM school WHERE code=?", Integer.class, schoolCode);
        if (codeCount != null && codeCount > 0) {
            throw new IllegalStateException("E2E school code already belongs to another fixture account");
        }

        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        int startYear = LocalDate.now().getYear();
        String label = startYear + "-" + (startYear + 1);

        jdbc.update("""
                INSERT INTO school
                    (id,code,name,country,currency,school_start_time,school_end_time,staff_portal_enabled)
                VALUES (?,?,?,'Cameroon','FCFA','07:30','17:00',false)
                """, schoolId, schoolCode, request.schoolName().trim());
        jdbc.update("""
                INSERT INTO academic_year(id,school_id,label,start_year,is_current)
                VALUES (?,?,?, ?,true)
                """, sessionId, schoolId, label, startYear);
        jdbc.update("""
                INSERT INTO academic_session
                    (id,school_id,code,label,start_date,end_date,status,is_current)
                VALUES (?,?,?,?,?,?,'OPEN',true)
                """, sessionId, schoolId, label, label,
                LocalDate.of(startYear, 1, 1), LocalDate.of(startYear + 1, 12, 31));
        jdbc.update("""
                INSERT INTO app_user
                    (id,school_id,username,password_hash,display_name,initials,role_code,
                     parcours_scope_mode)
                VALUES (?,?,?,?,?,'E2E','principal','GLOBAL')
                """, userId, schoolId, username, encoder.encode(request.adminPassword()),
                request.adminName().trim());
        jdbc.update("""
                INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,assigned_by,reason)
                VALUES (?,?,'principal',true,?,'E2E second-tenant fixture')
                """, schoolId, userId, userId);

        for (String module : READ_MODULES) {
            jdbc.update("""
                    INSERT INTO permission_grant(school_id,role_code,module,level)
                    VALUES (?,'principal',?,'read') ON CONFLICT DO NOTHING
                    """, schoolId, module);
        }
        jdbc.update("""
                INSERT INTO school_permission_version(school_id,version)
                VALUES (?,1) ON CONFLICT (school_id) DO NOTHING
                """, schoolId);
        jdbc.update("""
                INSERT INTO permission_policy_rollout
                    (school_id,mode,enforcement_enabled,reviewed_by,reviewed_at)
                VALUES (?,'SAFE_DEFAULT',true,?,now())
                ON CONFLICT (school_id) DO NOTHING
                """, schoolId, userId);
        for (String action : READ_ACTIONS) {
            jdbc.update("""
                    INSERT INTO permission_role_action
                        (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
                    SELECT ?, 'principal', action_code, effect, scope_mode, is_permanent,
                           'E2E second-tenant read fixture'
                      FROM permission_role_template_rule
                     WHERE template_code='principal_oversight' AND action_code=?
                    ON CONFLICT DO NOTHING
                    """, schoolId, action);
        }
        return new FixtureView(schoolId, sessionId, userId, schoolCode,
                request.schoolName().trim(), username);
    }

    private static String normalizeCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public record FixtureRequest(
            @NotBlank @Size(max = 32) String schoolCode,
            @NotBlank @Size(max = 160) String schoolName,
            @NotBlank @Size(max = 64) String adminUsername,
            @NotBlank @Size(min = 8, max = 128) String adminPassword,
            @NotBlank @Size(max = 120) String adminName) {}

    public record FixtureView(UUID schoolId, UUID sessionId, UUID userId,
                              String schoolCode, String schoolName, String adminUsername) {}
}
