package com.bbc.sms.staff;

import com.bbc.sms.staff.dto.StaffDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.can('hr','read')")
    public List<EmployeeView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.can('hr','read')")
    public EmployeeView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('hr','write')")
    public EmployeeView create(@Valid @RequestBody EmployeeUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('hr','write')")
    public EmployeeView update(@PathVariable UUID id, @Valid @RequestBody EmployeeUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('hr','write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /**
     * Create the employee's login account if missing, otherwise regenerate its
     * password; the new credentials are e-mailed to the employee. Doubles as the
     * admin "reset credentials" action.
     */
    @PostMapping("/{id}/reset-credentials")
    @PreAuthorize("@perm.can('hr','write')")
    public AccountResult resetCredentials(@PathVariable UUID id) {
        return service.resetCredentials(id);
    }
}
