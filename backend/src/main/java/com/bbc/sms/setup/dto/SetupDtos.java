package com.bbc.sms.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
    /** @param section cycle de rattachement (maternelle|primary|secondary), null si non défini. */
    public record TeacherOption(UUID id, String name, String code, String section) {}

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
                                UUID subjectId, String subjectCode, int coef, int defaultCoef) {}

    /** Directly create or update one class + subject teaching combination. */
    public record ClassCoefUpsert(@NotNull UUID classId, @NotNull UUID subjectId,
                                  @Positive int coef) {}

    // ---- Session-versioned curriculum --------------------------------------

    public record SubjectGroupView(UUID id, String code, Map<String, String> label,
                                   int displayOrder, boolean showSubtotal, boolean showRank,
                                   String averagePolicy, long version) {}

    public record SubjectGroupUpsert(@NotNull UUID academicSessionId, @NotBlank String code,
                                     Map<String, String> label, @Positive int displayOrder,
                                     Boolean showSubtotal, Boolean showRank, String averagePolicy,
                                     Long version) {}

    public record CurriculumTeacherView(UUID id, UUID employeeId, String employeeName,
                                        String employeeCode, String role, String source,
                                        boolean active, long version) {}

    public record CurriculumSubjectView(UUID id, UUID subjectId, String subjectCode,
                                        String subjectLabel, UUID groupId, String groupCode,
                                        int displayOrder, int coefficient, BigDecimal maxScore,
                                        boolean mandatory, BigDecimal passThreshold,
                                        boolean showSubjectRank, boolean remarkRequired,
                                        CurriculumTeacherView responsibleTeacher, long version) {}

    public record CurriculumView(UUID academicSessionId, String sessionCode, String sessionLabel,
                                 UUID classId, String className, List<SubjectGroupView> groups,
                                 List<CurriculumSubjectView> subjects) {}

    public record CurriculumSubjectUpsert(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                          @NotNull UUID subjectId, UUID groupId,
                                          Integer displayOrder, @Positive Integer coefficient,
                                          BigDecimal maxScore, Boolean mandatory,
                                          BigDecimal passThreshold, Boolean showSubjectRank,
                                          Boolean remarkRequired, Long version) {}

    public record CurriculumTeacherUpsert(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                          @NotNull UUID subjectId, @NotNull UUID employeeId,
                                          @NotBlank String role, String source,
                                          java.time.LocalDate effectiveFrom,
                                          java.time.LocalDate effectiveTo, Long version) {}
}
