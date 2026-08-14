package com.bbc.sms.finance.collections;

import com.bbc.sms.finance.PaymentChannel;
import com.bbc.sms.finance.PaymentChannelRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

/** Provider callbacks are persisted and matched only; they never post cash by themselves. */
@Service
public class ProviderReconciliationService {
    private final ProviderCallbackRepository callbacks;
    private final ProviderTransactionRepository transactions;
    private final FinancePaymentRepository payments;
    private final PaymentChannelRepository channels;
    private final AuditService audit;

    public ProviderReconciliationService(ProviderCallbackRepository callbacks,
                                         ProviderTransactionRepository transactions,
                                         FinancePaymentRepository payments,
                                         PaymentChannelRepository channels,
                                         AuditService audit) {
        this.callbacks = callbacks;
        this.transactions = transactions;
        this.payments = payments;
        this.channels = channels;
        this.audit = audit;
    }

    @Transactional
    public ProviderTransactionView ingest(ProviderCallbackRequest request) {
        UUID schoolId = TenantContext.get();
        String provider = request.providerCode().trim().toUpperCase();
        String event = request.eventId().trim();
        String reference = trim(request.externalReference());
        String callbackReference = reference == null ? event : reference;
        String hash = sha256(request.payload() == null ? "{}" : request.payload().toString());
        ProviderCallback existingCallback = callbacks.findBySchoolIdAndProviderCodeAndEventId(schoolId, provider, event).orElse(null);
        if (existingCallback != null) {
            ProviderTransaction existing = existingCallback.getProviderTransactionId() == null ? null
                    : transactions.findById(existingCallback.getProviderTransactionId()).orElse(null);
            return existing == null ? new ProviderTransactionView(null, provider, callbackReference, request.amountMinor(), currency(request.currency()),
                    existingCallback.getStatus(), null, existingCallback.getMessage(), existingCallback.getReceivedAt() == null ? null : existingCallback.getReceivedAt().atOffset(ZoneOffset.UTC)) : view(existing);
        }
        PaymentChannel channel = channels.findById(request.paymentChannelId()).filter(c -> schoolId.equals(c.getSchoolId()))
                .orElseThrow(() -> ApiException.notFound("Canal de paiement"));
        ProviderTransaction transaction = transactions.findBySchoolIdAndProviderCodeAndExternalReference(schoolId, provider, callbackReference).orElse(null);
        boolean matched = false;
        FinancePayment payment = reference == null ? null
                : payments.findBySchoolIdAndChannelCodeSnapshotAndReference(schoolId, channel.getCode(), reference).orElse(null);
        if (transaction == null) {
            transaction = new ProviderTransaction();
            transaction.setSchoolId(schoolId);
            transaction.setPaymentChannelId(channel.getId());
            transaction.setProviderCode(provider);
            transaction.setExternalReference(callbackReference);
            transaction.setAmountMinor(request.amountMinor());
            transaction.setCurrency(currency(request.currency()));
            transaction.setPayloadHash(hash);
            transaction.setStatus(payment == null ? "RECEIVED" : "MATCHED");
            transaction.setFinancePaymentId(payment == null ? null : payment.getId());
            transaction.setMatchedAt(payment == null ? null : java.time.Instant.now());
            transaction.setMatchedBy(payment == null ? null : currentUserId());
            transaction = transactions.saveAndFlush(transaction);
            matched = payment != null;
        }
        ProviderCallback callback = new ProviderCallback();
        callback.setSchoolId(schoolId);
        callback.setProviderCode(provider);
        callback.setEventId(event);
        callback.setExternalReference(callbackReference);
        callback.setPayloadHash(hash);
        callback.setPayload(request.payload() == null ? JsonNodeFactory.instance.objectNode() : request.payload());
        callback.setStatus(matched ? "MATCHED" : "MANUAL_REVIEW");
        callback.setProviderTransactionId(transaction.getId());
        callback.setMessage(matched ? "Référence rapprochée à un encaissement déjà posté; aucun encaissement n'a été créé." : "Aucune correspondance sûre; confirmation manuelle requise.");
        callbacks.saveAndFlush(callback);
        ProviderTransactionView result = view(transaction);
        audit.record("PROVIDER_CALLBACK_INGESTED", "ProviderCallback", callback.getId().toString(), null, result, result.message());
        return result;
    }

    @Transactional
    public ProviderTransactionView confirm(UUID id, ProviderConfirmRequest request) {
        ProviderTransaction tx = transactions.findById(id).filter(t -> TenantContext.get().equals(t.getSchoolId()))
                .orElseThrow(() -> ApiException.notFound("Transaction fournisseur"));
        if (tx.getVersion() != request.version()) throw ApiException.conflict("La transaction fournisseur a changé. Actualisez.");
        FinancePayment payment = payments.findByIdAndSchoolId(request.paymentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Encaissement"));
        if (tx.getAmountMinor() != null && tx.getAmountMinor() != payment.getAmountMinor()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "PROVIDER_AMOUNT_MISMATCH",
                    "Le montant fournisseur ne correspond pas à l'encaissement.", java.util.Map.of("amountMinor", "Vérifiez l'encaissement et la preuve fournisseur."), java.util.List.of());
        }
        tx.setFinancePaymentId(payment.getId());
        tx.setStatus("MANUAL_CONFIRMED");
        tx.setMatchedAt(java.time.Instant.now());
        tx.setMatchedBy(currentUserId());
        tx = transactions.saveAndFlush(tx);
        ProviderTransactionView result = view(tx);
        audit.record("PROVIDER_TRANSACTION_CONFIRMED", "ProviderTransaction", id.toString(), null, result, "Confirmation manuelle");
        return result;
    }

    private ProviderTransactionView view(ProviderTransaction tx) {
        return new ProviderTransactionView(tx.getId(), tx.getProviderCode(), tx.getExternalReference(), tx.getAmountMinor(),
                tx.getCurrency(), tx.getStatus(), tx.getFinancePaymentId(), tx.getRejectionReason(),
                tx.getReceivedAt() == null ? null : tx.getReceivedAt().atOffset(ZoneOffset.UTC));
    }
    private static String currency(String value) { return value == null || value.isBlank() ? "XAF" : value.trim().toUpperCase(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static UUID currentUserId() { var auth=SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
