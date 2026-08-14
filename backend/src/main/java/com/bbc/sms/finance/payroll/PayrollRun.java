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
@Table(name = "payroll_run")
@Getter
@Setter
public class PayrollRun {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payroll_period_id", nullable = false) private UUID payrollPeriodId;
    @Column(name = "run_number", nullable = false) private long runNumber;
    @Column(nullable = false, length = 16) private String status = "DRAFT";
    @Column(name = "proration_mode", nullable = false, length = 12) private String prorationMode = "NONE";
    @Column(name = "default_hours", nullable = false) private int defaultHours;
    @Column(name = "employee_scope_json", columnDefinition = "text") private String employeeScopeJson;
    @Column(name = "segregation_enabled", nullable = false) private boolean segregationEnabled = true;
    @Column(name = "employee_count", nullable = false) private int employeeCount;
    @Column(name = "exception_count", nullable = false) private int exceptionCount;
    @Column(name = "gross_minor", nullable = false) private long grossMinor;
    @Column(name = "deduction_minor", nullable = false) private long deductionMinor;
    @Column(name = "net_minor", nullable = false) private long netMinor;
    @Column(name = "employer_cost_minor", nullable = false) private long employerCostMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "calculation_snapshot_hash", length = 64) private String calculationSnapshotHash;
    @Column(name = "previous_snapshot_hash", length = 64) private String previousSnapshotHash;
    @Column(name = "snapshot_locked", nullable = false) private boolean snapshotLocked;
    @Column(name = "calculation_idempotency_key", length = 160) private String calculationIdempotencyKey;
    @Column(name = "source_event_key", nullable = false, length = 240) private String sourceEventKey;
    @Column(name = "accrual_journal_id") private UUID accrualJournalId;
    @Column(name = "payment_journal_id") private UUID paymentJournalId;
    @Column(name = "calculated_by") private UUID calculatedBy;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "paid_by") private UUID paidBy;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "voided_by") private UUID voidedBy;
    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "void_reason", length = 500) private String voidReason;
    @Version private long version;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
