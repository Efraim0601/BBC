package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * On-site fingerprint agent endpoint. Authenticated by a device API key
 * (header X-Device-Key), NOT by a user JWT. Idempotent via dedupKey so the
 * agent can safely replay buffered check-ins after an internet outage.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final AttendanceService service;

    public DeviceController(AttendanceService service) { this.service = service; }

    @PostMapping("/{deviceId}/attendance")
    public AttendanceView checkin(@PathVariable UUID deviceId,
                                  @RequestHeader("X-Device-Key") String apiKey,
                                  @Valid @RequestBody DeviceCheckin in) {
        return service.deviceCheckin(deviceId, apiKey, in);
    }
}
