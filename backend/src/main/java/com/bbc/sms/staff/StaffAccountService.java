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
            u.setRoleCode(pickRole(e));
        }
        // Keep the account in step with the current record and (re)activate it.
        u.setDisplayName(e.getName());
        u.setInitials(e.getInitials());
        u.setActive(true);
        u.setPasswordHash(encoder.encode(tempPassword));
        users.save(u);

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

    /** Primary role of the employee, restricted to a role that actually exists. */
    private String pickRole(Employee e) {
        Set<String> roles = e.getRoles();
        if (roles != null && !roles.isEmpty()) {
            Set<String> valid = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
            for (String r : roles) {
                if (r != null && valid.contains(r)) return r;
            }
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
