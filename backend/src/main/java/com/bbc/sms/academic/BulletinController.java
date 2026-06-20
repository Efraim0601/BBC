package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.BulletinDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/academic")
public class BulletinController {

    private final BulletinService service;

    public BulletinController(BulletinService service) { this.service = service; }

    @GetMapping("/students/{studentId}/bulletin")
    @PreAuthorize("@perm.can('academic','read')")
    public BulletinView bulletin(@PathVariable UUID studentId,
                                 @RequestParam(defaultValue = "1") int sequence) {
        return service.bulletin(studentId, sequence);
    }

    @GetMapping("/classes/{className}/pv")
    @PreAuthorize("@perm.can('academic','read')")
    public PvView pv(@PathVariable String className,
                     @RequestParam(defaultValue = "1") int sequence) {
        return service.pv(className, sequence);
    }

    @PostMapping("/students/{studentId}/bulletin/validate")
    @PreAuthorize("@perm.can('academic','write')")
    public BulletinView validate(@PathVariable UUID studentId,
                                 @Valid @RequestBody ValidateRequest req) {
        return service.validate(studentId, req);
    }
}
