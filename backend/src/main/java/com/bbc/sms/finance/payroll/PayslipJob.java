package com.bbc.sms.finance.payroll;

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
@Table(name = "payroll_payslip_job")
@Getter
@Setter
public class PayslipJob {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payroll_run_id", nullable = false) private UUID payrollRunId;
    @Column(nullable = false, length = 28) private String status = "RUNNING";
    @Column(name = "total_count", nullable = false) private int totalCount;
    @Column(name = "issued_count", nullable = false) private int issuedCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Version private long version;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}
