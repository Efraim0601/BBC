package com.bbc.sms.timetable.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class TimetableDtos {

    public record ClassRef(
            UUID id,
            String name,
            String sectionId,
            String subsystem,
            String level) {}

    public record SlotView(
            UUID id,
            int dayIdx,
            int slotIdx,
            String subjectCode,
            UUID teacherId,
            String room) {}

    public record SlotUpsert(
            @NotBlank String className,
            @Min(0) int dayIdx,
            @Min(0) int slotIdx,
            String subjectCode,
            UUID teacherId,
            String room) {}

    public record Conflict(
            String className,
            int dayIdx,
            int slotIdx,
            UUID teacherId) {}

    public record SlotSaveResult(
            SlotView slot,
            List<Conflict> conflicts) {}
}
