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
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TimetableVersionView> versions(@RequestParam java.util.UUID academicSessionId) {
        return versions.list(academicSessionId);
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("@perm.can('timetable','read')")
    public TimetableVersionView version(@PathVariable java.util.UUID id) { return versions.view(id); }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('timetable','write')")
    public TimetableVersionView createVersion(@Valid @RequestBody TimetableVersionUpsert in) { return versions.create(in); }

    @PostMapping("/versions/{id}/publish")
    @PreAuthorize("@perm.can('timetable','write')")
    public TimetableVersionView publishVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.publish(id, in); }

    @PostMapping("/versions/{id}/reopen")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('timetable','write')")
    public TimetableVersionView reopenVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.reopenAsNew(id, in); }

    @PostMapping("/versions/{id}/archive")
    @PreAuthorize("@perm.can('timetable','write')")
    public TimetableVersionView archiveVersion(@PathVariable java.util.UUID id, @Valid @RequestBody TimetableVersionActionRequest in) { return versions.archive(id, in); }

    @GetMapping("/versions/diff")
    @PreAuthorize("@perm.can('timetable','read')")
    public TimetableVersionDiff diff(@RequestParam java.util.UUID fromVersionId, @RequestParam java.util.UUID toVersionId) { return versions.diff(fromVersionId, toVersionId); }

    @GetMapping("/versions/{id}/drift")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TimetableDriftView> drift(@PathVariable java.util.UUID id) { return versions.drift(id); }

    @GetMapping("/versions/{id}/master")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TimetableProjectionSlotView> master(@PathVariable java.util.UUID id,
                                                     @RequestParam(required = false) java.time.LocalDate occurrenceDate) {
        return versions.master(id, occurrenceDate);
    }

    @GetMapping("/versions/{id}/export.csv")
    @PreAuthorize("@perm.can('timetable','read')")
    public org.springframework.http.ResponseEntity<String> exportCsv(@PathVariable java.util.UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition", "attachment; filename=timetable-"+id+".csv").header("X-Timetable-Version", id.toString()).body(versions.exportCsv(id));
    }

    @GetMapping("/versions/{id}/export.ics")
    @PreAuthorize("@perm.can('timetable','read')")
    public org.springframework.http.ResponseEntity<String> exportIcal(@PathVariable java.util.UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "text/calendar; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".ics").body(versions.exportIcal(id));
    }

    @GetMapping("/versions/{id}/export.xlsx")
    @PreAuthorize("@perm.can('timetable','read')")
    public org.springframework.http.ResponseEntity<byte[]> exportXlsx(@PathVariable java.util.UUID id) {
        byte[] content = versions.exportXlsx(id);
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".xlsx")
                .header("X-Timetable-Version", id.toString()).header("X-Timetable-Checksum", checksum(content)).body(content);
    }

    @GetMapping("/versions/{id}/export.pdf")
    @PreAuthorize("@perm.can('timetable','read')")
    public org.springframework.http.ResponseEntity<byte[]> exportPdf(@PathVariable java.util.UUID id) {
        byte[] content = versions.exportPdf(id);
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=timetable-"+id+".pdf")
                .header("X-Timetable-Version", id.toString()).header("X-Timetable-Checksum", checksum(content)).body(content);
    }

    @GetMapping("/resources/rooms")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<RoomView> roomsV2() { return versions.rooms(); }

    @PostMapping("/resources/rooms")
    @PreAuthorize("@perm.can('timetable','write')")
    public RoomView saveRoom(@RequestParam(required=false) java.util.UUID id, @Valid @RequestBody RoomUpsert in) { return versions.saveRoom(id, in); }

    @GetMapping("/resources/rooms/{roomId}/availability")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<RoomAvailabilityView> roomAvailability(@PathVariable java.util.UUID roomId) { return versions.roomAvailability(roomId); }

    @PutMapping("/resources/rooms/{roomId}/availability")
    @PreAuthorize("@perm.can('timetable','write')")
    public RoomAvailabilityView saveRoomAvailability(@PathVariable java.util.UUID roomId,
                                                      @Valid @RequestBody RoomAvailabilityUpsert in) { return versions.saveRoomAvailability(roomId, in); }

    @GetMapping("/resources/teachers/{teacherId}/availability")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TeacherAvailabilityView> teacherAvailability(@PathVariable java.util.UUID teacherId) { return versions.teacherAvailability(teacherId); }

    @PutMapping("/resources/teachers/{teacherId}/availability")
    @PreAuthorize("@perm.can('timetable','write')")
    public TeacherAvailabilityView saveTeacherAvailability(@PathVariable java.util.UUID teacherId,
                                                            @Valid @RequestBody TeacherAvailabilityUpsert in) { return versions.saveTeacherAvailability(teacherId, in); }

    @GetMapping("/resources/teachers/{teacherId}/workload")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TeacherWorkloadView> teacherWorkload(@PathVariable java.util.UUID teacherId) { return versions.teacherWorkload(teacherId); }

    @PutMapping("/resources/teachers/{teacherId}/workload")
    @PreAuthorize("@perm.can('timetable','write')")
    public TeacherWorkloadView saveTeacherWorkload(@PathVariable java.util.UUID teacherId,
                                                    @Valid @RequestBody TeacherWorkloadUpsert in) { return versions.saveTeacherWorkload(teacherId, in); }

    @GetMapping("/resources/teachers/{teacherId}/qualifications")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TeacherQualificationView> teacherQualifications(@PathVariable java.util.UUID teacherId) { return versions.teacherQualifications(teacherId); }

    @PostMapping("/resources/teachers/{teacherId}/qualifications")
    @PreAuthorize("@perm.can('timetable','write')")
    public TeacherQualificationView saveTeacherQualification(@PathVariable java.util.UUID teacherId,
                                                              @Valid @RequestBody TeacherQualificationUpsert in) { return versions.saveTeacherQualification(teacherId, in); }

    @GetMapping("/resources/subjects/qualification-requirements")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<SubjectQualificationRequirementView> subjectQualificationRequirements(@RequestParam java.util.UUID academicSessionId) { return versions.subjectQualificationRequirements(academicSessionId); }

    @PutMapping("/resources/subjects/qualification-requirements")
    @PreAuthorize("@perm.can('timetable','write')")
    public SubjectQualificationRequirementView saveSubjectQualificationRequirement(@Valid @RequestBody SubjectQualificationRequirementUpsert in) { return versions.saveSubjectQualificationRequirement(in); }

    @GetMapping("/substitutions")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<SubstitutionView> substitutions(@RequestParam java.util.UUID academicSessionId, @RequestParam(required=false) java.time.LocalDate occurrenceDate) { return versions.substitutions(academicSessionId, occurrenceDate); }

    @PostMapping("/substitutions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('timetable','write')")
    public SubstitutionView createSubstitution(@Valid @RequestBody SubstitutionUpsert in) { return versions.createSubstitution(in); }

    @PostMapping("/substitutions/{id}/approve")
    @PreAuthorize("@perm.can('timetable','write')")
    public SubstitutionView approveSubstitution(@PathVariable java.util.UUID id, @Valid @RequestBody SubstitutionActionRequest in) { return versions.approveSubstitution(id, in); }

    @PostMapping("/substitutions/{id}/cancel")
    @PreAuthorize("@perm.can('timetable','write')")
    public SubstitutionView cancelSubstitution(@PathVariable java.util.UUID id, @Valid @RequestBody SubstitutionActionRequest in) { return versions.cancelSubstitution(id, in); }

    @GetMapping("/classes")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<ClassRef> classes() {
        return service.classes();
    }

    @GetMapping("/classes/{classId}/subject-teachers")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<SubjectTeacherView> subjectTeachers(@PathVariable java.util.UUID classId) {
        return service.subjectTeachers(classId);
    }

    /** Distinct room labels already used in this school (suggestions for the slot editor). */
    @GetMapping("/rooms")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<String> rooms() {
        return service.rooms();
    }

    @GetMapping("/periods")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<PeriodView> periods() { return service.periods(); }

    @PutMapping("/periods/{slotIdx}")
    @PreAuthorize("@perm.can('timetable','write')")
    public PeriodView updatePeriod(@PathVariable int slotIdx, @Valid @RequestBody PeriodRequest in) {
        return service.updatePeriod(slotIdx, in);
    }

    @GetMapping
    @PreAuthorize("@perm.can('timetable','read')")
    public List<SlotView> grid(@RequestParam String className, @RequestParam(required = false) java.util.UUID versionId) {
        return service.grid(className, versionId);
    }

    /** Chevauchements d'enseignant sur toute la grille : un professeur, deux classes, la même heure. */
    @GetMapping("/conflicts")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<TeacherConflict> conflicts() {
        return service.conflicts();
    }

    @PutMapping("/slot")
    @PreAuthorize("@perm.can('timetable','write')")
    public SlotSaveResult upsertSlot(@Valid @RequestBody SlotUpsert in) {
        return service.upsertSlot(in);
    }

    @PutMapping("/classes/{classId}/config")
    @PreAuthorize("@perm.can('timetable','write')")
    public ClassRef configure(@PathVariable java.util.UUID classId, @Valid @RequestBody ClassConfigRequest in) {
        return service.configure(classId, in);
    }

    @PutMapping("/classes/{classId}/teachers/{teacherId}")
    @PreAuthorize("@perm.can('timetable','write')")
    public void assignTeacher(@PathVariable java.util.UUID classId,
                              @PathVariable java.util.UUID teacherId,
                              @RequestBody TeacherAssignmentRequest in) {
        service.assignTeacher(classId, teacherId, in);
    }

    @PostMapping("/classes/{classId}/publish")
    @PreAuthorize("@perm.can('timetable','write')")
    public ClassRef publish(@PathVariable java.util.UUID classId, @Valid @RequestBody PlanActionRequest in) {
        return service.publish(classId, in);
    }

    @PostMapping("/classes/{classId}/reopen")
    @PreAuthorize("@perm.can('timetable','write')")
    public ClassRef reopen(@PathVariable java.util.UUID classId, @Valid @RequestBody PlanActionRequest in) {
        return service.reopen(classId, in);
    }

    @GetMapping("/teachers/me")
    @PreAuthorize("@perm.can('timetable','read')")
    public TeacherSchedule mySchedule() { return service.mySchedule(); }

    @GetMapping("/teachers/{teacherId}")
    @PreAuthorize("@perm.can('timetable','write')")
    public TeacherSchedule teacherSchedule(@PathVariable java.util.UUID teacherId) {
        return service.teacherSchedule(teacherId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('timetable','write')")
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
