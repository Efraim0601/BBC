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
            long monthlySalary,
            int hourlyRate,
            Set<String> roles,
            boolean active) {}

    public record EmployeeUpsert(
            @NotBlank String name,
            String sex,
            String type,
            String email,
            String phone,
            String formClass,
            long monthlySalary,
            int hourlyRate,
            Set<String> roles) {}
}
