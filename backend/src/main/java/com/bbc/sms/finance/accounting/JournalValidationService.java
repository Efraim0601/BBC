package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class JournalValidationService {
    private final AccountService accounts;
    private final AccountingPeriodService periods;
    private final JdbcTemplate jdbc;

    public JournalValidationService(AccountService accounts, AccountingPeriodService periods, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.periods = periods;
        this.jdbc = jdbc;
    }

    public void validateDraftLines(List<JournalLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw journalError("JOURNAL_LINES_REQUIRED", "Ajoutez au moins deux lignes comptables.", Map.of("lines", "Les lignes sont obligatoires."));
        }
        Map<String, String> errors = new HashMap<>();
        Set<Integer> numbers = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            JournalLineInput line = lines.get(i);
            String field = "lines[" + i + "]";
            if (line == null || line.accountId() == null) errors.put(field + ".accountId", "Choisissez un compte.");
            if (line == null || line.debitMinor() < 0 || line.creditMinor() < 0) errors.put(field, "Les montants ne peuvent pas être négatifs.");
            if (line != null && ((line.debitMinor() > 0) == (line.creditMinor() > 0))) {
                errors.put(field, "Une ligne doit contenir un débit ou un crédit, mais jamais les deux.");
            }
            if (!numbers.add(i + 1)) errors.put(field, "Numéro de ligne dupliqué.");
        }
        if (!errors.isEmpty()) throw journalError("JOURNAL_LINE_INVALID", "Corrigez les lignes comptables signalées.", errors);
    }

    @Transactional(readOnly = true)
    public Totals validateForPost(JournalEntry entry, List<JournalLine> lines) {
        String currency = normalizeCurrency(entry.getCurrency());
        if (!currency.matches("[A-Z]{3}")) {
            throw journalError("INVALID_CURRENCY", "La devise du journal doit être un code ISO à trois lettres.",
                    Map.of("currency", "Exemple : XAF."));
        }
        if (entry.getDescription() == null || entry.getDescription().isBlank()) {
            throw journalError("JOURNAL_DESCRIPTION_REQUIRED", "Le libellé du journal est obligatoire.",
                    Map.of("description", "Saisissez un libellé."));
        }
        AccountingPeriod period = periods.require(entry.getAccountingPeriodId());
        if (!entry.getEntryDate().isBefore(period.getStartDate()) && !entry.getEntryDate().isAfter(period.getEndDate())) {
            if (!"OPEN".equals(period.getStatus())) {
                throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                        "POSTING_PERIOD_CLOSED", "La période comptable est fermée : cette écriture ne peut pas être postée.",
                        Map.of("accountingPeriodId", "Choisissez une période ouverte."), List.of(
                                new ApiException.Blocker("ACCOUNTING_PERIOD", period.getId().toString(), period.getCode(), "OPEN_PERIOD")));
            }
        } else {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "JOURNAL_DATE_OUTSIDE_PERIOD", "La date du journal doit être comprise dans sa période comptable.",
                    Map.of("entryDate", "Choisissez une date entre " + period.getStartDate() + " et " + period.getEndDate() + "."), List.of());
        }
        if (lines == null || lines.size() < 2) {
            throw journalError("JOURNAL_NEEDS_TWO_LINES", "Un journal posté doit contenir au moins deux lignes.",
                    Map.of("lines", "Ajoutez au moins une ligne débit et une ligne crédit."));
        }
        Map<String, String> errors = new HashMap<>();
        Set<Integer> lineNumbers = new HashSet<>();
        long debit = 0;
        long credit = 0;
        for (JournalLine line : lines) {
            String field = "lines[" + Math.max(0, line.getLineNumber() - 1) + "]";
            if (!lineNumbers.add(line.getLineNumber())) errors.put(field, "Numéro de ligne dupliqué.");
            if (line.getDebitMinor() < 0 || line.getCreditMinor() < 0) errors.put(field, "Les montants ne peuvent pas être négatifs.");
            if ((line.getDebitMinor() > 0) == (line.getCreditMinor() > 0)) {
                errors.put(field, "Une ligne doit contenir exactement un débit ou un crédit positif.");
            }
            ChartOfAccount account = accounts.require(line.getAccountId());
            if (!account.isActive() || !account.isPostingAllowed()) {
                errors.put(field + ".accountId", "Le compte n'est pas actif ou autorisé à recevoir des écritures.");
            }
            if (account.getCurrency() != null && !account.getCurrency().equals(currency)) {
                errors.put(field + ".accountId", "La devise du compte est incompatible avec celle du journal.");
            }
            validateDimension(line.getStudentId(), "student", field, errors);
            validateDimension(line.getEnrollmentId(), "student_enrollment", field, errors);
            validateDimension(line.getEmployeeId(), "employee", field, errors);
            validateDimension(line.getClassId(), "school_class", field, errors);
            try {
                debit = Math.addExact(debit, line.getDebitMinor());
                credit = Math.addExact(credit, line.getCreditMinor());
            } catch (ArithmeticException ex) {
                throw journalError("JOURNAL_AMOUNT_OVERFLOW", "Le total du journal dépasse la capacité autorisée.", Map.of());
            }
        }
        if (!errors.isEmpty()) throw journalError("JOURNAL_LINE_INVALID", "Corrigez les lignes comptables signalées.", errors);
        if (debit != credit) {
            throw ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "JOURNAL_NOT_BALANCED", "Le journal est déséquilibré : le total débit doit être égal au total crédit.",
                    Map.of("debitMinor", String.valueOf(debit), "creditMinor", String.valueOf(credit)), List.of());
        }
        return new Totals(debit, credit, currency);
    }

    private void validateDimension(UUID id, String table, String field, Map<String, String> errors) {
        if (id == null) return;
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id=? AND school_id=?)",
                Boolean.class, id, TenantContext.get());
        if (!Boolean.TRUE.equals(exists)) errors.put(field + ".dimension", "La dimension analytique n'appartient pas à cet établissement.");
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "XAF" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static ApiException journalError(String code, String message, Map<String, String> fields) {
        return ApiException.structured(org.springframework.http.HttpStatus.BAD_REQUEST, code, message, fields, List.of());
    }

    public record Totals(long debitMinor, long creditMinor, String currency) {}
}
