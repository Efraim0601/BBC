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
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation,
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
            String parentPhone,
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation) {}

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

    /** One imported row — same fields as StudentUpsert, minus the class (set for the whole batch). */
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
            String parentPhone,
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation) {}

    public record NewClassSpec(
            @NotBlank String name,
            @NotBlank String subsystem,
            @NotBlank String level) {}

    public record StudentImportRequest(
            UUID classId,
            NewClassSpec newClass,
            @NotEmpty List<StudentImportRow> rows) {}

    public record StudentImportError(int row, String name, String message) {}

    public record StudentImportResult(int created, int failed, List<StudentImportError> errors) {}
}
