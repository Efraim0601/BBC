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

    @GetMapping
    @PreAuthorize("@perm.can('timetable','read')")
    public List<SlotView> grid(@RequestParam String className) {
        return service.grid(className);
    }

    @PutMapping("/slot")
    @PreAuthorize("@perm.can('timetable','write')")
    public SlotSaveResult upsertSlot(@Valid @RequestBody SlotUpsert in) {
        return service.upsertSlot(in);
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
