package com.bbc.sms.academic.dto;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class BulletinDtos {

    public record BulletinLine(
            String subjectCode,
            String subjectLabel,
            int coef,
            BigDecimal mark,
            BigDecimal weighted) {}

    public record BulletinView(
            UUID studentId,
            String studentName,
            String className,
            int sequence,
            List<BulletinLine> lines,
            BigDecimal average,
            int rank,
            int classSize,
            BigDecimal classAverage,
            boolean validated,
            String generalAppreciation,
            boolean financiallyBlocked) {}

    public record PvRow(
            UUID studentId,
            String studentName,
            BigDecimal average,
            int rank) {}

    public record PvView(
            String className,
            int sequence,
            List<PvRow> rows,
            BigDecimal classAverage) {}

    public record ValidateRequest(
            @Min(1) int sequence,
            String generalAppreciation) {}
}
