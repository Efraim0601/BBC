package com.bbc.sms.student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student")
@Getter
@Setter
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String matricule;

    /** State "Numéro d'Identifiant Unique" from the official register (not unique). */
    private String niu;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String sex;

    private LocalDate dob;

    /** "Lieu de naissance" — place of birth, printed on report cards. */
    private String birthplace;

    /** "Redouble" — is the pupil repeating the year. */
    @Column(nullable = false)
    private boolean repeats = false;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "class_name")
    private String className;

    private String subsystem;

    private String level;

    @Column(name = "parent_name")
    private String parentName;

    @Column(name = "parent_phone")
    private String parentPhone;

    @Column(name = "father_name")
    private String fatherName;
    @Column(name = "father_phone")
    private String fatherPhone;
    @Column(name = "father_email")
    private String fatherEmail;

    @Column(name = "mother_name")
    private String motherName;
    @Column(name = "mother_phone")
    private String motherPhone;
    @Column(name = "mother_email")
    private String motherEmail;

    @Column(name = "guardian_name")
    private String guardianName;
    @Column(name = "guardian_phone")
    private String guardianPhone;
    @Column(name = "guardian_email")
    private String guardianEmail;
    @Column(name = "guardian_relation")
    private String guardianRelation;

    @Column(name = "photo_hue")
    private int photoHue = 210;

    @Column(nullable = false)
    private boolean active = true;
}
