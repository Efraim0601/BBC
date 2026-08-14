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
@Table(name = "charge_adjustment")
@Getter
@Setter
public class ChargeAdjustment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "charge_id", nullable = false) private UUID chargeId;
    @Column(name = "installment_id") private UUID installmentId;
    @Column(name = "adjustment_type", nullable = false, length = 12) private String adjustmentType;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "evidence_reference", length = 240) private String evidenceReference;
    @Column(name = "contra_account_id", nullable = false) private UUID contraAccountId;
    @Column(name = "effective_date", nullable = false) private LocalDate effectiveDate;
    @Column(nullable = false, length = 10) private String status = "REQUESTED";
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requested_at", insertable = false, updatable = false) private Instant requestedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
