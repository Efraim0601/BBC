package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) { this.service = service; }

    @GetMapping("/board")
    @PreAuthorize("@perm.can('presence','read')")
    public DailyBoard board(@RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.board(date != null ? date : LocalDate.now());
    }

    @PostMapping("/mark")
    @PreAuthorize("@perm.can('presence','write')")
    public AttendanceView mark(@Valid @RequestBody MarkRequest req) {
        return service.mark(req);
    }
}
