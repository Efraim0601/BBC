package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "academic_assessment")
@Getter @Setter
public class AcademicAssessment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "reporting_period_id", nullable = false) private UUID reportingPeriodId;
    @Column(name = "subject_code") private String subjectCode;
    @Column(name = "class_id") private UUID classId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String label;
    @Column(name = "assessment_type", nullable = false) private String assessmentType = "EVALUATION";
    @Column(nullable = false) private String source = "MANUAL";
    @Column(name = "generation_batch_id") private UUID generationBatchId;
    @Column(name = "legacy_secondary_competency_id") private UUID legacySecondaryCompetencyId;
    @Column(name = "max_score", nullable = false) private BigDecimal maxScore = BigDecimal.valueOf(20);
    @Column(nullable = false) private BigDecimal weight = BigDecimal.ONE;
    @Column(nullable = false) private boolean mandatory = true;
    @Column(name = "display_order", nullable = false) private int displayOrder = 1;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
