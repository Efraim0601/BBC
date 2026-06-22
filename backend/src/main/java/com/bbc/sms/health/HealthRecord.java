package com.bbc.sms.health;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A student's medical file: blood group, allergies, chronic conditions,
 * vaccination status, attending doctor and basic biometrics. One row per
 * student (enforced by a unique (school_id, student_id) constraint).
 */
@Entity
@Table(name = "health_record")
@Getter
@Setter
public class HealthRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(columnDefinition = "text")
    private String allergies;

    @Column(columnDefinition = "text")
    private String conditions;

    @Column(columnDefinition = "text")
    private String vaccinations;

    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "doctor_phone")
    private String doctorPhone;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
