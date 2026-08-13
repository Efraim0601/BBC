package com.bbc.sms.finance.collections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Append-only student credit movements; available credit is derived from entries. */
@Entity
@Table(name = "student_credit_ledger")
public class StudentCreditLedger {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_enrollment_id") private UUID studentEnrollmentId;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "payment_allocation_id") private UUID paymentAllocationId;
    @Column(name = "source_credit_id") private UUID sourceCreditId;
    @Column(name = "entry_type", nullable = false, length = 16) private String entryType;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "source_event_key", nullable = false, length = 240) private String sourceEventKey;
    @Column(name = "entry_date", nullable = false) private LocalDate entryDate;
    @Column(length = 500) private String reason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getSchoolId() { return schoolId; }
    public void setSchoolId(UUID value) { schoolId = value; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID value) { studentId = value; }
    public UUID getStudentEnrollmentId() { return studentEnrollmentId; }
    public void setStudentEnrollmentId(UUID value) { studentEnrollmentId = value; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID value) { paymentId = value; }
    public UUID getPaymentAllocationId() { return paymentAllocationId; }
    public void setPaymentAllocationId(UUID value) { paymentAllocationId = value; }
    public UUID getSourceCreditId() { return sourceCreditId; }
    public void setSourceCreditId(UUID value) { sourceCreditId = value; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String value) { entryType = value; }
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long value) { amountMinor = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getSourceEventKey() { return sourceEventKey; }
    public void setSourceEventKey(String value) { sourceEventKey = value; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate value) { entryDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
}
