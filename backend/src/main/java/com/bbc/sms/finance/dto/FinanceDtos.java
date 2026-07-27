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
            LocalDate paidOn) {}

    public record PaymentRequest(
            @NotNull UUID studentId,
            @Positive long amount,
            @NotBlank String method,
            String reference,
            Integer tranche,
            LocalDate paidOn) {}

    public record ExpenseView(
            UUID id,
            LocalDate spentOn,
            String category,
            String label,
            long amount) {}

    public record ExpenseRequest(
            @NotNull LocalDate spentOn,
            @NotBlank String category,
            @NotBlank String label,
            @Positive long amount) {}

    public record RevenuePoint(
            LocalDate date,
            long amount) {}

    public record FinanceSummary(
            long totalRevenue30d,
            long totalExpense30d,
            long balance30d,
            int paymentsCount,
            List<RevenuePoint> revenueSeries) {}
}
