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
            UUID classId,
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
            UUID classId,
            String className,
            String subsystem,
            String level,
            String parentName,
            String parentPhone) {}

    /** A real parent login linked to this student (review issue #2). */
    public record ParentAccountView(
            UUID userId,
            String displayName,
            String username,
            boolean active,
            int childCount) {}

    public record ParentLinkRequest(
            @NotBlank String displayName,
            @NotBlank String username,
            String password) {}
}
