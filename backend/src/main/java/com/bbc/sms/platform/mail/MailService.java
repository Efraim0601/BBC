package com.bbc.sms.platform.mail;

import com.bbc.sms.platform.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.UUID;

/**
 * Sends e-mail using the SMTP settings stored per tenant in {@code mail_config}
 * (configured from the admin Settings UI). A {@link JavaMailSenderImpl} is built
 * on demand from the current configuration, so changes take effect immediately.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailConfigRepository repo;

    public MailService(MailConfigRepository repo) {
        this.repo = repo;
    }

    /** Fire-and-forget notification when an account/employee is created. */
    @Async
    public void notifyUserCreated(UUID schoolId, String displayName, String toEmail) {
        if (toEmail == null || toEmail.isBlank()) return;          // nobody to notify
        MailConfig c = repo.findById(schoolId).orElse(null);
        if (c == null || !c.isEnabled() || !c.isNotifyOnUserCreate()) return;
        String subject = "BBC SMS — votre compte a été créé";
        String body = "Bonjour " + (displayName == null ? "" : displayName) + ",\n\n"
                + "Un compte vient d'être créé pour vous dans BBC SMS.\n"
                + "Rapprochez-vous de l'administration pour obtenir vos identifiants de connexion.\n\n"
                + "— BBC SMS";
        try {
            send(c, toEmail, subject, body);
            log.info("Notification de création envoyée à {}", toEmail);
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification à {} : {}", toEmail, e.getMessage());
        }
    }

    /** Synchronous test send — surfaces SMTP errors to the caller (admin UI). */
    public void sendTest(UUID schoolId, String to) {
        MailConfig c = repo.findById(schoolId)
                .orElseThrow(() -> ApiException.badRequest("SMTP non configuré."));
        if (c.getHost() == null || c.getHost().isBlank()) {
            throw ApiException.badRequest("Hôte SMTP manquant.");
        }
        try {
            send(c, to, "BBC SMS — e-mail de test",
                "Ceci est un e-mail de test depuis BBC SMS. Votre configuration SMTP fonctionne.");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Envoi échoué : " + e.getMessage());
        }
    }

    private void send(MailConfig c, String to, String subject, String text) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getHost());
        sender.setPort(c.getPort());
        boolean auth = c.getUsername() != null && !c.getUsername().isBlank();
        if (auth) sender.setUsername(c.getUsername());
        if (c.getPassword() != null && !c.getPassword().isBlank()) sender.setPassword(c.getPassword());

        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(auth));
        p.put("mail.smtp.starttls.enable", String.valueOf(c.isUseTls()));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromHeader(c));
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        sender.send(msg);
    }

    private String fromHeader(MailConfig c) {
        String addr = (c.getFromAddress() != null && !c.getFromAddress().isBlank())
                ? c.getFromAddress() : c.getUsername();
        return (c.getFromName() != null && !c.getFromName().isBlank())
                ? c.getFromName() + " <" + addr + ">" : addr;
    }
}
