package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_invoice_batch_job")
@Getter @Setter
public class FinanceInvoiceBatchJob {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "school_class_id") private UUID schoolClassId;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false) private String status = "PREVIEW";
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "enrollment_count", nullable = false) private int enrollmentCount;
    @Column(name = "issued_count", nullable = false) private int issuedCount;
    @Column(name = "already_issued_count", nullable = false) private int alreadyIssuedCount;
    @Column(name = "blocked_count", nullable = false) private int blockedCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "total_amount_minor", nullable = false) private long totalAmountMinor;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "last_error") private String lastError;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
