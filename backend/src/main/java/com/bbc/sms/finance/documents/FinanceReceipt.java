package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_receipt")
@Getter @Setter
public class FinanceReceipt {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "finance_payment_id", nullable = false) private UUID financePaymentId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "school_class_id_snapshot") private UUID schoolClassIdSnapshot;
    @Column(name = "class_name_snapshot") private String classNameSnapshot;
    @Column(name = "receipt_number", nullable = false) private String receiptNumber;
    @Column(nullable = false) private String status = "ISSUED";
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "allocated_minor", nullable = false) private long allocatedMinor;
    @Column(name = "credit_minor", nullable = false) private long creditMinor;
    @Column(name = "outstanding_minor", nullable = false) private long outstandingMinor;
    @Column(name = "channel_code_snapshot", nullable = false) private String channelCodeSnapshot;
    @Column(name = "payment_reference") private String paymentReference;
    @Column(name = "cashier_session_id") private UUID cashierSessionId;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
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
    @Column(name = "generation_error") private String generationError;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "issued_at") private Instant issuedAt;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
