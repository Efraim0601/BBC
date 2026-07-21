package com.bbc.sms.classkit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A single class resource line — either a supply (fournitures) or a payable book.
 * {@code kind} discriminates the two; {@code quantity} is used by supplies and
 * {@code price} (FCFA) by books.
 */
@Entity
@Table(name = "class_resource_item")
@Getter
@Setter
public class ClassResourceItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(nullable = false)
    private String kind;        // supplies | books

    @Column(nullable = false)
    private String label;

    private Integer quantity;   // supplies only

    private Long price;         // books only (FCFA)

    private String note;

    @Column(name = "subject_code")
    private String subjectCode; // books only — links to the subject

    private String author;      // books only — author / edition

    private Boolean mandatory;  // books only — required vs optional textbook

    @Column(nullable = false)
    private int position;
}
