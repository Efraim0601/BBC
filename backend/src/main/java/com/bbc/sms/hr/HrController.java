package com.bbc.sms.hr;

import com.bbc.sms.hr.dto.HrDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** HR / Operations API: departments and leave management (gated by the hr module). */
@RestController
@RequestMapping("/api/hr")
public class HrController {

    private final HrService service;

    public HrController(HrService service) { this.service = service; }

    // ---- Departments --------------------------------------------------------
    @GetMapping("/departments")
    @PreAuthorize("@perm.can('hr','read')")
    public List<DepartmentView> departments() { return service.listDepartments(); }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('hr','write')")
    public DepartmentView createDepartment(@Valid @RequestBody DepartmentUpsert in) { return service.createDepartment(in); }

    @PutMapping("/departments/{id}")
    @PreAuthorize("@perm.can('hr','write')")
    public DepartmentView updateDepartment(@PathVariable UUID id, @Valid @RequestBody DepartmentUpsert in) {
        return service.updateDepartment(id, in);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('hr','write')")
    public void deleteDepartment(@PathVariable UUID id) { service.deleteDepartment(id); }

    // ---- Leave --------------------------------------------------------------
    @GetMapping("/leaves")
    @PreAuthorize("@perm.can('hr','read')")
    public List<LeaveView> leaves() { return service.listLeaves(); }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('hr','write')")
    public LeaveView createLeave(@Valid @RequestBody LeaveCreate in) { return service.createLeave(in); }

    @PutMapping("/leaves/{id}/decision")
    @PreAuthorize("@perm.can('hr','write')")
    public LeaveView decideLeave(@PathVariable UUID id, @Valid @RequestBody LeaveDecision in) {
        return service.decideLeave(id, in);
    }
}
