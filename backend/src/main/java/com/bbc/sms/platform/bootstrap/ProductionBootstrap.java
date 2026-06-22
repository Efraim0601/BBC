package com.bbc.sms.platform.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * First-run bootstrap for a PRODUCTION database (no demo seed).
 *
 * <p>When the {@code app_user} table is empty (fresh production schema) and an
 * admin password is supplied via configuration, this creates exactly one
 * tenant (school + current academic year), the reference roles, a sensible
 * default permission matrix, and a single administrator account with the
 * {@code principal} role (full write on every module — including Settings, so
 * the admin can configure everything else from the UI).
 *
 * <p>It is a no-op when the database already contains users (demo profile, or
 * any subsequent start), so it is safe to leave enabled in all profiles.
 * Configure via env: {@code BBC_ADMIN_PASSWORD} (required to act),
 * {@code BBC_ADMIN_USERNAME}, {@code BBC_ADMIN_NAME}, {@code BBC_SCHOOL_NAME},
 * {@code BBC_SCHOOL_CODE}, {@code BBC_YEAR_LABEL}.
 */
@Component
public class ProductionBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionBootstrap.class);

    /** Every module the permission matrix can grant. */
    private static final String[] MODULES = {
        "dashboard", "presence", "students", "hr", "academic", "finance",
        "timetable", "events", "discipline", "reports", "settings", "journey",
        "alerts", "messages", "coursebook", "health", "documents"
    };

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Value("${bbc.bootstrap.enabled:true}")        private boolean enabled;
    @Value("${bbc.bootstrap.admin-username:admin}") private String adminUsername;
    @Value("${bbc.bootstrap.admin-password:}")      private String adminPassword;
    @Value("${bbc.bootstrap.admin-name:Administrateur}") private String adminName;
    @Value("${bbc.bootstrap.school-name:Mon établissement}") private String schoolName;
    @Value("${bbc.bootstrap.school-code:SCHOOL}")   private String schoolCode;
    @Value("${bbc.bootstrap.year-label:2025-2026}") private String yearLabel;

    public ProductionBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        Integer users = jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (users != null && users > 0) return;   // already initialised (incl. demo dataset)

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("=================================================================");
            log.warn(" Base vierge et BBC_ADMIN_PASSWORD non defini.");
            log.warn(" Aucun compte administrateur cree. Definissez BBC_ADMIN_PASSWORD");
            log.warn(" (voir .env.example) puis redemarrez pour amorcer l'etablissement.");
            log.warn("=================================================================");
            return;
        }

        UUID schoolId = UUID.randomUUID();
        jdbc.update("INSERT INTO school (id, code, name) VALUES (?,?,?)",
            schoolId, schoolCode, schoolName);
        jdbc.update("INSERT INTO academic_year (school_id, label, start_year, is_current) VALUES (?,?,?,true)",
            schoolId, yearLabel, parseStartYear(yearLabel));

        // Reference roles (idempotent — they may already exist globally).
        insertRole("principal", "Principal", "Principal");
        insertRole("prefect", "Préfet d'études", "Dean of studies");
        insertRole("econome", "Économe", "Bursar");
        insertRole("form_teacher", "Prof. Principal", "Form Teacher");
        insertRole("teacher", "Enseignant", "Teacher");
        insertRole("parent", "Parent", "Parent");

        // Permission matrix. Admin (principal) gets full write everywhere; the
        // other roles get a sensible default the admin can refine in Settings.
        for (String m : MODULES) grant(schoolId, "principal", m, "write");
        grants(schoolId, "prefect", "write", "presence", "timetable", "events", "discipline", "journey", "alerts", "messages", "documents");
        grants(schoolId, "prefect", "read", "dashboard", "students", "academic", "reports", "coursebook", "health");
        grants(schoolId, "econome", "write", "finance");
        grants(schoolId, "econome", "read", "dashboard", "students", "reports", "alerts");
        grants(schoolId, "form_teacher", "write", "academic", "discipline", "coursebook", "messages");
        grants(schoolId, "form_teacher", "read", "dashboard", "presence", "students", "timetable", "events", "journey", "alerts", "health", "documents");
        grants(schoolId, "teacher", "write", "academic", "coursebook");
        grants(schoolId, "teacher", "read", "dashboard", "presence", "timetable", "events", "messages");
        grant(schoolId, "parent", "parent", "read");

        jdbc.update("INSERT INTO app_user (school_id, username, password_hash, display_name, initials, role_code) "
                  + "VALUES (?,?,?,?,?, 'principal')",
            schoolId, adminUsername, encoder.encode(adminPassword), adminName, initialsOf(adminName));

        log.info("=================================================================");
        log.info(" Amorcage production OK — etablissement « {} » + admin « {} ».",
            schoolName, adminUsername);
        log.info(" Connectez-vous puis configurez tout depuis le module Parametres.");
        log.info("=================================================================");
    }

    private void insertRole(String code, String fr, String en) {
        jdbc.update("INSERT INTO role (code, label_fr, label_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING",
            code, fr, en);
    }

    private void grant(UUID school, String role, String module, String level) {
        jdbc.update("INSERT INTO permission_grant (school_id, role_code, module, level) VALUES (?,?,?,?) "
                  + "ON CONFLICT (school_id, role_code, module) DO NOTHING", school, role, module, level);
    }

    private void grants(UUID school, String role, String level, String... modules) {
        for (String m : modules) grant(school, role, m, level);
    }

    private int parseStartYear(String label) {
        try {
            return Integer.parseInt(label.substring(0, 4));
        } catch (RuntimeException e) {
            return 2025;
        }
    }

    private String initialsOf(String name) {
        String[] p = name.trim().split("\\s+");
        String first = p.length > 0 && !p[0].isEmpty() ? p[0].substring(0, 1) : "";
        String last = p.length > 1 && !p[p.length - 1].isEmpty() ? p[p.length - 1].substring(0, 1) : "";
        String s = (first + last).toUpperCase();
        return s.isEmpty() ? "AD" : s;
    }
}
