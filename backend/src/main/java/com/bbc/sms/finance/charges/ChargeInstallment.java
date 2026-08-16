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
@Table(name = "charge_installment")
@Getter
@Setter
public class ChargeInstallment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "charge_id", nullable = false) private UUID chargeId;
    @Column(name = "installment_no", nullable = false) private int installmentNo;
    @Column(name = "label_fr", nullable = false, length = 160) private String labelFr;
    @Column(name = "label_en", nullable = false, length = 160) private String labelEn;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(name = "waived_minor", nullable = false) private long waivedMinor;
    @Column(name = "outstanding_minor", nullable = false) private long outstandingMinor;
    @Column(nullable = false, length = 10) private String status = "OPEN";
    @Column(name = "generation_key", nullable = false, length = 240) private String generationKey;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
