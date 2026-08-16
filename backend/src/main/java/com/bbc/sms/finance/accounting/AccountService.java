package com.bbc.sms.finance.accounting;

import com.bbc.sms.finance.FinancePolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class AccountService {
    private static final Set<String> TYPES = Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");
    private static final Set<String> SIDES = Set.of("DEBIT", "CREDIT");
    private final ChartOfAccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final FinancePolicyService financePolicy;

    public AccountService(ChartOfAccountRepository accounts, JdbcTemplate jdbc, AuditService audit,
                          FinancePolicyService financePolicy) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.audit = audit;
        this.financePolicy = financePolicy;
    }

    @Transactional(readOnly = true)
    public List<AccountView> list(String query, boolean activeOnly) {
        financePolicy.requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return (activeOnly ? accounts.findBySchoolIdAndActiveTrueOrderByCodeAsc(schoolId)
                           : accounts.findBySchoolIdOrderByCodeAsc(schoolId)).stream()
                .filter(a -> needle.isBlank()
                        || a.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || a.getNameFr().toLowerCase(Locale.ROOT).contains(needle)
                        || a.getNameEn().toLowerCase(Locale.ROOT).contains(needle))
                .map(this::view)
                .toList();
    }

    @Transactional
    public AccountView create(AccountUpsert in) {
        financePolicy.requireSchool("ACCOUNT_MANAGE");
        UUID schoolId = TenantContext.get();
        String code = normalizeCode(in.code());
        validateIdentity(code, in.accountType(), in.normalSide(), in.currency(), in.effectiveFrom(), in.effectiveTo());
        if (accounts.existsBySchoolIdAndCode(schoolId, code)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "ACCOUNT_CODE_EXISTS", "Ce code de compte existe déjà dans cet établissement.",
                    Map.of("code", "Choisissez un code unique."), List.of());
        }
        ChartOfAccount a = new ChartOfAccount();
        a.setSchoolId(schoolId);
        apply(a, in, code);
        validateParent(a.getParentId(), schoolId, a.getId());
        a = accounts.saveAndFlush(a);
        AccountView result = view(a);
        audit.record("ACCOUNT_CREATED", "ChartOfAccount", a.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public AccountView update(UUID id, AccountUpsert in) {
        financePolicy.requireSchool("ACCOUNT_MANAGE");
        UUID schoolId = TenantContext.get();
        ChartOfAccount a = require(id);
        requireVersion(in.version(), a.getVersion(), "compte");
        String code = normalizeCode(in.code());
        validateIdentity(code, in.accountType(), in.normalSide(), in.currency(), in.effectiveFrom(), in.effectiveTo());
        long postedUsage = postedUsage(id);
        if (postedUsage > 0 && (!code.equals(a.getCode())
                || !in.accountType().trim().toUpperCase(Locale.ROOT).equals(a.getAccountType())
                || !in.normalSide().trim().toUpperCase(Locale.ROOT).equals(a.getNormalSide()))) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "ACCOUNT_USED_IMMUTABLE", "Le code et le type ne peuvent plus changer après utilisation dans un journal posté.",
                    Map.of("code", "Créez un nouveau compte pour une nouvelle nature comptable."),
                    List.of(new ApiException.Blocker("ACCOUNT", id.toString(), a.getCode(), "OPEN_ACCOUNT")));
        }
        apply(a, in, code);
        validateParent(a.getParentId(), schoolId, a.getId());
        AccountView before = view(a);
        a = accounts.saveAndFlush(a);
        AccountView result = view(a);
        audit.record("ACCOUNT_UPDATED", "ChartOfAccount", a.getId().toString(), before, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public ChartOfAccount requirePostingAccount(UUID id) {
        ChartOfAccount a = require(id);
        if (!a.isActive() || !a.isPostingAllowed()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "ACCOUNT_NOT_POSTABLE", "Le compte sélectionné n'est pas un compte de mouvement actif.",
                    Map.of("targetAccountId", "Choisissez un compte actif autorisé à recevoir des écritures."), List.of());
        }
        return a;
    }

    @Transactional(readOnly = true)
    public ChartOfAccount require(UUID id) {
        return accounts.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Compte comptable"));
    }

    @Transactional(readOnly = true)
    public long postedUsage(UUID id) {
        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM journal_line l
                  JOIN journal_entry j ON j.id=l.journal_entry_id AND j.school_id=l.school_id
                 WHERE l.school_id=? AND l.account_id=? AND j.status IN ('POSTED','REVERSED')
                """, Long.class, TenantContext.get(), id);
    }

    private AccountView view(ChartOfAccount a) {
        return new AccountView(a.getId(), a.getCode(), a.getNameFr(), a.getNameEn(), a.getAccountType(),
                a.getNormalSide(), a.getCurrency(), a.getParentId(), a.isPostingAllowed(), a.isActive(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getVersion(), postedUsage(a.getId()));
    }

    private void apply(ChartOfAccount a, AccountUpsert in, String code) {
        a.setCode(code);
        a.setNameFr(in.nameFr().trim());
        a.setNameEn(in.nameEn().trim());
        a.setAccountType(in.accountType().trim().toUpperCase(Locale.ROOT));
        a.setNormalSide(in.normalSide().trim().toUpperCase(Locale.ROOT));
        a.setCurrency(normalizeCurrency(in.currency()));
        a.setParentId(in.parentId());
        a.setPostingAllowed(in.postingAllowed() == null || in.postingAllowed());
        a.setActive(in.active() == null || in.active());
        a.setEffectiveFrom(in.effectiveFrom());
        a.setEffectiveTo(in.effectiveTo());
    }

    private void validateIdentity(String code, String type, String side, String currency,
                                  LocalDate from, LocalDate to) {
        if (!code.matches("[A-Z0-9][A-Z0-9_.-]{0,31}")) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_CODE", "Le code doit contenir 1 à 32 lettres majuscules, chiffres, points, tirets ou underscores.",
                    Map.of("code", "Utilisez par exemple 1100 ou AR_STUDENTS."), List.of());
        }
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String normalizedSide = side == null ? "" : side.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalizedType)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_TYPE", "Type de compte invalide.", Map.of("accountType", "Choisissez un type de compte."), List.of());
        }
        if (!SIDES.contains(normalizedSide)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_NORMAL_SIDE", "Sens normal invalide.", Map.of("normalSide", "Choisissez débit ou crédit."), List.of());
        }
        if (currency != null && !currency.isBlank() && !currency.trim().matches("[A-Za-z]{3}")) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_CURRENCY", "La devise doit être un code ISO à trois lettres.", Map.of("currency", "Exemple : XAF."), List.of());
        }
        if (to != null && from != null && to.isBefore(from)) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_EFFECTIVE_DATES", "La date de fin ne peut pas précéder la date de début.",
                    Map.of("effectiveTo", "Choisissez une date postérieure ou égale."), List.of());
        }
    }

    private void validateParent(UUID parentId, UUID schoolId, UUID selfId) {
        if (parentId == null) return;
        if (parentId.equals(selfId) || accounts.findByIdAndSchoolId(parentId, schoolId).isEmpty()) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_PARENT", "Le compte parent doit appartenir au même établissement et être différent du compte.",
                    Map.of("parentId", "Choisissez un compte parent valide."), List.of());
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "XAF" : currency.trim().toUpperCase(Locale.ROOT);
    }

    static void requireVersion(Long supplied, long current, String label) {
        if (supplied == null || supplied != current) {
            throw ApiException.conflict("Le " + label + " a changé ailleurs. Rechargez-le avant de réessayer.");
        }
    }
}
