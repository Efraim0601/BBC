package com.bbc.sms.academic.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class AcademicDtos {

    public record GradeView(
            UUID id,
            UUID studentId,
            String subjectCode,
            int sequence,
            BigDecimal mark) {}

    public record GradeUpsert(
            @NotNull UUID studentId,
            @NotBlank String subjectCode,
            @Min(1) int sequence,
            @NotNull @DecimalMin("0.0") @DecimalMax("20.0") BigDecimal mark) {}
}
