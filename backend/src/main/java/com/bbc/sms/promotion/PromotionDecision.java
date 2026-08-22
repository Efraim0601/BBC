package com.bbc.sms.promotion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * La décision de fin d'année d'un élève, telle qu'appliquée.
 *
 * <p>On conserve côte à côte {@code proposedResult} (ce que la règle calculait) et
 * {@code finalResult} (ce que l'administration a retenu) : c'est ce qui rend
 * l'arbitrage manuel auditable, avec son motif obligatoire.
 */
@Entity
@Table(name = "year_promotion_decision")
@Getter
@Setter
public class PromotionDecision {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "from_class_id")
    private UUID fromClassId;

    @Column(name = "from_class_name", nullable = false)
    private String fromClassName;

    @Column(name = "to_class_id")
    private UUID toClassId;

    @Column(name = "to_class_name")
    private String toClassName;

    @Column(name = "annual_average")
    private BigDecimal annualAverage;

    private Integer rank;

    @Column(name = "class_size")
    private Integer classSize;

    @Column(name = "sequences_counted", nullable = false)
    private int sequencesCounted;

    @Column(name = "prior_repeats", nullable = false)
    private int priorRepeats;

    @Column(name = "proposed_result", nullable = false)
    private String proposedResult;

    @Column(name = "final_result", nullable = false)
    private String finalResult;

    @Column(nullable = false)
    private boolean overridden;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();
}
