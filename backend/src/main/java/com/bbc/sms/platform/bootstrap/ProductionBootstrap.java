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

    /**
     * First-school setup authority for the one freshly bootstrapped account.
     * These are user exceptions rather than changes to the ordinary principal
     * template, so existing and subsequently-created principals remain on the
     * safe oversight profile.
     */
    private static final String[] BOOTSTRAP_SETUP_ACTIONS = {
        "SESSION_VIEW", "SESSION_MANAGE", "CALENDAR_MANAGE", "SCHOOL_PROFILE_MANAGE",
        "CLASS_MANAGE", "SUBJECT_MANAGE", "CURRICULUM_MANAGE",
        "CURRICULUM_CLASS_MANAGE", "CURRICULUM_CATALOG_MANAGE",
        "TEACHING_ASSIGNMENT_MANAGE", "TEACHING_CLASS_ASSIGNMENT_MANAGE",
        "MAIL_CONFIG_MANAGE", "DISCIPLINE_CATALOG_MANAGE", "ROLE_MANAGE",
        "ACADEMIC_ASSESSMENT_VIEW", "ACADEMIC_ASSESSMENT_MANAGE",
        "ATTENDANCE_ROSTER_VIEW", "ATTENDANCE_MARK", "ATTENDANCE_FINALIZE",
        "ATTENDANCE_REOPEN", "ATTENDANCE_ANALYTICS_VIEW", "ATTENDANCE_POLICY_MANAGE",
        "ATTENDANCE_RECONCILE", "ATTENDANCE_POLICY_VIEW", "ATTENDANCE_DEVICE_VIEW",
        "ATTENDANCE_DEVICE_MANAGE", "ATTENDANCE_NOTIFICATION_VIEW",
        "STUDENT_PROFILE_CREATE", "STUDENT_IMPORT"
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

        // Permission matrix. Principal starts with oversight/read access; the
        // policy workspace, rather than a module fallback, controls mutations.
        grants(schoolId, "principal", "read", "dashboard", "presence", "students", "hr", "academic",
            "finance", "timetable", "events", "discipline", "reports", "settings", "journey",
            "alerts", "messages", "coursebook", "health", "documents", "classkit");
        grants(schoolId, "prefect", "write", "presence", "timetable", "events", "discipline", "journey", "alerts", "messages", "documents");
        grants(schoolId, "prefect", "read", "dashboard", "students", "academic", "reports", "coursebook", "health", "classkit");
        grants(schoolId, "econome", "write", "finance");
        grants(schoolId, "econome", "read", "dashboard", "students", "reports", "alerts");
        grants(schoolId, "form_teacher", "write", "academic", "discipline", "coursebook", "messages", "classkit");
        grants(schoolId, "form_teacher", "read", "dashboard", "presence", "students", "timetable", "events", "journey", "alerts", "health", "documents");
        grants(schoolId, "teacher", "write", "academic", "coursebook");
        grants(schoolId, "teacher", "read", "dashboard", "presence", "students", "timetable", "events", "messages");
        grant(schoolId, "parent", "parent", "read");

        seedFoundation(schoolId, sessionId);
        seedPrincipalAcademicWorkflowLegacyAuthorities(schoolId);
        seedAttendanceDefaults(schoolId);

        seedPaymentChannels(schoolId);
        seedFinanceAccounting(schoolId, sessionId, startYear);

        UUID adminUserId = jdbc.queryForObject("""
            INSERT INTO app_user
                (school_id, username, password_hash, display_name, initials, role_code,
                 parcours_scope_mode)
            VALUES (?,?,?,?,?,'principal','GLOBAL')
            RETURNING id
            """, UUID.class, schoolId, adminUsername, encoder.encode(adminPassword),
            adminName, initialsOf(adminName));
        seedPermissionPolicyV2(schoolId, adminUserId);

        log.info("=================================================================");
        log.info(" Amorcage production OK — etablissement « {} » + admin « {} ».",
            schoolName, adminUsername);
        log.info(" Connectez-vous puis configurez tout depuis le module Parametres.");
        log.info("=================================================================");
    }

    /**
     * V118 runs before a fresh school exists, so its school-scoped backfill
     * cannot initialize this tenant.  Bootstrap the same least-privilege
     * policy explicitly and keep the initial administrator exception visible.
     */
    private void seedPermissionPolicyV2(UUID schoolId, UUID adminUserId) {
        jdbc.update("""
            INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,assigned_by,reason)
            VALUES (?,?, 'principal',true,?, 'Fresh-school policy bootstrap')
            ON CONFLICT DO NOTHING
            """, schoolId, adminUserId, adminUserId);
        jdbc.update("""
            INSERT INTO school_permission_version(school_id,version)
            VALUES (?,1) ON CONFLICT (school_id) DO NOTHING
            """, schoolId);
        jdbc.update("""
            INSERT INTO permission_policy_rollout
                (school_id,mode,compatibility_profile_code,enforcement_enabled,reviewed_by,reviewed_at)
            VALUES (?, 'SAFE_DEFAULT', NULL, true, ?, now())
            ON CONFLICT (school_id) DO UPDATE SET mode='SAFE_DEFAULT',
                compatibility_profile_code=NULL,enforcement_enabled=true,
                reviewed_by=EXCLUDED.reviewed_by,reviewed_at=EXCLUDED.reviewed_at,
                updated_at=now()
            """, schoolId, adminUserId);

        String[][] roleTemplates = {
            {"teacher", "primary_teacher"}, {"teacher", "secondary_teacher"},
            {"form_teacher", "form_teacher"}, {"principal", "principal_oversight"},
            {"econome", "finance_collector"}, {"accountant", "accountant"},
            {"parent", "parent_portal"}
        };
        for (String[] mapping : roleTemplates) {
            jdbc.update("""
                INSERT INTO permission_role_action
                    (school_id,role_code,action_code,effect,scope_mode,scope_payload,
                     effective_from,effective_to,is_permanent,reason)
                SELECT ?,?,action_code,effect,scope_mode,scope_payload,
                       effective_from,effective_to,is_permanent,reason
                  FROM permission_role_template_rule
                 WHERE template_code=?
                ON CONFLICT DO NOTHING
                """, schoolId, mapping[0], mapping[1]);
        }
        jdbc.update("""
            INSERT INTO permission_user_action
                (school_id,user_id,action_code,effect,scope_mode,is_permanent,reason)
            VALUES (?,?,'PERMISSION_MANAGE','ALLOW','SCHOOL_ALL',true,
                    'Initial emergency policy administrator; review and replace during access-control setup')
            ON CONFLICT DO NOTHING
            """, schoolId, adminUserId);
        for (String action : BOOTSTRAP_SETUP_ACTIONS) {
            jdbc.update("""
                INSERT INTO permission_user_action
                    (school_id,user_id,action_code,effect,scope_mode,is_permanent,reason)
                VALUES (?, ?, ?, 'ALLOW', 'SCHOOL_ALL', true,
                        'Fresh-school bootstrap setup authority; replace during access-control setup')
                ON CONFLICT DO NOTHING
                """, schoolId, adminUserId, action);
        }
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
     * The Direction workflow still passes through three legacy compatibility
     * gates while its resource scope is evaluated by Permission Policy V2.
     * Fresh-school bootstrap must seed the same narrow read/review authority
     * that V133 adds for an already-existing school.
     */
    private void seedPrincipalAcademicWorkflowLegacyAuthorities(UUID schoolId) {
        for (String action : new String[]{
                "ACADEMIC_GRADE_PACKET_REVIEW",
                "ACADEMIC_REPORT_CARD_VALIDATE",
                "ACADEMIC_REPORT_CARD_PUBLISH"}) {
            jdbc.update("""
                INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
                VALUES (?, 'principal', ?, true)
                ON CONFLICT (school_id,role_code,action_code)
                DO UPDATE SET allowed=EXCLUDED.allowed
                """, schoolId, action);
        }
    }

    /**
     * Attendance migrations run before the first school exists, so their
     * school-scoped seed rows cannot create defaults for a brand-new tenant.
     * Keep the same safe defaults in the first-run bootstrap as well.
     */
    private void seedAttendanceDefaults(UUID schoolId) {
        jdbc.update("""
            INSERT INTO attendance_policy
                (school_id, level, model, late_after_minutes,
                 chronic_absence_percent, require_absence_reason)
            VALUES
                (?, 'maternelle', 'DAILY', 15, 15.00, true),
                (?, 'primary',    'DAILY', 15, 15.00, true),
                (?, 'secondary',  'PERIOD', 10, 20.00, true)
            ON CONFLICT (school_id, level) DO NOTHING
            """, schoolId, schoolId, schoolId);

        String[] allAttendanceActions = {
            "ATTENDANCE_ROSTER_VIEW", "ATTENDANCE_MARK", "ATTENDANCE_FINALIZE",
            "ATTENDANCE_REOPEN", "ATTENDANCE_ANALYTICS_VIEW",
            "ATTENDANCE_POLICY_MANAGE", "ATTENDANCE_RECONCILE"
        };
        String[] scopedTeacherActions = {
            "ATTENDANCE_ROSTER_VIEW", "ATTENDANCE_MARK", "ATTENDANCE_FINALIZE",
            "ATTENDANCE_ANALYTICS_VIEW"
        };
        for (String action : allAttendanceActions) {
            for (String role : new String[]{"principal", "prefect"}) {
                grantAction(schoolId, role, action, true);
            }
        }
        for (String action : scopedTeacherActions) {
            for (String role : new String[]{"teacher", "form_teacher"}) {
                grantAction(schoolId, role, action, true);
            }
        }

        // Teachers need to read the active session so their academic and
        // attendance screens can validate dates; this does not grant session
        // administration or any class/subject data by itself.
        grantAction(schoolId, "teacher", "SESSION_VIEW", true);
        grantAction(schoolId, "form_teacher", "SESSION_VIEW", true);
    }

    private void grantAction(UUID schoolId, String role, String action, boolean allowed) {
        jdbc.update("""
            INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
            VALUES (?,?,?,?)
            ON CONFLICT (school_id, role_code, action_code)
            DO UPDATE SET allowed=EXCLUDED.allowed
            """, schoolId, role, action, allowed);
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

    /** Wave 1 accounting defaults for a school created after V59 has run. */
    private void seedFinanceAccounting(UUID schoolId, UUID sessionId, int startYear) {
        String[][] accountRows = {
            {"1000", "Caisse", "Cash on hand", "ASSET", "DEBIT"},
            {"1010", "Banque", "Bank", "ASSET", "DEBIT"},
            {"1020", "Compensation Orange Money", "Orange Money clearing", "ASSET", "DEBIT"},
            {"1030", "Compensation MoMo", "MoMo clearing", "ASSET", "DEBIT"},
            {"1040", "Compensation carte", "Card clearing", "ASSET", "DEBIT"},
            {"1100", "Créances élèves", "Accounts receivable - students", "ASSET", "DEBIT"},
            {"2100", "Crédits élèves", "Student credits", "LIABILITY", "CREDIT"},
            {"4000", "Produits de scolarité", "Tuition revenue", "REVENUE", "CREDIT"},
            {"4010", "Produits d'inscription", "Registration revenue", "REVENUE", "CREDIT"},
            {"4090", "Autres produits scolaires", "Other fee revenue", "REVENUE", "CREDIT"},
            {"2200", "Dettes de paie", "Payroll payable", "LIABILITY", "CREDIT"},
            {"6000", "Charges de personnel", "Salary expense", "EXPENSE", "DEBIT"},
            {"6900", "Compte de contrôle des dépenses", "Expense control", "EXPENSE", "DEBIT"},
            {"3000", "Fonds propres d'ouverture", "Opening balance equity", "EQUITY", "CREDIT"},
            {"3990", "Compte d'attente", "Suspense", "EQUITY", "CREDIT"}
        };
        for (String[] row : accountRows) {
            jdbc.update("""
                INSERT INTO chart_of_account
                    (school_id,code,name_fr,name_en,account_type,normal_side,currency,posting_allowed)
                VALUES (?,?,?,?,?,?, 'XAF', true)
                ON CONFLICT (school_id,code) DO NOTHING
                """, schoolId, row[0], row[1], row[2], row[3], row[4]);
        }

        LocalDate cursor = LocalDate.of(startYear, 9, 1);
        LocalDate end = LocalDate.of(startYear + 1, 7, 31);
        while (!cursor.isAfter(end)) {
            LocalDate periodEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            if (periodEnd.isAfter(end)) periodEnd = end;
            String code = cursor.toString().substring(0, 7);
            jdbc.update("""
                INSERT INTO accounting_period
                    (school_id,code,name_fr,name_en,start_date,end_date,academic_session_id,status)
                VALUES (?,?,?, ?,?,?,?,'OPEN') ON CONFLICT (school_id,code) DO NOTHING
                """, schoolId, code, "Période " + code, "Period " + code,
                    cursor, periodEnd, sessionId);
            jdbc.update("""
                INSERT INTO document_sequence(school_id,document_type,period_key,prefix,next_number,padding)
                VALUES (?, 'JOURNAL', ?, ?, 1, 6) ON CONFLICT DO NOTHING
                """, schoolId, code, "JRN/" + code + "/");
            cursor = cursor.plusMonths(1);
        }

        String[][] rules = {
            {"FEE_CHARGE", "DEBIT", "1100"}, {"FEE_CHARGE", "CREDIT", "4000"},
            {"PAYMENT_CASH", "DEBIT", "1000"}, {"PAYMENT_CASH", "CREDIT", "1100"},
            {"PAYMENT_OM", "DEBIT", "1020"}, {"PAYMENT_OM", "CREDIT", "1100"},
            {"PAYMENT_MOMO", "DEBIT", "1030"}, {"PAYMENT_MOMO", "CREDIT", "1100"},
            {"PAYMENT_CARD", "DEBIT", "1040"}, {"PAYMENT_CARD", "CREDIT", "1100"},
            {"PAYMENT_TRANSFER", "DEBIT", "1010"}, {"PAYMENT_TRANSFER", "CREDIT", "1100"},
            {"EXPENSE_POST", "DEBIT", "6900"}, {"EXPENSE_POST", "CREDIT", "1000"},
            {"PAYROLL_ACCRUAL", "DEBIT", "6000"}, {"PAYROLL_ACCRUAL", "CREDIT", "2200"},
            {"PAYROLL_PAYMENT", "DEBIT", "2200"}, {"PAYROLL_PAYMENT", "CREDIT", "1010"}
        };
        for (String[] rule : rules) {
            jdbc.update("""
                INSERT INTO posting_rule(school_id,event_type,side,target_account_id,priority,enabled)
                SELECT ?,?,?,id,0,true FROM chart_of_account
                 WHERE school_id=? AND code=?
                   AND NOT EXISTS (SELECT 1 FROM posting_rule p
                                    WHERE p.school_id=? AND p.event_type=? AND p.side=?
                                      AND p.priority=0 AND p.scope_code IS NULL
                                      AND p.fee_type_code IS NULL AND p.payment_channel_code IS NULL
                                      AND p.component_code IS NULL)
                """, schoolId, rule[0], rule[1], schoolId, rule[2], schoolId, rule[0], rule[1]);
        }

        jdbc.update("INSERT INTO permission_grant(school_id,role_code,module,level) VALUES (?, 'accountant','finance','write') ON CONFLICT DO NOTHING", schoolId);
        String[] actions = {
            "FINANCE_OVERVIEW_VIEW", "FEE_TYPE_MANAGE", "FEE_PLAN_DRAFT", "FEE_PLAN_ACTIVATE",
            "CHARGE_PREVIEW", "CHARGE_GENERATE", "CHARGE_ADJUST", "FEE_WAIVE_REQUEST", "FEE_WAIVE_APPROVE",
            "PAYMENT_COLLECT", "PAYMENT_REVERSE", "REFUND_REQUEST", "REFUND_APPROVE", "CASHIER_SESSION_CLOSE",
            "FINANCE_DOCUMENT_GENERATE", "FINANCE_DOCUMENT_VOID", "ACCOUNT_MANAGE", "POSTING_RULE_MANAGE",
            "LEDGER_POST", "LEDGER_REVERSE", "LEDGER_CLOSE", "LEDGER_REOPEN", "PAYROLL_CALCULATE",
            "PAYROLL_REVIEW", "PAYROLL_APPROVE", "PAYROLL_PAY", "PAYSLIP_VIEW_ALL", "FINANCE_REPORT_VIEW", "FINANCE_EXPORT"
        };
        for (String action : actions) {
            jdbc.update("""
                INSERT INTO permission_action_grant(school_id,role_code,action_code,allowed)
                SELECT ?, r.code, ?, CASE
                    WHEN r.code IN ('principal','accountant') THEN true
                    WHEN r.code='econome' AND ? IN ('FINANCE_OVERVIEW_VIEW','FINANCE_REPORT_VIEW','FINANCE_EXPORT') THEN true
                    ELSE false END
                  FROM role r WHERE r.code IN ('principal','econome','accountant')
                ON CONFLICT(school_id,role_code,action_code) DO UPDATE SET allowed=EXCLUDED.allowed
                """, schoolId, action, action);
        }
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
