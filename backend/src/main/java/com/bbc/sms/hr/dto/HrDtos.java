package com.bbc.sms.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** HR / Operations payloads: departments and leave management. */
public class HrDtos {

    public record DepartmentView(UUID id, String name, UUID headEmployeeId, String headName, long memberCount) {}

    public record DepartmentUpsert(@NotBlank String name, UUID headEmployeeId) {}

    public record LeaveView(
            UUID id,
            UUID employeeId,
            String employeeName,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            int days,
            String reason,
            String status,
            OffsetDateTime decidedAt) {}

    public record LeaveCreate(
            @NotNull UUID employeeId,
            @NotBlank String type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            String reason) {}

    public record LeaveDecision(@NotBlank String status) {}   // approved | rejected
}
