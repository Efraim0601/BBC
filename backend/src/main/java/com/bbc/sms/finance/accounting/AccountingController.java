package com.bbc.sms.finance.accounting;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@RestController
@RequestMapping("/api/finance/v2/accounting")
public class AccountingController {
    private final AccountService accountService;
    private final PostingRuleService postingRules;
    private final AccountingPeriodService periods;
    private final LedgerPostingService ledger;
    private final ReconciliationService reconciliation;
    private final FinanceReadinessService readiness;

    public AccountingController(AccountService accountService, PostingRuleService postingRules,
                                AccountingPeriodService periods, LedgerPostingService ledger,
                                ReconciliationService reconciliation, FinanceReadinessService readiness) {
        this.accountService = accountService;
        this.postingRules = postingRules;
        this.periods = periods;
        this.ledger = ledger;
        this.reconciliation = reconciliation;
        this.readiness = readiness;
    }

    @GetMapping("/readiness")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public ReadinessView readiness() { return readiness.check(); }

    @GetMapping("/accounts")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<AccountView> accounts(@RequestParam(required = false) String query,
                                      @RequestParam(defaultValue = "false") boolean activeOnly) {
        return accountService.list(query, activeOnly);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('ACCOUNT_MANAGE')")
    public AccountView createAccount(@Valid @RequestBody AccountUpsert request) {
        return accountService.create(request);
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("@perm.canAction('ACCOUNT_MANAGE')")
    public AccountView updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountUpsert request) {
        return accountService.update(id, request);
    }

    @GetMapping("/posting-rules")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<PostingRuleView> postingRules() { return postingRules.list(); }

    @PostMapping("/posting-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('POSTING_RULE_MANAGE')")
    public PostingRuleView createPostingRule(@Valid @RequestBody PostingRuleUpsert request) {
        return postingRules.create(request);
    }

    @PutMapping("/posting-rules/{id}")
    @PreAuthorize("@perm.canAction('POSTING_RULE_MANAGE')")
    public PostingRuleView updatePostingRule(@PathVariable UUID id, @Valid @RequestBody PostingRuleUpsert request) {
        return postingRules.update(id, request);
    }

    @GetMapping("/periods")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<PeriodView> periods() { return periods.list(); }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('LEDGER_CLOSE')")
    public PeriodView createPeriod(@Valid @RequestBody PeriodUpsert request) { return periods.create(request); }

    @PutMapping("/periods/{id}")
    @PreAuthorize("@perm.canAction('LEDGER_CLOSE')")
    public PeriodView updatePeriod(@PathVariable UUID id, @Valid @RequestBody PeriodUpsert request) {
        return periods.update(id, request);
    }

    @PostMapping("/periods/generate")
    @PreAuthorize("@perm.canAction('LEDGER_CLOSE')")
    public List<PeriodView> generatePeriods(@Valid @RequestBody GeneratePeriodsRequest request) {
        return periods.generate(request);
    }

    @PostMapping("/periods/{id}/close-preview")
    @PreAuthorize("@perm.canAction('LEDGER_CLOSE')")
    public ClosePreview closePreview(@PathVariable UUID id) { return periods.closePreview(id); }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize("@perm.canAction('LEDGER_CLOSE')")
    public PeriodView closePeriod(@PathVariable UUID id, @Valid @RequestBody PeriodActionRequest request) {
        return periods.close(id, request);
    }

    @PostMapping("/periods/{id}/reopen")
    @PreAuthorize("@perm.canAction('LEDGER_REOPEN')")
    public PeriodView reopenPeriod(@PathVariable UUID id, @Valid @RequestBody PeriodActionRequest request) {
        return periods.reopen(id, request);
    }

    @GetMapping("/journals")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public PageView<JournalView> journals(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        return ledger.list(page, size, status, from, to);
    }

    @GetMapping("/journals/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public JournalView journal(@PathVariable UUID id) { return ledger.detail(id); }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('LEDGER_POST')")
    public JournalView createJournal(@Valid @RequestBody JournalUpsert request) { return ledger.createDraft(request); }

    @PutMapping("/journals/{id}")
    @PreAuthorize("@perm.canAction('LEDGER_POST')")
    public JournalView updateJournal(@PathVariable UUID id, @Valid @RequestBody JournalUpsert request) {
        return ledger.updateDraft(id, request);
    }

    @PostMapping("/journals/{id}/post")
    @PreAuthorize("@perm.canAction('LEDGER_POST')")
    public JournalView postJournal(@PathVariable UUID id,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ledger.post(id, idempotencyKey);
    }

    @PostMapping("/journals/{id}/reverse")
    @PreAuthorize("@perm.canAction('LEDGER_REVERSE')")
    public JournalView reverseJournal(@PathVariable UUID id, @Valid @RequestBody ReverseRequest request,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ledger.reverse(id, request, idempotencyKey);
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public TrialBalanceView trialBalance(@RequestParam(required = false) LocalDate asOfDate,
                                         @RequestParam(defaultValue = "false") boolean includeZero) {
        return ledger.trialBalance(asOfDate, includeZero);
    }

    @GetMapping("/general-ledger")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public GeneralLedgerView generalLedger(@RequestParam UUID accountId,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        return ledger.generalLedger(accountId, from, to);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("@perm.canAction('FINANCE_REPORT_VIEW')")
    public List<ReconciliationView> reconciliation(@RequestParam(required = false) String state) {
        return reconciliation.list(state);
    }

    @PostMapping("/reconciliation/{id}/resolve")
    @PreAuthorize("@perm.canAction('LEDGER_POST')")
    public ReconciliationView resolveReconciliation(@PathVariable UUID id,
                                                    @Valid @RequestBody ReconciliationResolveRequest request) {
        return reconciliation.resolve(id, request);
    }
}
