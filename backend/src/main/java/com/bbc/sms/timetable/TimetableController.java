package com.bbc.sms.timetable;

import com.bbc.sms.timetable.dto.TimetableDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService service;

    public TimetableController(TimetableService service) { this.service = service; }

    @GetMapping("/classes")
    @PreAuthorize("@perm.can('timetable','read')")
    public List<ClassRef> classes() {
        return service.classes();
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
    public List<SlotView> grid(@RequestParam String className) {
        return service.grid(className);
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
}
