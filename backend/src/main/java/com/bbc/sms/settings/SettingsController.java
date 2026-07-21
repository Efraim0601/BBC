package com.bbc.sms.settings;

import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final PermissionAdminService service;
    private final MailAdminService mailAdmin;
    private final MailService mailService;
    private final SchoolProfileService schoolProfile;

    public SettingsController(PermissionAdminService service, MailAdminService mailAdmin,
                              MailService mailService, SchoolProfileService schoolProfile) {
        this.service = service;
        this.mailAdmin = mailAdmin;
        this.mailService = mailService;
        this.schoolProfile = schoolProfile;
    }

    // ---- School profile ------------------------------------------------------

    /**
     * Readable by any authenticated user: bulletins, receipts and the parent portal
     * print the school's identity, and those users have no settings permission.
     */
    @GetMapping("/school")
    public SchoolProfileView getSchool() {
        return schoolProfile.get();
    }

    @PutMapping("/school")
    @PreAuthorize("@perm.can('settings','write')")
    public SchoolProfileView updateSchool(@Valid @RequestBody SchoolProfileUpdate in) {
        return schoolProfile.update(in);
    }

    @GetMapping("/permissions")
    @PreAuthorize("@perm.can('settings','read')")
    public PermissionMatrix getMatrix() {
        return service.getMatrix();
    }

    @PutMapping("/permissions")
    @PreAuthorize("@perm.can('settings','write')")
    public PermissionMatrix update(@Valid @RequestBody UpdateRequest req) {
        return service.update(req);
    }

    // ---- SMTP / mail configuration -----------------------------------------

    @GetMapping("/mail")
    @PreAuthorize("@perm.can('settings','read')")
    public MailConfigView getMail() {
        return mailAdmin.get();
    }

    @PutMapping("/mail")
    @PreAuthorize("@perm.can('settings','write')")
    public MailConfigView updateMail(@Valid @RequestBody MailConfigUpdate in) {
        return mailAdmin.update(in);
    }

    @PostMapping("/mail/test")
    @PreAuthorize("@perm.can('settings','write')")
    public void testMail(@Valid @RequestBody TestMailRequest req) {
        mailService.sendTest(TenantContext.get(), req.to());
    }
}
