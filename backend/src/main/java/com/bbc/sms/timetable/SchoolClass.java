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

    /** Rang pédagogique dans la section (SIL=1, CP=2…) — ordonne l'échelle des classes. */
    @Column(name = "grade_order", nullable = false)
    private int gradeOrder = 0;

    /** Classe d'accueil l'année suivante ; null tant que la progression n'est pas configurée. */
    @Column(name = "next_class_id")
    private UUID nextClassId;

    /** Classe de sortie (Terminale, Upper Sixth) : la réussite y vaut « diplômé ». */
    @Column(nullable = false)
    private boolean terminal = false;
}
