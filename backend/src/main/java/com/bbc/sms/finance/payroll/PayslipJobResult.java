package com.bbc.sms.finance.payroll;

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
@Table(name = "payroll_payslip_job_result")
@Getter
@Setter
public class PayslipJobResult {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "employee_payroll_id", nullable = false) private UUID employeePayrollId;
    @Column(name = "payslip_id") private UUID payslipId;
    @Column(name = "result_status", nullable = false, length = 20) private String resultStatus;
    @Column(name = "error_detail", length = 1000) private String errorDetail;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
