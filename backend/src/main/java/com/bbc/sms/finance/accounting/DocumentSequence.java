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
import java.util.UUID;

@Entity
@Table(name = "document_sequence")
@Getter
@Setter
public class DocumentSequence {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "document_type", nullable = false, length = 48) private String documentType;
    @Column(name = "period_key", nullable = false, length = 32) private String periodKey;
    @Column(nullable = false, length = 80) private String prefix;
    @Column(name = "next_number", nullable = false) private long nextNumber = 1;
    @Column(nullable = false) private int padding = 6;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
