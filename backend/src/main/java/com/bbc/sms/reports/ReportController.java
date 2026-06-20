package com.bbc.sms.reports;

import com.bbc.sms.reports.dto.ReportDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/finance")
    @PreAuthorize("@perm.can('reports','read')")
    public FinanceReport finance() {
        return service.finance();
    }

    @GetMapping("/attendance/monthly")
    @PreAuthorize("@perm.can('reports','read')")
    public List<AttendanceRow> attendanceMonthly(@RequestParam(required = false) String month) {
        return service.attendanceMonthly(month);
    }

    @GetMapping("/demographics")
    @PreAuthorize("@perm.can('reports','read')")
    public Demographics demographics() {
        return service.demographics();
    }
}
