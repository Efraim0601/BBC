package com.bbc.sms.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Academic Setup payloads — the relational backbone (sections → classes, subjects). */
public class SetupDtos {

    // ---- Sections -----------------------------------------------------------
    public record SectionView(String id, String label, String subsystem, String level, long classCount) {}

    public record SectionUpsert(
            @NotBlank String label,
            @NotBlank @Pattern(regexp = "FR|EN") String subsystem,
            @NotBlank @Pattern(regexp = "maternelle|primary|secondary") String level) {}

    // ---- Classes ------------------------------------------------------------
    public record ClassView(UUID id, String name, String sectionId, String sectionLabel,
                            String subsystem, String level, long studentCount, long teacherCount) {}

    public record ClassUpsert(
            @NotBlank String name,
            @NotBlank String sectionId) {}

    // ---- Class ↔ teachers (N:N, 0..N teachers per class) --------------------
    public record TeacherOption(UUID id, String name, String code) {}

    public record SetClassTeachers(List<UUID> employeeIds) {}

    // ---- Subjects -----------------------------------------------------------
    public record SubjectView(UUID id, String code, String subsystem, Map<String, String> label, int coef) {}

    public record SubjectUpsert(
            @NotBlank String code,
            @Pattern(regexp = "FR|EN") String subsystem,
            Map<String, String> label,
            @PositiveOrZero int coef) {}
}
