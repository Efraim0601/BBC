package com.bbc.sms.academic.secondary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SecondaryCompetencyDtos {
    private SecondaryCompetencyDtos() {}

    public record CompetencyView(UUID id, UUID modelId, String code, String description,
                                 BigDecimal maxScore, int displayOrder, boolean active) {}

    public record MarkView(UUID id, UUID modelId, UUID competencyId, UUID reportingPeriodId,
                           UUID studentId, UUID enrollmentId, UUID teacherId,
                           BigDecimal mark, String valueStatus, long version) {}

    public record ModelView(UUID id, UUID academicSessionId, UUID reportingPeriodId,
                            UUID classId, UUID subjectId, String locale, String name,
                            int version, String status, String source,
                            List<CompetencyView> competencies) {}

    public record ModelRequest(@NotNull UUID academicSessionId, @NotNull UUID reportingPeriodId,
                               @NotNull UUID classId, @NotNull UUID subjectId,
                               @NotBlank String locale, @NotBlank String name,
                               @Valid @NotNull List<CompetencyInput> competencies) {}

    public record CompetencyInput(@NotBlank String code, @NotBlank String description,
                                  @NotNull @DecimalMin("0.01") @DecimalMax("1000") BigDecimal maxScore,
                                  int displayOrder) {}

    public record MarkRequest(@NotNull UUID modelId, @NotNull UUID competencyId,
                              @NotNull UUID reportingPeriodId, @NotNull UUID studentId,
                              UUID enrollmentId, UUID teacherId, BigDecimal mark,
                              String valueStatus, Long version) {}

    /** Spreadsheet/manual import uses the same model and is intentionally not the only workflow. */
    public record ImportRequest(@NotNull UUID modelId, @NotNull UUID reportingPeriodId,
                                @NotNull List<ImportRow> rows) {}

    public record ImportRow(@NotNull UUID studentId, UUID enrollmentId, UUID teacherId,
                            @NotBlank String competencyCode, BigDecimal mark,
                            String valueStatus) {}
}
