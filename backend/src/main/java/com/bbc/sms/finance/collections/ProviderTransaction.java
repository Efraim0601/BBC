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
@Table(name = "provider_transaction")
@Getter
@Setter
public class ProviderTransaction {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payment_channel_id", nullable = false) private UUID paymentChannelId;
    @Column(name = "finance_payment_id") private UUID financePaymentId;
    @Column(name = "provider_code", nullable = false, length = 32) private String providerCode;
    @Column(name = "external_reference", nullable = false, length = 180) private String externalReference;
    @Column(name = "amount_minor") private Long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(nullable = false, length = 16) private String status = "RECEIVED";
    @Column(name = "payload_hash", length = 128) private String payloadHash;
    @Column(name = "received_at", insertable = false, updatable = false) private Instant receivedAt;
    @Column(name = "matched_at") private Instant matchedAt;
    @Column(name = "matched_by") private UUID matchedBy;
    @Column(name = "rejection_reason", length = 1000) private String rejectionReason;
    @Version private long version;
}
