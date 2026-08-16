package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

/** Plain-language setup checks for the accounting foundation workspace. */
@Service
public class FinanceReadinessService {
    private final JdbcTemplate jdbc;

    public FinanceReadinessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ReadinessView check() {
        var schoolId = TenantContext.get();
        LocalDate today = LocalDate.now();
        List<ReadinessCheck> checks = new ArrayList<>();

        Integer currentSession = jdbc.queryForObject("""
                SELECT count(*) FROM academic_session
                 WHERE school_id=? AND is_current=true AND status='OPEN'
                """, Integer.class, schoolId);
        checks.add(check("CURRENT_SESSION", "Session académique courante", currentSession != null && currentSession > 0,
                "Une session courante ouverte donne le contexte des opérations financières.", "OPEN_SESSION"));

        Integer openPeriod = jdbc.queryForObject("""
                SELECT count(*) FROM accounting_period
                 WHERE school_id=? AND status='OPEN' AND start_date<=? AND end_date>=?
                """, Integer.class, schoolId, today, today);
        checks.add(check("OPEN_PERIOD", "Période comptable ouverte", openPeriod != null && openPeriod > 0,
                "Les journaux ne peuvent être postés que dans une période ouverte à la date choisie.", "OPEN_PERIOD"));

        String[] requiredAccounts = {"1000", "1010", "1100", "2100", "3990"};
        List<BlockerView> accountBlockers = new ArrayList<>();
        for (String code : requiredAccounts) {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM chart_of_account
                     WHERE school_id=? AND code=? AND active=true AND posting_allowed=true
                    """, Integer.class, schoolId, code);
            if (count == null || count == 0) accountBlockers.add(new BlockerView("ACCOUNT", code,
                    "Le compte " + code + " est manquant ou inactif", "OPEN_ACCOUNTS"));
        }
        checks.add(new ReadinessCheck("CHART", "Catalogue de comptes", accountBlockers.isEmpty() ? "READY" : "BLOCKED",
                "Les comptes de base sont nécessaires pour les écritures et les rapprochements.",
                "OPEN_ACCOUNTS", accountBlockers));

        Integer mappingCount = jdbc.queryForObject("""
                SELECT count(*) FROM posting_rule
                 WHERE school_id=? AND enabled=true
                   AND event_type IN ('FEE_CHARGE','EXPENSE_POST','PAYMENT_CASH')
                """, Integer.class, schoolId);
        boolean mappingReady = mappingCount != null && mappingCount >= 6;
        checks.add(check("POSTING_MAPPINGS", "Règles de comptabilisation", mappingReady,
                "Chaque événement financier doit avoir un compte débit et un compte crédit.", "OPEN_POSTING_MAPPINGS"));

        Integer unresolved = jdbc.queryForObject("""
                SELECT count(*) FROM reconciliation_item
                 WHERE school_id=? AND state NOT IN ('MATCHED','IGNORED')
                """, Integer.class, schoolId);
        checks.add(check("RECONCILIATION", "File de rapprochement", unresolved == null || unresolved == 0,
                "Les éléments non résolus doivent être examinés avant la clôture.", "OPEN_RECONCILIATION"));

        boolean ready = checks.stream().allMatch(c -> "READY".equals(c.status()));
        return new ReadinessView(ready, checks, OffsetDateTime.now());
    }

    private static ReadinessCheck check(String key, String label, boolean ready, String detail, String action) {
        List<BlockerView> blockers = ready ? List.of() : List.of(new BlockerView(key, null, label, action));
        return new ReadinessCheck(key, label, detail, ready ? "READY" : "BLOCKED", action, blockers);
    }
}
