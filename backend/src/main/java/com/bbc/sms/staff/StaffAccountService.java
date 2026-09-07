package com.bbc.sms.staff;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.staff.dto.StaffDtos.AccountResult;
import com.bbc.sms.staff.dto.StaffDtos.AccountOptions;
import com.bbc.sms.platform.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Provisions a staff login with optional email delivery. Only the protected
 * issuing operation receives the fresh plaintext password; stored accounts
 * contain its hash and profile reads never return credentials.
 */
@Service
public class StaffAccountService {

    /** Password alphabet without visually ambiguous glyphs (0/O, 1/l/I). */
    private static final String PW_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String PW_DIGITS = "23456789";
    private static final String PW_ALPHABET = PW_LETTERS + PW_DIGITS;
    private static final int PW_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_ROLE = "teacher";
    private static final List<String> ROLE_PRIORITY = List.of(
            "principal", "accountant", "econome", "prefect", "form_teacher",
            "secondary_teacher", "teacher");
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

    /** Existing email-only workflows (administrator settings and explicit bulk email import). */
    @Transactional
    public AccountResult provisionOrReset(Employee e) {
        AccountResult result = provisionOrReset(e, new AccountOptions(null, true));
        return result.withoutPassword();
    }

    @Transactional
    public AccountResult provisionOrReset(Employee e, AccountOptions requestedOptions) {
        if (!e.isActive()) {
            throw ApiException.badRequest("Réactivez la fiche de l'employé avant de créer ou réinitialiser son compte.");
        }
        AccountOptions options = requestedOptions == null ? AccountOptions.manual() : requestedOptions;
        boolean sendEmail = Boolean.TRUE.equals(options.sendEmail());
        String email = e.getEmail() == null ? "" : e.getEmail().trim();
        if (sendEmail && email.isEmpty()) {
            throw ApiException.badRequest("Renseignez un e-mail ou désactivez l'envoi par e-mail.");
        }
        String tempPassword = generatePassword();
        AppUser u = users.findByEmployeeId(e.getId()).orElse(null);
        String requestedUsername = options.username() == null ? ""
                : options.username().trim().toLowerCase(Locale.ROOT);
        if (!requestedUsername.isEmpty() && !requestedUsername.matches("^[a-z0-9][a-z0-9._-]{2,63}$")) {
            throw ApiException.badRequest("Identifiant : 3 à 64 lettres, chiffres, points, tirets ou underscores.");
        }
        if (u == null) {
            if (!requestedUsername.isEmpty()
                    && users.existsBySchoolIdAndUsernameIgnoreCase(e.getSchoolId(), requestedUsername)) {
                throw ApiException.badRequest("Cet identifiant est déjà utilisé. Choisissez-en un autre.");
            }
            u = new AppUser();
            u.setSchoolId(e.getSchoolId());
            u.setEmployeeId(e.getId());
            u.setUsername(requestedUsername.isEmpty() ? uniqueUsername(e) : requestedUsername);
        } else if (!requestedUsername.isEmpty() && !requestedUsername.equalsIgnoreCase(u.getUsername())) {
            throw ApiException.badRequest("Une réinitialisation conserve l'identifiant existant.");
        }
        // Keep the account in step with the current record and (re)activate it.
        u.setDisplayName(e.getName());
        u.setInitials(e.getInitials());
        u.setRoleCode(pickRole(e));
        u.setActive(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setPasswordHash(encoder.encode(tempPassword));
        u.setCredentialsVersion(u.getCredentialsVersion() + 1);
        synchronizeAccess(users.saveAndFlush(u), e);

        String username = u.getUsername();
        if (!sendEmail) {
            return new AccountResult(true, username, false,
                    "Compte prêt. Copiez les identifiants pour les transmettre à l'employé.",
                    tempPassword, false);
        }
        String schoolCode = schools.findById(e.getSchoolId()).map(s -> s.getCode()).orElse(null);
        boolean sent = mail.sendCredentials(e.getSchoolId(), e.getName(), email,
                username, tempPassword, schoolCode);
        String message = sent
                ? "Identifiants envoyés par e-mail à " + email + "."
                : "Compte prêt, mais l'e-mail n'a pas pu être envoyé. Vous pouvez transmettre les identifiants manuellement.";
        return new AccountResult(true, username, sent, message, tempPassword, true);
    }

    /** Keep an existing login aligned after HR changes without rotating its password. */
    @Transactional
    public void syncAccount(Employee employee) {
        AppUser user = users.findByEmployeeId(employee.getId()).orElse(null);
        if (user == null) return;
        if (user.isActive() != employee.isActive()
                || !pickRole(employee).equals(user.getRoleCode())) {
            user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        }
        user.setDisplayName(employee.getName());
        user.setInitials(employee.getInitials());
        user.setActive(employee.isActive());
        synchronizeAccess(users.saveAndFlush(user), employee);
    }

    /** Archive login access without changing assignments or historical HR data. */
    @Transactional
    public void deactivateAccount(Employee employee) {
        users.findByEmployeeId(employee.getId()).ifPresent(user -> {
            user.setActive(false);
            user.setCredentialsVersion(user.getCredentialsVersion() + 1);
            users.saveAndFlush(user);
        });
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
        if (Set.of("teacher", "secondary_teacher", "form_teacher").contains(role)) {
            return "ASSIGNMENT_DERIVED";
        }
        return "NONE";
    }

    /** Primary role of the employee, restricted to a role that actually exists. */
    private String pickRole(Employee e) {
        Set<String> roles = e.getRoles();
        if (roles != null && !roles.isEmpty()) {
            Set<String> valid = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
            for (String preferred : ROLE_PRIORITY) {
                if (!roles.contains(preferred) || !valid.contains(preferred)) continue;
                if ("teacher".equals(preferred) && "secondary".equalsIgnoreCase(e.getLevel())
                        && valid.contains("secondary_teacher")) return "secondary_teacher";
                return preferred;
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
        while (users.existsBySchoolIdAndUsernameIgnoreCase(e.getSchoolId(), candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }

    /** Lowercase, accent-free, dotted slug: "NGONO Jean Paul" -> "ngono.jean.paul". */
    private static String slug(String name) {
        if (name == null) return "";
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PW_LENGTH);
        sb.append(PW_LETTERS.charAt(RANDOM.nextInt(PW_LETTERS.length())));
        sb.append(PW_DIGITS.charAt(RANDOM.nextInt(PW_DIGITS.length())));
        for (int i = 2; i < PW_LENGTH; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char c = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, c);
        }
        return sb.toString();
    }
}
