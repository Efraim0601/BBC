package com.bbc.sms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FeeDtos {

    public record FeeConfigView(
            UUID id,
            String level,
            String subsystem,
            long total,
            List<Long> tranches,
            List<Map<String, Object>> items) {}

    public record FeeConfigUpdate(
            @NotBlank String level,
            String subsystem,
            @Positive long total,
            List<Long> tranches,
            List<Map<String, Object>> items) {}

    public record SituationView(
            UUID studentId,
            String studentName,
            String className,
            long total,
            long paid,
            long balance,
            int tranchesPaid,
            String status,
            int progressPct) {}
}
