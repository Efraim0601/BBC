package com.bbc.sms.alerts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A proactive at-risk signal raised for a student by the alert scan engine.
 * Alerts are recomputed from the operational data (grades, attendance,
 * discipline, fees) and upserted by {@code dedup_key} so that an acknowledged
 * or resolved alert is never resurrected on the next scan.
 */
@Entity
@Table(name = "alert")
@Getter
@Setter
public class Alert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String type;          // grade_drop | absences | discipline | unpaid

    @Column(nullable = false)
    private String severity;      // info | warn | critical

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(nullable = false)
    private String status = "open";   // open | ack | resolved

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ack_by")
    private UUID ackBy;

    @Column(name = "ack_at")
    private Instant ackAt;
}
