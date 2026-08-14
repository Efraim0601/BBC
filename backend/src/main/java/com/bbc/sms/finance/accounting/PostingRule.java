package com.bbc.sms.finance.accounting;

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
@Table(name = "posting_rule")
@Getter
@Setter
public class PostingRule {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(nullable = false, length = 6) private String side;
    @Column(name = "scope_code", length = 80) private String scopeCode;
    @Column(name = "fee_type_code", length = 64) private String feeTypeCode;
    @Column(name = "payment_channel_code", length = 32) private String paymentChannelCode;
    @Column(name = "component_code", length = 64) private String componentCode;
    @Column(name = "target_account_id", nullable = false) private UUID targetAccountId;
    @Column(nullable = false) private int priority;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(nullable = false) private boolean enabled = true;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
