package com.bbc.sms.promotion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Passage de classe : configuration (progression + règles), simulation, application. */
public class PromotionDtos {

    // ---- 1. Progression : quelle classe vient après --------------------------

    public record ProgressionView(UUID classId, String className, String sectionId, String sectionLabel,
                                  String subsystem, String level, int gradeOrder,
                                  UUID nextClassId, String nextClassName, boolean terminal,
                                  long studentCount) {}

    public record ProgressionLine(@NotNull UUID classId, int gradeOrder, UUID nextClassId, boolean terminal) {}

    public record ProgressionUpdate(@NotNull List<ProgressionLine> lines) {}

    // ---- 2. Règles de passage ------------------------------------------------

    /** @param scopeLabel libellé lisible du périmètre (« Toute l'école », « 3e », « Secondaire FR »). */
    public record RuleView(UUID id, String level, String subsystem, UUID classId, String className,
                           BigDecimal passMark, BigDecimal councilMargin, Integer maxRepeats,
                           String scopeLabel, int specificity) {}

    public record RuleUpsert(
            UUID id,
            String level,
            String subsystem,
            UUID classId,
            @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal passMark,
            @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal councilMargin,
            @Min(0) Integer maxRepeats) {}

    /** Tout ce dont l'écran de configuration a besoin en une requête. */
    public record PromotionConfig(String currentYear, String nextYear,
                                  List<ProgressionView> progression, List<RuleView> rules) {}

    // ---- 3. Simulation -------------------------------------------------------

    /**
     * Un élève dans la simulation.
     *
     * @param proposedResult promoted | repeated | graduated | review | undecided
     * @param appliedResult  décision déjà appliquée pour cette année, null sinon
     */
    public record CandidateView(UUID studentId, String matricule, String studentName, int photoHue,
                                BigDecimal annualAverage, Integer rank, Integer classSize,
                                int sequencesCounted, int priorRepeats,
                                String proposedResult, String proposalReason,
                                UUID proposedClassId, String proposedClassName,
                                String appliedResult) {}

    public record PromotionPreview(UUID classId, String className, String level, String subsystem,
                                   String academicYear, String nextAcademicYear,
                                   UUID nextClassId, String nextClassName, boolean terminal,
                                   BigDecimal passMark, BigDecimal councilMargin, Integer maxRepeats,
                                   String ruleScope, int total, int graded,
                                   List<CandidateView> candidates, List<String> warnings) {}

    // ---- 4. Application ------------------------------------------------------

    /**
     * @param result promoted | repeated | graduated | transferred_out | excluded
     * @param toClassId classe d'accueil ; null = celle du mapping de progression
     * @param reason motif — OBLIGATOIRE dès que la décision s'écarte de la proposition
     */
    public record PromotionLine(@NotNull UUID studentId, @NotBlank String result,
                                UUID toClassId, String reason) {}

    public record PromotionApply(@NotNull UUID classId,
                                 @NotBlank String academicYear,
                                 @NotBlank String nextAcademicYear,
                                 @NotEmpty List<PromotionLine> lines) {}

    public record PromotionResult(UUID batchId, int applied, int promoted, int repeated,
                                  int graduated, int other, int overridden, List<String> warnings) {}

    // ---- 5. Historique -------------------------------------------------------

    public record BatchView(UUID id, String academicYear, String nextAcademicYear, String className,
                            int studentsTotal, int promotedCount, int repeatedCount,
                            int graduatedCount, int otherCount, int overriddenCount,
                            String appliedBy, Instant appliedAt) {}

    // ---- 6. Clôture de l'année ------------------------------------------------

    /** Une classe où il reste des élèves sans décision de fin d'année. */
    public record PendingClass(String className, int students) {}

    /**
     * État des lieux avant clôture : ce qui sera archivé, et ce qui manque encore.
     *
     * @param closedAt non null quand l'année a déjà été clôturée (seconde clôture refusée)
     */
    public record ClosurePreview(String academicYear, String nextAcademicYear, Instant closedAt,
                                 int activeStudents, int studentsDecided, int studentsPending,
                                 List<PendingClass> pendingClasses,
                                 int gradesToArchive, int validationsToArchive,
                                 int feesToArchive, int feesToCreate,
                                 List<String> warnings) {}

    /**
     * @param ignorePending clôturer malgré des élèves sans décision — case à cocher explicite
     */
    public record ClosureRequest(@NotBlank String academicYear,
                                 @NotBlank String nextAcademicYear,
                                 boolean archiveGrades,
                                 boolean resetFees,
                                 boolean makeCurrent,
                                 boolean ignorePending) {}

    public record ClosureResult(UUID id, String academicYear, String nextAcademicYear,
                                int gradesArchived, int validationsArchived,
                                int feesArchived, int feesCreated, boolean madeCurrent,
                                List<String> warnings) {}

    public record ClosureView(UUID id, String academicYear, String nextAcademicYear,
                              int gradesArchived, int validationsArchived,
                              int feesArchived, int feesCreated,
                              int studentsActive, int studentsPending,
                              boolean madeCurrent, String closedBy, Instant closedAt) {}
}
