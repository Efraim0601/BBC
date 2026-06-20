package com.bbc.sms.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class EventDtos {

    public record EventView(
            UUID id,
            String title,
            String type,
            LocalDate eventDate,
            String description,
            String audience,
            List<String> targetClasses,
            boolean notified,
            LocalDate notifiedAt) {}

    public record EventUpsert(
            @NotBlank String title,
            @NotBlank String type,
            @NotNull LocalDate eventDate,
            String description,
            String audience,                 // all | classes
            List<String> targetClasses) {}

    /** Result of triggering parent notifications (SMS/WhatsApp simulated for now). */
    public record NotifyResult(UUID eventId, int notifiedCount) {}
}
