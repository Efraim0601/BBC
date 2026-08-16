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
@Table(name = "chart_of_account")
@Getter
@Setter
public class ChartOfAccount {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 32) private String code;
    @Column(name = "name_fr", nullable = false, length = 160) private String nameFr;
    @Column(name = "name_en", nullable = false, length = 160) private String nameEn;
    @Column(name = "account_type", nullable = false, length = 16) private String accountType;
    @Column(name = "normal_side", nullable = false, length = 6) private String normalSide;
    @Column(length = 3) private String currency;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "posting_allowed", nullable = false) private boolean postingAllowed = true;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
