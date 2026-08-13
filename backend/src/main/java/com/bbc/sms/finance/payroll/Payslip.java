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
@Table(name = "payslip")
@Getter
@Setter
public class Payslip {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "employee_payroll_id", nullable = false) private UUID employeePayrollId;
    @Column(name = "version_no", nullable = false) private int versionNo = 1;
    @Column(name = "payslip_number", nullable = false, length = 80) private String payslipNumber;
    @Column(nullable = false, length = 4) private String locale = "fr";
    @Column(nullable = false, length = 24) private String status = "GENERATION_FAILED";
    @Column(name = "generated_document_id") private UUID generatedDocumentId;
    @Column(name = "snapshot_hash", nullable = false, length = 64) private String snapshotHash;
    @Column(name = "source_event_key", nullable = false, length = 240) private String sourceEventKey;
    @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
    @Column(name = "generation_error", length = 1000) private String generationError;
    @Column(name = "superseded_by_id") private UUID supersededById;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "voided_by") private UUID voidedBy;
    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "void_reason", length = 500) private String voidReason;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
