package com.bbc.sms.finance.plans;

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
@Table(name = "fee_plan")
@Getter
@Setter
public class FeePlan {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "scope_type", nullable = false, length = 8) private String scopeType;
    @Column(nullable = false, length = 32) private String level;
    @Column(nullable = false, length = 16) private String subsystem;
    @Column(name = "school_class_id") private UUID schoolClassId;
    @Column(name = "plan_version_no", nullable = false) private int planVersionNo;
    @Column(nullable = false, length = 8) private String lifecycle = "DRAFT";
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "superseded_by_plan_id") private UUID supersededByPlanId;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "retired_by") private UUID retiredBy;
    @Column(name = "retired_at") private Instant retiredAt;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
