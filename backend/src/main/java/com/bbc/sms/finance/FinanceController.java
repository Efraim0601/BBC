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
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<PaymentView> listPayments() {
        return service.listPayments();
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('PAYMENT_COLLECT')")
    public PaymentView recordPayment(@Valid @RequestBody PaymentRequest in) {
        return service.recordPayment(in);
    }

    @GetMapping("/expenses")
    @PreAuthorize("@policy.canAction('FINANCE_EXPENSE_VIEW')")
    public List<ExpenseView> listExpenses() {
        return service.listExpenses();
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@policy.canAction('FINANCE_EXPENSE_CREATE')")
    public ExpenseView addExpense(@Valid @RequestBody ExpenseRequest in) {
        return service.addExpense(in);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@policy.canAction('FINANCE_EXPENSE_DELETE')")
    public void deleteExpense(@PathVariable UUID id) {
        service.deleteExpense(id);
    }

    @GetMapping("/summary")
    @PreAuthorize("@policy.canAction('FINANCE_OVERVIEW_VIEW')")
    public FinanceSummary summary() {
        return service.summary();
    }
}
