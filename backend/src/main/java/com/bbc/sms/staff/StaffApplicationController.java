package com.bbc.sms.staff;

import com.bbc.sms.staff.dto.StaffDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffApplicationController {

    private final StaffApplicationService service;

    public StaffApplicationController(StaffApplicationService service) {
        this.service = service;
    }

    @GetMapping("/portal")
    @PreAuthorize("@perm.can('hr','read') or @perm.can('settings','read')")
    public StaffPortalSettingsView getPortal() {
        return service.getPortalSettings();
    }

    @PutMapping("/portal")
    @PreAuthorize("@perm.can('hr','write') or @perm.can('settings','write')")
    public StaffPortalSettingsView updatePortal(@Valid @RequestBody StaffPortalSettingsUpdate in) {
        return service.updatePortalSettings(in);
    }

    @PostMapping("/portal/regenerate-token")
    @PreAuthorize("@perm.can('hr','write') or @perm.can('settings','write')")
    public StaffPortalSettingsView regenerateToken() {
        return service.regeneratePortalToken();
    }

    @GetMapping("/applications")
    @PreAuthorize("@perm.can('hr','read')")
    public List<StaffApplicationView> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/applications/{id}/accept")
    @PreAuthorize("@perm.can('hr','write')")
    public StaffApplicationView accept(@PathVariable UUID id) {
        return service.accept(id);
    }

    @PostMapping("/applications/{id}/reject")
    @PreAuthorize("@perm.can('hr','write')")
    public StaffApplicationView reject(@PathVariable UUID id, @Valid @RequestBody StaffApplicationReject in) {
        return service.reject(id, in);
    }

    @PostMapping("/applications/{id}/finalize")
    @PreAuthorize("@perm.can('hr','write')")
    public StaffApplicationView finalize(@PathVariable UUID id, @Valid @RequestBody StaffApplicationFinalize in) {
        return service.finalize(id, in);
    }
}
