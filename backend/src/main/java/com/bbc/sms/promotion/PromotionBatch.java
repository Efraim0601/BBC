package com.bbc.sms.promotion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Un passage de classe appliqué : une classe, une année, un compte rendu chiffré. */
@Entity
@Table(name = "year_promotion_batch")
@Getter
@Setter
public class PromotionBatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "next_academic_year", nullable = false)
    private String nextAcademicYear;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "students_total", nullable = false)
    private int studentsTotal;

    @Column(name = "promoted_count", nullable = false)
    private int promotedCount;

    @Column(name = "repeated_count", nullable = false)
    private int repeatedCount;

    @Column(name = "graduated_count", nullable = false)
    private int graduatedCount;

    @Column(name = "other_count", nullable = false)
    private int otherCount;

    @Column(name = "overridden_count", nullable = false)
    private int overriddenCount;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt = Instant.now();
}
