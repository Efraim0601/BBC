package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_receipt_line")
@Getter @Setter
public class FinanceReceiptLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "receipt_id", nullable = false) private UUID receiptId;
    @Column(name = "allocation_id", nullable = false) private UUID allocationId;
    @Column(name = "source_charge_id", nullable = false) private UUID sourceChargeId;
    @Column(name = "source_installment_id", nullable = false) private UUID sourceInstallmentId;
    @Column(name = "fee_type_code", nullable = false) private String feeTypeCode;
    @Column(name = "fee_type_name_fr", nullable = false) private String feeTypeNameFr;
    @Column(name = "fee_type_name_en", nullable = false) private String feeTypeNameEn;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "allocated_minor", nullable = false) private long allocatedMinor;
    @Column(name = "installment_remaining_minor", nullable = false) private long installmentRemainingMinor;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "created_at", insertable = false, updatable = false) private java.time.Instant createdAt;
}
