package com.bbc.sms.finance.charges;

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
@Table(name = "student_charge")
@Getter
@Setter
public class StudentCharge {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "fee_plan_id", nullable = false) private UUID feePlanId;
    @Column(name = "fee_plan_line_id", nullable = false) private UUID feePlanLineId;
    @Column(name = "fee_type_id", nullable = false) private UUID feeTypeId;
    @Column(name = "fee_type_revision_id", nullable = false) private UUID feeTypeRevisionId;
    @Column(name = "fee_plan_version_no", nullable = false) private int feePlanVersionNo;
    @Column(name = "fee_type_code", nullable = false, length = 64) private String feeTypeCode;
    @Column(name = "fee_type_name_fr", nullable = false, length = 160) private String feeTypeNameFr;
    @Column(name = "fee_type_name_en", nullable = false, length = 160) private String feeTypeNameEn;
    @Column(name = "fee_type_category", nullable = false, length = 32) private String feeTypeCategory;
    @Column(name = "scope_type", nullable = false, length = 8) private String scopeType;
    @Column(name = "level_snapshot", nullable = false, length = 32) private String levelSnapshot;
    @Column(name = "subsystem_snapshot", nullable = false, length = 16) private String subsystemSnapshot;
    @Column(name = "school_class_id_snapshot") private UUID schoolClassIdSnapshot;
    @Column(name = "class_name_snapshot", length = 160) private String classNameSnapshot;
    @Column(name = "receivable_account_id", nullable = false) private UUID receivableAccountId;
    @Column(name = "revenue_account_id", nullable = false) private UUID revenueAccountId;
    @Column(name = "original_amount_minor", nullable = false) private long originalAmountMinor;
    @Column(name = "adjusted_amount_minor", nullable = false) private long adjustedAmountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "charge_date", nullable = false) private LocalDate chargeDate;
    @Column(name = "proration_policy", nullable = false, length = 8) private String prorationPolicy = "NONE";
    @Column(name = "proration_formula", length = 500) private String prorationFormula;
    @Column(name = "generation_key", nullable = false, length = 220) private String generationKey;
    @Column(name = "transfer_from_enrollment_id") private UUID transferFromEnrollmentId;
    @Column(name = "transfer_policy", nullable = false, length = 24) private String transferPolicy = "INCREMENTAL_ONLY";
    @Column(nullable = false, length = 10) private String status = "DRAFT";
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(name = "waived_minor", nullable = false) private long waivedMinor;
    @Column(name = "outstanding_minor", nullable = false) private long outstandingMinor;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Version private long version;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
