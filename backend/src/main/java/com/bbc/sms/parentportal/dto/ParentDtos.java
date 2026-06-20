package com.bbc.sms.parentportal.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs for the parent portal module. */
public final class ParentDtos {

    private ParentDtos() {}

    public record ChildView(
            UUID studentId,
            String name,
            String className,
            long balance,
            String feeStatus,
            int attendanceRate
    ) {}

    public record GradeView(
            String subjectCode,
            int sequence,
            BigDecimal mark
    ) {}

    public record SuggestionView(
            UUID id,
            String category,
            String message,
            String status,
            OffsetDateTime createdAt
    ) {}

    public record SuggestionRequest(
            @NotBlank String category,   // suggestion | question | complaint | thanks
            @NotBlank String message
    ) {}
}
