package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "finance_invoice_line")
@Getter @Setter
public class FinanceInvoiceLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "line_no", nullable = false) private int lineNo;
    @Column(name = "source_charge_id", nullable = false) private UUID sourceChargeId;
    @Column(name = "source_installment_id", nullable = false) private UUID sourceInstallmentId;
    @Column(name = "fee_type_code", nullable = false) private String feeTypeCode;
    @Column(name = "fee_type_name_fr", nullable = false) private String feeTypeNameFr;
    @Column(name = "fee_type_name_en", nullable = false) private String feeTypeNameEn;
    @Column(name = "description_fr", nullable = false) private String descriptionFr;
    @Column(name = "description_en", nullable = false) private String descriptionEn;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(name = "outstanding_minor", nullable = false) private long outstandingMinor;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "created_at", insertable = false, updatable = false) private java.time.Instant createdAt;
}
