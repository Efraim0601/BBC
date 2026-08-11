package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;
    private final AttendanceWorkflowService workflow;

    public AttendanceController(AttendanceService service, AttendanceWorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
    }

    @GetMapping("/board")
    @PreAuthorize("@perm.can('presence','read')")
    public DailyBoard board(@RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.board(date != null ? date : LocalDate.now());
    }

    @PostMapping("/mark")
    @PreAuthorize("@perm.canAction('ATTENDANCE_MARK')")
    public AttendanceView mark(@Valid @RequestBody MarkRequest req) {
        return service.mark(req);
    }

    /** Reader health. Also read by Settings → Général, hence the settings fallback. */
    @GetMapping("/devices")
    @PreAuthorize("@perm.can('presence','read') or @perm.can('settings','read')")
    public List<DeviceView> devices() {
        return service.devices();
    }

    @GetMapping("/policies")
    @PreAuthorize("@perm.can('presence','read')")
    public List<PolicyView> policies() { return workflow.policies(); }

    @PutMapping("/policies/{level}")
    @PreAuthorize("@perm.canAction('ATTENDANCE_POLICY_MANAGE')")
    public PolicyView updatePolicy(@PathVariable String level, @Valid @RequestBody PolicyRequest request) {
        return workflow.updatePolicy(level, request);
    }

    @GetMapping("/classes")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ROSTER_VIEW')")
    public List<AttendanceClass> classes() { return workflow.attendanceClasses(); }

    @GetMapping("/sessions")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ROSTER_VIEW')")
    public List<SessionSummary> sessions(@RequestParam UUID classId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return workflow.sessionOptions(classId, date);
    }

    @GetMapping("/roster")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ROSTER_VIEW')")
    public RosterView roster(@RequestParam UUID classId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) String periodKey) {
        return workflow.roster(classId, date, periodKey);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ROSTER_VIEW')")
    public RosterView session(@PathVariable UUID id) { return workflow.rosterById(id); }

    @PutMapping("/sessions/marks")
    @PreAuthorize("@perm.canAction('ATTENDANCE_MARK')")
    public RosterView saveMarks(@Valid @RequestBody BulkMarkRequest request) { return workflow.save(request); }

    @PostMapping("/sessions/{id}/finalize")
    @PreAuthorize("@perm.canAction('ATTENDANCE_FINALIZE')")
    public RosterView finalizeSession(@PathVariable UUID id, @RequestBody ActionRequest request) {
        return workflow.finalizeSession(id, request);
    }

    @PostMapping("/sessions/{id}/reopen")
    @PreAuthorize("@perm.canAction('ATTENDANCE_REOPEN')")
    public RosterView reopen(@PathVariable UUID id, @RequestBody ActionRequest request) {
        return workflow.reopen(id, request);
    }

    @PostMapping("/generate")
    @PreAuthorize("@perm.canAction('ATTENDANCE_POLICY_MANAGE')")
    public GenerationResult generate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "false") boolean preview) {
        return workflow.generate(from, to, preview);
    }

    @GetMapping("/analytics")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ANALYTICS_VIEW')")
    public AnalyticsView analytics(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) UUID classId) {
        return workflow.analytics(from, to, classId);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("@perm.canAction('ATTENDANCE_RECONCILE')")
    public List<DeviceReconciliation> reconciliation(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return workflow.reconciliation(date);
    }

    @PostMapping("/reconciliation")
    @PreAuthorize("@perm.canAction('ATTENDANCE_RECONCILE')")
    public RosterView reconcile(@Valid @RequestBody ReconcileRequest request) {
        return workflow.reconcile(request);
    }

    @PostMapping("/alerts/scan")
    @PreAuthorize("@perm.canAction('ATTENDANCE_POLICY_MANAGE') and @perm.can('alerts','write')")
    public AlertScanResult scanAlerts(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return workflow.scanAlerts(from, to);
    }

    @GetMapping("/notifications")
    @PreAuthorize("@perm.canAction('ATTENDANCE_ANALYTICS_VIEW')")
    public List<NotificationView> notifications(@RequestParam(required = false) String status) {
        return workflow.notifications(status);
    }
}
