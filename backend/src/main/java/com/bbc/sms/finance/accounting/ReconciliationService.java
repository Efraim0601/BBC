package com.bbc.sms.finance.accounting;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class ReconciliationService {
    private final ReconciliationItemRepository items;
    private final AuditService audit;
    private final FinancePolicyService financePolicy;

    public ReconciliationService(ReconciliationItemRepository items, AuditService audit,
                                 FinancePolicyService financePolicy) {
        this.items = items;
        this.audit = audit;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationView> list(String state) {
        financePolicy.requireSchool("FINANCE_REPORT_VIEW");
        String normalized = state == null || state.isBlank() ? null : state.trim().toUpperCase(Locale.ROOT);
        List<ReconciliationItem> rows = normalized == null
                ? items.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get())
                : items.findBySchoolIdAndStateOrderByCreatedAtDesc(TenantContext.get(), normalized);
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public ReconciliationView resolve(UUID id, ReconciliationResolveRequest request) {
        financePolicy.requireSchool("LEDGER_POST");
        String state = request.state().trim().toUpperCase(Locale.ROOT);
        if (!state.equals("MATCHED") && !state.equals("IGNORED")) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_RECONCILIATION_STATE", "Un rapprochement doit être marqué comme résolu ou ignoré.",
                    Map.of("state", "Choisissez MATCHED ou IGNORED."), List.of());
        }
        ReconciliationItem item = items.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élément de rapprochement"));
        AccountService.requireVersion(request.version(), item.getVersion(), "élément de rapprochement");
        ReconciliationView before = view(item);
        item.setState(state);
        item.setResolvedAt(Instant.now());
        item.setResolvedBy(currentUserId());
        item.setResolutionNote(request.reason().trim());
        item = items.saveAndFlush(item);
        ReconciliationView result = view(item);
        audit.record("RECONCILIATION_RESOLVED", "ReconciliationItem", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public long unresolvedCount() {
        return items.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(item -> !item.getState().equals("MATCHED") && !item.getState().equals("IGNORED"))
                .count();
    }

    private ReconciliationView view(ReconciliationItem item) {
        return new ReconciliationView(item.getId(), item.getSourceType(), item.getSourceId(), item.getExpectedAmount(),
                item.getPostedAmount(), item.getCurrency(), item.getState(), item.getReason(),
                item.getResolvedAt() == null ? null : item.getResolvedAt().atOffset(ZoneOffset.UTC),
                item.getResolvedBy(), item.getResolutionNote(), item.getVersion());
    }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }
}
