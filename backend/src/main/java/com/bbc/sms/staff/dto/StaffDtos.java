package com.bbc.sms.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;

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
            /** Section (cycle) de rattachement : maternelle|primary|secondary, null si non enseignant. */
            String section,
            /** Cycles explicitly managed when the employee is a principal. */
            Set<String> managementLevels,
            UUID departmentId,
            String departmentName,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            boolean active,
            boolean hasLogin,
            UUID accountUserId,
            String username,
            boolean canManage,
            boolean canViewDocuments,
            @JsonInclude(JsonInclude.Include.NON_NULL) AccountResult credentials) {
        public EmployeeView withCredentials(AccountResult result) {
            return new EmployeeView(id, code, name, initials, sex, type, email, phone,
                    formClass, section, managementLevels, departmentId, departmentName,
                    monthlySalary, hourlyRate, roles, active, hasLogin, accountUserId, username, canManage, canViewDocuments, result);
        }
    }

    public record EmployeeUpsert(
            @NotBlank String name,
            String sex,
            String type,
            @Size(max = 160, message = "Adresse e-mail : 160 caractères maximum")
            @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Adresse e-mail invalide") String email,
            @Pattern(regexp = "^$|^[+0-9][0-9\\s().-]{5,24}$", message = "Numéro de téléphone invalide") String phone,
            String formClass,
            String section,
            Set<String> managementLevels,
            UUID departmentId,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            Boolean createLogin,
            @Valid AccountOptions accountOptions) {}

    /** Une classe assignée à un enseignant, telle qu'affichée sur sa fiche. */
    public record TeacherClassView(
            UUID id,
            String name,
            String level,
            String subsystem,
            String sectionLabel,
            int studentCount) {}

    /** Metadata for a private personnel document; the file is streamed by its own endpoint. */
    public record StaffDocumentView(
            UUID id,
            UUID employeeId,
            String documentType,
            String label,
            String fileName,
            String contentType,
            long byteSize,
            String uploadedByName,
            java.time.Instant uploadedAt) {}

    /** Remplace la totalité des classes d'un enseignant (liste vide = plus aucune). */
    public record SetTeacherClasses(List<UUID> classIds) {}

    /** Email/phone are explicit login choices; omitted mode preserves legacy username callers. */
    public record AccountOptions(
            @Pattern(regexp = "^$|^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$",
                    message = "Identifiant : 3 à 64 lettres, chiffres, points, tirets ou underscores") String username,
            Boolean sendEmail,
            @Pattern(regexp = "^(email|phone|username)$", message = "Choisissez e-mail ou téléphone") String loginMethod) {
        public AccountOptions {
            username = username == null ? null : username.trim();
            loginMethod = loginMethod == null ? "username" : loginMethod.trim().toLowerCase(java.util.Locale.ROOT);
        }
        public AccountOptions(String username, Boolean sendEmail) { this(username, sendEmail, null); }
        public static AccountOptions manual() { return new AccountOptions(null, false); }
    }

    /** Fresh credentials are returned only by the protected create/reset operation, never a profile read. */
    public record AccountResult(
            boolean hasAccount,
            String username,
            boolean emailSent,
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL) String password,
            boolean emailRequested) {
        public AccountResult withoutPassword() {
            return new AccountResult(hasAccount, username, emailSent, message, null, emailRequested);
        }
        @Override public String toString() {
            return "AccountResult[hasAccount=" + hasAccount + ", username=" + username
                    + ", emailSent=" + emailSent + ", password=[REDACTED]]";
        }
    }

    /** One row of a bulk staff import (CSV / Excel parsed on the client). */
    public record StaffImportRow(
            String name,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            /** Section (cycle) : maternelle | primary | secondary. */
            String section,
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

    /** Les employés cochés dans l'annuaire, à retirer d'un seul geste. */
    public record BulkDeleteRequest(@NotEmpty List<UUID> ids) {}

    public record BulkDeleteError(UUID id, String message) {}

    /**
     * Ce qu'une suppression groupée a réellement fait. Les échecs sont rendus
     * fiche par fiche — une seule hors section ne doit pas laisser croire que
     * rien n'a été supprimé.
     */
    public record BulkDeleteResult(int deleted, int failed, List<BulkDeleteError> errors) {}

    // ---- Staff self-registration portal ------------------------------------

    public record StaffPortalMeta(
            String schoolName,
            String schoolCode,
            boolean open) {}

    public record StaffApplicationSubmit(
            @NotBlank String name,
            String sex,
            String type,
            @Size(max = 160, message = "Adresse e-mail : 160 caractères maximum")
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
            java.time.Instant finalizedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) AccountResult credentials) {
        public StaffApplicationView withCredentials(AccountResult result) {
            return new StaffApplicationView(id, status, name, sex, type, email, phone, formClass,
                    departmentHint, desiredRoles, notes, rejectReason, employeeId, employeeCode,
                    submittedAt, decidedAt, finalizedAt, result);
        }
    }

    public record StaffApplicationReject(@NotBlank String reason) {}

    public record StaffApplicationFinalize(
            String type,
            UUID departmentId,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            String formClass,
            String section,
            Set<String> managementLevels,
            Boolean createLogin,
            @Valid AccountOptions accountOptions) {}

    public record StaffPortalSettingsView(
            boolean enabled,
            String slug,
            String token,
            String publicPath) {}

    public record StaffPortalSettingsUpdate(boolean enabled) {}
}
