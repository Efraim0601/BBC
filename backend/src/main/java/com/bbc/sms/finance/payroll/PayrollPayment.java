package com.bbc.sms.finance.payroll;

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
@Table(name = "payroll_payment")
@Getter
@Setter
public class PayrollPayment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "employee_payroll_id", nullable = false) private UUID employeePayrollId;
    @Column(name = "payment_channel_id") private UUID paymentChannelId;
    @Column(name = "channel_code", nullable = false, length = 48) private String channelCode;
    @Column(name = "payment_account_id", nullable = false) private UUID paymentAccountId;
    @Column(name = "payment_reference", length = 180) private String paymentReference;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
    @Column(nullable = false, length = 16) private String status = "POSTED";
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "source_event_key", nullable = false, length = 240) private String sourceEventKey;
    @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "posted_at") private Instant postedAt;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
