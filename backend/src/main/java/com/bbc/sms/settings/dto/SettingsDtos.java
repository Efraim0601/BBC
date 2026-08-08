package com.bbc.sms.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SettingsDtos {

    public record RoleView(String code, String labelFr, String labelEn, boolean builtin) {}

    public record RoleUpsert(
            String code,
            @NotBlank String labelFr,
            String labelEn) {}

    // ---- School profile -----------------------------------------------------

    public record SchoolProfileView(
            String code,
            String name,
            String motto,
            String city,
            String country,
            String address,
            String phone,
            String email,
            String website,
            String currency,
            String authority,
            String academicYear,
            String schoolStartTime,
            String schoolEndTime) {}

    public record SchoolProfileUpdate(
            @NotBlank String name,
            String motto,
            String city,
            String country,
            String address,
            String phone,
            String email,
            String website,
            String currency,
            String authority,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Heure invalide (HH:mm)")
            String schoolStartTime,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Heure invalide (HH:mm)")
            String schoolEndTime) {}

    public record HolidayView(UUID id, LocalDate date, String label) {}

    public record HolidayUpsert(
            @NotNull LocalDate date,
            @NotBlank String label) {}

    /** Full permission matrix for the Settings editor (§13.1). */
    public record PermissionMatrix(
            List<String> modules,
            List<RoleView> roles,
            Map<String, Map<String, String>> matrix) {}

    public record PermissionUpdate(
            @NotBlank String roleCode,
            @NotBlank String module,
            @NotBlank String level) {}

    public record UpdateRequest(@NotNull List<PermissionUpdate> updates) {}

    // ---- SMTP / mail configuration (§ admin) --------------------------------
    public record MailConfigView(
            boolean enabled,
            String host,
            int port,
            String username,
            boolean passwordSet,
            String fromAddress,
            String fromName,
            boolean useTls,
            boolean notifyOnUserCreate) {}

    public record MailConfigUpdate(
            boolean enabled,
            String host,
            Integer port,
            String username,
            String password,
            String fromAddress,
            String fromName,
            Boolean useTls,
            Boolean notifyOnUserCreate) {}

    public record TestMailRequest(@NotBlank String to) {}

    // ---- Discipline catalogs ------------------------------------------------
    public record CatalogItemView(
            UUID id,
            String kind,
            String code,
            String labelFr,
            String labelEn,
            int sortOrder,
            boolean active) {}

    public record CatalogItemUpsert(
            @NotBlank @Pattern(regexp = "type|sanction") String kind,
            String code,
            @NotBlank String labelFr,
            String labelEn,
            Integer sortOrder,
            Boolean active) {}

    // ---- Administrateurs ----------------------------------------------------

    /**
     * Un compte d'administration. {@code section} est null pour l'administrateur
     * principal : il répond de l'établissement entier, non d'un cycle.
     */
    public record AdminView(
            UUID userId,
            String username,
            String displayName,
            String roleCode,
            String section,
            String email,
            boolean active,
            UUID employeeId) {}

    public record AdminCreate(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "maternelle|primary|secondary",
                    message = "Section attendue : maternelle, primary ou secondary")
            String section,
            String email,
            String phone) {}

    public record AdminSectionChange(
            @NotBlank @Pattern(regexp = "maternelle|primary|secondary") String section) {}

    public record AdminActiveChange(@NotNull Boolean active) {}
}
