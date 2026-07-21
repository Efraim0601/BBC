package com.bbc.sms.staff.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;
import java.util.UUID;

public class StaffDtos {

    public record EmployeeView(
            UUID id,
            String code,
            String name,
            String initials,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            UUID departmentId,
            String departmentName,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            boolean active,
            boolean hasLogin,
            String username) {}

    public record EmployeeUpsert(
            @NotBlank String name,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            UUID departmentId,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            // When true, the UI will follow up with reset-credentials to provision the
            // login and e-mail the password; create() then skips its courtesy notice so
            // the employee doesn't receive two e-mails.
            Boolean createLogin) {}

    /** Outcome of provisioning/resetting a staff login — never carries the password. */
    public record AccountResult(
            boolean hasAccount,
            String username,
            boolean emailSent,
            String message) {}
}
