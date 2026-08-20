package com.bbc.sms.foundation.calendar;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.foundation.calendar.CalendarDtos.*;

@RestController
@RequestMapping("/api/settings/calendar")
public class SchoolCalendarController {
    private final SchoolCalendarService service;
    public SchoolCalendarController(SchoolCalendarService service) { this.service = service; }

    @GetMapping("/{sessionId}/days") @PreAuthorize("@policy.canAction('CALENDAR_VIEW')")
    public List<CalendarDayView> days(@PathVariable UUID sessionId) { return service.days(sessionId); }

    @PutMapping("/{sessionId}/days") @PreAuthorize("@policy.canAction('CALENDAR_MANAGE')")
    public CalendarDayView day(@PathVariable UUID sessionId, @Valid @RequestBody CalendarDayUpdate in) { return service.updateDay(sessionId, in); }

    @PostMapping("/generate") @PreAuthorize("@policy.canAction('CALENDAR_MANAGE')")
    public GenerationResult generate(@Valid @RequestBody GenerateRequest in) { return service.generate(in); }

    @GetMapping("/{sessionId}/expected") @PreAuthorize("@policy.canAction('CALENDAR_VIEW')")
    public List<ExpectedSessionView> expected(@PathVariable UUID sessionId,
                                              @RequestParam(required = false) LocalDate start,
                                              @RequestParam(required = false) LocalDate end,
                                              @RequestParam(required = false) UUID classId) {
        return service.expected(sessionId, start, end, classId);
    }
}
