package com.bbc.sms.settings;

import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import com.bbc.sms.staff.dto.StaffDtos.AccountResult;
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

    /**
     * Réglages qui engagent tout l'établissement : identité de l'école, matrice
     * des rôles, SMTP, calendrier, catalogues de discipline.
     *
     * <p>Un administrateur de section a pourtant « Paramètres : Complet » — c'est
     * ainsi qu'il configure les classes et matières de son cycle. La matrice ne
     * sait pas distinguer ces deux usages du même module ; {@code schoolWide()}
     * si. Sans lui, l'admin de maternelle rebattrait les droits de toute l'école.
     */
    private static final String SCHOOL_WIDE = "@perm.can('settings','write') and @perm.schoolWide()";

    /** Lecture des mêmes réglages : inutile de montrer ce qu'on ne peut pas changer. */
    private static final String SCHOOL_WIDE_READ = "@perm.can('settings','read') and @perm.schoolWide()";

    private final PermissionAdminService service;
    private final MailAdminService mailAdmin;
    private final MailService mailService;
    private final SchoolProfileService schoolProfile;
    private final DisciplineCatalogService catalogs;
    private final com.bbc.sms.platform.security.PermissionService permissionService;
    private final AdminAccountService admins;

    public SettingsController(PermissionAdminService service, MailAdminService mailAdmin,
                              MailService mailService, SchoolProfileService schoolProfile,
                              DisciplineCatalogService catalogs,
                              com.bbc.sms.platform.security.PermissionService permissionService,
                              AdminAccountService admins) {
        this.service = service;
        this.mailAdmin = mailAdmin;
        this.mailService = mailService;
        this.schoolProfile = schoolProfile;
        this.catalogs = catalogs;
        this.permissionService = permissionService;
        this.admins = admins;
    }

    @GetMapping("/permission-actions")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public Map<String, Boolean> permissionActions() {
        return permissionService.currentActions();
    }

    // ---- School profile ------------------------------------------------------

    @GetMapping("/school")
    @PreAuthorize("@policy.canAction('SCHOOL_PROFILE_VIEW')")
    public SchoolProfileView getSchool() {
        return schoolProfile.get();
    }

    @PutMapping("/school")
    @PreAuthorize("@policy.canAction('SCHOOL_PROFILE_MANAGE') and @perm.schoolWide()")
    public SchoolProfileView updateSchool(@Valid @RequestBody SchoolProfileUpdate in) {
        return schoolProfile.update(in);
    }

    @GetMapping("/holidays")
    @PreAuthorize("@policy.canAction('CALENDAR_VIEW')")
    public List<HolidayView> holidays() {
        return schoolProfile.listHolidays();
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('CALENDAR_MANAGE') and @perm.schoolWide()")
    public HolidayView addHoliday(@Valid @RequestBody HolidayUpsert in) {
        return schoolProfile.addHoliday(in);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('CALENDAR_MANAGE') and @perm.schoolWide()")
    public void deleteHoliday(@PathVariable UUID id) {
        schoolProfile.deleteHoliday(id);
    }

    // ---- Roles & permissions -------------------------------------------------

    /** Role catalogue for staff assignment and settings — labels only, no matrix. */
    @GetMapping("/roles")
    @PreAuthorize("@policy.canAction('ROLE_VIEW')")
    public List<RoleView> listRoles() {
        return service.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW') and @perm.schoolWide()")
    public PermissionMatrix getMatrix() {
        return service.getMatrix();
    }

    @PutMapping("/permissions")
    @PreAuthorize("@policy.canAction('PERMISSION_MANAGE') and @perm.schoolWide()")
    public PermissionMatrix update(@Valid @RequestBody UpdateRequest req) {
        return service.update(req);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('ROLE_MANAGE') and @perm.schoolWide()")
    public RoleView createRole(@Valid @RequestBody RoleUpsert in) {
        return service.createRole(in);
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize("@policy.canAction('ROLE_MANAGE') and @perm.schoolWide()")
    public RoleView updateRole(@PathVariable String code, @Valid @RequestBody RoleUpsert in) {
        return service.updateRole(code, in);
    }

    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('ROLE_MANAGE') and @perm.schoolWide()")
    public void deleteRole(@PathVariable String code) {
        service.deleteRole(code);
    }

    // ---- Discipline catalogs -------------------------------------------------

    @GetMapping("/discipline-catalog")
    @PreAuthorize("@policy.canAction('DISCIPLINE_CATALOG_VIEW')")
    public List<CatalogItemView> catalog(@RequestParam(required = false) String kind) {
        return catalogs.list(kind);
    }

    @PostMapping("/discipline-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('DISCIPLINE_CATALOG_MANAGE') and @perm.schoolWide()")
    public CatalogItemView createCatalog(@Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.create(in);
    }

    @PutMapping("/discipline-catalog/{id}")
    @PreAuthorize("@policy.canAction('DISCIPLINE_CATALOG_MANAGE') and @perm.schoolWide()")
    public CatalogItemView updateCatalog(@PathVariable UUID id, @Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.update(id, in);
    }

    @DeleteMapping("/discipline-catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('DISCIPLINE_CATALOG_MANAGE') and @perm.schoolWide()")
    public void deleteCatalog(@PathVariable UUID id) {
        catalogs.delete(id);
    }

    // ---- SMTP / mail configuration -----------------------------------------

    @GetMapping("/mail")
    @PreAuthorize("@policy.canAction('MAIL_CONFIG_VIEW') and @perm.schoolWide()")
    public MailConfigView getMail() {
        return mailAdmin.get();
    }

    @PutMapping("/mail")
    @PreAuthorize("@policy.canAction('MAIL_CONFIG_MANAGE') and @perm.schoolWide()")
    public MailConfigView updateMail(@Valid @RequestBody MailConfigUpdate in) {
        return mailAdmin.update(in);
    }

    @PostMapping("/mail/test")
    @PreAuthorize("@policy.canAction('MAIL_CONFIG_MANAGE') and @perm.schoolWide()")
    public void testMail(@Valid @RequestBody TestMailRequest req) {
        mailService.sendTest(TenantContext.get(), req.to());
    }

    // ---- Administrateurs -----------------------------------------------------
    // Seul l'administrateur principal nomme ses relais de section : un admin de
    // section qui pourrait en créer un autre contournerait son propre verrou.

    @GetMapping("/admins")
    @PreAuthorize(SCHOOL_WIDE_READ)
    public List<AdminView> listAdmins() {
        return admins.list();
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SCHOOL_WIDE)
    public AccountResult createAdmin(@Valid @RequestBody AdminCreate in) {
        return admins.create(in);
    }

    @PutMapping("/admins/{userId}/section")
    @PreAuthorize(SCHOOL_WIDE)
    public AdminView changeAdminSection(@PathVariable UUID userId,
                                        @Valid @RequestBody AdminSectionChange in) {
        return admins.changeSection(userId, in.section());
    }

    @PutMapping("/admins/{userId}/active")
    @PreAuthorize(SCHOOL_WIDE)
    public AdminView setAdminActive(@PathVariable UUID userId,
                                    @Valid @RequestBody AdminActiveChange in) {
        return admins.setActive(userId, in.active());
    }

    @PostMapping("/admins/{userId}/credentials")
    @PreAuthorize(SCHOOL_WIDE)
    public AccountResult resetAdminCredentials(@PathVariable UUID userId) {
        return admins.resetCredentials(userId);
    }
}
