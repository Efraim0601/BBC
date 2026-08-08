package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FinanceDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService service;

    public FinanceController(FinanceService service) { this.service = service; }

    @GetMapping("/payments")
    @PreAuthorize("@perm.can('finance','read')")
    public List<PaymentView> listPayments() {
        return service.listPayments();
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('finance','write')")
    public PaymentView recordPayment(@Valid @RequestBody PaymentRequest in) {
        return service.recordPayment(in);
    }

    @GetMapping("/expenses")
    @PreAuthorize("@perm.can('finance','read')")
    public List<ExpenseView> listExpenses() {
        return service.listExpenses();
    }

    /** Une dépense engage l'établissement, non un cycle : admin principal seul. */
    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('finance','write') and @perm.schoolWide()")
    public ExpenseView addExpense(@Valid @RequestBody ExpenseRequest in) {
        return service.addExpense(in);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('finance','write') and @perm.schoolWide()")
    public void deleteExpense(@PathVariable UUID id) {
        service.deleteExpense(id);
    }

    @GetMapping("/summary")
    @PreAuthorize("@perm.can('finance','read')")
    public FinanceSummary summary() {
        return service.summary();
    }
}
