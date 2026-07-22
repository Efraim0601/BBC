package com.bbc.sms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class StudentDtos {

    public record StudentView(
            UUID id,
            String matricule,
            String niu,
            String firstName,
            String lastName,
            String name,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
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
            String niu,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
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

    /**
     * One student line in an import batch; class comes from the request, not the row.
     * {@code name} is the official register's single "Nom et Prénom" column — when it
     * is present the server splits it into last/first, so callers may send either the
     * combined {@code name} OR the separate {@code firstName}/{@code lastName}.
     */
    public record StudentImportRow(
            String name,
            String firstName,
            String lastName,
            String niu,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
            String parentName,
            String parentPhone) {}

    /** A class to create on the fly during import, e.g. "5e A" (Francophone, secondary). */
    public record NewClassSpec(
            @NotBlank String name,
            @NotBlank String subsystem,   // FR | EN
            @NotBlank String level) {}    // maternelle | primary | secondary

    /**
     * Import several students at once into ONE class. Provide either an existing
     * {@code classId}, or a {@code newClass} spec to find-or-create the class by name
     * (the "(5e A)" format the registers are organised by).
     */
    public record StudentImportRequest(
            UUID classId,
            NewClassSpec newClass,
            @NotEmpty List<StudentImportRow> rows) {}

    /** A single row that could not be imported, with a human-readable reason. */
    public record StudentImportError(int row, String name, String message) {}

    /** Summary of an import run: how many were created, and per-row failures. */
    public record StudentImportResult(int created, int failed, List<StudentImportError> errors) {}
}
