package com.bbc.sms.finance.treasury;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TreasuryDtos {
    private TreasuryDtos() {}

    public record TreasuryAccountView(UUID id, UUID chartAccountId, String chartAccountCode,
                                      String kind, String displayName, String institutionName,
                                      String accountNumberLast4, String currency, boolean active,
                                      boolean defaultAccount, long balanceMinor, long version) {}

    public record TreasuryAccountCreate(
            @NotBlank String kind,
            @NotBlank String displayName,
            String institutionName,
            String accountNumberLast4,
            String currency,
            @PositiveOrZero long openingBalanceMinor,
            @NotNull LocalDate openingBalanceDate,
            String chartAccountCode) {}

    public record ArchiveRequest(@NotNull Long version, @NotBlank String reason) {}

    public record TreasuryMovementRequest(
            @NotBlank String movementType,
            @NotNull LocalDate entryDate,
            UUID fromAccountId,
            UUID toAccountId,
            UUID offsetAccountId,
            @Positive long amountMinor,
            String currency,
            @NotBlank String reason,
            String reference) {}

    public record TreasuryMovementView(UUID id, String movementNo, String movementType,
                                       LocalDate entryDate, UUID fromAccountId, String fromAccountName,
                                       UUID toAccountId, String toAccountName, UUID offsetAccountId,
                                       String offsetAccountCode, long amountMinor, String currency,
                                       String reason, String reference, String status,
                                       UUID journalEntryId, String journalNumber,
                                       UUID createdBy, OffsetDateTime createdAt, long version) {}
}
