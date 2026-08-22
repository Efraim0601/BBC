package com.bbc.sms.foundation.cohort;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CohortDtos {
    private CohortDtos() {}

    public record ProgrammeView(UUID id, UUID classId, String className, String subsystem,
                                String level, boolean reportCardEnabled, boolean active) {}

    public record ClassOption(UUID id, String name, String level, String subsystem,
                              String sectionLabel) {}

    public record CohortView(UUID id, UUID academicSessionId, String sessionLabel,
                             String code, String displayName, String level, String mode,
                             String attendanceMode, String status, int studentCount,
                             List<ProgrammeView> programmes, long version) {}

    public record CohortUpsert(
            @NotNull UUID academicSessionId,
            @NotBlank String code,
            @NotBlank String displayName,
            @NotBlank String level,
            @NotBlank String mode,
            @NotNull UUID francophoneClassId,
            UUID anglophoneClassId,
            String attendanceMode) {}

    public record PathwayTargetView(UUID cohortId, String displayName, String level,
                                    String mode, String programmeLabel, String subsystem) {}

    public record PathwayStudentView(UUID studentId, String matricule, String studentName,
                                     UUID currentCohortId, String currentCohortName,
                                     UUID selectedTargetCohortId, String selectedTargetCohortName,
                                     String status, long version) {}

    public record PathwayPreview(UUID sourceSessionId, String sourceSessionLabel,
                                 UUID targetSessionId, String targetSessionLabel,
                                 UUID sourceCohortId, String sourceCohortName,
                                 List<PathwayTargetView> targets,
                                 List<PathwayStudentView> students) {}

    public record PathwayChoice(UUID studentId, @NotNull UUID targetCohortId,
                                String reason) {}

    public record PathwayApply(@NotNull UUID sourceSessionId, @NotNull UUID targetSessionId,
                               @NotNull UUID sourceCohortId, List<PathwayChoice> choices,
                               boolean confirm) {}

    public record PathwayApplyResult(int saved, int confirmed, int plannedEnrollments,
                                     List<String> warnings, Instant appliedAt) {}
}
