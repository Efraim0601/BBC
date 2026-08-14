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
@Table(name = "accounting_period")
@Getter
@Setter
public class AccountingPeriod {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 32) private String code;
    @Column(name = "name_fr", nullable = false, length = 160) private String nameFr;
    @Column(name = "name_en", nullable = false, length = 160) private String nameEn;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "academic_session_id") private UUID academicSessionId;
    @Column(nullable = false, length = 8) private String status = "OPEN";
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "close_reason", length = 500) private String closeReason;
    @Column(name = "reopened_at") private Instant reopenedAt;
    @Column(name = "reopened_by") private UUID reopenedBy;
    @Column(name = "reopen_reason", length = 500) private String reopenReason;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
