package com.bbc.sms.journey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class JourneyDtos {

    /** A single year in the timeline. */
    public record JourneyView(
            UUID id,
            UUID studentId,
            String academicYear,
            String className,
            String level,
            String subsystem,
            String result,
            BigDecimal generalAverage,
            Integer rank,
            Integer classSize,
            String decision,
            String note,
            UUID sourceSessionId,
            UUID targetSessionId,
            UUID promotionBatchId,
            String recommendation,
            String finalDecision,
            String targetClassName,
            String overrideReason,
            UUID decisionBy,
            java.time.Instant decisionAt) {}

    /**
     * Full parcours for one student: identity header + ordered timeline.
     * {@code yearsCount} and {@code averageTrend} are computed for the UI.
     */
    public record StudentJourney(
            UUID studentId,
            String studentName,
            String matricule,
            String currentClass,
            int yearsCount,
            BigDecimal bestAverage,
            List<JourneyView> entries) {}

    /**
     * Create/update a year. {@code result} is one of:
     * in_progress | promoted | repeated | transferred_in | transferred_out | graduated | excluded.
     */
    public record JourneyUpsert(
            @NotNull UUID studentId,
            @NotBlank String academicYear,
            @NotBlank String className,
            String level,
            String subsystem,
            String result,
            BigDecimal generalAverage,
            Integer rank,
            Integer classSize,
            String decision,
            String note) {}
}
