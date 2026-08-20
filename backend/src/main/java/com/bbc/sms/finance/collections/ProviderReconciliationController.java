package com.bbc.sms.finance.collections;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

@RestController
@RequestMapping("/api/finance/v2/collections/provider")
public class ProviderReconciliationController {
    private final ProviderReconciliationService provider;
    public ProviderReconciliationController(ProviderReconciliationService provider) { this.provider = provider; }

    @PostMapping("/callbacks")
    @PreAuthorize("@perm.canAction('PROVIDER_CALLBACK_REVIEW')")
    public ProviderTransactionView ingest(@Valid @RequestBody ProviderCallbackRequest request) { return provider.ingest(request); }

    @PostMapping("/transactions/{id}/confirm")
    @PreAuthorize("@perm.canAction('PROVIDER_CALLBACK_REVIEW')")
    public ProviderTransactionView confirm(@PathVariable UUID id, @Valid @RequestBody ProviderConfirmRequest request) {
        return provider.confirm(id, request);
    }
}
