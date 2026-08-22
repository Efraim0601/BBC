package com.bbc.sms.finance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense")
@Getter
@Setter
public class Expense {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private long amount;

    @Column(name = "treasury_account_id")
    private UUID treasuryAccountId;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(nullable = false, length = 12)
    private String status = "POSTED";

    @Column(name = "created_by")
    private UUID createdBy;
}
