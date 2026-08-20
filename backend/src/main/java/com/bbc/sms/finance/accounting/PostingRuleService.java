package com.bbc.sms.finance.accounting;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class PostingRuleService {
    private final PostingRuleRepository rules;
    private final AccountService accountService;
    private final ChartOfAccountRepository accounts;
    private final AuditService audit;
    private final FinancePolicyService financePolicy;

    public PostingRuleService(PostingRuleRepository rules, AccountService accountService,
                              ChartOfAccountRepository accounts, AuditService audit,
                              FinancePolicyService financePolicy) {
        this.rules = rules;
        this.accountService = accountService;
        this.accounts = accounts;
        this.audit = audit;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public List<PostingRuleView> list() {
        financePolicy.requireSchool("FINANCE_OVERVIEW_VIEW");
        return rules.findBySchoolIdOrderByEventTypeAscSideAscPriorityDesc(TenantContext.get())
                .stream().map(this::view).toList();
    }

    @Transactional
    public PostingRuleView create(PostingRuleUpsert in) {
        financePolicy.requireSchool("POSTING_RULE_MANAGE");
        PostingRule rule = new PostingRule();
        rule.setSchoolId(TenantContext.get());
        apply(rule, in);
        rule = rules.saveAndFlush(rule);
        PostingRuleView result = view(rule);
        audit.record("POSTING_RULE_CREATED", "PostingRule", rule.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public PostingRuleView update(UUID id, PostingRuleUpsert in) {
        financePolicy.requireSchool("POSTING_RULE_MANAGE");
        PostingRule rule = rules.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Règle de comptabilisation"));
        AccountService.requireVersion(in.version(), rule.getVersion(), "règle de comptabilisation");
        PostingRuleView before = view(rule);
        apply(rule, in);
        rule = rules.saveAndFlush(rule);
        PostingRuleView result = view(rule);
        audit.record("POSTING_RULE_UPDATED", "PostingRule", rule.getId().toString(), before, result, null);
        return result;
    }

    private void apply(PostingRule rule, PostingRuleUpsert in) {
        String event = in.eventType() == null ? "" : in.eventType().trim().toUpperCase(Locale.ROOT);
        String side = in.side() == null ? "" : in.side().trim().toUpperCase(Locale.ROOT);
        if (!event.matches("[A-Z0-9_.-]{2,80}")) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_EVENT_TYPE", "Le type d'événement est invalide.",
                    Map.of("eventType", "Utilisez un code métier comme EXPENSE_POST."), List.of());
        }
        if (!side.equals("DEBIT") && !side.equals("CREDIT")) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_POSTING_SIDE", "Le sens de la règle doit être débit ou crédit.",
                    Map.of("side", "Choisissez débit ou crédit."), List.of());
        }
        if (in.priority() < 0) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_RULE_PRIORITY", "La priorité ne peut pas être négative.",
                    Map.of("priority", "Utilisez zéro ou une priorité supérieure."), List.of());
        }
        if (in.effectiveFrom() != null && in.effectiveTo() != null
                && in.effectiveTo().isBefore(in.effectiveFrom())) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_RULE_DATES", "La date de fin ne peut pas précéder la date de début.",
                    Map.of("effectiveTo", "Choisissez une date postérieure ou égale."), List.of());
        }
        ChartOfAccount target = accountService.requirePostingAccount(in.targetAccountId());
        rule.setEventType(event);
        rule.setSide(side);
        rule.setScopeCode(trim(in.scopeCode()));
        rule.setFeeTypeCode(trim(in.feeTypeCode()));
        rule.setPaymentChannelCode(trim(in.paymentChannelCode()));
        rule.setComponentCode(trim(in.componentCode()));
        rule.setTargetAccountId(target.getId());
        rule.setPriority(in.priority());
        rule.setEffectiveFrom(in.effectiveFrom());
        rule.setEffectiveTo(in.effectiveTo());
        rule.setEnabled(in.enabled() == null || in.enabled());
    }

    private PostingRuleView view(PostingRule rule) {
        String code = accounts.findByIdAndSchoolId(rule.getTargetAccountId(), TenantContext.get())
                .map(ChartOfAccount::getCode).orElse("?");
        return new PostingRuleView(rule.getId(), rule.getEventType(), rule.getSide(), rule.getScopeCode(),
                rule.getFeeTypeCode(), rule.getPaymentChannelCode(), rule.getComponentCode(),
                rule.getTargetAccountId(), code, rule.getPriority(), rule.getEffectiveFrom(),
                rule.getEffectiveTo(), rule.isEnabled(), rule.getVersion());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

/** Resolves the most specific active mapping and turns missing/ambiguous mappings into visible blockers. */
@Service
class PostingRuleResolver {
    private final PostingRuleRepository rules;
    private final AccountService accounts;
    private final ReconciliationItemRepository reconciliation;

    PostingRuleResolver(PostingRuleRepository rules, AccountService accounts,
                        ReconciliationItemRepository reconciliation) {
        this.rules = rules;
        this.accounts = accounts;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public ChartOfAccount resolve(String eventType, String side, LocalDate date,
                                  String scopeCode, String feeTypeCode, String paymentChannelCode,
                                  String componentCode, String sourceType, String sourceId,
                                  long expectedAmount, String currency) {
        UUID schoolId = TenantContext.get();
        String normalizedEvent = eventType.trim().toUpperCase(Locale.ROOT);
        String normalizedSide = side.trim().toUpperCase(Locale.ROOT);
        List<PostingRule> candidates = rules
                .findBySchoolIdAndEventTypeAndEnabledTrueOrderByPriorityDescEffectiveFromDesc(schoolId, normalizedEvent)
                .stream()
                .filter(r -> r.getSide().equals(normalizedSide))
                .filter(r -> activeOn(r, date))
                .filter(r -> matches(r.getScopeCode(), scopeCode))
                .filter(r -> matches(r.getFeeTypeCode(), feeTypeCode))
                .filter(r -> matches(r.getPaymentChannelCode(), paymentChannelCode))
                .filter(r -> matches(r.getComponentCode(), componentCode))
                .sorted(Comparator.comparingInt((PostingRule r) -> specificity(r, scopeCode, feeTypeCode,
                        paymentChannelCode, componentCode)).reversed()
                        .thenComparing(PostingRule::getPriority, Comparator.reverseOrder()))
                .toList();

        if (candidates.isEmpty()) {
            recordBlocker(sourceType, sourceId, expectedAmount, currency,
                    "ACCOUNT_MAPPING_MISSING", "Aucun compte de comptabilisation ne couvre cet événement.");
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "ACCOUNT_MAPPING_MISSING", "Cet événement ne peut pas être posté : son compte comptable n'est pas configuré.",
                    Map.of(), List.of(new ApiException.Blocker("POSTING_RULE", normalizedEvent,
                            normalizedEvent + " / " + normalizedSide, "OPEN_POSTING_MAPPINGS")));
        }
        int best = specificity(candidates.getFirst(), scopeCode, feeTypeCode, paymentChannelCode, componentCode);
        int bestPriority = candidates.getFirst().getPriority();
        List<PostingRule> bestRules = candidates.stream()
                .filter(r -> specificity(r, scopeCode, feeTypeCode, paymentChannelCode, componentCode) == best)
                .filter(r -> r.getPriority() == bestPriority)
                .toList();
        if (bestRules.stream().map(PostingRule::getTargetAccountId).distinct().count() > 1) {
            recordBlocker(sourceType, sourceId, expectedAmount, currency,
                    "ACCOUNT_MAPPING_AMBIGUOUS", "Plusieurs comptes de même priorité couvrent cet événement.");
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "ACCOUNT_MAPPING_AMBIGUOUS", "Cet événement ne peut pas être posté : les règles comptables sont ambiguës.",
                    Map.of(), List.of(new ApiException.Blocker("POSTING_RULE", normalizedEvent,
                            normalizedEvent + " / " + normalizedSide, "OPEN_POSTING_MAPPINGS")));
        }
        return accounts.requirePostingAccount(bestRules.getFirst().getTargetAccountId());
    }

    private void recordBlocker(String sourceType, String sourceId, long expectedAmount,
                               String currency, String reasonCode, String reason) {
        if (sourceType == null || sourceType.isBlank()) return;
        ReconciliationItem item = new ReconciliationItem();
        item.setSchoolId(TenantContext.get());
        item.setSourceType(sourceType.trim());
        item.setSourceId(sourceId == null ? null : sourceId.trim());
        item.setExpectedAmount(Math.max(0, expectedAmount));
        item.setPostedAmount(0);
        item.setCurrency(currency == null || currency.isBlank() ? "XAF" : currency.toUpperCase(Locale.ROOT));
        item.setState("MISSING");
        item.setReason(reasonCode + ": " + reason);
        reconciliation.save(item);
    }

    private static boolean activeOn(PostingRule rule, LocalDate date) {
        return (rule.getEffectiveFrom() == null || !date.isBefore(rule.getEffectiveFrom()))
                && (rule.getEffectiveTo() == null || !date.isAfter(rule.getEffectiveTo()));
    }

    private static boolean matches(String ruleValue, String requested) {
        return ruleValue == null || Objects.equals(ruleValue, requested);
    }

    private static int specificity(PostingRule r, String scope, String fee, String channel, String component) {
        return (r.getScopeCode() != null && Objects.equals(r.getScopeCode(), scope) ? 1 : 0)
                + (r.getFeeTypeCode() != null && Objects.equals(r.getFeeTypeCode(), fee) ? 1 : 0)
                + (r.getPaymentChannelCode() != null && Objects.equals(r.getPaymentChannelCode(), channel) ? 1 : 0)
                + (r.getComponentCode() != null && Objects.equals(r.getComponentCode(), component) ? 1 : 0);
    }
}
