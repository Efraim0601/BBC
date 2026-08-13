package com.bbc.sms.finance.accounting;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.finance.accounting.AccountingDtos.*;

@Service
public class LedgerPostingService {
    private final JournalEntryRepository journals;
    private final JournalLineRepository lines;
    private final ChartOfAccountRepository accounts;
    private final AccountingPeriodService periods;
    private final JournalValidationService validation;
    private final DocumentSequenceService sequences;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public LedgerPostingService(JournalEntryRepository journals, JournalLineRepository lines,
                                ChartOfAccountRepository accounts, AccountingPeriodService periods,
                                JournalValidationService validation, DocumentSequenceService sequences,
                                IdempotencyService idempotency, AuditService audit, JdbcTemplate jdbc) {
        this.journals = journals;
        this.lines = lines;
        this.accounts = accounts;
        this.periods = periods;
        this.validation = validation;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public JournalView createDraft(JournalUpsert in) {
        validation.validateDraftLines(in.lines());
        AccountingPeriod period = periods.require(in.accountingPeriodId());
        if (!"OPEN".equals(period.getStatus())) {
            throw ApiException.conflict("Les journaux d'une période fermée ne peuvent plus être créés ou modifiés.");
        }
        String sourceKey = trim(in.sourceEventKey());
        rejectDuplicateSourceKey(sourceKey, null);
        JournalEntry entry = new JournalEntry();
        entry.setSchoolId(TenantContext.get());
        entry.setNumber(sequences.allocateJournalNumber(period.getCode()));
        entry.setEntryDate(in.entryDate());
        entry.setDescription(in.description().trim());
        entry.setCurrency(normalizeCurrency(in.currency()));
        entry.setAccountingPeriodId(period.getId());
        entry.setSourceType(trim(in.sourceType()));
        entry.setSourceId(trim(in.sourceId()));
        entry.setSourceEventKey(sourceKey);
        entry = journals.saveAndFlush(entry);
        saveLines(entry, in.lines());
        JournalView result = view(entry);
        audit.record("JOURNAL_DRAFT_CREATED", "JournalEntry", entry.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public JournalView updateDraft(UUID id, JournalUpsert in) {
        validation.validateDraftLines(in.lines());
        JournalEntry entry = journals.findForUpdate(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Journal"));
        AccountService.requireVersion(in.version(), entry.getVersion(), "journal");
        if (!"DRAFT".equals(entry.getStatus())) {
            throw ApiException.conflict("Un journal posté ou renversé est immuable. Créez une écriture de correction.");
        }
        AccountingPeriod period = periods.require(in.accountingPeriodId());
        if (!"OPEN".equals(period.getStatus())) throw ApiException.conflict("La période comptable est fermée.");
        String sourceKey = trim(in.sourceEventKey());
        rejectDuplicateSourceKey(sourceKey, id);
        JournalView before = view(entry);
        entry.setEntryDate(in.entryDate());
        entry.setDescription(in.description().trim());
        entry.setCurrency(normalizeCurrency(in.currency()));
        entry.setAccountingPeriodId(period.getId());
        entry.setSourceType(trim(in.sourceType()));
        entry.setSourceId(trim(in.sourceId()));
        entry.setSourceEventKey(sourceKey);
        entry = journals.saveAndFlush(entry);
        lines.deleteBySchoolIdAndJournalEntryId(TenantContext.get(), id);
        lines.flush();
        saveLines(entry, in.lines());
        JournalView result = view(entry);
        audit.record("JOURNAL_DRAFT_UPDATED", "JournalEntry", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public JournalView post(UUID id, String idempotencyKey) {
        return idempotency.execute("finance-v2/journals/post", idempotencyKey,
                new CommandKey(id), JournalView.class, () -> postNow(id));
    }

    @Transactional
    public JournalView postNow(UUID id) {
        JournalEntry entry = journals.findForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Journal"));
        if ("POSTED".equals(entry.getStatus())) return view(entry);
        if ("REVERSED".equals(entry.getStatus())) throw ApiException.conflict("Ce journal a déjà été renversé.");
        if (entry.getSourceEventKey() != null) {
            JournalEntry existing = journals.findBySchoolIdAndSourceEventKey(TenantContext.get(), entry.getSourceEventKey()).orElse(null);
            if (existing != null && !existing.getId().equals(id)) {
                if ("POSTED".equals(existing.getStatus())) return view(existing);
                throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                        "SOURCE_EVENT_DUPLICATE", "La clé d'événement source est déjà utilisée par un journal brouillon.",
                        Map.of("sourceEventKey", "Utilisez une clé source unique."), List.of(
                                new ApiException.Blocker("JOURNAL", existing.getId().toString(), existing.getNumber(), "REUSE_EXISTING_JOURNAL")));
            }
        } else {
            entry.setSourceEventKey("MANUAL:" + id);
        }
        List<JournalLine> journalLines = lines.findBySchoolIdAndJournalEntryIdOrderByLineNumberAsc(
                TenantContext.get(), id);
        JournalValidationService.Totals totals = validation.validateForPost(entry, journalLines);
        entry.setCurrency(totals.currency());
        entry.setStatus("POSTED");
        entry.setPostedAt(Instant.now());
        entry.setPostedBy(currentUserId());
        entry = journals.saveAndFlush(entry);
        JournalView result = view(entry);
        audit.record("JOURNAL_POSTED", "JournalEntry", id.toString(), null, result, null);
        return result;
    }

    @Transactional
    public JournalView reverse(UUID id, ReverseRequest request, String idempotencyKey) {
        return idempotency.execute("finance-v2/journals/reverse", idempotencyKey,
                new ReverseCommand(id, request.entryDate(), request.reason(), request.version()),
                JournalView.class, () -> reverseNow(id, request));
    }

    @Transactional
    public JournalView reverseNow(UUID id, ReverseRequest request) {
        JournalEntry original = journals.findForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Journal"));
        AccountService.requireVersion(request.version(), original.getVersion(), "journal");
        if ("REVERSED".equals(original.getStatus())) {
            return journals.findBySchoolIdAndReversalOfId(TenantContext.get(), id).map(this::view)
                    .orElseThrow(() -> ApiException.conflict("Le journal est déjà renversé mais son écriture inverse est introuvable."));
        }
        if (!"POSTED".equals(original.getStatus())) throw ApiException.conflict("Seul un journal posté peut être renversé.");
        AccountingPeriod period = periods.requireOpenForDate(request.entryDate());
        String sourceKey = "REVERSAL:" + id;
        JournalEntry existing = journals.findBySchoolIdAndSourceEventKey(TenantContext.get(), sourceKey).orElse(null);
        if (existing != null) return view(existing);

        JournalEntry reversal = new JournalEntry();
        reversal.setSchoolId(TenantContext.get());
        reversal.setNumber(sequences.allocateJournalNumber(period.getCode()));
        reversal.setEntryDate(request.entryDate());
        reversal.setStatus("DRAFT");
        reversal.setSourceType("REVERSAL");
        reversal.setSourceId(id.toString());
        reversal.setSourceEventKey(sourceKey);
        reversal.setDescription("Renversement de " + original.getNumber() + " — " + request.reason().trim());
        reversal.setCurrency(original.getCurrency());
        reversal.setAccountingPeriodId(period.getId());
        reversal.setReversalOfId(original.getId());
        reversal = journals.saveAndFlush(reversal);
        List<JournalLine> originalLines = lines.findBySchoolIdAndJournalEntryIdOrderByLineNumberAsc(
                TenantContext.get(), original.getId());
        List<JournalLine> reversalLines = new ArrayList<>();
        for (JournalLine source : originalLines) {
            JournalLine line = new JournalLine();
            line.setSchoolId(TenantContext.get());
            line.setJournalEntryId(reversal.getId());
            line.setLineNumber(source.getLineNumber());
            line.setAccountId(source.getAccountId());
            line.setDebitMinor(source.getCreditMinor());
            line.setCreditMinor(source.getDebitMinor());
            line.setStudentId(source.getStudentId());
            line.setEnrollmentId(source.getEnrollmentId());
            line.setEmployeeId(source.getEmployeeId());
            line.setClassId(source.getClassId());
            line.setFeeTypeCode(source.getFeeTypeCode());
            line.setDescription("Renversement — " + (source.getDescription() == null ? "" : source.getDescription()));
            reversalLines.add(line);
        }
        lines.saveAllAndFlush(reversalLines);
        JournalValidationService.Totals totals = validation.validateForPost(reversal, reversalLines);
        reversal.setCurrency(totals.currency());
        reversal.setStatus("POSTED");
        reversal.setPostedAt(Instant.now());
        reversal.setPostedBy(currentUserId());
        reversal = journals.saveAndFlush(reversal);

        JournalView originalBefore = view(original);
        original.setStatus("REVERSED");
        original.setReversedBy(currentUserId());
        original = journals.saveAndFlush(original);
        audit.record("JOURNAL_REVERSED", "JournalEntry", original.getId().toString(), originalBefore, view(original), request.reason());
        JournalView result = view(reversal);
        audit.record("JOURNAL_REVERSAL_POSTED", "JournalEntry", reversal.getId().toString(), null, result, request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public PageView<JournalView> list(int page, int size, String status, LocalDate from, LocalDate to) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<JournalView> all = (from != null && to != null
                ? journals.findBySchoolIdAndEntryDateBetweenOrderByEntryDateAscNumberAsc(TenantContext.get(), from, to)
                : journals.findBySchoolIdOrderByEntryDateDescNumberDesc(TenantContext.get())).stream()
                .filter(j -> status == null || status.isBlank() || j.getStatus().equalsIgnoreCase(status.trim()))
                .map(this::view).toList();
        int safePage = Math.max(0, page);
        int start = Math.min(safePage * safeSize, all.size());
        int end = Math.min(start + safeSize, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / safeSize);
        return new PageView<>(all.subList(start, end), safePage, safeSize, all.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public JournalView detail(UUID id) {
        return view(journals.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Journal")));
    }

    @Transactional(readOnly = true)
    public TrialBalanceView trialBalance(LocalDate asOfDate, boolean includeZero) {
        LocalDate date = asOfDate == null ? LocalDate.now() : asOfDate;
        List<TrialBalanceRow> rows = jdbc.query("""
                SELECT a.id, a.code, a.name_fr, a.account_type, COALESCE(a.currency,'XAF'),
                       COALESCE(SUM(CASE WHEN j.status='POSTED' THEN l.debit_minor ELSE 0 END),0),
                       COALESCE(SUM(CASE WHEN j.status='POSTED' THEN l.credit_minor ELSE 0 END),0)
                  FROM chart_of_account a
                  LEFT JOIN journal_line l ON l.school_id=a.school_id AND l.account_id=a.id
                  LEFT JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                         AND j.entry_date <= ? AND j.status='POSTED'
                 WHERE a.school_id=?
                 GROUP BY a.id, a.code, a.name_fr, a.account_type, a.currency
                 ORDER BY a.code
                """, (rs, n) -> {
                    long debit = rs.getLong(6), credit = rs.getLong(7);
                    return new TrialBalanceRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), debit, credit, debit - credit);
                }, date, TenantContext.get()).stream()
                .filter(r -> includeZero || r.debitMinor() != 0 || r.creditMinor() != 0)
                .toList();
        long debit = rows.stream().mapToLong(TrialBalanceRow::debitMinor).sum();
        long credit = rows.stream().mapToLong(TrialBalanceRow::creditMinor).sum();
        return new TrialBalanceView(date, "XAF", rows, debit, credit, debit == credit);
    }

    @Transactional(readOnly = true)
    public GeneralLedgerView generalLedger(UUID accountId, LocalDate from, LocalDate to) {
        ChartOfAccount account = accounts.findByIdAndSchoolId(accountId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Compte comptable"));
        LocalDate start = from == null ? LocalDate.of(1900, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) throw ApiException.badRequest("La date de fin doit être postérieure à la date de début.");
        List<GeneralLedgerLine> result = jdbc.query("""
                SELECT j.id, j.number, j.entry_date, j.status, j.source_type, j.description,
                       l.debit_minor, l.credit_minor
                  FROM journal_line l
                  JOIN journal_entry j ON j.school_id=l.school_id AND j.id=l.journal_entry_id
                 WHERE l.school_id=? AND l.account_id=? AND j.status='POSTED'
                   AND j.entry_date BETWEEN ? AND ?
                 ORDER BY j.entry_date, j.number, l.line_number
                """, (rs, n) -> new RawLedgerLine(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getLong(7), rs.getLong(8)), TenantContext.get(), accountId, start, end)
                .stream().map(new java.util.function.Function<RawLedgerLine, GeneralLedgerLine>() {
                    long running;
                    @Override public GeneralLedgerLine apply(RawLedgerLine row) {
                        running += row.debitMinor() - row.creditMinor();
                        return new GeneralLedgerLine(row.journalId(), row.journalNumber(), row.entryDate(), row.status(),
                                row.sourceType(), row.description(), row.debitMinor(), row.creditMinor(), running);
                    }
                }).toList();
        return new GeneralLedgerView(account.getId(), account.getCode(), account.getNameFr(), start, end, result,
                result.stream().mapToLong(GeneralLedgerLine::debitMinor).sum(),
                result.stream().mapToLong(GeneralLedgerLine::creditMinor).sum());
    }

    private void saveLines(JournalEntry entry, List<JournalLineInput> input) {
        List<JournalLine> entities = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            JournalLineInput source = input.get(i);
            accounts.findByIdAndSchoolId(source.accountId(), TenantContext.get())
                    .orElseThrow(() -> ApiException.notFound("Compte comptable"));
            JournalLine line = new JournalLine();
            line.setSchoolId(TenantContext.get());
            line.setJournalEntryId(entry.getId());
            line.setLineNumber(i + 1);
            line.setAccountId(source.accountId());
            line.setDebitMinor(source.debitMinor());
            line.setCreditMinor(source.creditMinor());
            line.setStudentId(source.studentId());
            line.setEnrollmentId(source.enrollmentId());
            line.setEmployeeId(source.employeeId());
            line.setClassId(source.classId());
            line.setFeeTypeCode(trim(source.feeTypeCode()));
            line.setDescription(trim(source.description()));
            entities.add(line);
        }
        lines.saveAllAndFlush(entities);
    }

    private JournalView view(JournalEntry entry) {
        List<JournalLineView> lineViews = lines.findBySchoolIdAndJournalEntryIdOrderByLineNumberAsc(
                        TenantContext.get(), entry.getId()).stream().map(line -> {
                    ChartOfAccount account = accounts.findByIdAndSchoolId(line.getAccountId(), TenantContext.get()).orElse(null);
                    return new JournalLineView(line.getId(), line.getLineNumber(), line.getAccountId(),
                            account == null ? "?" : account.getCode(), account == null ? "?" : account.getNameFr(),
                            line.getDebitMinor(), line.getCreditMinor(), line.getStudentId(), line.getEnrollmentId(),
                            line.getEmployeeId(), line.getClassId(), line.getFeeTypeCode(), line.getDescription(), line.getVersion());
                }).toList();
        long debit = lineViews.stream().mapToLong(JournalLineView::debitMinor).sum();
        long credit = lineViews.stream().mapToLong(JournalLineView::creditMinor).sum();
        return new JournalView(entry.getId(), entry.getNumber(), entry.getEntryDate(), entry.getStatus(),
                entry.getSourceType(), entry.getSourceId(), entry.getSourceEventKey(), entry.getDescription(),
                entry.getCurrency(), entry.getAccountingPeriodId(), entry.getReversalOfId(), entry.getReversedBy(),
                entry.getPostedAt() == null ? null : entry.getPostedAt().atOffset(ZoneOffset.UTC), entry.getPostedBy(),
                entry.getVersion(), debit, credit, lineViews);
    }

    private void rejectDuplicateSourceKey(String key, UUID selfId) {
        if (key == null) return;
        JournalEntry existing = journals.findBySchoolIdAndSourceEventKey(TenantContext.get(), key).orElse(null);
        if (existing != null && (selfId == null || !existing.getId().equals(selfId))) {
            throw ApiException.structured(org.springframework.http.HttpStatus.CONFLICT,
                    "SOURCE_EVENT_DUPLICATE", "Cette clé d'événement source est déjà liée à un journal.",
                    Map.of("sourceEventKey", "Utilisez une clé source unique."), List.of(
                            new ApiException.Blocker("JOURNAL", existing.getId().toString(), existing.getNumber(), "REUSE_EXISTING_JOURNAL")));
        }
    }

    private static String normalizeCurrency(String value) {
        String result = value == null || value.isBlank() ? "XAF" : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) throw ApiException.badRequest("La devise du journal doit être un code ISO à trois lettres.");
        return result;
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }

    private record CommandKey(UUID journalId) {}
    private record ReverseCommand(UUID journalId, LocalDate entryDate, String reason, long version) {}
    private record RawLedgerLine(UUID journalId, String journalNumber, LocalDate entryDate, String status,
                                 String sourceType, String description, long debitMinor, long creditMinor) {}
}
