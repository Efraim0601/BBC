package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FeeDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FeeController {

    private final FeeService service;

    public FeeController(FeeService service) { this.service = service; }

    @GetMapping("/fees/config")
    @PreAuthorize("@perm.can('finance','read')")
    public List<FeeConfigView> listConfig() {
        return service.listConfig();
    }

    @PutMapping("/fees/config")
    @PreAuthorize("@perm.can('finance','write')")
    public FeeConfigView upsertConfig(@Valid @RequestBody FeeConfigUpdate in) {
        return service.upsertConfig(in);
    }

    @GetMapping("/situation")
    @PreAuthorize("@perm.can('finance','read')")
    public List<SituationView> situation() {
        return service.situation();
    }

    @GetMapping("/debtors")
    @PreAuthorize("@perm.can('finance','read')")
    public List<SituationView> debtors() {
        return service.debtors();
    }
}
