package com.bbc.sms.academic;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox boundary for parent publication notifications.  The
 * publication transaction only appends a PENDING event; delivery/retry can be
 * run by a worker without ever changing bulletin visibility or issuing a
 * second document.
 */
@Service
public class BulletinPublicationOutboxService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public BulletinPublicationOutboxService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public OutboxView enqueue(UUID bulletinVersionId, UUID parentVisibilityId,
                              UUID generatedDocumentId, UUID studentId,
                              UUID reportingPeriodId, String publicationProduct,
                              String snapshotHash) {
        UUID schoolId = TenantContext.get();
        String eventKey = "bulletin-published:" + bulletinVersionId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bulletinVersionId", bulletinVersionId);
        payload.put("parentVisibilityId", parentVisibilityId);
        payload.put("generatedDocumentId", generatedDocumentId);
        payload.put("studentId", studentId);
        payload.put("reportingPeriodId", reportingPeriodId);
        payload.put("publicationProduct", publicationProduct);
        payload.put("snapshotHash", snapshotHash);
        String json = json(payload);
        jdbc.update("""
                INSERT INTO bulletin_publication_outbox
                    (school_id,bulletin_version_id,parent_visibility_id,event_key,
                     event_type,payload,status,available_at)
                VALUES (?,?,?,?,? ,?::jsonb,'PENDING',now())
                ON CONFLICT (school_id,event_key) DO NOTHING
                """, schoolId, bulletinVersionId, parentVisibilityId, eventKey,
                "BULLETIN_PUBLISHED", json);
        return findByKey(schoolId, eventKey);
    }

    @Transactional(readOnly = true)
    public List<OutboxView> pending(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
                SELECT id,bulletin_version_id,parent_visibility_id,event_key,event_type,
                       status,attempt_count,available_at,last_error,created_at,processed_at
                  FROM bulletin_publication_outbox
                 WHERE school_id=? AND status IN ('PENDING','FAILED')
                   AND available_at<=now()
                 ORDER BY created_at,id
                 LIMIT ?
                """, (rs, n) -> view(rs), TenantContext.get(), safeLimit);
    }

    /** Claim one event for a delivery worker without duplicating a message. */
    @Transactional
    public OutboxView claim(UUID id) {
        int changed = jdbc.update("""
                UPDATE bulletin_publication_outbox
                   SET status='PROCESSING',attempt_count=attempt_count+1,last_error=NULL
                 WHERE school_id=? AND id=?
                   AND status IN ('PENDING','FAILED') AND available_at<=now()
                """, TenantContext.get(), id);
        if (changed == 0) throw ApiException.conflictWithDetails("OUTBOX_NOT_CLAIMABLE",
                "Cette notification a déjà été traitée ou n'est pas encore disponible.",
                Map.of("state", "PROCESSING_OR_SENT", "correctiveAction", "Rechargez la file de notifications."));
        return find(id);
    }

    @Transactional
    public OutboxView markSent(UUID id) {
        jdbc.update("""
                UPDATE bulletin_publication_outbox
                   SET status='SENT',processed_at=now(),last_error=NULL
                 WHERE school_id=? AND id=? AND status='PROCESSING'
                """, TenantContext.get(), id);
        return find(id);
    }

    @Transactional
    public OutboxView markFailed(UUID id, String error, Instant retryAt) {
        jdbc.update("""
                UPDATE bulletin_publication_outbox
                   SET status='FAILED',last_error=?,available_at=?
                 WHERE school_id=? AND id=? AND status='PROCESSING'
                """, clip(error), Timestamp.from(retryAt == null ? Instant.now() : retryAt),
                TenantContext.get(), id);
        return find(id);
    }

    @Transactional(readOnly = true)
    public OutboxView find(UUID id) {
        return jdbc.query("""
                SELECT id,bulletin_version_id,parent_visibility_id,event_key,event_type,
                       status,attempt_count,available_at,last_error,created_at,processed_at
                  FROM bulletin_publication_outbox
                 WHERE school_id=? AND id=?
                """, rs -> rs.next() ? view(rs) : null, TenantContext.get(), id);
    }

    private OutboxView findByKey(UUID schoolId, String eventKey) {
        return jdbc.query("""
                SELECT id,bulletin_version_id,parent_visibility_id,event_key,event_type,
                       status,attempt_count,available_at,last_error,created_at,processed_at
                  FROM bulletin_publication_outbox
                 WHERE school_id=? AND event_key=?
                """, rs -> rs.next() ? view(rs) : null, schoolId, eventKey);
    }

    private OutboxView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OutboxView(rs.getObject("id", UUID.class),
                rs.getObject("bulletin_version_id", UUID.class),
                rs.getObject("parent_visibility_id", UUID.class),
                rs.getString("event_key"), rs.getString("event_type"),
                rs.getString("status"), rs.getInt("attempt_count"),
                instant(rs.getTimestamp("available_at")), rs.getString("last_error"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("processed_at")));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Impossible de créer l'événement outbox", ex); }
    }

    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String clip(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        return clean.length() <= 1000 ? clean : clean.substring(0, 1000);
    }

    public record OutboxView(UUID id, UUID bulletinVersionId, UUID parentVisibilityId,
                             String eventKey, String eventType, String status,
                             int attemptCount, Instant availableAt, String lastError,
                             Instant createdAt, Instant processedAt) {}
}
