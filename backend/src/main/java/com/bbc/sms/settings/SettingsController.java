package com.bbc.sms.settings;

import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final PermissionAdminService service;
    private final MailAdminService mailAdmin;
    private final MailService mailService;
    private final SchoolProfileService schoolProfile;
    private final DisciplineCatalogService catalogs;
    private final com.bbc.sms.platform.security.PermissionService permissionService;

    public SettingsController(PermissionAdminService service, MailAdminService mailAdmin,
                              MailService mailService, SchoolProfileService schoolProfile,
                              DisciplineCatalogService catalogs,
                              com.bbc.sms.platform.security.PermissionService permissionService) {
        this.service = service;
        this.mailAdmin = mailAdmin;
        this.mailService = mailService;
        this.schoolProfile = schoolProfile;
        this.catalogs = catalogs;
        this.permissionService = permissionService;
    }

    @GetMapping("/permission-actions")
    public Map<String, Boolean> permissionActions() {
        return permissionService.currentActions();
    }

    // ---- School profile ------------------------------------------------------

    @GetMapping("/school")
    public SchoolProfileView getSchool() {
        return schoolProfile.get();
    }

    @PutMapping("/school")
    @PreAuthorize("@perm.can('settings','write')")
    public SchoolProfileView updateSchool(@Valid @RequestBody SchoolProfileUpdate in) {
        return schoolProfile.update(in);
    }

    @GetMapping("/holidays")
    @PreAuthorize("@perm.can('settings','read') or @perm.can('presence','read')")
    public List<HolidayView> holidays() {
        return schoolProfile.listHolidays();
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('settings','write')")
    public HolidayView addHoliday(@Valid @RequestBody HolidayUpsert in) {
        return schoolProfile.addHoliday(in);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('settings','write')")
    public void deleteHoliday(@PathVariable UUID id) {
        schoolProfile.deleteHoliday(id);
    }

    // ---- Roles & permissions -------------------------------------------------

    /** Role catalogue for staff assignment and settings — labels only, no matrix. */
    @GetMapping("/roles")
    @PreAuthorize("@perm.can('settings','read') or @perm.can('hr','read')")
    public List<RoleView> listRoles() {
        return service.listRoles();
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

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('settings','write')")
    public RoleView createRole(@Valid @RequestBody RoleUpsert in) {
        return service.createRole(in);
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize("@perm.can('settings','write')")
    public RoleView updateRole(@PathVariable String code, @Valid @RequestBody RoleUpsert in) {
        return service.updateRole(code, in);
    }

    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('settings','write')")
    public void deleteRole(@PathVariable String code) {
        service.deleteRole(code);
    }

    // ---- Discipline catalogs -------------------------------------------------

    @GetMapping("/discipline-catalog")
    @PreAuthorize("@perm.can('settings','read') or @perm.can('discipline','read')")
    public List<CatalogItemView> catalog(@RequestParam(required = false) String kind) {
        return catalogs.list(kind);
    }

    @PostMapping("/discipline-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('settings','write')")
    public CatalogItemView createCatalog(@Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.create(in);
    }

    @PutMapping("/discipline-catalog/{id}")
    @PreAuthorize("@perm.can('settings','write')")
    public CatalogItemView updateCatalog(@PathVariable UUID id, @Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.update(id, in);
    }

    @DeleteMapping("/discipline-catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('settings','write')")
    public void deleteCatalog(@PathVariable UUID id) {
        catalogs.delete(id);
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
