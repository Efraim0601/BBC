package com.bbc.sms.finance.fees;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FeeTypeDtos {
    private FeeTypeDtos() {}

    public record FeeTypeRevisionInput(
            @NotBlank String nameFr,
            @NotBlank String nameEn,
            @Size(max = 500) String descriptionFr,
            @Size(max = 500) String descriptionEn,
            @NotBlank String category,
            @NotNull @PositiveOrZero Long defaultAmountMinor,
            @NotBlank String defaultCurrency,
            @NotBlank String frequency,
            Boolean mandatory,
            Boolean refundable,
            Boolean taxable,
            @PositiveOrZero Integer taxBasisPoints,
            UUID receivableAccountId,
            UUID revenueAccountId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Long version) {}

    public record FeeTypeCreateRequest(
            @NotBlank String code,
            @NotNull @Valid FeeTypeRevisionInput revision) {}

    public record FeeTypeDraftUpdate(
            @NotBlank String code,
            @NotNull @Valid FeeTypeRevisionInput revision,
            @NotNull @PositiveOrZero Long typeVersion) {}

    public record FeeTypeRevisionCreateRequest(
            @NotNull @Valid FeeTypeRevisionInput revision,
            @NotNull @PositiveOrZero Long typeVersion,
            @Size(max = 500) String reason) {}

    public record FeeTypeActionRequest(
            @NotNull @PositiveOrZero Long typeVersion,
            @Size(max = 500) String reason) {}

    public record AccountRef(UUID id, String code, String nameFr, String nameEn,
                             String accountType, String currency, boolean active,
                             boolean postingAllowed, boolean compatible, String compatibilityMessage) {}

    public record FeeTypeRevisionView(
            UUID id,
            int revisionNo,
            String revisionStatus,
            String nameFr,
            String nameEn,
            String descriptionFr,
            String descriptionEn,
            String category,
            long defaultAmountMinor,
            String defaultCurrency,
            String frequency,
            boolean mandatory,
            boolean refundable,
            boolean taxable,
            int taxBasisPoints,
            UUID receivableAccountId,
            AccountRef receivableAccount,
            UUID revenueAccountId,
            AccountRef revenueAccount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String effectiveStatus,
            Instant activatedAt,
            long version) {}

    public record FeeTypeView(
            UUID id,
            String code,
            String lifecycle,
            Integer currentRevisionNo,
            FeeTypeRevisionView currentRevision,
            List<FeeTypeRevisionView> revisions,
            long usageCount,
            String effectiveStatus,
            long version,
            Instant createdAt,
            Instant activatedAt,
            Instant deactivatedAt,
            String deactivationReason) {}

    public record FeeTypeDependency(
            String entityType,
            String entityId,
            String label,
            String sessionId,
            String sessionLabel,
            String classId,
            String classLabel,
            String status,
            String detail) {}

    public record FeeTypeUsageView(UUID feeTypeId, String code, long usageCount,
                                   List<FeeTypeDependency> dependencies) {}

    public record LegacyFeeCandidate(
            String sourceKey,
            String sourceConfigId,
            String level,
            String classId,
            String rawName,
            String suggestedCode,
            String suggestedNameFr,
            String suggestedNameEn,
            long amountMinor,
            String currency,
            String category,
            boolean ambiguous,
            String reviewReason) {}

    public record LegacyPreviewView(List<LegacyFeeCandidate> candidates,
                                    int candidateCount, int ambiguousCount,
                                    int unresolvedCount, Instant generatedAt) {}

    public record LegacyMappingRow(
            @NotBlank String sourceKey,
            Boolean accept,
            UUID feeTypeId,
            String code,
            String nameFr,
            String nameEn,
            String category) {}

    public record LegacyMappingRequest(
            @NotEmpty @Valid List<LegacyMappingRow> rows,
            @Size(max = 500) String reason) {}

    public record LegacyMigrationResult(int acceptedCount, int unresolvedCount,
                                        List<FeeTypeView> mappedFeeTypes,
                                        List<LegacyFeeCandidate> unresolved,
                                        Instant completedAt) {}

    public record FeeTypeComparison(UUID feeTypeId, String code, int leftRevision,
                                    int rightRevision, List<ComparisonField> differences) {}

    public record ComparisonField(String field, String leftValue, String rightValue) {}
}
