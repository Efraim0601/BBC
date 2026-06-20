package com.bbc.sms.discipline;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "discipline_incident")
@Getter
@Setter
public class DisciplineIncident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false)
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column
    private String sanction;
}
