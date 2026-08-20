package com.bbc.sms.academic.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AcademicAccessDtos {
    private AcademicAccessDtos() {}

    public record DelegationView(UUID id, UUID academicSessionId, UUID employeeId,
                                 String employeeName, String employeeCode,
                                 String accountUsername, String accountRole,
                                 boolean accountActive, UUID classId, String className,
                                 UUID subjectId, String subjectCode, String capabilityCode,
                                 LocalDate effectiveFrom, LocalDate effectiveTo,
                                 String status, String reason, UUID requestedBy,
                                 UUID approvedBy, OffsetDateTime approvedAt,
                                 UUID revokedBy, OffsetDateTime revokedAt,
                                 String revocationReason, String source, long version) {}

    public record DelegationRequest(@NotNull UUID academicSessionId, @NotNull UUID employeeId,
                                    @NotNull UUID classId, UUID subjectId,
                                    String subjectCode, @NotBlank String capabilityCode,
                                    @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
                                    @NotBlank String reason, String source, Long version) {}

    public record DelegationRevokeRequest(@NotBlank String reason, Long version) {}

    public record DelegationPreview(@NotNull UUID academicSessionId, @NotNull UUID employeeId,
                                    @NotNull UUID classId, UUID subjectId, String subjectCode,
                                    @NotBlank String capabilityCode, LocalDate effectiveFrom,
                                    LocalDate effectiveTo, String employeeName,
                                    String employeeCode, String accountUsername,
                                    List<String> capabilitiesGranted, List<String> warnings,
                                    List<String> blockers, String fingerprint) {}

    public record ReadinessIssue(String code, String severity, UUID academicSessionId,
                                 UUID classId, String className, UUID subjectId,
                                 String subjectCode, UUID employeeId, String employeeName,
                                 String employeeCode, String accountUsername,
                                 String messageFr, String messageEn, String repairTarget) {}

    public record ReadinessView(UUID academicSessionId, String sessionCode,
                                String sessionLabel, int issueCount,
                                int missingHomeroomCount, int missingResponsibleCount,
                                int ambiguousResponsibleCount, int duplicateNameCount,
                                int unlinkedTeacherCount, List<ReadinessIssue> issues) {}

    public record ScopeSubject(String code, String label, UUID classId, String className,
                               String level, String source, UUID assignmentId,
                               long assignmentVersion, Map<String, Boolean> capabilities) {}

    public record MyScopeView(UUID academicSessionId, UUID reportingPeriodId,
                              String periodCode, String periodLabel,
                              List<ScopeSubject> subjects,
                              List<ScopeSubject> classOverviews) {}
}
