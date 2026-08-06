package com.bbc.sms.guardian;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GuardianDtos {
    private GuardianDtos() {}

    public record GuardianSearchView(UUID id, String displayName, String maskedEmail,
        String maskedPhone, int linkedChildren, String accountStatus, boolean exactMatch) {}

    public record GuardianInput(UUID guardianId, @NotBlank String displayName, String email,
        String phone, @NotBlank String relationshipType, @NotBlank String accessMode,
        String initialPassword, Boolean legalGuardian, Boolean livesWith, Integer emergencyPriority,
        Boolean pickupAuthorized, Boolean financeResponsible, Boolean receivesAcademic,
        Boolean receivesAttendance, Boolean receivesFinance, Boolean receivesDiscipline,
        Boolean receivesHealth, Boolean portalAccess, String notes) {}

    public record RelationshipUpsert(@NotBlank String relationshipType, Boolean legalGuardian,
        Boolean livesWith, Integer emergencyPriority, Boolean pickupAuthorized,
        Boolean financeResponsible, Boolean receivesAcademic, Boolean receivesAttendance,
        Boolean receivesFinance, Boolean receivesDiscipline, Boolean receivesHealth,
        Boolean portalAccess, LocalDate effectiveFrom, LocalDate effectiveTo, String notes, Long version) {}

    public record GuardianRelationshipView(UUID relationshipId, UUID guardianId, String displayName,
        String email, String phone, String relationshipType, boolean legalGuardian, boolean livesWith,
        Integer emergencyPriority, boolean pickupAuthorized, boolean financeResponsible,
        boolean receivesAcademic, boolean receivesAttendance, boolean receivesFinance,
        boolean receivesDiscipline, boolean receivesHealth, boolean portalAccess,
        LocalDate effectiveFrom, LocalDate effectiveTo, String accountStatus,
        String invitationStatus, long version) {}

    public record MergeRequest(@NotNull UUID targetGuardianId, @NotBlank String reason) {}
    public record LifecycleRequest(@NotBlank String reason) {}
    public record InviteResult(UUID guardianId, String status, String destination, String expiresAt) {}

    public record AcceptInviteRequest(@NotBlank String token, @Size(min=8,max=100) String password) {}
    public record ForgotParentPasswordRequest(@NotBlank String schoolCode, @Email @NotBlank String email) {}
    public record ResetParentPasswordRequest(@NotBlank String token, @Size(min=8,max=100) String password) {}
    public record PublicMessage(String message) {}

    public record FamilyImportGuardian(String displayName, String email, String phone,
        String relationshipType, String accessMode) {}
    public record FamilyImportRow(@NotBlank String externalKey, @NotBlank String firstName,
        @NotBlank String lastName, String niu, String sex, LocalDate dob, String birthplace,
        Boolean repeats, UUID classId,
        List<FamilyImportGuardian> guardians) {}
    public record FamilyImportRequest(String sourceName, @NotEmpty List<FamilyImportRow> rows) {}
    public record FamilyImportRowView(int rowNumber, String externalKey, String studentName,
        String outcome, String message) {}
    public record FamilyImportView(UUID jobId, String status, int totalRows, int validRows,
        int createdRows, int linkedGuardians, int failedRows, List<FamilyImportRowView> rows) {}
}
