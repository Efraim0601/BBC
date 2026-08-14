package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_invoice")
@Getter @Setter
public class FinanceInvoice {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "school_class_id_snapshot") private UUID schoolClassIdSnapshot;
    @Column(name = "class_name_snapshot") private String classNameSnapshot;
    @Column(name = "invoice_number", nullable = false) private String invoiceNumber;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(name = "outstanding_minor", nullable = false) private long outstandingMinor;
    @Column(name = "recipient_guardian_id") private UUID recipientGuardianId;
    @Column(name = "recipient_name", nullable = false) private String recipientName;
    @Column(name = "recipient_email") private String recipientEmail;
    @Column(name = "recipient_phone") private String recipientPhone;
    @Column(name = "recipient_source", nullable = false) private String recipientSource = "FINANCE_RESPONSIBLE";
    @Column(name = "recipient_warning") private String recipientWarning;
    @Column(name = "snapshot_hash", nullable = false) private String snapshotHash;
    @Column(name = "source_event_key", nullable = false) private String sourceEventKey;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "generated_document_id") private UUID generatedDocumentId;
    @Column(name = "superseded_by_invoice_id") private UUID supersededByInvoiceId;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "superseded_by") private UUID supersededBy;
    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "voided_by") private UUID voidedBy;
    @Column(name = "void_reason") private String voidReason;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "issued_at") private Instant issuedAt;
    @Version private long version;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
