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
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
