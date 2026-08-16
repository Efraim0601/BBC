package com.bbc.sms.finance.accounting;

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
@Table(name = "reconciliation_item")
@Getter
@Setter
public class ReconciliationItem {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "source_type", nullable = false, length = 80) private String sourceType;
    @Column(name = "source_id", length = 120) private String sourceId;
    @Column(name = "expected_amount", nullable = false) private long expectedAmount;
    @Column(name = "posted_amount", nullable = false) private long postedAmount;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(nullable = false, length = 12) private String state = "MISSING";
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolution_note", length = 500) private String resolutionNote;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
