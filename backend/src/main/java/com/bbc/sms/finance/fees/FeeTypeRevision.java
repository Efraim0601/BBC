package com.bbc.sms.finance.fees;

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
@Table(name = "fee_type_revision")
@Getter
@Setter
public class FeeTypeRevision {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "fee_type_id", nullable = false) private UUID feeTypeId;
    @Column(name = "revision_no", nullable = false) private int revisionNo;
    @Column(name = "revision_status", nullable = false, length = 10) private String revisionStatus = "DRAFT";
    @Column(name = "name_fr", nullable = false, length = 160) private String nameFr;
    @Column(name = "name_en", nullable = false, length = 160) private String nameEn;
    @Column(name = "description_fr", length = 500) private String descriptionFr;
    @Column(name = "description_en", length = 500) private String descriptionEn;
    @Column(nullable = false, length = 32) private String category;
    @Column(name = "default_amount_minor", nullable = false) private long defaultAmountMinor;
    @Column(name = "default_currency", nullable = false, length = 3) private String defaultCurrency = "XAF";
    @Column(nullable = false, length = 12) private String frequency = "ONCE";
    @Column(nullable = false) private boolean mandatory = true;
    @Column(nullable = false) private boolean refundable;
    @Column(nullable = false) private boolean taxable;
    @Column(name = "tax_basis_points", nullable = false) private int taxBasisPoints;
    @Column(name = "receivable_account_id") private UUID receivableAccountId;
    @Column(name = "revenue_account_id") private UUID revenueAccountId;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "activated_at") private Instant activatedAt;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
