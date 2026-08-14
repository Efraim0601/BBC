package com.bbc.sms.finance.collections;

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

/** Immutable posted collection snapshot. Corrections use reversal/refund records. */
@Entity
@Table(name = "finance_payment")
@Getter
@Setter
public class FinancePayment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "payment_channel_id", nullable = false) private UUID paymentChannelId;
    @Column(name = "channel_code_snapshot", nullable = false, length = 20) private String channelCodeSnapshot;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
    @Column(length = 180) private String reference;
    @Column(name = "payer_name", length = 180) private String payerName;
    @Column(length = 1000) private String note;
    @Column(nullable = false, length = 20) private String status = "POSTED";
    @Column(name = "receipt_no", nullable = false, length = 80) private String receiptNo;
    @Column(name = "legacy_receipt_no", length = 80) private String legacyReceiptNo;
    @Column(name = "cashier_session_id") private UUID cashierSessionId;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "source_event_key", nullable = false, length = 240) private String sourceEventKey;
    @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
    @Version private long version;
}
