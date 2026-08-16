package com.bbc.sms.finance.charges;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "charge_generation_job")
@Getter
@Setter
public class ChargeGenerationJob {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "school_class_id") private UUID schoolClassId;
    @Column(length = 32) private String level;
    @Column(length = 16) private String subsystem;
    @Column(name = "charge_date", nullable = false) private LocalDate chargeDate;
    @Column(name = "proration_policy", nullable = false, length = 8) private String prorationPolicy = "NONE";
    @Column(name = "transfer_policy", nullable = false, length = 24) private String transferPolicy = "INCREMENTAL_ONLY";
    @Column(nullable = false, length = 24) private String status = "PREVIEW";
    @Column(name = "idempotency_key", length = 240) private String idempotencyKey;
    @Column(name = "enrollment_count", nullable = false) private int enrollmentCount;
    @Column(name = "generated_count", nullable = false) private int generatedCount;
    @Column(name = "already_exists_count", nullable = false) private int alreadyExistsCount;
    @Column(name = "blocked_count", nullable = false) private int blockedCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "total_amount_minor", nullable = false) private long totalAmountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
