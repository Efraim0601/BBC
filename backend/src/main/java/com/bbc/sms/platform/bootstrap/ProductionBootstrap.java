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
import java.time.LocalDate;

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
        "alerts", "messages", "coursebook", "health", "documents", "classkit"
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
        UUID sessionId = UUID.randomUUID();
        int startYear = parseStartYear(yearLabel);
        jdbc.update("INSERT INTO academic_year (id, school_id, label, start_year, is_current) VALUES (?,?,?,?,true)",
            sessionId, schoolId, yearLabel, startYear);
        jdbc.update("""
            INSERT INTO academic_session
            (id,school_id,code,label,start_date,end_date,status,is_current)
            VALUES (?,?,?,?,?,?,'OPEN',true)
            """, sessionId, schoolId, yearLabel, yearLabel,
            LocalDate.of(startYear, 9, 1), LocalDate.of(startYear + 1, 7, 31));

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
        grants(schoolId, "prefect", "read", "dashboard", "students", "academic", "reports", "coursebook", "health", "classkit");
        grants(schoolId, "econome", "write", "finance");
        grants(schoolId, "econome", "read", "dashboard", "students", "reports", "alerts");
        grants(schoolId, "form_teacher", "write", "academic", "discipline", "coursebook", "messages", "classkit");
        grants(schoolId, "form_teacher", "read", "dashboard", "presence", "students", "timetable", "events", "journey", "alerts", "health", "documents");
        grants(schoolId, "teacher", "write", "academic", "coursebook");
        grants(schoolId, "teacher", "read", "dashboard", "presence", "timetable", "events", "messages");
        grant(schoolId, "parent", "parent", "read");

        seedFoundation(schoolId, sessionId);

        seedPaymentChannels(schoolId);

        jdbc.update("INSERT INTO app_user (school_id, username, password_hash, display_name, initials, role_code) "
                  + "VALUES (?,?,?,?,?, 'principal')",
            schoolId, adminUsername, encoder.encode(adminPassword), adminName, initialsOf(adminName));

        log.info("=================================================================");
        log.info(" Amorcage production OK — etablissement « {} » + admin « {} ».",
            schoolName, adminUsername);
        log.info(" Connectez-vous puis configurez tout depuis le module Parametres.");
        log.info("=================================================================");
    }

    private void seedFoundation(UUID schoolId, UUID sessionId) {
        for (int day = 1; day <= 7; day++) {
            jdbc.update("""
                INSERT INTO school_calendar_day
                (school_id,academic_session_id,day_of_week,teaching_day,start_time,end_time)
                VALUES (?,?,?,?,?::time,?::time) ON CONFLICT DO NOTHING
                """, schoolId, sessionId, day, day <= 5, "07:30", "17:00");
        }
        String[][] actions = {
            {"settings","write","SESSION_MANAGE"}, {"settings","read","SESSION_VIEW"},
            {"students","write","ENROLLMENT_MANAGE"}, {"students","read","ENROLLMENT_VIEW"},
            {"settings","write","CALENDAR_MANAGE"}, {"settings","read","CALENDAR_VIEW"},
            {"settings","read","AUDIT_VIEW"}, {"documents","write","DOCUMENT_GENERATE"},
            {"documents","write","DOCUMENT_REVOKE"}, {"documents","read","DOCUMENT_VIEW"}
        };
        for (String[] action : actions) {
            jdbc.update("""
                INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
                SELECT school_id,role_code,?,CASE WHEN ?='read' THEN level IN ('read','write') ELSE level='write' END
                FROM permission_grant WHERE school_id=? AND module=? ON CONFLICT DO NOTHING
                """, action[2], action[1], schoolId, action[0]);
        }
        jdbc.update("""
            INSERT INTO document_template (school_id,type,locale,name,body_template)
            VALUES
            (?,'ENROLLMENT_CERTIFICATE','fr','Certificat de scolarité',
             'Certifie que {{studentName}} ({{matricule}}) est inscrit(e) en {{className}} pour l''année {{sessionLabel}}.'),
            (?,'ENROLLMENT_CERTIFICATE','en','Enrollment certificate',
             'This certifies that {{studentName}} ({{matricule}}) is enrolled in {{className}} for {{sessionLabel}}.'),
            (?,'GENERIC','fr','Document officiel','{{content}}'),
            (?,'GENERIC','en','Official document','{{content}}')
            ON CONFLICT DO NOTHING
            """, schoolId, schoolId, schoolId, schoolId);
    }

    /**
     * Catalogue des moyens de paiement du nouvel établissement. Les migrations
     * ne peuvent pas s'en charger : elles s'exécutent avant que cet amorçage ne
     * crée l'établissement. Sans cela, l'onglet « Moyens de paiement » resterait
     * vide et aucun encaissement ne serait possible.
     *
     * <p>Coordonnées et instructions définitives se saisissent ensuite depuis
     * Finance → Moyens de paiement → Coordonnées.
     */
    private void seedPaymentChannels(UUID schoolId) {
        insertChannel(schoolId, "CASH", "Espèces", "Cash", false, false, 1,
            "Versement au guichet de l'économat, contre reçu.",
            "Payment at the bursary desk, against a receipt.");
        insertChannel(schoolId, "OM", "Orange Money", "Orange Money", true, true, 2,
            "Composez #150*1# puis suivez « Transfert d'argent » vers le numéro de l'école. Conservez l'ID de transaction et communiquez-le à l'économat.",
            "Dial #150*1#, choose “Money transfer” to the school number. Keep the transaction ID and give it to the bursary.");
        insertChannel(schoolId, "MOMO", "MTN Mobile Money", "MTN Mobile Money", true, true, 3,
            "Composez *126# puis « Transfert » vers le numéro de l'école. Conservez l'ID de transaction et communiquez-le à l'économat.",
            "Dial *126#, choose “Transfer” to the school number. Keep the transaction ID and give it to the bursary.");
        insertChannel(schoolId, "MPGS", "Carte bancaire (MPGS)", "Bank card (MPGS)", true, true, 4,
            "Paiement par carte auprès de la banque partenaire. Présentez le numéro d'autorisation à l'économat.",
            "Card payment through the partner bank. Show the authorisation number to the bursary.");
        insertChannel(schoolId, "TRANSFER", "Virement bancaire", "Bank transfer", true, true, 5,
            "Virement sur le compte de l'établissement, en précisant le matricule de l'élève.",
            "Transfer to the school account, quoting the student ID.");
        insertChannel(schoolId, "SARA", "SARA", "SARA", true, true, 6,
            "Depuis votre compte SARA, effectuez le transfert vers le numéro de l'école. Conservez l'ID de transaction et communiquez-le à l'économat.",
            "From your SARA account, transfer to the school number. Keep the transaction ID and give it to the bursary.");
    }

    private void insertChannel(UUID schoolId, String code, String labelFr, String labelEn,
                               boolean requiresReference, boolean visibleToParents, int sortOrder,
                               String instructionsFr, String instructionsEn) {
        jdbc.update("INSERT INTO payment_channel (school_id, code, label_fr, label_en, requires_reference, "
                  + "visible_to_parents, sort_order, instructions_fr, instructions_en) "
                  + "VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (school_id, code) DO NOTHING",
            schoolId, code, labelFr, labelEn, requiresReference, visibleToParents, sortOrder,
            instructionsFr, instructionsEn);
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
