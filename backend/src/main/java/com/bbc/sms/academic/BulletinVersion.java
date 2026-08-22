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
    @Column(name = "programme_class_id") private UUID programmeClassId;
    @Column(name = "enrollment_id") private UUID enrollmentId;
    @Column(nullable = false) private String state = "DRAFT";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false) private String snapshotJson;
    @Column(name = "snapshot_hash", nullable = false) private String snapshotHash;
    @Column(nullable = false) private BigDecimal average = BigDecimal.ZERO;
    private Integer rank;
    @Column(name = "class_size", nullable = false) private int classSize;
    @Column(name = "calculation_policy", nullable = false) private String calculationPolicy = "DEFAULT";
    @Column(name = "template_version") private String templateVersion;
    @Column(name = "template_id") private UUID templateId;
    @Column(name = "branding_id") private UUID brandingId;
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
    @Version private long version;
}
