package com.bbc.sms.finance.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_line")
@Getter
@Setter
public class JournalLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "journal_entry_id", nullable = false) private UUID journalEntryId;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "debit_minor", nullable = false) private long debitMinor;
    @Column(name = "credit_minor", nullable = false) private long creditMinor;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "enrollment_id") private UUID enrollmentId;
    @Column(name = "employee_id") private UUID employeeId;
    @Column(name = "class_id") private UUID classId;
    @Column(name = "fee_type_code", length = 64) private String feeTypeCode;
    @Column(length = 500) private String description;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
