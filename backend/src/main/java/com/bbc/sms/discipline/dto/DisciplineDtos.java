package com.bbc.sms.discipline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public class DisciplineDtos {

    public record IncidentView(
            UUID id,
            UUID studentId,
            String studentName,
            String className,
            LocalDate incidentDate,
            String type,
            String description,
            String sanction) {}

    /**
     * Incident creation payload.
     *
     * <p>{@code type} is a free string but is expected to be one of:
     * Retard | Absence | Conduite | Tenue.</p>
     *
     * <p>{@code sanction} is a free string; graduated sanctions (from least to most severe):
     * Avertissement verbal | Avertissement écrit | Convocation parent |
     * Exclusion temporaire | Conseil de discipline.</p>
     */
    public record IncidentUpsert(
            @NotNull UUID studentId,
            @NotNull LocalDate incidentDate,
            @NotBlank String type,
            String description,
            String sanction) {}
}
