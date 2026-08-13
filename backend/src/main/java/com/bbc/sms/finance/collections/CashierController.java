package com.bbc.sms.finance.collections;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

@RestController
@RequestMapping("/api/finance/v2/collections/cashier")
public class CashierController {
    private final CashierService cashiers;
    public CashierController(CashierService cashiers) { this.cashiers = cashiers; }

    @GetMapping("/current")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public CashierSessionView current() { return cashiers.current(); }

    @GetMapping
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public List<CashierSessionView> list() { return cashiers.list(); }

    @PostMapping
    @PreAuthorize("@perm.canAction('CASHIER_SESSION_OPEN')")
    public CashierSessionView open(@Valid @RequestBody CashierOpenRequest request) { return cashiers.open(request); }

    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.canAction('CASHIER_SESSION_CLOSE')")
    public CashierSessionView close(@PathVariable UUID id, @Valid @RequestBody CashierCloseRequest request) {
        return cashiers.close(id, request);
    }

    @PostMapping("/{id}/approve-close")
    @PreAuthorize("@perm.canAction('CASHIER_SESSION_APPROVE')")
    public CashierSessionView approveClose(@PathVariable UUID id, @Valid @RequestBody CashierCloseRequest request) {
        return cashiers.approveClose(id, request);
    }
}
