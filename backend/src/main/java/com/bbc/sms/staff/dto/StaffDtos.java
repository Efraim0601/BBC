package com.bbc.sms.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StaffDtos {

    public record EmployeeView(
            UUID id,
            String code,
            String name,
            String initials,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            UUID departmentId,
            String departmentName,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            boolean active,
            boolean hasLogin,
            String username) {}

    public record EmployeeUpsert(
            @NotBlank String name,
            String sex,
            String type,
            @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Adresse e-mail invalide") String email,
            @Pattern(regexp = "^$|^[+0-9][0-9\\s().-]{5,24}$", message = "Numéro de téléphone invalide") String phone,
            String formClass,
            UUID departmentId,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            // When true, the UI will follow up with reset-credentials to provision the
            // login and e-mail the password; create() then skips its courtesy notice so
            // the employee doesn't receive two e-mails.
            Boolean createLogin) {}

    /** Outcome of provisioning/resetting a staff login — never carries the password. */
    public record AccountResult(
            boolean hasAccount,
            String username,
            boolean emailSent,
            String message) {}

    /** One row of a bulk staff import (CSV / Excel parsed on the client). */
    public record StaffImportRow(
            String name,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            /** Department name — resolved case-insensitively when {@code departmentId} is null. */
            String department,
            UUID departmentId,
            Long monthlySalary,
            Integer hourlyRate,
            List<String> roles) {}

    public record StaffImportRequest(
            /** When true, provision a login for each row that has an e-mail. */
            Boolean createLogin,
            @NotEmpty List<StaffImportRow> rows) {}

    public record StaffImportError(int row, String name, String message) {}

    public record StaffImportResult(int created, int failed, List<StaffImportError> errors) {}

    // ---- Staff self-registration portal ------------------------------------

    public record StaffPortalMeta(
            String schoolName,
            String schoolCode,
            boolean open) {}

    public record StaffApplicationSubmit(
            @NotBlank String name,
            String sex,
            String type,
            @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Adresse e-mail invalide") String email,
            @Pattern(regexp = "^$|^[+0-9][0-9\\s().-]{5,24}$", message = "Numéro de téléphone invalide") String phone,
            String formClass,
            String departmentHint,
            String desiredRoles,
            String notes) {}

    public record StaffApplicationView(
            UUID id,
            String status,
            String name,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            String departmentHint,
            String desiredRoles,
            String notes,
            String rejectReason,
            UUID employeeId,
            String employeeCode,
            java.time.Instant submittedAt,
            java.time.Instant decidedAt,
            java.time.Instant finalizedAt) {}

    public record StaffApplicationReject(@NotBlank String reason) {}

    public record StaffApplicationFinalize(
            String type,
            UUID departmentId,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            String formClass,
            Boolean createLogin) {}

    public record StaffPortalSettingsView(
            boolean enabled,
            String slug,
            String token,
            String publicPath) {}

    public record StaffPortalSettingsUpdate(boolean enabled) {}
}
