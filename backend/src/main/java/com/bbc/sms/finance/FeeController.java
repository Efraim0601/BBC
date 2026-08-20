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
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<FeeConfigView> listConfig() {
        return service.listConfig();
    }

    @PutMapping("/fees/config")
    @PreAuthorize("@policy.canAction('FEE_CONFIGURE')")
    public FeeConfigView upsertConfig(@Valid @RequestBody FeeConfigUpdate in) {
        return service.upsertConfig(in);
    }

    @DeleteMapping("/fees/config/{id}")
    @PreAuthorize("@policy.canAction('FEE_CONFIGURE')")
    public void deleteConfig(@PathVariable UUID id) {
        service.deleteConfig(id);
    }

    /** Situation détaillée d'un élève : tranches couvertes, reste à payer, reçus. */
    @GetMapping("/students/{studentId}/statement")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public StudentFeeStatementView statement(@PathVariable UUID studentId) {
        return service.statement(TenantContext.get(), studentId);
    }

    @GetMapping("/channels")
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<PaymentChannelView> channels() {
        return service.listChannels();
    }

    @PutMapping("/channels/{code}")
    @PreAuthorize("@policy.canAction('FEE_CONFIGURE')")
    public PaymentChannelView updateChannel(@PathVariable String code,
                                            @Valid @RequestBody PaymentChannelUpdate in) {
        return service.updateChannel(code, in);
    }

    @GetMapping("/situation")
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<SituationView> situation() {
        return service.situation();
    }

    @GetMapping("/debtors")
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<SituationView> debtors() {
        return service.debtors();
    }
}
