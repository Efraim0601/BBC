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
    private final AdminAccountService admins;

    public SettingsController(PermissionAdminService service, MailAdminService mailAdmin,
                              MailService mailService, SchoolProfileService schoolProfile,
                              DisciplineCatalogService catalogs, AdminAccountService admins) {
        this.service = service;
        this.mailAdmin = mailAdmin;
        this.mailService = mailService;
        this.schoolProfile = schoolProfile;
        this.catalogs = catalogs;
        this.admins = admins;
    }

    // ---- School profile ------------------------------------------------------

    @GetMapping("/school")
    public SchoolProfileView getSchool() {
        return schoolProfile.get();
    }

    @PutMapping("/school")
    @PreAuthorize(SCHOOL_WIDE)
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
    @PreAuthorize(SCHOOL_WIDE)
    public HolidayView addHoliday(@Valid @RequestBody HolidayUpsert in) {
        return schoolProfile.addHoliday(in);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SCHOOL_WIDE)
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
    @PreAuthorize(SCHOOL_WIDE_READ)
    public PermissionMatrix getMatrix() {
        return service.getMatrix();
    }

    @PutMapping("/permissions")
    @PreAuthorize(SCHOOL_WIDE)
    public PermissionMatrix update(@Valid @RequestBody UpdateRequest req) {
        return service.update(req);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SCHOOL_WIDE)
    public RoleView createRole(@Valid @RequestBody RoleUpsert in) {
        return service.createRole(in);
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize(SCHOOL_WIDE)
    public RoleView updateRole(@PathVariable String code, @Valid @RequestBody RoleUpsert in) {
        return service.updateRole(code, in);
    }

    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SCHOOL_WIDE)
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
    @PreAuthorize(SCHOOL_WIDE)
    public CatalogItemView createCatalog(@Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.create(in);
    }

    @PutMapping("/discipline-catalog/{id}")
    @PreAuthorize(SCHOOL_WIDE)
    public CatalogItemView updateCatalog(@PathVariable UUID id, @Valid @RequestBody CatalogItemUpsert in) {
        return catalogs.update(id, in);
    }

    @DeleteMapping("/discipline-catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SCHOOL_WIDE)
    public void deleteCatalog(@PathVariable UUID id) {
        catalogs.delete(id);
    }

    // ---- SMTP / mail configuration -----------------------------------------

    @GetMapping("/mail")
    @PreAuthorize(SCHOOL_WIDE_READ)
    public MailConfigView getMail() {
        return mailAdmin.get();
    }

    @PutMapping("/mail")
    @PreAuthorize(SCHOOL_WIDE)
    public MailConfigView updateMail(@Valid @RequestBody MailConfigUpdate in) {
        return mailAdmin.update(in);
    }

    @PostMapping("/mail/test")
    @PreAuthorize(SCHOOL_WIDE)
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
