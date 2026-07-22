package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Overrides {@link Subject#getCoef()} for a specific class. Lets a subject weigh
 * differently in, say, 6e vs 3e — as the official coefficient tables require.
 */
@Entity
@Table(name = "subject_class_coef")
@Getter
@Setter
public class SubjectClassCoef {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(nullable = false)
    private int coef = 1;
}
