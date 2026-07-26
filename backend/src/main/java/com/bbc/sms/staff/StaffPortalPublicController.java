package com.bbc.sms.staff;

import com.bbc.sms.staff.dto.StaffDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Public (unauthenticated) staff self-registration portal.
 * School is resolved from slug + token — never from JWT TenantContext.
 */
@RestController
@RequestMapping("/api/public/staff-portal")
public class StaffPortalPublicController {

    private final StaffApplicationService service;

    public StaffPortalPublicController(StaffApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{slug}/meta")
    public StaffPortalMeta meta(@PathVariable String slug, @RequestParam("t") String token) {
        return service.publicMeta(slug, token);
    }

    @PostMapping("/{slug}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffApplicationView apply(@PathVariable String slug,
                                      @RequestParam("t") String token,
                                      @Valid @RequestBody StaffApplicationSubmit in) {
        return service.submit(slug, token, in);
    }
}
