package com.bbc.sms.finance.collections;

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
@Table(name = "cashier_session")
@Getter
@Setter
public class CashierSession {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "cashier_user_id", nullable = false) private UUID cashierUserId;
    @Column(nullable = false, length = 8) private String status = "OPEN";
    @Column(name = "opened_at", insertable = false, updatable = false) private Instant openedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "opening_cash_minor", nullable = false) private long openingCashMinor;
    @Column(name = "expected_cash_minor", nullable = false) private long expectedCashMinor;
    @Column(name = "declared_cash_minor") private Long declaredCashMinor;
    @Column(name = "variance_minor") private Long varianceMinor;
    @Column(name = "close_note", length = 1000) private String closeNote;
    @Column(name = "manager_approved_by") private UUID managerApprovedBy;
    @Column(name = "manager_approved_at") private Instant managerApprovedAt;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
    @Version private long version;
}
