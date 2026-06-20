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

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String sex;

    private LocalDate dob;

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

    @Column(name = "photo_hue")
    private int photoHue = 210;

    @Column(nullable = false)
    private boolean active = true;
}
