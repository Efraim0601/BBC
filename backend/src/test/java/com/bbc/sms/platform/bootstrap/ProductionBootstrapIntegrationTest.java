package com.bbc.sms.platform.bootstrap;

import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fresh-tenant regression coverage for the bootstrap administrator exception.
 * The ordinary principal policy must stay oversight-only while the one
 * bootstrap user can perform the setup actions required by the first-run UI.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "bbc.bootstrap.admin-password=bootstrap-password",
        "bbc.bootstrap.admin-username=admin",
        "bbc.bootstrap.admin-name=Bootstrap Test Administrator",
        "bbc.bootstrap.school-code=BOOTSTRAP-TEST",
        "bbc.bootstrap.school-name=Bootstrap Test School"
})
class ProductionBootstrapIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bootstrap_test")
            .withUsername("bbc")
            .withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwt;

    private String bootstrapAccessToken() {
        Map<String, Object> admin = jdbc.queryForMap("""
                SELECT id, school_id, username, role_code, display_name, initials
                  FROM app_user
                 WHERE username='admin'
                """);
        return jwt.issueAccess(new AppUserPrincipal(
                (UUID) admin.get("id"), (UUID) admin.get("school_id"),
                (String) admin.get("username"), (String) admin.get("role_code"),
                (String) admin.get("display_name"), (String) admin.get("initials")));
    }

    @Test
    void bootstrapAdminCanWriteSetupThroughTheProtectedEndpoint() throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT role_code FROM app_user WHERE username='admin'", String.class))
                .isEqualTo("administrator");
        mockMvc.perform(post("/api/setup/sections")
                        .header("Authorization", "Bearer " + bootstrapAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Bootstrap regression section","subsystem":"FR","level":"maternelle"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void ordinaryPrincipalCannotOpenAccessControlOrLeaveAssignedParcours() throws Exception {
        Map<String, Object> bootstrap = jdbc.queryForMap(
                "SELECT school_id FROM app_user WHERE username='admin'");
        UUID principalId = UUID.randomUUID();
        UUID schoolId = (UUID) bootstrap.get("school_id");
        jdbc.update("""
                INSERT INTO app_user
                    (id,school_id,username,password_hash,display_name,initials,role_code,parcours_scope_mode)
                VALUES (?,?,?,'unused','Scoped Principal','SP','principal','EXPLICIT')
                """, principalId, schoolId, "scoped-principal-" + principalId);
        jdbc.update("""
                INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason)
                VALUES (?,?,'principal',true,'Principal scope integration test')
                """, schoolId, principalId);
        jdbc.update("""
                INSERT INTO app_user_parcours(user_id,level,subsystem)
                VALUES (?,'primary','FR')
                """, principalId);
        String token = jwt.issueAccess(new AppUserPrincipal(
                principalId, schoolId, "scoped-principal", "principal",
                "Scoped Principal", "SP"));

        mockMvc.perform(get("/api/access/catalog")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Parcours", "primary:FR"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/setup/classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/setup/classes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Parcours", "secondary:FR"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/setup/classes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Parcours", "primary:FR"))
                .andExpect(status().isOk());
    }

    @Test
    void bootstrapAdminCanCreateAcademicSessionThroughV2PolicyGuard() throws Exception {
        mockMvc.perform(post("/api/settings/academic-sessions")
                        .header("Authorization", "Bearer " + bootstrapAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"V2-GUARD-2026",
                                  "label":"V2 guard regression session",
                                  "startDate":"2026-09-01",
                                  "endDate":"2027-07-16",
                                  "status":"DRAFT",
                                  "current":false,
                                  "timezone":"Africa/Douala"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void bootstrapAdminCanUpdateCalendarThroughV2PolicyGuard() throws Exception {
        UUID sessionId = jdbc.queryForObject(
                "SELECT id FROM academic_session WHERE code='2025-2026' AND school_id=(SELECT school_id FROM app_user WHERE username='admin')",
                UUID.class);

        mockMvc.perform(put("/api/settings/calendar/{sessionId}/days", sessionId)
                        .header("Authorization", "Bearer " + bootstrapAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayOfWeek":1,"teachingDay":true,"startTime":"07:30","endTime":"16:30"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void accessPolicyPreviewAcceptsJsonNullForInheritedScope() throws Exception {
        Long version = jdbc.queryForObject(
                "SELECT version FROM school_permission_version WHERE school_id=(SELECT school_id FROM app_user WHERE username='admin')",
                Long.class);

        mockMvc.perform(post("/api/access/roles/principal/preview")
                        .header("Authorization", "Bearer " + bootstrapAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedPolicyVersion": %d,
                                  "reason": "Inherited scope JSON-null regression",
                                  "rules": [{
                                    "actionCode": "STUDENT_DIRECTORY_VIEW",
                                    "effect": "INHERIT",
                                    "scopeMode": "NONE",
                                    "scopePayload": null,
                                    "effectiveFrom": null,
                                    "effectiveTo": null,
                                    "permanent": false,
                                    "reason": "Inherited scope JSON-null regression"
                                  }],
                                  "confirmHighRisk": false,
                                  "separationOfDutiesOverride": false
                                }
                                """.formatted(version)))
                .andExpect(status().isOk());
    }

    @Test
    void bootstrapAdminGetsSetupWritesWithoutBroadeningPrincipalTemplate() {
        List<String> bootstrapActions = jdbc.queryForList("""
                SELECT action_code
                  FROM permission_user_action
                 WHERE user_id=(SELECT id FROM app_user WHERE username='admin')
                   AND effect='ALLOW'
                   AND scope_mode='SCHOOL_ALL'
                 ORDER BY action_code
                """, String.class);

        assertThat(bootstrapActions).contains(
                "CALENDAR_MANAGE", "CLASS_MANAGE", "CURRICULUM_CATALOG_MANAGE",
                "CURRICULUM_CLASS_MANAGE", "CURRICULUM_MANAGE", "DISCIPLINE_CATALOG_MANAGE",
                "MAIL_CONFIG_MANAGE", "ROLE_MANAGE", "SCHOOL_PROFILE_MANAGE",
                "SESSION_MANAGE", "SESSION_VIEW", "SUBJECT_MANAGE", "TEACHING_ASSIGNMENT_MANAGE",
                "TEACHING_CLASS_ASSIGNMENT_MANAGE", "ACADEMIC_ASSESSMENT_VIEW",
                "ACADEMIC_ASSESSMENT_MANAGE", "ATTENDANCE_ROSTER_VIEW", "ATTENDANCE_MARK",
                "ATTENDANCE_FINALIZE", "ATTENDANCE_REOPEN", "ATTENDANCE_ANALYTICS_VIEW",
                "ATTENDANCE_POLICY_MANAGE", "ATTENDANCE_RECONCILE", "ATTENDANCE_POLICY_VIEW",
                "ATTENDANCE_DEVICE_VIEW", "ATTENDANCE_DEVICE_MANAGE", "ATTENDANCE_NOTIFICATION_VIEW",
                "STUDENT_PROFILE_CREATE", "STUDENT_IMPORT");

        Integer ordinaryPrincipalSetupWrites = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_action
                 WHERE role_code='principal'
                   AND action_code IN (
                       'CALENDAR_MANAGE', 'CLASS_MANAGE', 'CURRICULUM_CATALOG_MANAGE',
                       'CURRICULUM_CLASS_MANAGE', 'CURRICULUM_MANAGE',
                       'DISCIPLINE_CATALOG_MANAGE', 'MAIL_CONFIG_MANAGE', 'ROLE_MANAGE',
                       'SCHOOL_PROFILE_MANAGE', 'SESSION_MANAGE', 'SUBJECT_MANAGE',
                       'TEACHING_ASSIGNMENT_MANAGE', 'TEACHING_CLASS_ASSIGNMENT_MANAGE',
                       'ACADEMIC_ASSESSMENT_VIEW', 'ACADEMIC_ASSESSMENT_MANAGE')
                   AND effect='ALLOW'
                """, Integer.class);

        assertThat(ordinaryPrincipalSetupWrites).isZero();

        Integer ordinaryPrincipalStudentSetupWrites = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_action
                 WHERE role_code='principal'
                   AND action_code IN ('STUDENT_PROFILE_CREATE', 'STUDENT_IMPORT')
                   AND effect='ALLOW'
                """, Integer.class);
        assertThat(ordinaryPrincipalStudentSetupWrites).isZero();

        List<Map<String, Object>> attendancePolicies = jdbc.queryForList("""
                SELECT level, model, late_after_minutes, chronic_absence_percent,
                       require_absence_reason
                  FROM attendance_policy
                 WHERE school_id=(SELECT school_id FROM app_user WHERE username='admin')
                 ORDER BY level
                """);
        assertThat(attendancePolicies).containsExactly(
                Map.of("level", "maternelle", "model", "DAILY", "late_after_minutes", 15,
                        "chronic_absence_percent", new java.math.BigDecimal("15.00"),
                        "require_absence_reason", false),
                Map.of("level", "primary", "model", "DAILY", "late_after_minutes", 15,
                        "chronic_absence_percent", new java.math.BigDecimal("15.00"),
                        "require_absence_reason", false),
                Map.of("level", "secondary", "model", "PERIOD", "late_after_minutes", 10,
                        "chronic_absence_percent", new java.math.BigDecimal("20.00"),
                        "require_absence_reason", false));
    }

    @Test
    void forwardMigrationRepairsOnlyTheBootstrapException() {
        Integer migrated = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='124' AND success=true",
                Integer.class);
        assertThat(migrated).isEqualTo(124);

        Integer attendanceDefaultsMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='129' AND success=true",
                Integer.class);
        assertThat(attendanceDefaultsMigration).isEqualTo(129);

        Integer formTeacherScopeMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='130' AND success=true",
                Integer.class);
        assertThat(formTeacherScopeMigration).isEqualTo(130);

        Integer formTeacherAssignedRosterMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='131' AND success=true",
                Integer.class);
        assertThat(formTeacherAssignedRosterMigration).isEqualTo(131);

        Integer deviceManagementMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='132' AND success=true",
                Integer.class);
        assertThat(deviceManagementMigration).isEqualTo(132);

        Integer bootstrapStudentSetupMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='145' AND success=true",
                Integer.class);
        assertThat(bootstrapStudentSetupMigration).isEqualTo(145);

        Integer bootstrapStudentSetupActions = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_user_action ua
                  JOIN app_user u ON u.id=ua.user_id
                 WHERE u.username='admin'
                   AND ua.action_code IN ('STUDENT_PROFILE_CREATE', 'STUDENT_IMPORT')
                   AND ua.effect='ALLOW'
                   AND ua.scope_mode='SCHOOL_ALL'
                """, Integer.class);
        assertThat(bootstrapStudentSetupActions).isEqualTo(2);

        Integer principalWorkflowMigration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='134' AND success=true",
                Integer.class);
        assertThat(principalWorkflowMigration).isEqualTo(134);

        Integer bootstrapDeviceAuthority = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_user_action ua
                  JOIN app_user u ON u.id=ua.user_id
                 WHERE u.username='admin'
                   AND ua.action_code='ATTENDANCE_DEVICE_MANAGE'
                   AND ua.effect='ALLOW'
                   AND ua.scope_mode='SCHOOL_ALL'
                """, Integer.class);
        assertThat(bootstrapDeviceAuthority).isEqualTo(1);

        Integer principalDeviceAuthority = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_action
                 WHERE role_code='principal'
                   AND action_code='ATTENDANCE_DEVICE_MANAGE'
                   AND effect='ALLOW'
                """, Integer.class);
        assertThat(principalDeviceAuthority).isZero();

        Integer formTeacherSecondaryAttendanceRules = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_template_rule
                 WHERE template_code='form_teacher'
                   AND scope_mode='TIMETABLE_OCCURRENCES_ASSIGNED'
                   AND action_code IN ('ATTENDANCE_ROSTER_VIEW','ATTENDANCE_MARK',
                                       'ATTENDANCE_FINALIZE','ATTENDANCE_ANALYTICS_VIEW')
        """, Integer.class);
        assertThat(formTeacherSecondaryAttendanceRules).isEqualTo(4);

        Integer formTeacherAssignedRosterRules = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_template_rule
                 WHERE template_code='form_teacher'
                   AND action_code='ACADEMIC_ROSTER_VIEW'
                   AND scope_mode='ASSIGNED_CLASSES'
                """, Integer.class);
        assertThat(formTeacherAssignedRosterRules).isEqualTo(1);

        Integer ordinaryPrincipalBootstrapRows = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_user_action ua
                  JOIN app_user u ON u.id=ua.user_id
                 WHERE u.username <> 'admin'
                   AND ua.reason='Fresh-school bootstrap setup authority; replace during access-control setup'
                """, Integer.class);
        assertThat(ordinaryPrincipalBootstrapRows).isZero();
    }

    @Test
    void principalAcademicWorkflowAuthorityKeepsLegacyAndV2GatesAligned() {
        Integer migration = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version='133' AND success=true",
                Integer.class);
        assertThat(migration).isEqualTo(133);

        Integer legacyActions = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_action_grant
                 WHERE role_code='principal'
                   AND action_code IN ('ACADEMIC_GRADE_PACKET_REVIEW',
                                       'ACADEMIC_REPORT_CARD_VALIDATE',
                                       'ACADEMIC_REPORT_CARD_PUBLISH')
                   AND allowed=true
                """, Integer.class);
        assertThat(legacyActions).isEqualTo(3);

        Integer v2Actions = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_action
                 WHERE role_code='principal'
                   AND action_code IN ('ACADEMIC_GRADE_PACKET_REVIEW',
                                       'ACADEMIC_REPORT_CARD_VALIDATE',
                                       'ACADEMIC_REPORT_CARD_PUBLISH')
                   AND effect='ALLOW'
                   AND scope_mode='SCHOOL_ALL'
                """, Integer.class);
        assertThat(v2Actions).isEqualTo(3);

        Integer setupWrites = jdbc.queryForObject("""
                SELECT count(*)
                  FROM permission_role_action
                 WHERE role_code='principal'
                   AND action_code IN ('CURRICULUM_CLASS_MANAGE',
                                       'CURRICULUM_CATALOG_MANAGE',
                                       'TEACHING_CLASS_ASSIGNMENT_MANAGE')
                   AND effect='ALLOW'
                """, Integer.class);
        assertThat(setupWrites).isZero();
    }

    @Test
    void forwardMigrationSupportsDescriptiveLifecycleSubjectCodes() throws Exception {
        Integer subjectCodeLength = jdbc.queryForObject("""
                SELECT character_maximum_length
                  FROM information_schema.columns
                 WHERE table_name='subject' AND column_name='code'
                """, Integer.class);
        assertThat(subjectCodeLength).isGreaterThanOrEqualTo(32);

        mockMvc.perform(post("/api/setup/subjects")
                        .header("Authorization", "Bearer " + bootstrapAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"LANGUAGE_ACTIVITIES",
                                  "subsystem":"EN",
                                  "label":{"fr":"Activités langagières","en":"Language activities"},
                                  "coef":2
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
