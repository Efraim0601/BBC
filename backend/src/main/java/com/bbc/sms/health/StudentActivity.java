package com.bbc.sms.health;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * An extracurricular activity a student takes part in — a club, a sport, an
 * art discipline or other school-life engagement, with an optional role and
 * season. Part of the "vie scolaire" picture alongside the medical record.
 */
@Entity
@Table(name = "student_activity")
@Getter
@Setter
public class StudentActivity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String role;

    private String season;
}
