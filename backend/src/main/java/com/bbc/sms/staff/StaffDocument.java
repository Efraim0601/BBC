package com.bbc.sms.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Metadata for one private HR document; the bytes live in MinIO. */
@Entity
@Table(name = "staff_document")
@Getter
@Setter
public class StaffDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "document_type", nullable = false, length = 24)
    private String documentType;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "object_key", nullable = false, unique = true, length = 300)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_by_name", length = 120)
    private String uploadedByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
