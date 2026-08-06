package com.bbc.sms.foundation.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {}
    public record AuditView(UUID id, UUID actorUserId, String actorUsername, String action,
                            String aggregateType, String aggregateId, JsonNode beforeData,
                            JsonNode afterData, String reason, String requestId,
                            String correlationId, Instant occurredAt) {}
}
