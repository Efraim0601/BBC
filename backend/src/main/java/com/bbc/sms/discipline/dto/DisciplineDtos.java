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
     * <p>{@code studentRef} accepts either the student's UUID or matricule
     * (e.g. {@code BBC-1001}) — resolved server-side so the UI can type a badge ID.</p>
     *
     * <p>{@code type} is a free string but is expected to be one of:
     * Retard | Absence | Conduite | Tenue.</p>
     *
     * <p>{@code sanction} is a free string; graduated sanctions (from least to most severe):
     * Avertissement verbal | Avertissement écrit | Convocation parent |
     * Exclusion temporaire | Conseil de discipline.</p>
     */
    public record IncidentUpsert(
            @NotBlank String studentRef,
            @NotNull LocalDate incidentDate,
            @NotBlank String type,
            String description,
            String sanction) {}

    /** Lightweight student card for the incident form (auto-filled from matricule). */
    public record StudentLookup(
            UUID id,
            String matricule,
            String name,
            String className,
            String parentName,
            String parentPhone) {}

    public record NotifyRequest(
            @NotBlank String studentRef,
            @NotBlank String channel,   // sms | email
            @NotBlank String message) {}

    /**
     * Outcome of a parent notification. SMS/email delivery is simulated until a
     * provider is wired — {@code delivered} is true when a parent contact exists.
     */
    public record NotifyResult(
            UUID studentId,
            String channel,
            boolean delivered,
            String recipient,
            String message) {}
}
