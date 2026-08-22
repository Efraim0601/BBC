package com.bbc.sms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class FinanceDtos {

    public record PaymentView(
            UUID id,
            String receiptNo,
            UUID studentId,
            String studentName,
            String matricule,
            String className,
            long amount,
            /** Code du canal encaissé : CASH, OM, MOMO, MPGS, TRANSFER… */
            String method,
            String methodLabelFr,
            String methodLabelEn,
            /** Référence de transaction chez l'opérateur (mobile money, carte, virement). */
            String reference,
            Integer tranche,
            LocalDate paidOn,
            UUID treasuryAccountId,
            String treasuryAccountName,
            UUID journalEntryId) {}

    public record PaymentRequest(
            @NotNull UUID studentId,
            @Positive long amount,
            @NotBlank String method,
            String reference,
            Integer tranche,
            LocalDate paidOn,
            @NotNull UUID treasuryAccountId) {}

    public record ExpenseView(
            UUID id,
            LocalDate spentOn,
            String category,
            String label,
            long amount,
            UUID treasuryAccountId,
            String treasuryAccountName,
            UUID journalEntryId,
            String status) {}

    public record ExpenseRequest(
            @NotNull LocalDate spentOn,
            @NotBlank String category,
            @NotBlank String label,
            @Positive long amount,
            @NotNull UUID treasuryAccountId) {}

    public record RevenuePoint(
            LocalDate date,
            long amount) {}

    /**
     * Synthèse à 30 jours. {@code section} est non nul pour un administrateur de
     * cycle : les recettes ne portent alors que sur ses élèves, et les dépenses
     * — qui n'appartiennent à aucune section — sont exclues plutôt que
     * réparties arbitrairement. L'écran doit le dire, faute de quoi un solde
     * amputé passerait pour le solde de l'école.
     */
    public record FinanceSummary(
            long totalRevenue30d,
            long totalExpense30d,
            long balance30d,
            int paymentsCount,
            List<RevenuePoint> revenueSeries,
            String section) {}
}
