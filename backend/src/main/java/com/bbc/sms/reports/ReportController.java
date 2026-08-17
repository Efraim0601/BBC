package com.bbc.sms.reports;

import com.bbc.sms.finance.reporting.FinanceReportingService;
import com.bbc.sms.reports.dto.ReportDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;
    private final FinanceReportingService financeReporting;

    public ReportController(ReportService service, FinanceReportingService financeReporting) {
        this.service = service;
        this.financeReporting = financeReporting;
    }

    @GetMapping("/finance")
    @PreAuthorize("@policy.canAction('FINANCE_REPORT_VIEW')")
    public FinanceReport finance() {
        return financeReporting.legacyFinance();
    }

    @GetMapping("/attendance/monthly")
    @PreAuthorize("@policy.canAction('REPORTS_VIEW')")
    public List<AttendanceRow> attendanceMonthly(@RequestParam(required = false) String month) {
        return service.attendanceMonthly(month);
    }

    @GetMapping("/demographics")
    @PreAuthorize("@policy.canAction('REPORTS_VIEW')")
    public Demographics demographics() {
        return service.demographics();
    }
}
