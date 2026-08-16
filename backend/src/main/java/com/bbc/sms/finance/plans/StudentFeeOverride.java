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
@Table(name = "student_fee_override")
@Getter
@Setter
public class StudentFeeOverride {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "fee_plan_line_id", nullable = false) private UUID feePlanLineId;
    @Column(name = "override_type", nullable = false, length = 10) private String overrideType;
    @Column(name = "amount_minor") private Long amountMinor;
    @Column(name = "percentage_basis_points") private Integer percentageBasisPoints;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, length = 10) private String status = "REQUESTED";
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requested_at", insertable = false, updatable = false) private Instant requestedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
