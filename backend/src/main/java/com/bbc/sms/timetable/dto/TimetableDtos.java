package com.bbc.sms.timetable.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class TimetableDtos {
    public record ClassRef(UUID id, String name, String sectionId, String subsystem, String level,
                           String model, String status, UUID homeroomTeacherId,
                           String homeroomTeacherName, long version) {}

    /**
     * The effective teacher for one subject in one class for the current session.
     * The timetable displays this mapping but never treats its teacher as editable.
     */
    public record SubjectTeacherView(String subjectCode, UUID teacherId, String teacherName,
                                     String teacherCode, String source, boolean locked,
                                     String message, String status, String errorCode,
                                     UUID assignmentId, long assignmentVersion) {
        public SubjectTeacherView(String subjectCode, UUID teacherId, String teacherName,
                                  String teacherCode, String source, boolean locked, String message) {
            this(subjectCode, teacherId, teacherName, teacherCode, source, locked, message,
                    teacherId == null ? "MISSING" : "RESOLVED",
                    teacherId == null ? "ASSIGNMENT_MISSING" : "ASSIGNMENT_RESOLVED",
                    null, 0);
        }
    }

    public record PeriodView(UUID id, int slotIdx, String label, String startTime,
                             String endTime, boolean active) {}
    public record PeriodRequest(@NotBlank String label, @NotBlank String startTime,
                                @NotBlank String endTime, boolean active) {}

    public record ClassConfigRequest(UUID homeroomTeacherId, long version) {}
    public record TeacherAssignmentRequest(List<String> subjectCodes) {}
    public record PlanActionRequest(long version, String reason) {}

    public record SlotView(UUID id, int dayIdx, int slotIdx, String subjectCode,
                           UUID teacherId, String room, String className, String subjectName) {
        public SlotView(UUID id, int dayIdx, int slotIdx, String subjectCode,
                        UUID teacherId, String room, String className) {
            this(id, dayIdx, slotIdx, subjectCode, teacherId, room, className, null);
        }
    }

    public record SlotUpsert(@NotBlank String className, @Min(0) int dayIdx,
                             @Min(0) int slotIdx, String subjectCode,
                             UUID teacherId, String room) {}

    public record ConflictSlot(UUID classId, String className, String subjectCode, String room) {}
    public record TeacherConflict(int dayIdx, int slotIdx, UUID teacherId,
                                  String teacherName, List<ConflictSlot> slots) {}
    public record SlotSaveResult(SlotView slot, List<TeacherConflict> conflicts) {}
    /**
     * A teacher-facing schedule is self describing.  Teachers may read their
     * published timetable without having the administrator-only
     * TIMETABLE_MASTER_VIEW permission, so the bell-period metadata travels
     * with the schedule instead of requiring a second privileged request.
     */
    public record TeacherSchedule(UUID teacherId, String teacherName, String sessionLabel,
                                  List<PeriodView> periods, List<SlotView> slots) {
        public TeacherSchedule(UUID teacherId, String teacherName, String sessionLabel,
                               List<SlotView> slots) {
            this(teacherId, teacherName, sessionLabel, List.of(), slots);
        }
    }
}
