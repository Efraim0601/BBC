package com.bbc.sms.staff;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.staff.dto.StaffDtos.AccountResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns an {@link Employee} into a login-capable {@code app_user} and mails the
 * (single-use) temporary password to the employee. The plaintext password is
 * never returned to the caller/UI — it only ever leaves through the e-mail — so
 * the admin's recovery path when SMTP is down is simply to "reset" again once
 * mail is configured. Creating and resetting share the same flow: reset just
 * regenerates the password of the account already linked to the employee.
 */
@Service
public class StaffAccountService {

    /** Password alphabet without visually ambiguous glyphs (0/O, 1/l/I). */
    private static final String PW_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PW_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_ROLE = "teacher";
    private static final List<String> ROLE_PRIORITY = List.of(
            "principal", "accountant", "econome", "prefect", "form_teacher", "teacher");
    private static final Set<String> GLOBAL_ROLES = Set.of(
            "administrator", "admin", "school_admin", "prefect", "accountant", "econome",
            "bursar", "cashier", "finance_officer");

    private final AppUserRepository users;
    private final SchoolRepository schools;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final JdbcTemplate jdbc;

    public StaffAccountService(AppUserRepository users, SchoolRepository schools,
                               PasswordEncoder encoder, MailService mail, JdbcTemplate jdbc) {
        this.users = users;
        this.schools = schools;
        this.encoder = encoder;
        this.mail = mail;
        this.jdbc = jdbc;
    }

    /**
     * Create the employee's login account (or reset its password if it already
     * exists), then e-mail the fresh credentials. Idempotent enough to double as
     * the admin "reset" action.
     */
    @Transactional
    public AccountResult provisionOrReset(Employee e) {
        String tempPassword = generatePassword();
        AppUser u = users.findByEmployeeId(e.getId()).orElse(null);
        if (u == null) {
            u = new AppUser();
            u.setSchoolId(e.getSchoolId());
            u.setEmployeeId(e.getId());
            u.setUsername(uniqueUsername(e));
        }
        // Keep the account in step with the current record and (re)activate it.
        u.setDisplayName(e.getName());
        u.setInitials(e.getInitials());
        u.setRoleCode(pickRole(e));
        u.setActive(true);
        u.setPasswordHash(encoder.encode(tempPassword));
        synchronizeAccess(users.saveAndFlush(u), e);

        String username = u.getUsername();
        String email = e.getEmail();
        if (email == null || email.isBlank()) {
            return new AccountResult(true, username, false,
                    "Compte créé (identifiant « " + username + " »), mais aucun e-mail n'est "
                    + "renseigné : ajoutez une adresse puis réinitialisez pour transmettre le mot de passe.");
        }
        String schoolCode = schools.findById(e.getSchoolId()).map(s -> s.getCode()).orElse(null);
        boolean sent = mail.sendCredentials(e.getSchoolId(), e.getName(), email,
                username, tempPassword, schoolCode);
        String message = sent
                ? "Identifiants envoyés par e-mail à " + email + "."
                : "Compte prêt (identifiant « " + username + " »), mais l'e-mail n'a pas pu être "
                  + "envoyé. Configurez le SMTP dans Paramètres, puis réinitialisez les identifiants.";
        return new AccountResult(true, username, sent, message);
    }

    /** Keep an existing login aligned after HR changes without rotating its password. */
    @Transactional
    public void syncAccount(Employee employee) {
        AppUser user = users.findByEmployeeId(employee.getId()).orElse(null);
        if (user == null) return;
        user.setDisplayName(employee.getName());
        user.setInitials(employee.getInitials());
        user.setActive(employee.isActive());
        synchronizeAccess(users.saveAndFlush(user), employee);
    }

    private void synchronizeAccess(AppUser user, Employee employee) {
        String primaryRole = pickRole(employee);
        user.setRoleCode(primaryRole);
        users.saveAndFlush(user);

        jdbc.update("DELETE FROM app_user_role WHERE school_id=? AND user_id=? "
                        + "AND (is_primary=true OR role_code='principal_legacy_compat' OR role_code=?)",
                user.getSchoolId(), user.getId(), primaryRole);
        jdbc.update("""
                INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason)
                VALUES (?,?,?,true,'Synchronized from staff record')
                """, user.getSchoolId(), user.getId(), primaryRole);

        String mode = scopeMode(primaryRole);
        jdbc.update("UPDATE app_user SET parcours_scope_mode=? WHERE id=? AND school_id=?",
                mode, user.getId(), user.getSchoolId());
        jdbc.update("DELETE FROM app_user_parcours WHERE user_id=?", user.getId());
        if ("EXPLICIT".equals(mode)) {
            Set<String> levels = employee.getManagementLevels() == null
                    ? Set.of() : employee.getManagementLevels();
            for (String level : levels) {
                for (String subsystem : List.of("FR", "EN")) {
                    jdbc.update("""
                            INSERT INTO app_user_parcours(user_id,level,subsystem)
                            VALUES (?,?,?) ON CONFLICT DO NOTHING
                            """, user.getId(), level, subsystem);
                }
            }
        }
    }

    private String scopeMode(String role) {
        if ("principal".equals(role)) return "EXPLICIT";
        if (GLOBAL_ROLES.contains(role)) return "GLOBAL";
        if (Set.of("teacher", "form_teacher").contains(role)) return "ASSIGNMENT_DERIVED";
        return "NONE";
    }

    /** Primary role of the employee, restricted to a role that actually exists. */
    private String pickRole(Employee e) {
        Set<String> roles = e.getRoles();
        if (roles != null && !roles.isEmpty()) {
            Set<String> valid = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
            for (String preferred : ROLE_PRIORITY) {
                if (roles.contains(preferred) && valid.contains(preferred)) return preferred;
            }
            return roles.stream().filter(valid::contains).sorted().findFirst().orElse(DEFAULT_ROLE);
        }
        return DEFAULT_ROLE;
    }

    /** Build a readable, school-unique username from the employee name (fallback: code). */
    private String uniqueUsername(Employee e) {
        String base = slug(e.getName());
        if (base.isBlank()) base = slug(e.getCode());
        if (base.isBlank()) base = "user";
        if (base.length() > 56) base = base.substring(0, 56);
        String candidate = base;
        int n = 2;
        while (users.existsBySchoolIdAndUsername(e.getSchoolId(), candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }

    /** Lowercase, accent-free, dotted slug: "NGONO Jean Paul" -> "ngono.jean.paul". */
    private static String slug(String name) {
        if (name == null) return "";
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PW_LENGTH);
        for (int i = 0; i < PW_LENGTH; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        return sb.toString();
    }
}
