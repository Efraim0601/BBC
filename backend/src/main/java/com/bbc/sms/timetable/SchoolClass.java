package com.bbc.sms.timetable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "school_class")
@Getter
@Setter
public class SchoolClass {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "section_id", nullable = false)
    private String sectionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subsystem;

    @Column(nullable = false)
    private String level;
}
