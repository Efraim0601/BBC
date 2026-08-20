package com.bbc.sms.settings.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the staged, reviewable Access Control workspace. */
public final class AccessControlDtos {
    private AccessControlDtos() {}

    public record ActionView(String code, String module, String groupCode,
                             String labelFr, String labelEn,
                             String descriptionFr, String descriptionEn,
                             String riskLevel, String scopeType, String requiredLevel,
                             boolean defaultReadAction, int displayOrder) {}

    public record ActionGroupView(String code, String labelFr, String labelEn,
                                  List<ActionView> actions) {}

    public record RuleView(UUID id, String subjectType, String subjectCode,
                           String actionCode, String effect, String scopeMode,
                           JsonNode scopePayload, LocalDate effectiveFrom,
                           LocalDate effectiveTo, boolean permanent, String reason,
                           long version) {}

    public record RoleWorkspace(String roleCode, String labelFr, String labelEn,
                                boolean builtin, long policyVersion,
                                List<ActionGroupView> groups, List<RuleView> rules) {}

    public record RuleInput(
            @NotBlank String actionCode,
            @NotBlank String effect,
            @NotBlank String scopeMode,
            JsonNode scopePayload,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean permanent,
            @NotBlank String reason) {}

    public record RoleMutation(
            @NotNull Long expectedPolicyVersion,
            @NotBlank String reason,
            @NotNull List<@Valid RuleInput> rules,
            boolean confirmHighRisk,
            boolean separationOfDutiesOverride,
            String separationOfDutiesReason) {}

    public record UserMutation(
            @NotNull Long expectedPolicyVersion,
            @NotBlank String reason,
            @NotNull List<@Valid RuleInput> rules,
            boolean confirmHighRisk,
            boolean separationOfDutiesOverride,
            String separationOfDutiesReason) {}

    public record PreviewChange(String actionCode, String beforeEffect, String afterEffect,
                                String beforeScopeMode, String afterScopeMode,
                                String riskLevel, String changeType) {}

    public record RiskWarning(String code, String severity, String messageFr, String messageEn) {}

    public record PolicyPreview(String subjectType, String subjectCode,
                                long currentPolicyVersion, List<PreviewChange> changes,
                                List<RiskWarning> warnings, boolean requiresConfirmation,
                                List<UserSelection> affectedUsers,
                                List<RuleView> preservedUserExceptions) {}

    public record TemplateView(String code, String labelFr, String labelEn,
                               String descriptionFr, String descriptionEn,
                               String baseRoleCode, List<RuleView> rules) {}

    public record UserSelection(UUID id, String username, String displayName,
                                String roleCode, boolean active, List<String> roles) {}

    public record UserWorkspace(UserSelection user, long policyVersion,
                                List<RuleView> overrides,
                                List<EffectiveActionView> effectiveActions) {}

    public record RoleAssignmentInput(@NotBlank String roleCode, boolean primary,
                                      LocalDate effectiveFrom, LocalDate effectiveTo,
                                      @NotBlank String reason) {}

    public record RoleAssignmentMutation(@NotNull Long expectedPolicyVersion,
                                         @NotBlank String reason,
                                         @NotNull List<@Valid RoleAssignmentInput> assignments,
                                         boolean confirmHighRisk) {}

    public record EffectiveActionView(String actionCode, String labelFr, String labelEn,
                                      String effect, String scopeMode, String source,
                                      boolean requiresContext, String riskLevel) {}

    public record CapabilityView(long policyVersion, String parcoursScopeMode,
                                 List<String> allowedParcours,
                                 List<EffectiveActionView> actions) {}

    /** Resource context sent to the contextual decision endpoint. */
    public record ContextDecisionRequest(
            @NotBlank String actionCode,
            UUID academicSessionId,
            LocalDate effectiveDate,
            String parcours,
            UUID classId,
            String subjectCode,
            UUID studentId,
            UUID timetableOccurrenceId,
            UUID documentId,
            String periodKey,
            String level) {}

    public record AuditView(UUID id, UUID actorUserId, String targetRoleCode,
                            UUID targetUserId, String mutationType, String reason,
                            String correlationId, java.time.OffsetDateTime occurredAt) {}
}
