package com.bbc.sms.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_template")
@Getter @Setter
public class DocumentTemplate {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String locale = "fr";
    @Column(nullable = false) private String name;
    @Column(name = "template_version", nullable = false) private int templateVersion = 1;
    @Column(name = "body_template", nullable = false, columnDefinition = "text") private String bodyTemplate;
    @Column(name = "template_family", nullable = false) private String templateFamily = "GENERIC";
    @Column(nullable = false) private String product = "GENERIC";
    @Column private String subsystem;
    @Column(nullable = false) private String status = "PUBLISHED";
    @Column(name = "reference_family", nullable = false) private String referenceFamily = "GENERIC";
    @Column(nullable = false, length = 64) private String checksum;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
