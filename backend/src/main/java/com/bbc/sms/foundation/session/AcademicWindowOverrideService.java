package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.*;

/** Creates short-lived, separately audited exceptions to configured workflow windows. */
@Service
public class AcademicWindowOverrideService {
    private static final List<String> ACTIONS = List.of("GRADE_ENTRY", "TEACHER_SUBMISSION", "REVIEW",
            "VALIDATION", "PUBLICATION", "CORRECTION");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AcademicWindowOverrideService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WindowOverrideView> list(UUID sessionId, UUID periodId) {
        ensureSession(sessionId);
        String suffix = periodId == null ? "" : " AND reporting_period_id=?";
        Object[] args = periodId == null
                ? new Object[]{TenantContext.get(), sessionId}
                : new Object[]{TenantContext.get(), sessionId, periodId};
        return jdbc.query("SELECT id,academic_session_id,reporting_period_id,action,scope,reason,opens_at,expires_at,created_by,created_at,version,(opens_at<=now() AND expires_at>now()) AS active "
                        + "FROM academic_window_override WHERE school_id=? AND academic_session_id=?" + suffix
                        + " ORDER BY opens_at DESC",
                (rs, n) -> view(rs), args);
    }

    @Transactional
    public WindowOverrideView create(UUID sessionId, WindowOverrideUpsert in) {
        throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "WORKFLOW_WINDOWS_REPLACED",
                "Les dérogations par action ont été retirées. Utilisez Paramètres → Années & périodes → Accès par trimestre.");
        /* Legacy create validation remains below for rollback/reference only. */
        /*
        ensureSession(sessionId);
        String action = in.action().trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "WINDOW_OVERRIDE_ACTION_INVALID",
                    "L'action de la dérogation est invalide.", "action", "Choose a valid workflow action.");
        }
        if (!in.expiresAt().isAfter(in.opensAt())) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "WINDOW_OVERRIDE_RANGE_INVALID",
                    "La fin de la dérogation doit être postérieure à son ouverture.", "expiresAt", "Choose an expiration after the opening time.");
        }
        if (Duration.between(in.opensAt(), in.expiresAt()).compareTo(Duration.ofDays(31)) > 0) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "WINDOW_OVERRIDE_TOO_LONG",
                    "Une dérogation ne peut pas dépasser 31 jours.", "expiresAt", "Use a shorter emergency window.");
        }
        if (in.reportingPeriodId() != null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_reporting_period WHERE id=? AND school_id=? AND academic_session_id=?",
                    Integer.class, in.reportingPeriodId(), TenantContext.get(), sessionId);
            if (count == null || count == 0) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "WINDOW_OVERRIDE_PERIOD_INVALID", "La période ne correspond pas à la session.", "reportingPeriodId",
                    "Choose a reporting period from this session.");
        }
        UUID id = UUID.randomUUID();
        UUID actor = currentUser();
        jdbc.update("INSERT INTO academic_window_override(id,school_id,academic_session_id,reporting_period_id,action,scope,reason,opens_at,expires_at,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, TenantContext.get(), sessionId, in.reportingPeriodId(), action, in.scope().trim(), in.reason().trim(),
                java.sql.Timestamp.from(in.opensAt()), java.sql.Timestamp.from(in.expiresAt()), actor);
        WindowOverrideView result = jdbc.queryForObject("SELECT id,academic_session_id,reporting_period_id,action,scope,reason,opens_at,expires_at,created_by,created_at,version,(opens_at<=now() AND expires_at>now()) AS active FROM academic_window_override WHERE id=? AND school_id=?",
                (rs, n) -> view(rs), id, TenantContext.get());
        audit.record("ACADEMIC_WINDOW_OVERRIDE_CREATED", "AcademicWindowOverride", id.toString(), null, result, in.reason());
        return result;
        */
    }

    @Transactional
    public void revoke(UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                "REASON_REQUIRED", "Le motif est obligatoire.", "reason", "Provide a reason before revoking the override.");
        WindowOverrideView before = jdbc.queryForObject("SELECT id,academic_session_id,reporting_period_id,action,scope,reason,opens_at,expires_at,created_by,created_at,version,(opens_at<=now() AND expires_at>now()) AS active FROM academic_window_override WHERE id=? AND school_id=?",
                (rs, n) -> view(rs), id, TenantContext.get());
        int changed = jdbc.update("UPDATE academic_window_override SET expires_at=LEAST(expires_at,now()),version=version+1 WHERE id=? AND school_id=? AND expires_at>now()",
                id, TenantContext.get());
        if (changed == 0) throw ApiException.conflict("La dérogation est déjà expirée ou n'existe plus.");
        audit.record("ACADEMIC_WINDOW_OVERRIDE_REVOKED", "AcademicWindowOverride", id.toString(), before, null, reason.trim());
    }

    private void ensureSession(UUID sessionId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_session WHERE id=? AND school_id=?", Integer.class,
                sessionId, TenantContext.get());
        if (count == null || count == 0) throw ApiException.notFound("Session académique");
    }

    private static WindowOverrideView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp opens = rs.getTimestamp("opens_at");
        java.sql.Timestamp expires = rs.getTimestamp("expires_at");
        java.sql.Timestamp created = rs.getTimestamp("created_at");
        return new WindowOverrideView(rs.getObject("id", UUID.class), rs.getObject("academic_session_id", UUID.class),
                rs.getObject("reporting_period_id", UUID.class), rs.getString("action"), rs.getString("scope"),
                rs.getString("reason"), opens == null ? null : opens.toInstant(), expires == null ? null : expires.toInstant(),
                rs.getObject("created_by", UUID.class), created == null ? null : created.toInstant(), rs.getLong("version"), rs.getBoolean("active"));
    }

    private static UUID currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
