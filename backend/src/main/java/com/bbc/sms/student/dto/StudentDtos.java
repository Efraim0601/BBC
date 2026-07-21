package com.bbc.sms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
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

    // ---- Bulk import (students into a given class) ---------------------------

    /** One student line in an import batch; class comes from the request, not the row. */
    public record StudentImportRow(
            String firstName,
            String lastName,
            String sex,
            LocalDate dob,
            String parentName,
            String parentPhone) {}

    /** Import several students at once into a single existing class. */
    public record StudentImportRequest(
            @NotNull UUID classId,
            @NotEmpty List<StudentImportRow> rows) {}

    /** A single row that could not be imported, with a human-readable reason. */
    public record StudentImportError(int row, String name, String message) {}

    /** Summary of an import run: how many were created, and per-row failures. */
    public record StudentImportResult(int created, int failed, List<StudentImportError> errors) {}
}
