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
@Table(name = "payroll_component_type")
@Getter
@Setter
public class PayrollComponentType {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "name_fr", nullable = false, length = 160) private String nameFr;
    @Column(name = "name_en", nullable = false, length = 160) private String nameEn;
    @Column(name = "component_kind", nullable = false, length = 28) private String componentKind;
    @Column(name = "calculation_mode", nullable = false, length = 20) private String calculationMode;
    @Column(name = "default_amount_minor", nullable = false) private long defaultAmountMinor;
    @Column(name = "default_rate_bps", nullable = false) private int defaultRateBps;
    @Column(name = "expense_account_id") private UUID expenseAccountId;
    @Column(name = "liability_account_id") private UUID liabilityAccountId;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(nullable = false) private boolean active = true;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
