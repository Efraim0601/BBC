package com.bbc.sms.finance.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_payroll")
@Getter
@Setter
public class EmployeePayroll {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "payroll_run_id", nullable = false) private UUID payrollRunId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "employee_code", nullable = false, length = 32) private String employeeCode;
    @Column(name = "employee_name", nullable = false, length = 160) private String employeeName;
    @Column(name = "employee_email", length = 180) private String employeeEmail;
    @Column(name = "employment_type", nullable = false, length = 32) private String employmentType;
    @Column(name = "hired_on_snapshot") private LocalDate hiredOnSnapshot;
    @Column(name = "exited_on_snapshot") private LocalDate exitedOnSnapshot;
    @Column(name = "employment_mode", nullable = false, length = 16) private String employmentMode;
    @Column(name = "monthly_salary_minor", nullable = false) private long monthlySalaryMinor;
    @Column(name = "hourly_rate_minor", nullable = false) private long hourlyRateMinor;
    @Column(name = "approved_hours", nullable = false) private int approvedHours;
    @Column(nullable = false) private boolean eligible = true;
    @Column(nullable = false, length = 16) private String status = "READY";
    @Column(name = "exception_code", length = 80) private String exceptionCode;
    @Column(name = "exception_message", length = 1000) private String exceptionMessage;
    @Column(length = 500) private String formula;
    @Column(name = "gross_minor", nullable = false) private long grossMinor;
    @Column(name = "deduction_minor", nullable = false) private long deductionMinor;
    @Column(name = "net_minor", nullable = false) private long netMinor;
    @Column(name = "employer_cost_minor", nullable = false) private long employerCostMinor;
    @Column(name = "snapshot_hash", nullable = false, length = 64) private String snapshotHash;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
