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
import java.util.UUID;

@Entity
@Table(name = "employee_payroll_line")
@Getter
@Setter
public class EmployeePayrollLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "employee_payroll_id", nullable = false) private UUID employeePayrollId;
    @Column(name = "line_no", nullable = false) private int lineNo;
    @Column(name = "component_type_id") private UUID componentTypeId;
    @Column(name = "component_code", nullable = false, length = 64) private String componentCode;
    @Column(name = "component_name_fr", nullable = false, length = 160) private String componentNameFr;
    @Column(name = "component_name_en", nullable = false, length = 160) private String componentNameEn;
    @Column(name = "component_kind", nullable = false, length = 28) private String componentKind;
    @Column(name = "calculation_mode", nullable = false, length = 20) private String calculationMode;
    @Column(nullable = false) private long quantity;
    @Column(name = "rate_bps", nullable = false) private int rateBps;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 12) private String source = "DEFAULT";
    @Column(length = 500) private String reason;
    @Column(name = "expense_account_id") private UUID expenseAccountId;
    @Column(name = "liability_account_id") private UUID liabilityAccountId;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
