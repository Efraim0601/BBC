package com.bbc.sms.timetable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TimetableVersionDtos {
    private TimetableVersionDtos() {}

    public record TimetableVersionView(UUID id, UUID academicSessionId, int versionNo, String status,
                                       LocalDate effectiveFrom, LocalDate effectiveTo, String timezone,
                                       UUID copiedFromVersionId, int slotCount, int classCount,
                                       long version) {}

    public record TimetableVersionUpsert(@NotNull UUID academicSessionId, @NotNull LocalDate effectiveFrom,
                                         LocalDate effectiveTo, String timezone, UUID copyFromVersionId,
                                         @NotBlank String reason) {}

    public record TimetableVersionActionRequest(@NotBlank String reason, Long version) {}

    public record TimetableVersionDiff(UUID fromVersionId, UUID toVersionId, int added, int removed,
                                       int changed, List<String> changes) {}

    public record RoomView(UUID id, String code, String label, Integer capacity,
                           String resourceType, boolean active, long version) {}

    public record RoomUpsert(@NotBlank String code, @NotBlank String label, Integer capacity,
                             String resourceType, boolean active, Long version) {}

    public record RoomAvailabilityView(UUID id, UUID roomId, int dayIdx, int slotIdx,
                                       boolean available, String reason) {}

    public record RoomAvailabilityUpsert(int dayIdx, int slotIdx, boolean available, String reason) {}

    public record TeacherAvailabilityView(UUID id, UUID employeeId, int dayIdx, int slotIdx,
                                          boolean available, String reason) {}

    public record TeacherAvailabilityUpsert(int dayIdx, int slotIdx, boolean available, String reason) {}

    public record TeacherWorkloadView(UUID id, UUID employeeId, Integer maxSlotsPerDay,
                                      Integer maxSlotsPerWeek, LocalDate effectiveFrom,
                                      LocalDate effectiveTo, String reason, long version) {}

    public record TeacherWorkloadUpsert(Integer maxSlotsPerDay, Integer maxSlotsPerWeek,
                                        @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
                                        String reason, Long version) {}

    public record TeacherQualificationView(UUID id, UUID employeeId, String qualificationCode,
                                          LocalDate validFrom, LocalDate validTo,
                                          String evidenceReference, long version) {}

    public record TeacherQualificationUpsert(@NotBlank String qualificationCode,
                                             @NotNull LocalDate validFrom, LocalDate validTo,
                                             String evidenceReference, Long version) {}

    public record SubjectQualificationRequirementView(UUID id, UUID academicSessionId,
                                                      String subjectCode, String qualificationCode,
                                                      LocalDate effectiveFrom, LocalDate effectiveTo,
                                                      String reason, long version) {}

    public record SubjectQualificationRequirementUpsert(@NotNull UUID academicSessionId,
                                                        @NotBlank String subjectCode,
                                                        @NotBlank String qualificationCode,
                                                        @NotNull LocalDate effectiveFrom,
                                                        LocalDate effectiveTo, String reason,
                                                        Long version) {}

    public record TimetableDriftView(UUID slotId, UUID classId, String className, String subjectCode,
                                     int dayIdx, int slotIdx, UUID publishedTeacherId,
                                     String publishedTeacherName, UUID currentTeacherId,
                                     String currentTeacherName, UUID publishedAssignmentId,
                                     long publishedAssignmentVersion, UUID currentAssignmentId,
                                     long currentAssignmentVersion, String status, String message) {}

    public record TimetableProjectionSlotView(UUID id, UUID classId, String className,
                                               String subjectCode, UUID teacherId,
                                               String teacherName, String room,
                                               int dayIdx, int slotIdx, LocalDate occurrenceDate,
                                               String substitutionAction, String substitutionTeacherName) {}

    public record SubstitutionView(UUID id, UUID academicSessionId, UUID timetableVersionId,
                                   LocalDate occurrenceDate, UUID classId, String className,
                                   String subjectCode, int dayIdx, int slotIdx,
                                   UUID originalTeacherId, String originalTeacherName,
                                   UUID replacementTeacherId, String replacementTeacherName,
                                   String action, String reason, String status, long version) {}

    public record SubstitutionUpsert(@NotNull UUID academicSessionId, UUID timetableVersionId,
                                     @NotNull LocalDate occurrenceDate, @NotNull UUID classId,
                                     String subjectCode, int dayIdx, int slotIdx,
                                     UUID originalTeacherId, UUID replacementTeacherId,
                                     @NotBlank String action, @NotBlank String reason) {}

    public record SubstitutionActionRequest(@NotBlank String reason, Long version) {}
}
