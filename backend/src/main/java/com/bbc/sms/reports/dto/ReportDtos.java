package com.bbc.sms.reports.dto;

import java.util.Map;
import java.util.UUID;

/** Read-only DTOs for the reports module. No JPA — all values are aggregated via JdbcTemplate. */
public class ReportDtos {

    /** Finance snapshot for the current tenant. Money is integer FCFA. */
    public record FinanceReport(
            long totalRevenue,
            long totalExpense,
            long balance,
            double recoveryRate) {}

    /** One row per student for the monthly attendance report. */
    public record AttendanceRow(
            UUID studentId,
            String studentName,
            String className,
            int present,
            int late,
            int absent,
            int rate) {}

    /** Active-student demographics broken down across three dimensions. */
    public record Demographics(
            long total,
            Map<String, Long> byLevel,
            Map<String, Long> bySubsystem,
            Map<String, Long> bySex) {}
}
