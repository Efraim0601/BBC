package com.bbc.sms.student.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public class StudentDtos {

    public record StudentView(
            UUID id,
            String matricule,
            String firstName,
            String lastName,
            String name,
            String sex,
            LocalDate dob,
            String className,
            String subsystem,
            String level,
            String parentName,
            String parentPhone,
            int photoHue) {}

    public record StudentUpsert(
            @NotBlank String firstName,
            @NotBlank String lastName,
            String sex,
            LocalDate dob,
            String className,
            String subsystem,
            String level,
            String parentName,
            String parentPhone) {}
}
