package com.bbc.sms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FeeDtos {

    /** Une tranche de la grille : libellé affiché, montant et échéance facultative. */
    public record TrancheView(
            String label,
            long amount,
            LocalDate dueOn) {}

    public record FeeConfigView(
            UUID id,
            String level,
            String subsystem,
            /** Null pour une grille de niveau ; renseigné pour une surcharge de classe. */
            UUID classId,
            String className,
            long total,
            List<TrancheView> tranches,
            List<Map<String, Object>> items) {}

    public record FeeConfigUpdate(
            @NotBlank String level,
            String subsystem,
            UUID classId,
            @Positive long total,
            List<TrancheView> tranches,
            List<Map<String, Object>> items) {}

    public record SituationView(
            UUID studentId,
            String studentName,
            String className,
            long total,
            long paid,
            long balance,
            int tranchesPaid,
            String status,
            int progressPct) {}

    // ------------------------------------------------------------ moyens de paiement

    public record PaymentChannelView(
            UUID id,
            String code,
            String labelFr,
            String labelEn,
            String accountRef,
            String accountName,
            String instructionsFr,
            String instructionsEn,
            boolean requiresReference,
            boolean enabled,
            boolean visibleToParents,
            int sortOrder) {}

    public record PaymentChannelUpdate(
            String labelFr,
            String labelEn,
            String accountRef,
            String accountName,
            String instructionsFr,
            String instructionsEn,
            Boolean requiresReference,
            Boolean enabled,
            Boolean visibleToParents,
            Integer sortOrder) {}

    // ------------------------------------------------- situation détaillée d'un élève

    /** État d'une tranche pour un élève : ce qui est couvert par les versements reçus. */
    public record TrancheStatusView(
            int index,
            String label,
            long amount,
            LocalDate dueOn,
            long paid,
            long remaining,
            /** paid | partial | pending */
            String status,
            boolean overdue) {}

    public record StudentFeeStatementView(
            UUID studentId,
            String studentName,
            String matricule,
            String className,
            /** Origine de la grille appliquée : "class" ou "level" (null si aucune). */
            String gridSource,
            long total,
            long paid,
            long balance,
            int progressPct,
            String status,
            List<TrancheStatusView> tranches,
            List<PaymentLineView> payments) {}

    public record PaymentLineView(
            String receiptNo,
            LocalDate paidOn,
            long amount,
            String method,
            String methodLabelFr,
            String methodLabelEn,
            String reference,
            Integer tranche) {}
}
