package com.bbc.sms.finance.collections;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

@RestController
@RequestMapping("/api/finance/v2/collections")
public class PaymentCorrectionController {
    private final PaymentCorrectionService corrections;
    public PaymentCorrectionController(PaymentCorrectionService corrections) { this.corrections = corrections; }

    @GetMapping("/{id}/reversal-preview")
    @PreAuthorize("@perm.canAction('PAYMENT_REVERSE')")
    public ReversalPreview reversalPreview(@PathVariable UUID id) { return corrections.reversalPreview(id); }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("@perm.canAction('PAYMENT_REVERSE')")
    public PaymentView reverse(@PathVariable UUID id, @Valid @RequestBody ReversalRequest request,
                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return corrections.reverse(id, request, idempotencyKey);
    }

    @GetMapping("/{id}/refunds")
    @PreAuthorize("@perm.canAction('PAYMENT_VIEW')")
    public List<RefundView> refunds(@PathVariable UUID id) { return corrections.refunds(id); }

    @PostMapping("/{id}/refunds")
    @PreAuthorize("@perm.canAction('REFUND_REQUEST')")
    public RefundView requestRefund(@PathVariable UUID id, @Valid @RequestBody RefundCreateRequest request) {
        return corrections.requestRefund(id, request);
    }

    @PostMapping("/refunds/{refundId}/decision")
    @PreAuthorize("@perm.canAction('REFUND_APPROVE')")
    public RefundView decideRefund(@PathVariable UUID refundId,
                                   @Valid @RequestBody RefundDecisionRequest request) {
        return corrections.decideRefund(refundId, request);
    }
}
