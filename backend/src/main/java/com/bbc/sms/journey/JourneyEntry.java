package com.bbc.sms.journey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One academic year in a student's school history. The collection of a
 * student's entries forms their longitudinal "parcours" — class progression,
 * end-of-year averages/ranks and conseil de classe decisions across cycles.
 */
@Entity
@Table(name = "journey_entry")
@Getter
@Setter
public class JourneyEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "class_name", nullable = false)
    private String className;

    private String level;

    private String subsystem;

    @Column(nullable = false)
    private String result = "in_progress";

    @Column(name = "general_average")
    private BigDecimal generalAverage;

    private Integer rank;

    @Column(name = "class_size")
    private Integer classSize;

    @Column(columnDefinition = "text")
    private String decision;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "source_session_id") private UUID sourceSessionId;
    @Column(name = "target_session_id") private UUID targetSessionId;
    @Column(name = "promotion_batch_id") private UUID promotionBatchId;
    private String recommendation;
    @Column(name = "final_decision") private String finalDecision;
    @Column(name = "target_class_name") private String targetClassName;
    @Column(name = "override_reason") private String overrideReason;
    @Column(name = "decision_by") private UUID decisionBy;
    @Column(name = "decision_at") private Instant decisionAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "voided_by") private UUID voidedBy;
    @Column(name = "void_reason") private String voidReason;
}
