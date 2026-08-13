package com.bbc.sms.finance.charges;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "charge_generation_result")
@Getter
@Setter
public class ChargeGenerationResult {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "student_enrollment_id") private UUID studentEnrollmentId;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "fee_plan_id") private UUID feePlanId;
    @Column(name = "fee_plan_line_id") private UUID feePlanLineId;
    @Column(name = "student_charge_id") private UUID studentChargeId;
    @Column(name = "school_class_id") private UUID schoolClassId;
    @Column(name = "class_name_snapshot", length = 160) private String classNameSnapshot;
    @Column(name = "result_status", nullable = false, length = 18) private String resultStatus;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency = "XAF";
    @Column(name = "blocker_code", length = 80) private String blockerCode;
    @Column(name = "blocker_message", length = 1000) private String blockerMessage;
    @Column(name = "action_link", length = 240) private String actionLink;
    @Column(name = "error_detail", length = 1000) private String errorDetail;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
