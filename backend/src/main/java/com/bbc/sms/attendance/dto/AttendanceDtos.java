package com.bbc.sms.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AttendanceDtos {

    public record AttendanceView(
            UUID studentId,
            String matricule,
            String studentName,
            String className,
            LocalDate date,
            String status,
            String checkInTime,
            int lateMinutes,
            String source) {}

    /** Manual marking by a staff member from the UI. */
    public record MarkRequest(
            @NotNull UUID studentId,
            @NotNull LocalDate date,
            @NotBlank String status,         // present | late | absent
            String checkInTime,
            int lateMinutes) {}

    /** Posted by the on-site fingerprint agent. */
    public record DeviceCheckin(
            @NotBlank String matricule,
            String time,                     // HH:mm; defaults to now on the device
            @NotBlank String dedupKey) {}    // device + timestamp, for idempotency

    public record DailyBoard(
            LocalDate date,
            int present,
            int late,
            int absent,
            List<AttendanceView> records) {}
}
