package com.bbc.sms.foundation.audit;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_event")
@Getter
public class AuditEvent {
    @Id private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "actor_username") private String actorUsername;
    @Column(nullable = false) private String action;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id") private String aggregateId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_data", columnDefinition = "jsonb") private String beforeData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_data", columnDefinition = "jsonb") private String afterData;
    private String reason;
    @Column(name = "request_id") private String requestId;
    @Column(name = "correlation_id") private String correlationId;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "occurred_at", insertable = false, updatable = false) private Instant occurredAt;
}
