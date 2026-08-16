package com.bbc.sms.coursebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public class CoursebookDtos {

    /** Class reference filtered by the authenticated teacher/parcours scope. */
    public record ClassRef(UUID id, String name, String sectionId, String subsystem, String level) {}

    /** One day's log line for a class: what was covered + the homework set. */
    public record EntryView(
            UUID id,
            String className,
            String subjectCode,
            String subjectLabel,
            LocalDate entryDate,
            String content,
            String homework,
            LocalDate dueDate) {}

    /** Create/update a coursebook entry. */
    public record EntryUpsert(
            @NotBlank String className,
            @NotBlank String subjectCode,
            @NotNull LocalDate entryDate,
            @NotBlank String content,
            String homework,
            LocalDate dueDate) {}
}
