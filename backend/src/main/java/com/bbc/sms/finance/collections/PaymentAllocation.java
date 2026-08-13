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
@Table(name = "payment_allocation")
@Getter
@Setter
public class PaymentAllocation {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "charge_installment_id", nullable = false) private UUID chargeInstallmentId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "allocated_minor", nullable = false) private long allocatedMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(nullable = false, length = 12) private String status = "ACTIVE";
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
    @Version private long version;
}
