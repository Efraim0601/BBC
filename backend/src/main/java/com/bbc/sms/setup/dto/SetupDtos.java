package com.bbc.sms.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    public record TeacherOption(UUID id, String name, String code, String section,
                                String accountUsername, String accountRole, boolean accountActive) {
        public TeacherOption(UUID id, String name, String code, String section) {
            this(id, name, code, section, null, null, false);
        }
    }

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
                                        boolean active, long version, String accountUsername,
                                        String accountRole, boolean accountActive) {
        public CurriculumTeacherView(UUID id, UUID employeeId, String employeeName,
                                     String employeeCode, String role, String source,
                                     boolean active, long version) {
            this(id, employeeId, employeeName, employeeCode, role, source, active, version,
                    null, null, false);
        }
    }

    public record CurriculumSubjectView(UUID id, UUID subjectId, String subjectCode,
                                        String subjectLabel, UUID groupId, String groupCode,
                                        int displayOrder, int coefficient, BigDecimal maxScore,
                                        boolean mandatory, BigDecimal passThreshold,
                                        boolean showSubjectRank, boolean remarkRequired,
                                        CurriculumTeacherView responsibleTeacher, long version,
                                        LocalDate activeFrom, LocalDate activeTo) {
        public CurriculumSubjectView(UUID id, UUID subjectId, String subjectCode,
                                     String subjectLabel, UUID groupId, String groupCode,
                                     int displayOrder, int coefficient, BigDecimal maxScore,
                                     boolean mandatory, BigDecimal passThreshold,
                                     boolean showSubjectRank, boolean remarkRequired,
                                     CurriculumTeacherView responsibleTeacher, long version) {
            this(id, subjectId, subjectCode, subjectLabel, groupId, groupCode, displayOrder,
                    coefficient, maxScore, mandatory, passThreshold, showSubjectRank,
                    remarkRequired, responsibleTeacher, version, null, null);
        }
    }

    public record CurriculumView(UUID academicSessionId, String sessionCode, String sessionLabel,
                                 UUID classId, String className, List<SubjectGroupView> groups,
                                 List<CurriculumSubjectView> subjects,
                                 CurriculumTeacherView homeroomTeacher) {
        public CurriculumView(UUID academicSessionId, String sessionCode, String sessionLabel,
                              UUID classId, String className, List<SubjectGroupView> groups,
                              List<CurriculumSubjectView> subjects) {
            this(academicSessionId, sessionCode, sessionLabel, classId, className, groups, subjects, null);
        }
    }

    public record CurriculumSubjectUpsert(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                          @NotNull UUID subjectId, UUID groupId,
                                          Integer displayOrder, @Positive Integer coefficient,
                                          BigDecimal maxScore, Boolean mandatory,
                                          BigDecimal passThreshold, Boolean showSubjectRank,
                                          Boolean remarkRequired, Long version,
                                          LocalDate activeFrom, LocalDate activeTo) {
        public CurriculumSubjectUpsert(UUID academicSessionId, UUID classId, UUID subjectId,
                                       UUID groupId, Integer displayOrder, Integer coefficient,
                                       BigDecimal maxScore, Boolean mandatory,
                                       BigDecimal passThreshold, Boolean showSubjectRank,
                                       Boolean remarkRequired, Long version) {
            this(academicSessionId, classId, subjectId, groupId, displayOrder, coefficient,
                    maxScore, mandatory, passThreshold, showSubjectRank, remarkRequired,
                    version, null, null);
        }
    }

    public record CurriculumCopyEdit(@NotBlank String key, @NotBlank String field, String value) {}

    public record CurriculumCopyPreviewRequest(@NotNull UUID sourceSessionId,
                                                UUID targetSessionId, List<UUID> classIds,
                                                Boolean allMatchingClasses,
                                                Boolean includeGroups, Boolean includeTeachers,
                                                String mergeMode, List<String> selectedKeys,
                                                List<CurriculumCopyEdit> edits) {
        public CurriculumCopyPreviewRequest(UUID sourceSessionId, UUID targetSessionId, List<UUID> classIds,
                                            Boolean allMatchingClasses, Boolean includeGroups, Boolean includeTeachers,
                                            String mergeMode, List<String> selectedKeys) {
            this(sourceSessionId, targetSessionId, classIds, allMatchingClasses, includeGroups, includeTeachers,
                    mergeMode, selectedKeys, List.of());
        }
    }

    public record CurriculumCopyRow(String key, UUID classId, String className,
                                     UUID subjectId, String subjectCode, String subjectLabel,
                                     String status, Map<String, Object> source,
                                     Map<String, Object> proposed, Map<String, Object> existing,
                                     String teacherStatus, String teacherMessage,
                                     List<String> warnings, List<String> blockers) {}

    public record CurriculumCopyPreview(UUID sourceSessionId, UUID targetSessionId,
                                        int classCount, List<SubjectGroupView> groups,
                                        List<CurriculumCopyRow> rows,
                                        List<String> warnings, List<String> blockers,
                                        String fingerprint, int createCount,
                                        int updateCount, int keepCount) {}

    public record CurriculumCopyApplyRequest(@NotNull UUID sourceSessionId,
                                             UUID targetSessionId, List<UUID> classIds,
                                             Boolean allMatchingClasses,
                                             Boolean includeGroups, Boolean includeTeachers,
                                             String mergeMode, List<String> selectedKeys,
                                             List<CurriculumCopyEdit> edits,
                                             @NotBlank String reason,
                                             @NotBlank String previewFingerprint) {
        public CurriculumCopyApplyRequest(UUID sourceSessionId, UUID targetSessionId, List<UUID> classIds,
                                          Boolean allMatchingClasses, Boolean includeGroups, Boolean includeTeachers,
                                          String mergeMode, List<String> selectedKeys, String reason,
                                          String previewFingerprint) {
            this(sourceSessionId, targetSessionId, classIds, allMatchingClasses, includeGroups, includeTeachers,
                    mergeMode, selectedKeys, List.of(), reason, previewFingerprint);
        }
    }

    public record CurriculumTeacherUpsert(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                          @NotNull UUID subjectId, @NotNull UUID employeeId,
                                          @NotBlank String role, String source,
                                          java.time.LocalDate effectiveFrom,
                                          java.time.LocalDate effectiveTo, Long version) {}

    public record HomeroomAssignmentUpsert(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                           @NotNull UUID employeeId, java.time.LocalDate effectiveFrom,
                                           java.time.LocalDate effectiveTo, Long version) {}

    /** Read-only consequence report shown before a dated assignment is changed. */
    public record AssignmentImpactRequest(@NotNull UUID academicSessionId, @NotNull UUID classId,
                                          UUID subjectId, @NotNull UUID employeeId, @NotBlank String role,
                                          java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo) {}

    public record AssignmentImpactSlotView(UUID versionId, int versionNo, String versionStatus,
                                           UUID slotId, String subjectCode, int dayIdx, int slotIdx,
                                           UUID publishedTeacherId, String publishedTeacherName,
                                           boolean teacherChanges) {}

    public record AssignmentImpactView(UUID academicSessionId, UUID classId, UUID subjectId,
                                       String role, UUID proposedTeacherId, java.time.LocalDate effectiveFrom,
                                       java.time.LocalDate effectiveTo, int draftSlotCount,
                                       int publishedSlotCount, boolean publishedScheduleDrift,
                                       boolean requiresNewDraftVersion, List<AssignmentImpactSlotView> affectedPublishedSlots,
                                       List<String> warnings, List<String> blockers) {}
}
