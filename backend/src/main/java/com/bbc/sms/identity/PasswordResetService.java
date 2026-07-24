package com.bbc.sms.identity;

import com.bbc.sms.identity.dto.AuthDtos.ForgotPasswordRequest;
import com.bbc.sms.identity.dto.AuthDtos.ForgotPasswordResponse;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * Self-service password reset from the login screen. Looks up the account by
 * username (+ optional school code), regenerates a temporary password when an
 * employee e-mail is on file, and sends it via the tenant SMTP config.
 * Responses are deliberately generic so usernames cannot be enumerated.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final String PW_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PW_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String GENERIC_OK =
            "Si un compte correspondant existe et dispose d'une adresse e-mail, "
                    + "un nouveau mot de passe temporaire y a été envoyé. "
                    + "Sinon, contactez l'administrateur de l'établissement.";

    private final AppUserRepository users;
    private final SchoolRepository schools;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final MailService mail;

    public PasswordResetService(AppUserRepository users, SchoolRepository schools,
                                EmployeeRepository employees, PasswordEncoder encoder,
                                MailService mail) {
        this.users = users;
        this.schools = schools;
        this.employees = employees;
        this.encoder = encoder;
        this.mail = mail;
    }

    @Transactional
    public ForgotPasswordResponse requestReset(ForgotPasswordRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        if (username.isEmpty()) {
            return new ForgotPasswordResponse(true, GENERIC_OK);
        }

        AppUser user = resolveUserQuietly(username, req.schoolCode());
        if (user == null) {
            return new ForgotPasswordResponse(true, GENERIC_OK);
        }

        String email = resolveEmail(user);
        if (email == null || email.isBlank()) {
            log.info("Forgot-password: no e-mail on file for user {}", user.getUsername());
            return new ForgotPasswordResponse(true, GENERIC_OK);
        }

        String tempPassword = generatePassword();
        user.setPasswordHash(encoder.encode(tempPassword));
        users.save(user);

        String schoolCode = schools.findById(user.getSchoolId()).map(School::getCode).orElse(null);
        boolean sent = mail.sendPasswordReset(user.getSchoolId(), user.getDisplayName(), email,
                user.getUsername(), tempPassword, schoolCode);
        if (!sent) {
            log.warn("Forgot-password: SMTP failed or disabled for user {} (school {})",
                    user.getUsername(), user.getSchoolId());
        }
        return new ForgotPasswordResponse(true, GENERIC_OK);
    }

    private AppUser resolveUserQuietly(String username, String schoolCode) {
        try {
            if (schoolCode != null && !schoolCode.isBlank()) {
                return schools.findByCode(schoolCode.trim())
                        .flatMap(s -> users.findBySchoolIdAndUsernameAndActiveTrue(s.getId(), username))
                        .orElse(null);
            }
            List<AppUser> matches = users.findByUsernameAndActiveTrue(username);
            if (matches.size() == 1) return matches.get(0);
            // Ambiguous or missing — do not reset (admin must use school code / staff reset).
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveEmail(AppUser user) {
        if (user.getEmployeeId() == null) return null;
        return employees.findById(user.getEmployeeId())
                .map(Employee::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(PW_LENGTH);
        for (int i = 0; i < PW_LENGTH; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        return sb.toString();
    }
}
