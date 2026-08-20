package com.bbc.sms.finance.fees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_type")
@Getter
@Setter
public class FeeType {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 10) private String lifecycle = "DRAFT";
    @Column(name = "current_revision_no") private Integer currentRevisionNo;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "deactivated_by") private UUID deactivatedBy;
    @Column(name = "deactivated_at") private Instant deactivatedAt;
    @Column(name = "deactivation_reason", length = 500) private String deactivationReason;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
