package com.bbc.sms.finance.collections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CollectionDtos {
    private CollectionDtos() {}

    public record BlockerView(String code, String message, String actionLink) {}

    public record StudentSearchView(UUID studentId, String studentName, String matricule,
                                    UUID enrollmentId, UUID academicSessionId,
                                    String className,
                                    LocalDate enrolledOn, LocalDate exitedOn,
                                    long outstandingMinor, long overdueMinor) {}

    public record InstallmentProposal(UUID installmentId, UUID chargeId, String feeTypeCode,
                                      String label, LocalDate dueDate, long outstandingMinor,
                                      long proposedMinor, String status) {}

    public record ChannelView(UUID id, String code, String labelFr, String labelEn,
                              boolean requiresReference, boolean enabled, boolean cashierRequired,
                              UUID debitAccountId, String debitAccountCode,
                              String debitAccountName, String currency) {}

    public record QuoteRequest(@NotNull UUID enrollmentId, @Positive long amountMinor,
                               @NotNull LocalDate paymentDate) {}

    public record PaymentQuoteView(UUID enrollmentId, UUID studentId, String studentName,
                                   UUID academicSessionId, String className,
                                   long requestedMinor, long existingCreditMinor,
                                   long proposedAllocatedMinor, long newCreditMinor,
                                   long projectedOutstandingMinor, String currency,
                                   boolean postingPeriodOpen, String postingPeriodCode,
                                   List<InstallmentProposal> installments,
                                   List<ChannelView> channels, List<BlockerView> blockers,
                                   List<TreasuryAccountOption> treasuryAccounts) {}

    public record TreasuryAccountOption(UUID id, UUID chartAccountId, String displayName, String kind,
                                       String currency, long balanceMinor) {}

    public record AllocationInput(@NotNull UUID installmentId, @Positive long amountMinor) {}

    public record PaymentRequest(@NotNull UUID enrollmentId, @Positive long amountMinor,
                                 @NotNull UUID paymentChannelId, UUID treasuryAccountId,
                                 @NotNull LocalDate paymentDate,
                                 String reference, String payerName, String note,
                                 List<@Valid AllocationInput> allocations,
                                 String legacyReceiptNo) {}

    public record AllocationView(UUID id, UUID installmentId, long allocatedMinor,
                                 String currency, String status) {}

    public record PaymentView(UUID id, UUID studentId, UUID enrollmentId,
                              UUID academicSessionId, long amountMinor, String currency,
                              LocalDate paymentDate, String channelCode, String reference,
                              String status, String receiptNo, String legacyReceiptNo,
                              UUID journalEntryId, long allocatedMinor, long creditMinor,
                              long outstandingMinor, long version,
                              List<AllocationView> allocations,
                              UUID receiptDocumentId, String receiptDocumentNumber,
                              String receiptDocumentStatus, String receiptGenerationError,
                              UUID treasuryAccountId, String treasuryAccountName) {}

    public record ReversalPreview(UUID paymentId, String receiptNo, long amountMinor,
                                  long allocatedMinor, long remainingCreditMinor,
                                  boolean allowed, List<BlockerView> blockers) {}

    public record ReversalRequest(@NotBlank String reason, @NotNull Long version) {}

    public record RefundCreateRequest(@Positive long amountMinor, @NotBlank String channelCode,
                                      String reference, @NotBlank String reason,
                                      @NotNull Long version) {}

    public record RefundDecisionRequest(@NotNull Long version, boolean approve,
                                        @NotBlank String decisionReason) {}

    public record RefundView(UUID id, UUID paymentId, String refundNo, long amountMinor,
                             String currency, String channelCode, String reference,
                             String status, String reason, UUID requestedBy,
                             UUID approvedBy, UUID journalEntryId, long version) {}

    public record CashierOpenRequest(@PositiveOrZero long openingCashMinor) {}

    public record CashierCloseRequest(@PositiveOrZero long declaredCashMinor,
                                      @NotBlank String closeNote, @NotNull Long version) {}

    public record CashierSessionView(UUID id, UUID cashierUserId, String status,
                                     OffsetDateTime openedAt, OffsetDateTime closedAt,
                                     long openingCashMinor, long expectedCashMinor,
                                     Long declaredCashMinor, Long varianceMinor,
                                     String closeNote, UUID managerApprovedBy, long version) {}

    public record ProviderCallbackRequest(@NotBlank String providerCode, @NotBlank String eventId,
                                          @NotNull UUID paymentChannelId,
                                          String externalReference, Long amountMinor,
                                          String currency, JsonNode payload) {}

    public record ProviderTransactionView(UUID id, String providerCode, String externalReference,
                                         Long amountMinor, String currency, String status,
                                         UUID paymentId, String message, OffsetDateTime receivedAt) {}

    public record ProviderConfirmRequest(@NotNull UUID paymentId, @NotNull Long version) {}

    public record PaymentListFilters(UUID academicSessionId, String status, String channelCode,
                                     LocalDate fromDate, LocalDate toDate, UUID studentId,
                                     String reference, UUID cashierSessionId) {}
}
