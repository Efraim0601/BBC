package com.bbc.sms.timetable;

import com.bbc.sms.timetable.dto.TimetableDtos.*;
import com.bbc.sms.timetable.dto.TimetableVersionDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService service;
    private final TimetableVersionService versions;

    public TimetableController(TimetableService service, TimetableVersionService versions) {
        this.service = service; this.versions = versions;
    }

    @GetMapping("/versions")
    @PreAuthorize("@policy.canAction('TIMETABLE_MASTER_VIEW')")
    public List<TimetableVersionView> versions(@RequestParam java.util.UUID academicSessionId) {
        return versions.list(academicSessionId);
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("@policy.canAction('TIMETABLE_MASTER_VIEW')")
    public TimetableVersionView version(@PathVariable java.util.UUID id) { return versions.view(id); }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public TimetableVersionView createVersion(@Valid @RequestBody TimetableVersionUpsert in) { return versions.create(in); }

    @PostMapping("/versions/{id}/publish")
    @PreAuthorize("@policy.canAction('TIMETABLE_PUBLISH')")
    public TimetableVersionView publishVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.publish(id, in); }

    @PostMapping("/versions/{id}/reopen")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('TIMETABLE_REOPEN')")
    public TimetableVersionView reopenVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.reopenAsNew(id, in); }

    @PostMapping("/versions/{id}/archive")
    @PreAuthorize("@policy.canAction('TIMETABLE_ARCHIVE')")
    public TimetableVersionView archiveVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.archive(id, in); }

    @GetMapping("/versions/diff")
    @PreAuthorize("@policy.canAction('TIMETABLE_MASTER_VIEW')")
    public TimetableVersionDiff diff(@RequestParam java.util.UUID fromVersionId, @RequestParam java.util.UUID toVersionId) { return versions.diff(fromVersionId, toVersionId); }

    @GetMapping("/versions/{id}/drift")
    @PreAuthorize("@policy.canAction('TIMETABLE_MASTER_VIEW')")
    public List<TimetableDriftView> drift(@PathVariable java.util.UUID id) { return versions.drift(id); }

    @GetMapping("/versions/{id}/master")
    @PreAuthorize("@policy.canAction('TIMETABLE_MASTER_VIEW')")
    public List<TimetableProjectionSlotView> master(@PathVariable java.util.UUID id,
                                                     @RequestParam(required = false) java.time.LocalDate occurrenceDate) {
        return versions.master(id, occurrenceDate);
    }

    @GetMapping("/versions/{id}/export.csv")
    @PreAuthorize("@policy.canAction('TIMETABLE_EXPORT')")
    public org.springframework.http.ResponseEntity<String> exportCsv(@PathVariable java.util.UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition", "attachment; filename=timetable-"+id+".csv").header("X-Timetable-Version", id.toString()).body(versions.exportCsv(id));
    }

    @GetMapping("/versions/{id}/export.ics")
    @PreAuthorize("@policy.canAction('TIMETABLE_EXPORT')")
    public org.springframework.http.ResponseEntity<String> exportIcal(@PathVariable java.util.UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "text/calendar; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".ics").body(versions.exportIcal(id));
    }

    @GetMapping("/versions/{id}/export.xlsx")
    @PreAuthorize("@policy.canAction('TIMETABLE_EXPORT')")
    public org.springframework.http.ResponseEntity<byte[]> exportXlsx(@PathVariable java.util.UUID id) {
        byte[] content = versions.exportXlsx(id);
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".xlsx")
                .header("X-Timetable-Version", id.toString()).header("X-Timetable-Checksum", checksum(content)).body(content);
    }

    @GetMapping("/versions/{id}/export.pdf")
    @PreAuthorize("@policy.canAction('TIMETABLE_EXPORT')")
    public org.springframework.http.ResponseEntity<byte[]> exportPdf(@PathVariable java.util.UUID id) {
        byte[] content = versions.exportPdf(id);
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".pdf")
                .header("X-Timetable-Version", id.toString()).header("X-Timetable-Checksum", checksum(content)).body(content);
    }

    @GetMapping("/resources/rooms")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<RoomView> roomsV2() { return versions.rooms(); }

    @PostMapping("/resources/rooms")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public RoomView saveRoom(@RequestParam(required=false) java.util.UUID id, @Valid @RequestBody RoomUpsert in) { return versions.saveRoom(id, in); }

    @GetMapping("/resources/rooms/{roomId}/availability")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<RoomAvailabilityView> roomAvailability(@PathVariable java.util.UUID roomId) { return versions.roomAvailability(roomId); }

    @PutMapping("/resources/rooms/{roomId}/availability")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public RoomAvailabilityView saveRoomAvailability(@PathVariable java.util.UUID roomId,
                                                      @Valid @RequestBody RoomAvailabilityUpsert in) { return versions.saveRoomAvailability(roomId, in); }

    @GetMapping("/resources/teachers/{teacherId}/availability")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<TeacherAvailabilityView> teacherAvailability(@PathVariable java.util.UUID teacherId) { return versions.teacherAvailability(teacherId); }

    @PutMapping("/resources/teachers/{teacherId}/availability")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public TeacherAvailabilityView saveTeacherAvailability(@PathVariable java.util.UUID teacherId,
                                                            @Valid @RequestBody TeacherAvailabilityUpsert in) { return versions.saveTeacherAvailability(teacherId, in); }

    @GetMapping("/resources/teachers/{teacherId}/workload")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<TeacherWorkloadView> teacherWorkload(@PathVariable java.util.UUID teacherId) { return versions.teacherWorkload(teacherId); }

    @PutMapping("/resources/teachers/{teacherId}/workload")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public TeacherWorkloadView saveTeacherWorkload(@PathVariable java.util.UUID teacherId,
                                                    @Valid @RequestBody TeacherWorkloadUpsert in) { return versions.saveTeacherWorkload(teacherId, in); }

    @GetMapping("/resources/teachers/{teacherId}/qualifications")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<TeacherQualificationView> teacherQualifications(@PathVariable java.util.UUID teacherId) { return versions.teacherQualifications(teacherId); }

    @PostMapping("/resources/teachers/{teacherId}/qualifications")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public TeacherQualificationView saveTeacherQualification(@PathVariable java.util.UUID teacherId,
                                                              @Valid @RequestBody TeacherQualificationUpsert in) { return versions.saveTeacherQualification(teacherId, in); }

    @GetMapping("/resources/subjects/qualification-requirements")
    @PreAuthorize("@policy.canAction('TIMETABLE_RESOURCE_VIEW')")
    public List<SubjectQualificationRequirementView> subjectQualificationRequirements(@RequestParam java.util.UUID academicSessionId) { return versions.subjectQualificationRequirements(academicSessionId); }

    @PutMapping("/resources/subjects/qualification-requirements")
    @PreAuthorize("@policy.canAction('TIMETABLE_DRAFT')")
    public SubjectQualificationRequirementView saveSubjectQualificationRequirement(@Valid @RequestBody SubjectQualificationRequirementUpsert in) { return versions.saveSubjectQualificationRequirement(in); }

    @GetMapping("/substitutions")
    @PreAuthorize("@perm.canAction('TIMETABLE_SUBSTITUTION_VIEW')")
    public List<SubstitutionView> substitutions(@RequestParam java.util.UUID academicSessionId, @RequestParam(required=false) java.time.LocalDate occurrenceDate) { return versions.substitutions(academicSessionId, occurrenceDate); }

    @PostMapping("/substitutions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('TIMETABLE_SUBSTITUTION_MANAGE')")
    public SubstitutionView createSubstitution(@Valid @RequestBody SubstitutionUpsert in) { return versions.createSubstitution(in); }

    @PostMapping("/substitutions/{id}/approve")
    @PreAuthorize("@perm.canAction('TIMETABLE_SUBSTITUTION_MANAGE')")
    public SubstitutionView approveSubstitution(@PathVariable java.util.UUID id, @Valid @RequestBody SubstitutionActionRequest in) { return versions.approveSubstitution(id, in); }

    @PostMapping("/substitutions/{id}/cancel")
    @PreAuthorize("@perm.canAction('TIMETABLE_SUBSTITUTION_MANAGE')")
    public SubstitutionView cancelSubstitution(@PathVariable java.util.UUID id, @Valid @RequestBody SubstitutionActionRequest in) { return versions.cancelSubstitution(id, in); }

    @GetMapping("/classes")
    @PreAuthorize("@perm.canAction('TIMETABLE_CLASS_SCHEDULE_VIEW')")
    public List<ClassRef> classes() {
        return service.classes();
    }

    @GetMapping("/classes/{classId}/subject-teachers")
    @PreAuthorize("@perm.canAction('TIMETABLE_CLASS_SCHEDULE_VIEW')")
    public List<SubjectTeacherView> subjectTeachers(@PathVariable java.util.UUID classId) {
        return service.subjectTeachers(classId);
    }

    /** Distinct room labels already used in this school (suggestions for the slot editor). */
    @GetMapping("/rooms")
    @PreAuthorize("@perm.canAction('TIMETABLE_ROOM_VIEW')")
    public List<String> rooms() {
        return service.rooms();
    }

    @GetMapping("/periods")
    @PreAuthorize("@perm.canAction('TIMETABLE_MASTER_VIEW')")
    public List<PeriodView> periods() { return service.periods(); }

    @PutMapping("/periods/{slotIdx}")
    @PreAuthorize("@perm.canAction('TIMETABLE_DRAFT')")
    public PeriodView updatePeriod(@PathVariable int slotIdx, @Valid @RequestBody PeriodRequest in) {
        return service.updatePeriod(slotIdx, in);
    }

    @GetMapping
    @PreAuthorize("@perm.canAction('TIMETABLE_CLASS_SCHEDULE_VIEW')")
    public List<SlotView> grid(@RequestParam String className, @RequestParam(required = false) java.util.UUID versionId) {
        return service.grid(className, versionId);
    }

    /** Chevauchements d'enseignant sur toute la grille : un professeur, deux classes, la même heure. */
    @GetMapping("/conflicts")
    @PreAuthorize("@perm.canAction('TIMETABLE_MASTER_VIEW')")
    public List<TeacherConflict> conflicts() {
        return service.conflicts();
    }

    @PutMapping("/slot")
    @PreAuthorize("@perm.canAction('TIMETABLE_DRAFT')")
    public SlotSaveResult upsertSlot(@Valid @RequestBody SlotUpsert in) {
        return service.upsertSlot(in);
    }

    @PutMapping("/classes/{classId}/config")
    @PreAuthorize("@perm.canAction('TIMETABLE_DRAFT')")
    public ClassRef configure(@PathVariable java.util.UUID classId, @Valid @RequestBody ClassConfigRequest in) {
        return service.configure(classId, in);
    }

    @PutMapping("/classes/{classId}/teachers/{teacherId}")
    @PreAuthorize("@perm.canAction('TIMETABLE_DRAFT')")
    public void assignTeacher(@PathVariable java.util.UUID classId,
                              @PathVariable java.util.UUID teacherId,
                              @RequestBody TeacherAssignmentRequest in) {
        service.assignTeacher(classId, teacherId, in);
    }

    @PostMapping("/classes/{classId}/publish")
    @PreAuthorize("@perm.canAction('TIMETABLE_PUBLISH')")
    public ClassRef publish(@PathVariable java.util.UUID classId, @Valid @RequestBody PlanActionRequest in) {
        return service.publish(classId, in);
    }

    @PostMapping("/classes/{classId}/reopen")
    @PreAuthorize("@perm.canAction('TIMETABLE_REOPEN')")
    public ClassRef reopen(@PathVariable java.util.UUID classId, @Valid @RequestBody PlanActionRequest in) {
        return service.reopen(classId, in);
    }

    @GetMapping("/teachers/me")
    @PreAuthorize("@perm.canAction('TIMETABLE_MY_SCHEDULE_VIEW')")
    public TeacherSchedule mySchedule() { return service.mySchedule(); }

    @GetMapping("/teachers/{teacherId}")
    @PreAuthorize("@perm.canAction('TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL')")
    public TeacherSchedule teacherSchedule(@PathVariable java.util.UUID teacherId) {
        return service.teacherSchedule(teacherId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('TIMETABLE_DRAFT')")
    public void deleteSlot(@RequestParam String className,
                           @RequestParam int dayIdx,
                           @RequestParam int slotIdx) {
        service.deleteSlot(className, dayIdx, slotIdx);
    }

    private static String checksum(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash timetable export", ex);
        }
    }
}
