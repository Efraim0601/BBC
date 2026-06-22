package com.bbc.sms.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A conseil de classe orientation/conseil decision for a student at a given
 * stage of their schooling (e.g. "Orientation 3ème"): the recommendation and
 * the final decision recorded for an academic year.
 */
@Entity
@Table(name = "orientation_decision")
@Getter
@Setter
public class OrientationDecision {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(nullable = false, length = 60)
    private String stage;

    @Column(columnDefinition = "text")
    private String recommendation;

    @Column(columnDefinition = "text")
    private String decision;

    @Column(name = "council_date")
    private LocalDate councilDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
