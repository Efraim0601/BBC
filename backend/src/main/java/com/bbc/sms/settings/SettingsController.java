package com.bbc.sms.settings;

import com.bbc.sms.settings.dto.SettingsDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final PermissionAdminService service;

    public SettingsController(PermissionAdminService service) { this.service = service; }

    @GetMapping("/permissions")
    @PreAuthorize("@perm.can('settings','read')")
    public PermissionMatrix getMatrix() {
        return service.getMatrix();
    }

    @PutMapping("/permissions")
    @PreAuthorize("@perm.can('settings','write')")
    public PermissionMatrix update(@Valid @RequestBody UpdateRequest req) {
        return service.update(req);
    }
}
