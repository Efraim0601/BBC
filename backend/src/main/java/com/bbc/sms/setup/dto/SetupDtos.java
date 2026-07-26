package com.bbc.sms.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Academic Setup payloads — the relational backbone (sections → classes, subjects). */
public class SetupDtos {

    // ---- Sections -----------------------------------------------------------
    public record SectionView(String id, String label, String subsystem, String level, long classCount) {}

    public record SectionUpsert(
            @NotBlank @Size(max = 120, message = "Le libellé ne peut pas dépasser 120 caractères") String label,
            @NotBlank @Pattern(regexp = "FR|EN") String subsystem,
            @NotBlank @Pattern(regexp = "maternelle|primary|secondary") String level) {}

    // ---- Classes ------------------------------------------------------------
    public record ClassView(UUID id, String name, String sectionId, String sectionLabel,
                            String subsystem, String level, long studentCount, long teacherCount) {}

    public record ClassUpsert(
            @NotBlank @Size(max = 80, message = "Le nom de classe ne peut pas dépasser 80 caractères") String name,
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

    // ---- Per-class subject coefficients -------------------------------------

    /**
     * One coefficient line from the official tables. {@code klass} is a class name
     * or a grade label ("5e", "Form 2") — a grade applies to every class whose name
     * starts with it (so "5e" covers 5e A and 5e B). A missing subject is created.
     */
    public record CoefImportRow(
            String subsystem,      // FR | EN
            String code,           // subject code (matched case-insensitively)
            String label,          // subject name, used when the subject must be created
            String klass,          // class name or grade label
            Integer coef) {}

    public record CoefImportRequest(List<CoefImportRow> rows) {}

    public record CoefImportError(int row, String label, String message) {}

    /** Outcome of a coefficient import: cells written, subjects auto-created, skipped. */
    public record CoefImportResult(int applied, int subjectsCreated, int skipped, List<CoefImportError> errors) {}

    /** A single stored override, for the read-back matrix (Academic Setup display). */
    public record ClassCoefView(UUID classId, String className, String subsystem,
                                UUID subjectId, String subjectCode, int coef) {}
}
