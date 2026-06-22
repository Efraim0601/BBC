package com.bbc.sms.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class HealthDtos {

    /** A student's medical file (may be absent until first saved). */
    public record HealthRecordView(
            UUID id,
            UUID studentId,
            String bloodGroup,
            String allergies,
            String conditions,
            String vaccinations,
            String doctorName,
            String doctorPhone,
            Integer heightCm,
            Integer weightKg) {}

    /** Create/update the medical file (one per student). */
    public record HealthRecordUpsert(
            String bloodGroup,
            String allergies,
            String conditions,
            String vaccinations,
            String doctorName,
            String doctorPhone,
            Integer heightCm,
            Integer weightKg) {}

    /** One logged infirmary visit. */
    public record VisitView(
            UUID id,
            UUID studentId,
            LocalDate visitDate,
            String reason,
            String treatment) {}

    /** Add an infirmary visit. */
    public record VisitUpsert(
            @NotNull LocalDate visitDate,
            @NotBlank String reason,
            String treatment) {}

    /** One extracurricular activity. */
    public record ActivityView(
            UUID id,
            UUID studentId,
            String name,
            String category,
            String role,
            String season) {}

    /** Add an activity. {@code category} is club | sport | art | other. */
    public record ActivityUpsert(
            @NotBlank String name,
            @NotBlank String category,
            String role,
            String season) {}

    /**
     * Full santé & vie scolaire picture for one student: identity header,
     * the medical record (or null), the infirmary visits and the activities.
     */
    public record StudentHealth(
            UUID studentId,
            String studentName,
            String matricule,
            String className,
            HealthRecordView record,
            List<VisitView> visits,
            List<ActivityView> activities) {}
}
