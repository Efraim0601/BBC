package com.bbc.sms.finance.collections;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.collections.CollectionDtos.*;

@Service
public class CashierService {
    private final CashierSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CashierService(CashierSessionRepository sessions, JdbcTemplate jdbc, AuditService audit) {
        this.sessions = sessions;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public CashierSessionView open(CashierOpenRequest request) {
        UUID schoolId = TenantContext.get();
        UUID userId = currentUserId();
        if (userId == null) throw ApiException.forbidden("Un utilisateur authentifié est requis pour ouvrir une caisse.");
        if (sessions.findBySchoolIdAndCashierUserIdAndStatus(schoolId, userId, "OPEN").isPresent()) {
            throw ApiException.conflict("Une session de caisse est déjà ouverte pour cet utilisateur.");
        }
        CashierSession session = new CashierSession();
        session.setSchoolId(schoolId);
        session.setCashierUserId(userId);
        session.setStatus("OPEN");
        session.setOpeningCashMinor(request == null ? 0 : request.openingCashMinor());
        session.setExpectedCashMinor(session.getOpeningCashMinor());
        session = sessions.saveAndFlush(session);
        CashierSessionView result = view(session);
        audit.record("CASHIER_SESSION_OPENED", "CashierSession", session.getId().toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public CashierSessionView current() {
        UUID userId = currentUserId();
        if (userId == null) return null;
        return sessions.findBySchoolIdAndCashierUserIdAndStatus(TenantContext.get(), userId, "OPEN")
                .map(this::view).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CashierSessionView> list() {
        return sessions.findBySchoolIdOrderByOpenedAtDesc(TenantContext.get()).stream().map(this::view).toList();
    }

    @Transactional
    public CashierSessionView close(UUID id, CashierCloseRequest request) {
        CashierSession session = require(id);
        if (!"OPEN".equals(session.getStatus())) throw ApiException.conflict("Cette session de caisse est déjà fermée.");
        if (!currentUserId().equals(session.getCashierUserId())) throw ApiException.forbidden("Seul le caissier peut fermer sa session.");
        checkVersion(session, request.version());
        recalculate(session);
        long variance = request.declaredCashMinor() - session.getExpectedCashMinor();
        if (variance != 0) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT, "CASHIER_MANAGER_APPROVAL_REQUIRED",
                    "La fermeture présente un écart et doit être approuvée par un responsable.",
                    Map.of("declaredCashMinor", "Un responsable doit approuver l'écart."),
                    List.of(new ApiException.Blocker("CASHIER_SESSION", id.toString(), "Écart de caisse: " + variance + " XAF", "APPROVE_CASHIER_CLOSE")));
        }
        return closeNow(session, request, null);
    }

    @Transactional
    public CashierSessionView approveClose(UUID id, CashierCloseRequest request) {
        CashierSession session = require(id);
        if (!"OPEN".equals(session.getStatus())) throw ApiException.conflict("Cette session de caisse est déjà fermée.");
        UUID manager = currentUserId();
        if (manager == null || manager.equals(session.getCashierUserId())) {
            throw ApiException.forbidden("La fermeture avec écart doit être approuvée par un autre utilisateur.");
        }
        checkVersion(session, request.version());
        recalculate(session);
        session.setDeclaredCashMinor(request.declaredCashMinor());
        session.setVarianceMinor(request.declaredCashMinor() - session.getExpectedCashMinor());
        session.setCloseNote(request.closeNote().trim());
        session.setManagerApprovedBy(manager);
        session.setManagerApprovedAt(Instant.now());
        CashierSessionView result = closeNow(session, request, manager);
        audit.record("CASHIER_SESSION_CLOSE_APPROVED", "CashierSession", id.toString(), null, result, request.closeNote());
        return result;
    }

    private CashierSessionView closeNow(CashierSession session, CashierCloseRequest request, UUID manager) {
        session.setDeclaredCashMinor(request.declaredCashMinor());
        session.setVarianceMinor(request.declaredCashMinor() - session.getExpectedCashMinor());
        session.setCloseNote(request.closeNote().trim());
        if (manager != null) {
            session.setManagerApprovedBy(manager);
            session.setManagerApprovedAt(Instant.now());
        }
        session.setStatus("CLOSED");
        session.setClosedAt(Instant.now());
        session = sessions.saveAndFlush(session);
        CashierSessionView result = view(session);
        audit.record("CASHIER_SESSION_CLOSED", "CashierSession", session.getId().toString(), null, result, request.closeNote());
        return result;
    }

    private void recalculate(CashierSession session) {
        Long incoming = jdbc.queryForObject("SELECT coalesce(sum(amount_minor),0) FROM finance_payment WHERE school_id=? AND cashier_session_id=? AND status='POSTED' AND channel_code_snapshot='CASH'",
                Long.class, TenantContext.get(), session.getId());
        Long outgoing = jdbc.queryForObject("SELECT coalesce(sum(rt.amount_minor),0) FROM refund_transaction rt JOIN finance_payment p ON p.school_id=rt.school_id AND p.id=rt.payment_id WHERE rt.school_id=? AND p.cashier_session_id=? AND rt.channel_code='CASH'",
                Long.class, TenantContext.get(), session.getId());
        session.setExpectedCashMinor(session.getOpeningCashMinor() + (incoming == null ? 0 : incoming) - (outgoing == null ? 0 : outgoing));
    }

    private CashierSession require(UUID id) {
        return sessions.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Session de caisse"));
    }

    private static void checkVersion(CashierSession session, long version) {
        if (session.getVersion() != version) throw ApiException.conflict("La session de caisse a changé. Actualisez avant de continuer.");
    }

    private CashierSessionView view(CashierSession s) {
        return new CashierSessionView(s.getId(), s.getCashierUserId(), s.getStatus(),
                s.getOpenedAt() == null ? null : s.getOpenedAt().atOffset(ZoneOffset.UTC),
                s.getClosedAt() == null ? null : s.getClosedAt().atOffset(ZoneOffset.UTC),
                s.getOpeningCashMinor(), s.getExpectedCashMinor(), s.getDeclaredCashMinor(), s.getVarianceMinor(),
                s.getCloseNote(), s.getManagerApprovedBy(), s.getVersion());
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
