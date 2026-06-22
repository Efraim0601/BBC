package com.bbc.sms.setup;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A school section groups classes by subsystem (FR/EN) and level (primary/secondary),
 * e.g. "Primaire francophone". Its id is a short human-chosen code (pri-fr, sec-en…).
 */
@Entity
@Table(name = "section")
@Getter
@Setter
public class Section {

    @Id
    private String id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String subsystem;   // FR | EN

    @Column(nullable = false)
    private String level;       // primary | secondary
}
