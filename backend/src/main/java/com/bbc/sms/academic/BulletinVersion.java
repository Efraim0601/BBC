package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulletin_version")
@Getter @Setter
public class BulletinVersion {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "reporting_period_id", nullable = false) private UUID reportingPeriodId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "enrollment_id") private UUID enrollmentId;
    @Column(nullable = false) private String state = "DRAFT";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false) private String snapshotJson;
    @Column(name = "snapshot_hash", nullable = false) private String snapshotHash;
    @Column(name = "snapshot_contract_version", nullable = false) private int snapshotContractVersion = 1;
    @Column(name = "generation_actor_id") private UUID generationActorId;
    @Column(name = "generation_time") private Instant generationTime;
    @Column(name = "canonical_snapshot_hash") private String canonicalSnapshotHash;
    @Column(name = "source_version_fingerprint") private String sourceVersionFingerprint;
    @Column(nullable = false) private BigDecimal average = BigDecimal.ZERO;
    private Integer rank;
    @Column(name = "class_size", nullable = false) private int classSize;
    @Column(name = "calculation_policy", nullable = false) private String calculationPolicy = "DEFAULT";
    @Column(name = "template_version") private String templateVersion;
    @Column(name = "template_id") private UUID templateId;
    @Column(name = "template_hash") private String templateHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config_json", columnDefinition = "jsonb") private String templateConfigJson;
    @Column(name = "branding_id") private UUID brandingId;
    @Column(name = "branding_version") private Integer brandingVersion;
    @Column(name = "branding_hash") private String brandingHash;
    @Column(name = "resolved_asset_hash") private String resolvedAssetHash;
    @Column(name = "render_contract_version", nullable = false) private int renderContractVersion = 1;
    @Column(name = "snapshot_locale") private String snapshotLocale;
    @Column(name = "evidence_generated_at") private Instant evidenceGeneratedAt;
    @Column(name = "general_appreciation", columnDefinition = "text") private String generalAppreciation;
    @Column(name = "supersedes_id") private UUID supersedesId;
    @Column(name = "corrects_bulletin_version_id") private UUID correctsBulletinVersionId;
    @Column(name = "correction_reason", columnDefinition = "text") private String correctionReason;
    @Column(name = "correction_requested_by") private UUID correctionRequestedBy;
    @Column(name = "correction_requested_at") private Instant correctionRequestedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "validated_by") private UUID validatedBy;
    @Column(name = "published_by") private UUID publishedBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "validated_at") private Instant validatedAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "publication_reason", columnDefinition = "text") private String publicationReason;
    @Column(name = "publication_product", nullable = false) private String publicationProduct = "SEQUENCE";
    @Column(name = "publication_locale") private String publicationLocale;
    @Column(name = "generated_document_id") private UUID generatedDocumentId;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "superseded_by") private UUID supersededBy;
    @Version private long version;
}
