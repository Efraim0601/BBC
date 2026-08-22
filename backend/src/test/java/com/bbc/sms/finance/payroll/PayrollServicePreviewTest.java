package com.bbc.sms.finance.payroll;

import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.finance.PaymentChannelRepository;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.treasury.TreasuryService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServicePreviewTest {
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();
    private static final UUID LIABILITY_ID = UUID.randomUUID();

    @Mock PayrollComponentTypeRepository components;
    @Mock PayrollPeriodRepository periods;
    @Mock PayrollRunRepository runs;
    @Mock EmployeePayrollRepository employeePayrolls;
    @Mock EmployeePayrollLineRepository payrollLines;
    @Mock PayrollPaymentRepository payments;
    @Mock PayslipJobRepository payslipJobs;
    @Mock PayslipJobResultRepository payslipJobResults;
    @Mock PayslipRepository payslips;
    @Mock EmployeeRepository employees;
    @Mock ChartOfAccountRepository accounts;
    @Mock PaymentChannelRepository channels;
    @Mock AccountingPeriodService accountingPeriods;
    @Mock LedgerPostingService ledger;
    @Mock DocumentSequenceService sequences;
    @Mock IdempotencyService idempotency;
    @Mock AuditService audit;
    @Mock OfficialDocumentService officialDocuments;
    @Mock PayrollPdfRenderer pdf;
    @Mock JdbcTemplate jdbc;
    @Mock AuthorizationPolicyService policy;
    @Mock TreasuryService treasury;

    private PayrollService service;
    private PayrollPeriod period;

    @BeforeEach
    void setUp() {
        TenantContext.set(SCHOOL_ID);
        service = new PayrollService(components, periods, runs, employeePayrolls, payrollLines, payments, payslipJobs,
                payslipJobResults, payslips, employees, accounts, channels, accountingPeriods, ledger, sequences,
                idempotency, audit, officialDocuments, pdf, jdbc, policy, treasury);
        period = new PayrollPeriod();
        period.setId(PERIOD_ID);
        period.setSchoolId(SCHOOL_ID);
        period.setCode("2026-02");
        period.setStartDate(LocalDate.of(2026, 2, 1));
        period.setEndDate(LocalDate.of(2026, 2, 28));
        period.setPaymentDate(LocalDate.of(2026, 2, 28));
        period.setStatus("OPEN");
        when(periods.findByIdAndSchoolId(PERIOD_ID, SCHOOL_ID)).thenReturn(java.util.Optional.of(period));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void previewReportsMonthlyAndHourlyEligibilityWithIntegerTotals() {
        PayrollComponentType monthly = component("BASE_SALARY", "EARNING", "FIXED", EXPENSE_ID, LIABILITY_ID);
        PayrollComponentType hourly = component("HOURLY_WORK", "EARNING", "HOURLY", EXPENSE_ID, LIABILITY_ID);
        Employee permanent = employee("EMP-001", "Permanent", 300_000, 0, 0, true);
        Employee contractor = employee("EMP-002", "Vacataire", 0, 2_500, 20, true);
        when(employees.findBySchoolId(SCHOOL_ID)).thenReturn(List.of(permanent, contractor));
        when(components.findBySchoolIdAndActiveTrueOrderByCodeAsc(SCHOOL_ID)).thenReturn(List.of(monthly, hourly));

        var preview = service.preview(new PayrollDtos.RunRequest(PERIOD_ID, List.of(), "NONE", 0, true));

        assertThat(preview.employeeCount()).isEqualTo(2);
        assertThat(preview.eligibleCount()).isEqualTo(2);
        assertThat(preview.exceptionCount()).isZero();
        assertThat(preview.grossMinor()).isEqualTo(350_000);
        assertThat(preview.netMinor()).isEqualTo(350_000);
    }

    @Test
    void previewReturnsPreciseSalaryAndAccountMappingBlockers() {
        PayrollComponentType monthly = component("BASE_SALARY", "EARNING", "FIXED", null, null);
        Employee missingSalary = employee("EMP-003", "Permanent", 0, 0, 0, true);
        when(employees.findBySchoolId(SCHOOL_ID)).thenReturn(List.of(missingSalary));
        when(components.findBySchoolIdAndActiveTrueOrderByCodeAsc(SCHOOL_ID)).thenReturn(List.of(monthly));

        var preview = service.preview(new PayrollDtos.RunRequest(PERIOD_ID, List.of(), "NONE", 0, true));

        assertThat(preview.exceptionCount()).isEqualTo(1);
        assertThat(preview.employees().getFirst().exceptionCode()).isEqualTo("MISSING_MONTHLY_SALARY");

        Employee paid = employee("EMP-004", "Permanent", 200_000, 0, 0, true);
        when(employees.findBySchoolId(SCHOOL_ID)).thenReturn(List.of(paid));
        var mappingPreview = service.preview(new PayrollDtos.RunRequest(PERIOD_ID, List.of(), "NONE", 0, true));
        assertThat(mappingPreview.employees().getFirst().exceptionCode()).isEqualTo("ACCOUNT_MAPPING_MISSING");
    }

    private PayrollComponentType component(String code, String kind, String mode, UUID expense, UUID liability) {
        PayrollComponentType component = new PayrollComponentType();
        component.setId(UUID.randomUUID());
        component.setSchoolId(SCHOOL_ID);
        component.setCode(code);
        component.setNameFr(code);
        component.setNameEn(code);
        component.setComponentKind(kind);
        component.setCalculationMode(mode);
        component.setExpenseAccountId(expense);
        component.setLiabilityAccountId(liability);
        component.setActive(true);
        return component;
    }

    private Employee employee(String code, String type, long salary, int rate, int hours, boolean active) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setSchoolId(SCHOOL_ID);
        employee.setCode(code);
        employee.setName(code);
        employee.setType(type);
        employee.setMonthlySalary(salary);
        employee.setHourlyRate(rate);
        employee.setMonthlyHours(hours);
        employee.setHiredOn(LocalDate.of(2025, 9, 1));
        employee.setActive(active);
        return employee;
    }
}
