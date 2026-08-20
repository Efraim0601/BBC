package com.bbc.sms.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generated_document")
@Getter @Setter
public class GeneratedDocument {
    /** Application-assigned because the UUID is also the immutable storage key. */
    @Id private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "document_template_id") private UUID documentTemplateId;
    @Column(name = "document_type", nullable = false) private String documentType;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;
    @Column(name = "aggregate_version", nullable = false) private String aggregateVersion = "1";
    @Column(nullable = false) private String locale = "fr";
    @Column(name = "document_number", nullable = false) private String documentNumber;
    @Column(nullable = false) private String title;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(nullable = false) private String sha256;
    @Column(name = "mime_type", nullable = false) private String mimeType = "application/pdf";
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false) private String status = "ISSUED";
    @Column(nullable = false) private String visibility = "STAFF";
    @Column(name = "generated_by") private UUID generatedBy;
    @Column(name = "generated_at", insertable = false, updatable = false) private Instant generatedAt;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revoke_reason") private String revokeReason;
    @Column(name = "superseded_by_id") private UUID supersededById;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "superseded_by") private UUID supersededBy;
    @Column(name = "void_reason") private String voidReason;
    @Column(name = "source_event_key") private String sourceEventKey;
    @Version private long version;
}
