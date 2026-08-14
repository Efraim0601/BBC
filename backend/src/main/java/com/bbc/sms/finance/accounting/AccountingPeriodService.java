package com.bbc.sms.finance.accounting;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class AccountingPeriodService {
    private final AccountingPeriodRepository periods;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final FinancePolicyService financePolicy;

    public AccountingPeriodService(AccountingPeriodRepository periods, JdbcTemplate jdbc, AuditService audit,
                                   FinancePolicyService financePolicy) {
        this.periods = periods;
        this.jdbc = jdbc;
        this.audit = audit;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public List<PeriodView> list() {
        financePolicy.requireSchool("FINANCE_OVERVIEW_VIEW");
        return periods.findBySchoolIdOrderByStartDateDesc(TenantContext.get()).stream().map(this::view).toList();
    }

    @Transactional
    public PeriodView create(PeriodUpsert in) {
        financePolicy.requireSchool("LEDGER_CLOSE");
        validateDates(in.startDate(), in.endDate());
        String status = normalizeStatus(in.status());
        UUID schoolId = TenantContext.get();
        validateAcademicSession(in.academicSessionId(), schoolId);
        if (periods.existsBySchoolIdAndCode(schoolId, in.code().trim())) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "PERIOD_CODE_EXISTS", "Ce code de période existe déjà.",
                    Map.of("code", "Choisissez un code unique."), List.of());
        }
        if ("OPEN".equals(status) && overlapsOpen(schoolId, in.startDate(), in.endDate(), null)) {
            throw overlapError();
        }
        AccountingPeriod p = new AccountingPeriod();
        p.setSchoolId(schoolId);
        apply(p, in, status);
        p = periods.saveAndFlush(p);
        PeriodView result = view(p);
        audit.record("ACCOUNTING_PERIOD_CREATED", "AccountingPeriod", p.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public PeriodView update(UUID id, PeriodUpsert in) {
        financePolicy.requireSchool("LEDGER_CLOSE");
        AccountingPeriod p = require(id);
        AccountService.requireVersion(in.version(), p.getVersion(), "période comptable");
        validateDates(in.startDate(), in.endDate());
        String status = normalizeStatus(in.status() == null ? p.getStatus() : in.status());
        validateAcademicSession(in.academicSessionId(), TenantContext.get());
        if (!status.equals(p.getStatus())) {
            throw ApiException.conflict("Utilisez l'action dédiée pour fermer ou rouvrir une période.");
        }
        if ("OPEN".equals(status) && overlapsOpen(TenantContext.get(), in.startDate(), in.endDate(), id)) {
            throw overlapError();
        }
        PeriodView before = view(p);
        apply(p, in, status);
        p = periods.saveAndFlush(p);
        PeriodView result = view(p);
        audit.record("ACCOUNTING_PERIOD_UPDATED", "AccountingPeriod", p.getId().toString(), before, result, null);
        return result;
    }

    @Transactional
    public List<PeriodView> generate(GeneratePeriodsRequest in) {
        financePolicy.requireSchool("LEDGER_CLOSE");
        validateDates(in.startDate(), in.endDate());
        UUID schoolId = TenantContext.get();
        validateAcademicSession(in.academicSessionId(), schoolId);
        List<PeriodView> generated = new ArrayList<>();
        LocalDate cursor = in.startDate().withDayOfMonth(1);
        String prefix = in.prefix() == null || in.prefix().isBlank() ? "" : in.prefix().trim();
        while (!cursor.isAfter(in.endDate())) {
            LocalDate end = cursor.withDayOfMonth(cursor.lengthOfMonth());
            if (end.isAfter(in.endDate())) end = in.endDate();
            String code = prefix + cursor.toString().substring(0, 7);
            AccountingPeriod p = periods.findBySchoolIdOrderByStartDateDesc(schoolId).stream()
                    .filter(existing -> existing.getCode().equals(code)).findFirst().orElse(null);
            if (p == null) {
                if (overlapsOpen(schoolId, cursor, end, null)) throw overlapError();
                p = new AccountingPeriod();
                p.setSchoolId(schoolId);
                p.setCode(code);
                p.setNameFr("Période " + code);
                p.setNameEn("Period " + code);
                p.setStartDate(cursor);
                p.setEndDate(end);
                p.setAcademicSessionId(in.academicSessionId());
                p.setStatus("OPEN");
                p = periods.saveAndFlush(p);
                audit.record("ACCOUNTING_PERIOD_CREATED", "AccountingPeriod", p.getId().toString(), null, view(p), "Génération mensuelle");
            }
            generated.add(view(p));
            cursor = cursor.plusMonths(1);
        }
        return generated;
    }

    @Transactional(readOnly = true)
    public AccountingPeriod requireOpenForDate(LocalDate date) {
        if (date == null) throw ApiException.badRequest("La date comptable est obligatoire.");
        return periods.findFirstBySchoolIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        TenantContext.get(), "OPEN", date, date)
                .orElseThrow(() -> ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                        "POSTING_PERIOD_CLOSED", "La date choisie appartient à une période fermée ou non configurée.",
                        Map.of("entryDate", "Choisissez une date d'une période ouverte."), List.of()));
    }

    /**
     * Resolve a period for a finance operation without silently crossing an
     * academic-session boundary. Date-only lookup remains available for
     * manual journals, while student finance carries its session explicitly.
     */
    @Transactional(readOnly = true)
    public AccountingPeriod requireOpenForDate(LocalDate date, UUID academicSessionId) {
        if (academicSessionId == null) return requireOpenForDate(date);
        if (date == null) throw ApiException.badRequest("La date comptable est obligatoire.");
        UUID schoolId = TenantContext.get();
        return periods.findFirstBySchoolIdAndAcademicSessionIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        schoolId, academicSessionId, "OPEN", date, date)
                .orElseThrow(() -> {
                    AccountingPeriod covering = periods.findFirstBySchoolIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                            schoolId, "OPEN", date, date).orElse(null);
                    if (covering != null && covering.getAcademicSessionId() != null
                            && !academicSessionId.equals(covering.getAcademicSessionId())) {
                        return ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                                "POSTING_PERIOD_SESSION_MISMATCH",
                                "La date est couverte par une période comptable " + covering.getCode()
                                        + ", rattachée à une autre session académique.",
                                Map.of("entryDate", "Associez une période ouverte à la session sélectionnée."), List.of());
                    }
                    return ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                            "POSTING_PERIOD_CLOSED", "Aucune période comptable ouverte de la session sélectionnée ne couvre cette date.",
                            Map.of("entryDate", "Choisissez une date couverte par une période ouverte de la session."), List.of());
                });
    }

    @Transactional(readOnly = true)
    public AccountingPeriod require(UUID id) {
        return periods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période comptable"));
    }

    @Transactional(readOnly = true)
    public ClosePreview closePreview(UUID id) {
        financePolicy.requireSchool("LEDGER_CLOSE");
        AccountingPeriod p = require(id);
        int drafts = jdbc.queryForObject("SELECT count(*) FROM journal_entry WHERE school_id=? AND accounting_period_id=? AND status='DRAFT'",
                Integer.class, TenantContext.get(), id);
        int unresolved = jdbc.queryForObject("SELECT count(*) FROM reconciliation_item WHERE school_id=? AND state NOT IN ('MATCHED','IGNORED')",
                Integer.class, TenantContext.get());
        List<BlockerView> blockers = new ArrayList<>();
        if (drafts > 0) blockers.add(new BlockerView("JOURNAL_PERIOD", id.toString(),
                drafts + " journal(s) brouillon(s) dans " + p.getCode(), "OPEN_JOURNALS"));
        if (unresolved > 0) blockers.add(new BlockerView("RECONCILIATION", null,
                unresolved + " élément(s) de rapprochement non résolu(s)", "OPEN_RECONCILIATION"));
        return new ClosePreview(id, p.getCode(), drafts, unresolved, blockers, blockers.isEmpty());
    }

    @Transactional
    public PeriodView close(UUID id, PeriodActionRequest request) {
        financePolicy.requireSchool("LEDGER_CLOSE");
        AccountingPeriod p = require(id);
        AccountService.requireVersion(request.version(), p.getVersion(), "période comptable");
        if (!"OPEN".equals(p.getStatus())) throw ApiException.conflict("Cette période est déjà fermée.");
        ClosePreview preview = closePreview(id);
        if (!preview.ready()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "PERIOD_CLOSE_BLOCKED", "La période ne peut pas être fermée tant que les blocages ne sont pas résolus.",
                    Map.of(), preview.blockers().stream().map(b -> new ApiException.Blocker(
                            b.entityType(), b.entityId(), b.label(), b.action())).toList());
        }
        PeriodView before = view(p);
        p.setStatus("CLOSED");
        p.setClosedAt(Instant.now());
        p.setClosedBy(currentUserId());
        p.setCloseReason(request.reason().trim());
        p = periods.saveAndFlush(p);
        PeriodView result = view(p);
        audit.record("ACCOUNTING_PERIOD_CLOSED", "AccountingPeriod", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional
    public PeriodView reopen(UUID id, PeriodActionRequest request) {
        financePolicy.requireSchool("LEDGER_REOPEN");
        AccountingPeriod p = require(id);
        AccountService.requireVersion(request.version(), p.getVersion(), "période comptable");
        if (!"CLOSED".equals(p.getStatus())) throw ApiException.conflict("Seule une période fermée peut être rouverte.");
        PeriodView before = view(p);
        if (overlapsOpen(TenantContext.get(), p.getStartDate(), p.getEndDate(), id)) throw overlapError();
        p.setStatus("OPEN");
        p.setReopenedAt(Instant.now());
        p.setReopenedBy(currentUserId());
        p.setReopenReason(request.reason().trim());
        p = periods.saveAndFlush(p);
        PeriodView result = view(p);
        audit.record("ACCOUNTING_PERIOD_REOPENED", "AccountingPeriod", id.toString(), before, result, request.reason());
        return result;
    }

    private boolean overlapsOpen(UUID schoolId, LocalDate start, LocalDate end, UUID excludeId) {
        return periods.findBySchoolIdOrderByStartDateDesc(schoolId).stream()
                .filter(p -> "OPEN".equals(p.getStatus()))
                .filter(p -> excludeId == null || !p.getId().equals(excludeId))
                .anyMatch(p -> !start.isAfter(p.getEndDate()) && !end.isBefore(p.getStartDate()));
    }

    private void apply(AccountingPeriod p, PeriodUpsert in, String status) {
        p.setCode(in.code().trim());
        p.setNameFr(in.nameFr().trim());
        p.setNameEn(in.nameEn().trim());
        p.setStartDate(in.startDate());
        p.setEndDate(in.endDate());
        p.setAcademicSessionId(in.academicSessionId());
        p.setStatus(status);
    }

    private PeriodView view(AccountingPeriod p) {
        return new PeriodView(p.getId(), p.getCode(), p.getNameFr(), p.getNameEn(), p.getStartDate(), p.getEndDate(),
                p.getAcademicSessionId(), p.getStatus(), toOffset(p.getClosedAt()), p.getClosedBy(), p.getCloseReason(),
                toOffset(p.getReopenedAt()), p.getReopenedBy(), p.getReopenReason(), p.getVersion());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_PERIOD_DATES", "La période doit avoir une date de début antérieure ou égale à la date de fin.",
                    Map.of("startDate", "Date de début obligatoire.", "endDate", "Date de fin invalide."), List.of());
        }
    }

    private void validateAcademicSession(UUID sessionId, UUID schoolId) {
        if (sessionId == null) return;
        Boolean belongsToSchool = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM academic_session WHERE id=? AND school_id=?)",
                Boolean.class, sessionId, schoolId);
        if (!Boolean.TRUE.equals(belongsToSchool)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ACADEMIC_SESSION", "La session académique n'appartient pas à cet établissement.",
                    Map.of("academicSessionId", "Choisissez une session du même établissement."), List.of());
        }
    }

    private static String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("OPEN") && !value.equals("CLOSED")) throw ApiException.badRequest("Statut de période invalide.");
        return value;
    }

    private static ApiException overlapError() {
        return ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                "OPEN_PERIOD_OVERLAP", "Les périodes ouvertes ne peuvent pas se chevaucher.", Map.of(), List.of());
    }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }
}
