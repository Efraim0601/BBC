package com.bbc.sms.finance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "receipt_no", nullable = false)
    private String receiptNo;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private long amount;

    /** Code du canal (payment_channel.code) : CASH, OM, MOMO, MPGS, TRANSFER… */
    @Column(nullable = false)
    private String method;

    /** Référence de la transaction chez l'opérateur — la preuve côté parent. */
    private String reference;

    private Integer tranche;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
