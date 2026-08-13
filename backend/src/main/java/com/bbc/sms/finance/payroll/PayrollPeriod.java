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
@Table(name = "payroll_period")
@Getter
@Setter
public class PayrollPeriod {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 48) private String code;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
    @Column(name = "accounting_period_id", nullable = false) private UUID accountingPeriodId;
    @Column(nullable = false, length = 12) private String status = "OPEN";
    @Version private long version;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
