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
import java.util.UUID;

@Entity
@Table(name = "payment_reversal_request")
@Getter
@Setter
public class PaymentReversalRequest {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "reversal_no", length = 80) private String reversalNo;
    @Column(nullable = false, length = 12) private String status = "REQUESTED";
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requested_at", insertable = false, updatable = false) private Instant requestedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "posted_at") private Instant postedAt;
    @Version private long version;
}
