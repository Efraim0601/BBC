package com.bbc.sms.staff;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_application")
@Getter
@Setter
public class StaffApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(nullable = false, length = 120)
    private String name;

    private String sex;

    @Column(nullable = false, length = 16)
    private String type = "Permanent";

    private String email;
    private String phone;

    @Column(name = "form_class")
    private String formClass;

    @Column(name = "department_hint")
    private String departmentHint;

    @Column(name = "desired_roles")
    private String desiredRoles;

    private String notes;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;
}
