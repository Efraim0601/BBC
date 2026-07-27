package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FeeDtos.*;
import com.bbc.sms.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @DeleteMapping("/fees/config/{id}")
    @PreAuthorize("@perm.can('finance','write')")
    public void deleteConfig(@PathVariable UUID id) {
        service.deleteConfig(id);
    }

    /** Situation détaillée d'un élève : tranches couvertes, reste à payer, reçus. */
    @GetMapping("/students/{studentId}/statement")
    @PreAuthorize("@perm.can('finance','read')")
    public StudentFeeStatementView statement(@PathVariable UUID studentId) {
        return service.statement(TenantContext.get(), studentId);
    }

    @GetMapping("/channels")
    @PreAuthorize("@perm.can('finance','read')")
    public List<PaymentChannelView> channels() {
        return service.listChannels();
    }

    @PutMapping("/channels/{code}")
    @PreAuthorize("@perm.can('finance','write')")
    public PaymentChannelView updateChannel(@PathVariable String code,
                                            @Valid @RequestBody PaymentChannelUpdate in) {
        return service.updateChannel(code, in);
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
