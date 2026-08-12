package com.bbc.sms.foundation.audit;

import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.foundation.audit.AuditDtos.AuditView;

@Service
public class AuditService {
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken",
            "secret", "apikey", "medicalnotes", "confidentialnotes");
    private final JdbcTemplate jdbc;
    private final AuditEventRepository repository;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc, AuditEventRepository repository, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public void record(String action, String aggregateType, String aggregateId,
                       Object before, Object after, String reason) {
        recordWithId(action, aggregateType, aggregateId, before, after, reason);
    }

    /**
     * Records an audit event and returns its durable id.  Most callers only
     * need {@link #record}; lifecycle aggregates use the id to make their
     * append-only transition row auditable without a race-prone follow-up
     * lookup.
     */
    @Transactional
    public UUID recordWithId(String action, String aggregateType, String aggregateId,
                             Object before, Object after, String reason) {
        AppUserPrincipal principal = principal();
        HttpServletRequest req = request();
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                INSERT INTO audit_event
                (id, school_id, actor_user_id, actor_username, action, aggregate_type,
                 aggregate_id, before_data, after_data, reason, request_id, correlation_id,
                 ip_address, user_agent)
                VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?)
                """);
            ps.setObject(1, id);
            ps.setObject(2, TenantContext.get());
            ps.setObject(3, principal == null ? null : principal.userId());
            ps.setString(4, principal == null ? "system" : principal.username());
            ps.setString(5, action);
            ps.setString(6, aggregateType);
            ps.setString(7, aggregateId);
            ps.setObject(8, json(before), Types.VARCHAR);
            ps.setObject(9, json(after), Types.VARCHAR);
            ps.setString(10, trim(reason, 500));
            ps.setString(11, req == null ? null : trim(req.getHeader("X-Request-ID"), 100));
            ps.setString(12, req == null ? null : trim(req.getHeader("X-Correlation-ID"), 100));
            ps.setString(13, req == null ? null : trim(req.getRemoteAddr(), 64));
            ps.setString(14, req == null ? null : trim(req.getHeader("User-Agent"), 300));
            return ps;
        });
        return id;
    }

    @Transactional(readOnly = true)
    public List<AuditView> forAggregate(String aggregateType, String aggregateId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findBySchoolIdAndAggregateTypeAndAggregateIdOrderByOccurredAtDesc(
                TenantContext.get(), aggregateType, aggregateId, PageRequest.of(0, safeLimit))
                .stream().map(this::view).toList();
    }

    private AuditView view(AuditEvent e) {
        return new AuditView(e.getId(), e.getActorUserId(), e.getActorUsername(), e.getAction(),
                e.getAggregateType(), e.getAggregateId(), parse(e.getBeforeData()), parse(e.getAfterData()),
                e.getReason(), e.getRequestId(), e.getCorrelationId(), e.getOccurredAt());
    }

    private String json(Object value) {
        if (value == null) return null;
        JsonNode node = mapper.valueToTree(value);
        redact(node);
        try { return mapper.writeValueAsString(node); }
        catch (JsonProcessingException ex) { return "{\"serializationError\":true}"; }
    }

    private void redact(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fields = obj.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                if (SECRET_KEYS.contains(name.replaceAll("[^A-Za-z]", "").toLowerCase())) {
                    obj.put(name, "[REDACTED]");
                } else redact(obj.get(name));
            }
        } else if (node.isArray()) node.forEach(this::redact);
    }

    private JsonNode parse(String raw) {
        if (raw == null) return null;
        try { return mapper.readTree(raw); }
        catch (JsonProcessingException ex) { return mapper.createObjectNode().put("unreadable", true); }
    }

    private static AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p : null;
    }

    private static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
