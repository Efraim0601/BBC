package com.bbc.sms.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One administrative document attached to a student's file. Only metadata is
 * stored here — the actual binary lives elsewhere; {@code fileRef} is a label
 * or external URL pointing at it (the physical/digital filing reference).
 */
@Entity
@Table(name = "student_document")
@Getter
@Setter
public class StudentDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** birth_cert | photo | prior_report | certificate | medical | other */
    @Column(nullable = false, length = 24)
    private String kind;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "file_ref", length = 300)
    private String fileRef;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
