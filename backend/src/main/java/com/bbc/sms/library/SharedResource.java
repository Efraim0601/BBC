package com.bbc.sms.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un document mis à disposition par la direction : circulaire, progression,
 * formulaire, image… Deux colonnes portent toute la granularité demandée —
 * {@link #audience} dit à QUI, {@link #section} dit JUSQU'OÙ.
 *
 * <p>Le fichier lui-même est dans MinIO ; {@link #objectKey} le désigne. La
 * ligne fait foi : effacer la ligne, c'est retirer le document, que l'objet
 * survive ou non au ménage.
 */
@Entity
@Table(name = "shared_resource")
@Getter
@Setter
public class SharedResource {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** circular | pedagogy | admin | form | other */
    @Column(nullable = false, length = 16)
    private String category = "other";

    /** all (personnel + parents) | staff | parents */
    @Column(nullable = false, length = 8)
    private String audience;

    /** null = toute l'école ; sinon maternelle | primary | secondary */
    @Column(length = 16)
    private String section;

    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_by_name", length = 120)
    private String uploadedByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
