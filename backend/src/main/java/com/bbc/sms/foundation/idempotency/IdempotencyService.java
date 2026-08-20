package com.bbc.sms.foundation.idempotency;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public <T> T execute(String endpoint, String key, Object request, Class<T> responseType, Supplier<T> command) {
        if (key == null || key.isBlank()) return command.get();
        String safeKey = key.trim();
        if (safeKey.length() > 120) throw ApiException.badRequest("Idempotency-Key trop longue");
        UUID schoolId = TenantContext.get();
        String hash = sha256(json(request));
        String lockName = schoolId + "|" + endpoint + "|" + safeKey;
        jdbc.execute((ConnectionCallback<Void>) con -> {
            var ps = con.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))");
            ps.setString(1, lockName);
            ps.execute();
            return null;
        });
        List<Row> rows = jdbc.query("""
                SELECT request_hash, response_json::text FROM idempotency_key
                WHERE school_id=? AND endpoint=? AND idempotency_key=? AND expires_at>now()
                """, (rs, n) -> new Row(rs.getString(1), rs.getString(2)), schoolId, endpoint, safeKey);
        if (!rows.isEmpty()) {
            Row row = rows.get(0);
            if (!row.requestHash.equals(hash)) throw ApiException.conflict("Cette clé d’idempotence a été utilisée avec une autre requête");
            try { return mapper.readValue(row.responseJson, responseType); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Réponse idempotente illisible", ex); }
        }
        T response = command.get();
        jdbc.update("""
            INSERT INTO idempotency_key
            (school_id, endpoint, idempotency_key, request_hash, response_json, response_type, expires_at)
            VALUES (?,?,?,?,?::jsonb,?,?)
            """, schoolId, endpoint, safeKey, hash, json(response), responseType.getName(),
                java.sql.Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        return response;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw ApiException.badRequest("Requête non sérialisable"); }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private record Row(String requestHash, String responseJson) {}
}
