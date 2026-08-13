package com.bbc.sms.finance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Moyen de paiement accepté par l'établissement (Espèces, Orange Money, MTN MoMo,
 * carte MPGS, virement). Le code est celui stocké dans {@code payment.method}.
 *
 * <p>Les coordonnées ({@code accountRef}) et les instructions sont affichées au parent
 * dans son portail : c'est ce qui lui permet de régler une tranche par lui-même, puis
 * de communiquer la référence de transaction à l'économat.
 */
@Entity
@Table(name = "payment_channel")
@Getter
@Setter
public class PaymentChannel {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String code;

    @Column(name = "label_fr", nullable = false)
    private String labelFr;

    @Column(name = "label_en", nullable = false)
    private String labelEn;

    /** Numéro Orange Money / MoMo, identifiant marchand MPGS, RIB… */
    @Column(name = "account_ref")
    private String accountRef;

    @Column(name = "account_name")
    private String accountName;

    /** Ledger account debited when a collection is posted through this channel. */
    @Column(name = "debit_account_id")
    private UUID debitAccountId;

    @Column(name = "instructions_fr", columnDefinition = "text")
    private String instructionsFr;

    @Column(name = "instructions_en", columnDefinition = "text")
    private String instructionsEn;

    /** Impose la saisie d'une référence de transaction à l'encaissement. */
    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "visible_to_parents", nullable = false)
    private boolean visibleToParents = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
