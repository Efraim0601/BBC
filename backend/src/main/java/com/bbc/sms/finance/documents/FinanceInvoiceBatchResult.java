package com.bbc.sms.finance.documents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finance_invoice_batch_result")
@Getter @Setter
public class FinanceInvoiceBatchResult {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "student_enrollment_id") private UUID studentEnrollmentId;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "finance_invoice_id") private UUID financeInvoiceId;
    @Column(name = "result_status", nullable = false) private String resultStatus;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false) private String currency = "XAF";
    @Column(name = "blocker_code") private String blockerCode;
    @Column(name = "blocker_message") private String blockerMessage;
    @Column(name = "action_link") private String actionLink;
    @Column(name = "error_detail") private String errorDetail;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
