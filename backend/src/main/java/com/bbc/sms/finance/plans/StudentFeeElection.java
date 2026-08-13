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
import java.util.UUID;

@Entity
@Table(name = "student_fee_election")
@Getter
@Setter
public class StudentFeeElection {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_enrollment_id", nullable = false) private UUID studentEnrollmentId;
    @Column(name = "fee_plan_line_id", nullable = false) private UUID feePlanLineId;
    @Column(nullable = false, length = 10) private String status = "PENDING";
    @Column(length = 500) private String reason;
    @Column(name = "acted_by") private UUID actedBy;
    @Column(name = "acted_at") private Instant actedAt;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
