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

    /**
     * @param allowOverlap force l'enregistrement d'un créneau qui met l'enseignant
     *                     dans deux classes à la même heure (classes regroupées).
     *                     Le chevauchement reste signalé après coup.
     */
    public record SlotUpsert(
            @NotBlank String className,
            @Min(0) int dayIdx,
            @Min(0) int slotIdx,
            String subjectCode,
            UUID teacherId,
            String room,
            boolean allowOverlap) {}

    /** Un créneau impliqué dans un chevauchement. */
    public record ConflictSlot(
            UUID classId,
            String className,
            String subjectCode,
            String room) {}

    /** Un même enseignant placé dans plusieurs classes (donc plusieurs salles) au même créneau. */
    public record TeacherConflict(
            int dayIdx,
            int slotIdx,
            UUID teacherId,
            String teacherName,
            List<ConflictSlot> slots) {}

    public record SlotSaveResult(
            SlotView slot,
            List<TeacherConflict> conflicts) {}
}
