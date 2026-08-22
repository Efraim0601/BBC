package com.bbc.sms.finance.treasury;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.treasury.TreasuryDtos.*;

@RestController
@RequestMapping("/api/finance/v2/treasury")
public class TreasuryController {
    private final TreasuryService treasury;

    public TreasuryController(TreasuryService treasury) { this.treasury = treasury; }

    @GetMapping("/accounts")
    @PreAuthorize("@perm.canAction('TREASURY_ACCOUNT_VIEW')")
    public List<TreasuryAccountView> accounts() { return treasury.listAccounts(); }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('TREASURY_ACCOUNT_MANAGE')")
    public TreasuryAccountView createAccount(@Valid @RequestBody TreasuryAccountCreate request) {
        return treasury.createAccount(request);
    }

    @PutMapping("/accounts/{id}/archive")
    @PreAuthorize("@perm.canAction('TREASURY_ACCOUNT_MANAGE')")
    public TreasuryAccountView archiveAccount(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        return treasury.archiveAccount(id, request);
    }

    @GetMapping("/movements")
    @PreAuthorize("@perm.canAction('TREASURY_MOVEMENT_VIEW')")
    public List<TreasuryMovementView> movements(@RequestParam(defaultValue = "100") int limit) {
        return treasury.listMovements(limit);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('TREASURY_MOVEMENT_CREATE')")
    public TreasuryMovementView createMovement(@Valid @RequestBody TreasuryMovementRequest request,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return treasury.createMovement(request, idempotencyKey);
    }
}
