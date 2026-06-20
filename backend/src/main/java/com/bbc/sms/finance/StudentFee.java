package com.bbc.sms.finance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "student_fee")
@Getter
@Setter
public class StudentFee {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private long total;

    @Column(nullable = false)
    private long paid;

    @Column(nullable = false)
    private long balance;

    @Column(name = "tranches_paid", nullable = false)
    private int tranchesPaid;

    @Column(nullable = false)
    private String status;
}
