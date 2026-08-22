package com.bbc.sms.finance.treasury;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.treasury.TreasuryDtos.*;

/** Operational treasury workflow backed by immutable accounting journals. */
@Service
public class TreasuryService {
    private static final Set<String> KINDS = Set.of("CASH", "BANK", "MOBILE_WALLET", "OTHER");
    private static final Set<String> MOVEMENT_TYPES = Set.of("OPENING", "DEPOSIT", "WITHDRAWAL", "TRANSFER", "ADJUSTMENT");

    private final JdbcTemplate jdbc;
    private final ChartOfAccountRepository accounts;
    private final AccountingPeriodService periods;
    private final LedgerPostingService ledger;
    private final FinancePolicyService financePolicy;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public TreasuryService(JdbcTemplate jdbc, ChartOfAccountRepository accounts,
                           AccountingPeriodService periods, LedgerPostingService ledger,
                           FinancePolicyService financePolicy, IdempotencyService idempotency,
                           AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.periods = periods;
        this.ledger = ledger;
        this.financePolicy = financePolicy;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TreasuryAccountView> listAccounts() {
        financePolicy.requireSchool("TREASURY_ACCOUNT_VIEW");
        return listAccountsInternal();
    }

    @Transactional(readOnly = true)
    public List<TreasuryMovementView> listMovements(int requestedLimit) {
        financePolicy.requireSchool("TREASURY_MOVEMENT_VIEW");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbc.query("""
                SELECT m.id,m.movement_no,m.movement_type,m.entry_date,
                       m.from_account_id,fa.display_name,
                       m.to_account_id,ta.display_name,
                       m.offset_account_id,oa.code,m.amount_minor,m.currency,
                       m.reason,m.reference,m.status,m.journal_entry_id,j.number,
                       m.created_by,m.created_at,m.version
                  FROM treasury_movement m
                  LEFT JOIN treasury_account fa ON fa.school_id=m.school_id AND fa.id=m.from_account_id
                  LEFT JOIN treasury_account ta ON ta.school_id=m.school_id AND ta.id=m.to_account_id
                  LEFT JOIN chart_of_account oa ON oa.school_id=m.school_id AND oa.id=m.offset_account_id
                  LEFT JOIN journal_entry j ON j.school_id=m.school_id AND j.id=m.journal_entry_id
                 WHERE m.school_id=?
                 ORDER BY m.entry_date DESC,m.created_at DESC,m.movement_no DESC
                 LIMIT ?
                """, (rs, n) -> movementView(rs), TenantContext.get(), limit);
    }

    /** Read-only account projection for an already-authorized finance workflow. */
    @Transactional(readOnly = true)
    public List<TreasuryAccountView> listAccountsForWorkflow() {
        return listAccountsInternal();
    }

    /** Resolve legacy channel mappings without requiring the new UI permission. */
    @Transactional(readOnly = true)
    public TreasuryRecord findForChartAccountForWorkflow(UUID chartAccountId) {
        if (chartAccountId == null) return null;
        TreasuryRecord record = jdbc.query("""
                SELECT id,chart_account_id,kind,display_name,currency,active
                  FROM treasury_account
                 WHERE school_id=? AND chart_account_id=? AND active=true
                """, rs -> rs.next() ? new TreasuryRecord(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6)) : null,
                TenantContext.get(), chartAccountId);
        return record;
    }

    @Transactional(readOnly = true)
    public String displayNameForWorkflow(UUID id) {
        if (id == null) return null;
        return jdbc.query("SELECT display_name FROM treasury_account WHERE school_id=? AND id=?",
                rs -> rs.next() ? rs.getString(1) : null, TenantContext.get(), id);
    }

    @Transactional
    public TreasuryAccountView createAccount(TreasuryAccountCreate request) {
        financePolicy.requireSchool("TREASURY_ACCOUNT_MANAGE");
        validateKind(request.kind());
        String displayName = required(request.displayName(), "Le nom du compte est obligatoire.");
        String currency = normalizeCurrency(request.currency());
        UUID schoolId = TenantContext.get();
        String code = normalizeChartCode(request.chartAccountCode(), displayName);
        if (jdbc.queryForObject("SELECT count(*) FROM chart_of_account WHERE school_id=? AND code=?", Integer.class, schoolId, code) > 0) {
            throw ApiException.conflict("Ce code comptable existe déjà. Choisissez un autre code.");
        }
        ChartOfAccount chart = new ChartOfAccount();
        chart.setSchoolId(schoolId);
        chart.setCode(code);
        chart.setNameFr(displayName);
        chart.setNameEn(displayName);
        chart.setAccountType("ASSET");
        chart.setNormalSide("DEBIT");
        chart.setCurrency(currency);
        chart.setPostingAllowed(true);
        chart.setActive(true);
        chart = accounts.saveAndFlush(chart);

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO treasury_account
                    (id,school_id,chart_account_id,kind,display_name,institution_name,
                     account_number_last4,currency,active,is_default)
                VALUES (?,?,?,?,?,?,?,?,true,false)
                """, id, schoolId, chart.getId(), normalizeKind(request.kind()), displayName,
                trim(request.institutionName()), trim(request.accountNumberLast4()), currency);

        if (request.openingBalanceMinor() > 0) {
            UUID offsetId = chartAccountByCode("3000", request.openingBalanceDate()).getId();
            createMovementInternal(new TreasuryMovementRequest("OPENING", request.openingBalanceDate(),
                    null, id, offsetId, request.openingBalanceMinor(), currency,
                    "Solde initial — " + displayName, null));
        }
        TreasuryAccountView result = accountById(id);
        audit.record("TREASURY_ACCOUNT_CREATED", "TreasuryAccount", id.toString(), null, result, null);
        return result;
    }

    @Transactional
    public TreasuryAccountView archiveAccount(UUID id, ArchiveRequest request) {
        financePolicy.requireSchool("TREASURY_ACCOUNT_MANAGE");
        TreasuryAccountView existing = accountById(id);
        if (!existing.active()) throw ApiException.conflict("Ce compte de trésorerie est déjà archivé.");
        int updated = jdbc.update("""
                UPDATE treasury_account
                   SET active=false, is_default=false, archived_at=now(), archived_by=?, version=version+1, updated_at=now()
                 WHERE school_id=? AND id=? AND active=true AND version=?
                """, currentUserId(), TenantContext.get(), id, request.version());
        if (updated != 1) throw ApiException.conflict("Le compte a changé ailleurs. Rechargez-le avant de l'archiver.");
        TreasuryAccountView result = accountById(id);
        audit.record("TREASURY_ACCOUNT_ARCHIVED", "TreasuryAccount", id.toString(), existing, result, request.reason());
        return result;
    }

    @Transactional
    public TreasuryMovementView createMovement(TreasuryMovementRequest request, String idempotencyKey) {
        financePolicy.requireSchool("TREASURY_MOVEMENT_CREATE");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Une clé d'idempotence est obligatoire pour enregistrer un mouvement.");
        }
        return idempotency.execute("finance-v2/treasury/movements", idempotencyKey.trim(), request,
                TreasuryMovementView.class, () -> createMovementInternal(request));
    }

    /** Used by account creation after its own manage permission has been checked. */
    @Transactional
    public TreasuryMovementView createMovementInternal(TreasuryMovementRequest request) {
        String type = normalizeMovementType(request.movementType());
        if (request.amountMinor() <= 0) throw ApiException.badRequest("Le montant doit être supérieur à zéro.");
        String currency = normalizeCurrency(request.currency());
        LocalDate date = request.entryDate();
        AccountingPeriod period = periods.requireOpenForDate(date);
        UUID schoolId = TenantContext.get();
        TreasuryRecord from = request.fromAccountId() == null ? null : requireActiveRecord(request.fromAccountId());
        TreasuryRecord to = request.toAccountId() == null ? null : requireActiveRecord(request.toAccountId());
        ChartOfAccount offset = request.offsetAccountId() == null ? null : requirePostingAccount(request.offsetAccountId(), date);
        validateMovement(type, from, to, offset);
        UUID id = UUID.randomUUID();
        String movementNo = "TRS/" + date + "/" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO treasury_movement
                    (id,school_id,movement_no,movement_type,entry_date,from_account_id,to_account_id,
                     offset_account_id,amount_minor,currency,reason,reference,status,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'POSTED',?)
                """, id, schoolId, movementNo, type, date,
                from == null ? null : from.id(), to == null ? null : to.id(),
                offset == null ? null : offset.getId(), request.amountMinor(), currency,
                required(request.reason(), "Le motif est obligatoire."), trim(request.reference()), currentUserId());

        UUID debit = debitAccount(type, from, to, offset);
        UUID credit = creditAccount(type, from, to, offset);
        List<JournalLineInput> lines = List.of(
                new JournalLineInput(debit, request.amountMinor(), 0, null, null, null, null, null, movementNo),
                new JournalLineInput(credit, 0, request.amountMinor(), null, null, null, null, null, movementNo));
        var journal = ledger.createDraftInternal(new JournalUpsert(date,
                movementNo + " — " + request.reason().trim(), currency, period.getId(),
                "TREASURY_MOVEMENT", id.toString(), "TREASURY_MOVEMENT:" + id, lines, null));
        var posted = ledger.postNowInternal(journal.id());
        jdbc.update("UPDATE treasury_movement SET journal_entry_id=?, updated_at=now() WHERE school_id=? AND id=?",
                posted.id(), schoolId, id);
        TreasuryMovementView result = movementById(id);
        audit.record("TREASURY_MOVEMENT_POSTED", "TreasuryMovement", id.toString(), null, result, request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public TreasuryRecord requireActiveRecord(UUID id) {
        TreasuryRecord record = jdbc.query("""
                SELECT t.id,t.chart_account_id,t.kind,t.display_name,t.currency,t.active
                  FROM treasury_account t WHERE t.school_id=? AND t.id=?
                """, rs -> rs.next() ? new TreasuryRecord(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6)) : null, TenantContext.get(), id);
        if (record == null) throw ApiException.notFound("Compte de trésorerie");
        if (!record.active()) throw ApiException.badRequest("Le compte de trésorerie sélectionné est archivé.");
        return record;
    }

    private List<TreasuryAccountView> listAccountsInternal() {
        return jdbc.query(accountSql(), (rs, n) -> accountView(rs), TenantContext.get());
    }

    private TreasuryAccountView accountById(UUID id) {
        return jdbc.query(accountSql(), (rs, n) -> accountView(rs), TenantContext.get()).stream()
                .filter(account -> account.id().equals(id))
                .findFirst().orElseThrow(() -> ApiException.notFound("Compte de trésorerie"));
    }

    private TreasuryMovementView movementById(UUID id) {
        return jdbc.query("""
                SELECT m.id,m.movement_no,m.movement_type,m.entry_date,
                       m.from_account_id,fa.display_name,
                       m.to_account_id,ta.display_name,
                       m.offset_account_id,oa.code,m.amount_minor,m.currency,
                       m.reason,m.reference,m.status,m.journal_entry_id,j.number,
                       m.created_by,m.created_at,m.version
                  FROM treasury_movement m
                  LEFT JOIN treasury_account fa ON fa.school_id=m.school_id AND fa.id=m.from_account_id
                  LEFT JOIN treasury_account ta ON ta.school_id=m.school_id AND ta.id=m.to_account_id
                  LEFT JOIN chart_of_account oa ON oa.school_id=m.school_id AND oa.id=m.offset_account_id
                  LEFT JOIN journal_entry j ON j.school_id=m.school_id AND j.id=m.journal_entry_id
                 WHERE m.school_id=? AND m.id=?
                """, (rs, n) -> movementView(rs), TenantContext.get(), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Mouvement de trésorerie"));
    }

    private ChartOfAccount chartAccountByCode(String code, LocalDate date) {
        return accounts.findBySchoolIdAndCode(TenantContext.get(), code)
                .map(a -> requirePostingAccount(a.getId(), date)).orElseThrow(() -> ApiException.notFound("Compte comptable " + code));
    }

    private ChartOfAccount requirePostingAccount(UUID id, LocalDate date) {
        ChartOfAccount account = accounts.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Compte comptable"));
        if (!account.isActive() || !account.isPostingAllowed()) throw ApiException.badRequest("Le compte comptable sélectionné n'est pas actif ou mouvementable.");
        if (account.getEffectiveFrom() != null && date.isBefore(account.getEffectiveFrom())) throw ApiException.badRequest("Le compte comptable n'est pas encore effectif à cette date.");
        if (account.getEffectiveTo() != null && date.isAfter(account.getEffectiveTo())) throw ApiException.badRequest("Le compte comptable n'est plus effectif à cette date.");
        return account;
    }

    private static void validateMovement(String type, TreasuryRecord from, TreasuryRecord to, ChartOfAccount offset) {
        if ("TRANSFER".equals(type)) {
            if (from == null || to == null || from.id().equals(to.id())) throw ApiException.badRequest("Un transfert doit avoir deux comptes de trésorerie différents.");
            if (offset != null) throw ApiException.badRequest("Un transfert ne peut pas utiliser de compte de contrepartie.");
            return;
        }
        if (to == null && from == null) throw ApiException.badRequest("Sélectionnez le compte de trésorerie concerné.");
        if (to != null && from != null) throw ApiException.badRequest("Un dépôt ou retrait ne peut concerner qu'un seul compte de trésorerie.");
        if (offset == null) throw ApiException.badRequest("Sélectionnez le compte de contrepartie.");
    }

    private static UUID debitAccount(String type, TreasuryRecord from, TreasuryRecord to, ChartOfAccount offset) {
        return "WITHDRAWAL".equals(type) ? offset.getId() : (to != null ? to.chartAccountId() : from.chartAccountId());
    }

    private static UUID creditAccount(String type, TreasuryRecord from, TreasuryRecord to, ChartOfAccount offset) {
        return "WITHDRAWAL".equals(type) ? from.chartAccountId() : (to != null && !"TRANSFER".equals(type) ? offset.getId() : from.chartAccountId());
    }

    private static String accountSql() {
        return """
                SELECT t.id,t.chart_account_id,a.code,t.kind,t.display_name,t.institution_name,
                       t.account_number_last4,t.currency,t.active,t.is_default,t.version,
                       COALESCE(SUM(CASE WHEN j.status IN ('POSTED','REVERSED') THEN l.debit_minor-l.credit_minor ELSE 0 END),0)
                  FROM treasury_account t
                  JOIN chart_of_account a ON a.school_id=t.school_id AND a.id=t.chart_account_id
                  LEFT JOIN journal_line l ON l.school_id=t.school_id AND l.account_id=t.chart_account_id
                  LEFT JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                 WHERE t.school_id=?
                 GROUP BY t.id,t.chart_account_id,a.code,t.kind,t.display_name,t.institution_name,
                          t.account_number_last4,t.currency,t.active,t.is_default,t.version
                 ORDER BY t.active DESC,t.kind,t.display_name
                """;
    }

    private static TreasuryAccountView accountView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TreasuryAccountView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getBoolean(9), rs.getBoolean(10), rs.getLong(12), rs.getLong(11));
    }

    private static TreasuryMovementView movementView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TreasuryMovementView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getObject(5, UUID.class), rs.getString(6),
                rs.getObject(7, UUID.class), rs.getString(8), rs.getObject(9, UUID.class), rs.getString(10),
                rs.getLong(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15),
                rs.getObject(16, UUID.class), rs.getString(17), rs.getObject(18, UUID.class),
                rs.getObject(19, OffsetDateTime.class), rs.getLong(20));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        return value.trim();
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String normalizeKind(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private static void validateKind(String value) { if (value == null || !KINDS.contains(normalizeKind(value))) throw ApiException.badRequest("Le type de trésorerie est invalide."); }
    private static String normalizeMovementType(String value) { String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!MOVEMENT_TYPES.contains(result)) throw ApiException.badRequest("Le type de mouvement est invalide."); return result; }
    private static String normalizeCurrency(String value) { String result = value == null || value.isBlank() ? "XAF" : value.trim().toUpperCase(Locale.ROOT); if (!result.matches("[A-Z]{3}")) throw ApiException.badRequest("La devise doit être un code ISO à trois lettres."); return result; }

    private static String normalizeChartCode(String supplied, String displayName) {
        if (supplied != null && !supplied.isBlank()) {
            String code = supplied.trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9][A-Z0-9_.-]{0,31}")) throw ApiException.badRequest("Le code comptable est invalide.");
            return code;
        }
        return "TR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }

    public record TreasuryRecord(UUID id, UUID chartAccountId, String kind, String displayName, String currency, boolean active) {}
}
