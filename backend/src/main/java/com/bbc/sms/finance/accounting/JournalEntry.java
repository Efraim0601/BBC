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
@Table(name = "journal_entry")
@Getter
@Setter
public class JournalEntry {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 80) private String number;
    @Column(name = "entry_date", nullable = false) private LocalDate entryDate;
    @Column(nullable = false, length = 10) private String status = "DRAFT";
    @Column(name = "source_type", length = 80) private String sourceType;
    @Column(name = "source_id", length = 120) private String sourceId;
    @Column(name = "source_event_key", length = 180) private String sourceEventKey;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "accounting_period_id", nullable = false) private UUID accountingPeriodId;
    @Column(name = "reversal_of_id") private UUID reversalOfId;
    @Column(name = "reversed_by") private UUID reversedBy;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "posted_by") private UUID postedBy;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
