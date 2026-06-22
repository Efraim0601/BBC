package com.bbc.sms.coursebook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line in the cahier de textes: what a teacher covered in a given class on a
 * given day, plus the homework assigned and its due date. The collection forms
 * the class's daily log that parents and the administration can consult.
 */
@Entity
@Table(name = "coursebook_entry")
@Getter
@Setter
public class CoursebookEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "subject_code", nullable = false)
    private String subjectCode;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(columnDefinition = "text")
    private String homework;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
