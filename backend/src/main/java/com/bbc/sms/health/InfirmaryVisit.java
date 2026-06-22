package com.bbc.sms.health;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One logged visit to the school infirmary: when, why and what treatment was
 * given. The collection of a student's visits forms their infirmary history.
 */
@Entity
@Table(name = "infirmary_visit")
@Getter
@Setter
public class InfirmaryVisit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false)
    private String reason;

    @Column(columnDefinition = "text")
    private String treatment;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
