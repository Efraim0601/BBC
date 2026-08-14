package com.bbc.sms.finance.plans;

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
@Table(name = "fee_plan_line")
@Getter
@Setter
public class FeePlanLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "fee_plan_id", nullable = false) private UUID feePlanId;
    @Column(name = "line_order", nullable = false) private int lineOrder;
    @Column(name = "fee_type_id", nullable = false) private UUID feeTypeId;
    @Column(name = "fee_type_revision_id", nullable = false) private UUID feeTypeRevisionId;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(nullable = false) private boolean mandatory = true;
    @Column(nullable = false) private boolean refundable;
    @Column(nullable = false) private int priority;
    @Column(name = "installment_template_id") private UUID installmentTemplateId;
    @Column(name = "proration_policy", nullable = false, length = 8) private String prorationPolicy = "NONE";
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
