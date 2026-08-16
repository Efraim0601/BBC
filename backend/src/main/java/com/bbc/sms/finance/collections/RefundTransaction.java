package com.bbc.sms.finance.collections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_transaction")
public class RefundTransaction {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "refund_request_id", nullable = false) private UUID refundRequestId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "refund_no", nullable = false, length = 80) private String refundNo;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "channel_code", nullable = false, length = 20) private String channelCode;
    @Column(length = 180) private String reference;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "posted_at", insertable = false, updatable = false) private Instant postedAt;
    @Column(name = "posted_by") private UUID postedBy;

    public UUID getId() { return id; }
    public void setSchoolId(UUID value) { schoolId = value; }
    public UUID getSchoolId() { return schoolId; }
    public void setRefundRequestId(UUID value) { refundRequestId = value; }
    public UUID getRefundRequestId() { return refundRequestId; }
    public void setPaymentId(UUID value) { paymentId = value; }
    public UUID getPaymentId() { return paymentId; }
    public void setRefundNo(String value) { refundNo = value; }
    public String getRefundNo() { return refundNo; }
    public void setAmountMinor(long value) { amountMinor = value; }
    public long getAmountMinor() { return amountMinor; }
    public void setCurrency(String value) { currency = value; }
    public String getCurrency() { return currency; }
    public void setChannelCode(String value) { channelCode = value; }
    public String getChannelCode() { return channelCode; }
    public void setReference(String value) { reference = value; }
    public String getReference() { return reference; }
    public void setJournalEntryId(UUID value) { journalEntryId = value; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedBy(UUID value) { postedBy = value; }
    public UUID getPostedBy() { return postedBy; }
}
