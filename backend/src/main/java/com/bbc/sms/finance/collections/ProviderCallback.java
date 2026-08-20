package com.bbc.sms.finance.collections;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_callback")
@Getter
@Setter
public class ProviderCallback {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "provider_code", nullable = false, length = 32) private String providerCode;
    @Column(name = "event_id", nullable = false, length = 180) private String eventId;
    @Column(name = "external_reference", length = 180) private String externalReference;
    @Column(name = "payload_hash", nullable = false, length = 128) private String payloadHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private JsonNode payload;
    @Column(nullable = false, length = 16) private String status = "RECEIVED";
    @Column(name = "provider_transaction_id") private UUID providerTransactionId;
    @Column(name = "received_at", insertable = false, updatable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "processed_by") private UUID processedBy;
    @Column(length = 1000) private String message;
    @Version private long version;
}
