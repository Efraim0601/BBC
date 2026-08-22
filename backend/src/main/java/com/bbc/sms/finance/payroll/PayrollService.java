package com.bbc.sms.finance.payroll;

import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentDtos.RevokeRequest;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.finance.PaymentChannel;
import com.bbc.sms.finance.PaymentChannelRepository;
import com.bbc.sms.finance.treasury.TreasuryService;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalLineInput;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalView;
import com.bbc.sms.finance.accounting.AccountingDtos.JournalUpsert;
import com.bbc.sms.finance.accounting.AccountingDtos.ReverseRequest;
import com.bbc.sms.finance.accounting.AccountingPeriod;
import com.bbc.sms.finance.accounting.AccountingPeriodService;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.DocumentSequenceService;
import com.bbc.sms.finance.accounting.LedgerPostingService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.payroll.PayrollDtos.*;

/**
 * Payroll command/read boundary. All money is integer XAF minor units; statutory
 * calculations are intentionally represented by configurable component types.
 */
@Service
public class PayrollService {
    private static final String CURRENCY = "XAF";
    private static final Set<String> COMPONENT_KINDS = Set.of("EARNING", "DEDUCTION", "EMPLOYER_CONTRIBUTION");
    private static final Set<String> CALCULATION_MODES = Set.of("FIXED", "PERCENTAGE", "HOURLY", "MANUAL");

    private final PayrollComponentTypeRepository components;
    private final PayrollPeriodRepository periods;
    private final PayrollRunRepository runs;
    private final EmployeePayrollRepository employeePayrolls;
    private final EmployeePayrollLineRepository payrollLines;
    private final PayrollPaymentRepository payments;
    private final PayslipJobRepository payslipJobs;
    private final PayslipJobResultRepository payslipJobResults;
    private final PayslipRepository payslips;
    private final EmployeeRepository employees;
    private final ChartOfAccountRepository accounts;
    private final PaymentChannelRepository channels;
    private final AccountingPeriodService accountingPeriods;
    private final LedgerPostingService ledger;
    private final DocumentSequenceService sequences;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final OfficialDocumentService officialDocuments;
    private final PayrollPdfRenderer pdf;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;
    private final TreasuryService treasury;

    public PayrollService(PayrollComponentTypeRepository components,
                          PayrollPeriodRepository periods,
                          PayrollRunRepository runs,
                          EmployeePayrollRepository employeePayrolls,
                          EmployeePayrollLineRepository payrollLines,
                          PayrollPaymentRepository payments,
                          PayslipJobRepository payslipJobs,
                          PayslipJobResultRepository payslipJobResults,
                          PayslipRepository payslips,
                          EmployeeRepository employees,
                          ChartOfAccountRepository accounts,
                          PaymentChannelRepository channels,
                          AccountingPeriodService accountingPeriods,
                          LedgerPostingService ledger,
                          DocumentSequenceService sequences,
                          IdempotencyService idempotency,
                          AuditService audit,
                          OfficialDocumentService officialDocuments,
                          PayrollPdfRenderer pdf,
                          JdbcTemplate jdbc,
                          AuthorizationPolicyService policy,
                          TreasuryService treasury) {
        this.components = components;
        this.periods = periods;
        this.runs = runs;
        this.employeePayrolls = employeePayrolls;
        this.payrollLines = payrollLines;
        this.payments = payments;
        this.payslipJobs = payslipJobs;
        this.payslipJobResults = payslipJobResults;
        this.payslips = payslips;
        this.employees = employees;
        this.accounts = accounts;
        this.channels = channels;
        this.accountingPeriods = accountingPeriods;
        this.ledger = ledger;
        this.sequences = sequences;
        this.idempotency = idempotency;
        this.audit = audit;
        this.officialDocuments = officialDocuments;
        this.pdf = pdf;
        this.jdbc = jdbc;
        this.policy = policy;
        this.treasury = treasury;
    }

    @Transactional(readOnly = true)
    public List<ComponentView> components() {
        return components.findBySchoolIdOrderByCodeAsc(TenantContext.get()).stream().map(this::componentView).toList();
    }

    @Transactional
    public ComponentView createComponent(ComponentUpsert request) {
        UUID schoolId = TenantContext.get();
        String code = normalizeComponentCode(request.code());
        validateComponent(request, code);
        if (components.findBySchoolIdAndCode(schoolId, code).isPresent()) {
            throw structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_COMPONENT_CODE_EXISTS",
                    "Ce code de composant de paie existe déjà.", Map.of("code", "Choisissez un code unique."), List.of());
        }
        PayrollComponentType component = new PayrollComponentType();
        component.setSchoolId(schoolId);
        applyComponent(component, request, code);
        component = components.saveAndFlush(component);
        ComponentView result = componentView(component);
        audit.record("PAYROLL_COMPONENT_CREATED", "PayrollComponentType", component.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public ComponentView updateComponent(UUID id, ComponentUpsert request) {
        PayrollComponentType component = components.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Composant de paie"));
        requireVersion(request.version(), component.getVersion(), "composant de paie");
        String code = normalizeComponentCode(request.code());
        validateComponent(request, code);
        components.findBySchoolIdAndCode(TenantContext.get(), code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw structured(org.springframework.http.HttpStatus.CONFLICT,
                    "PAYROLL_COMPONENT_CODE_EXISTS", "Ce code de composant de paie existe déjà.",
                    Map.of("code", "Choisissez un code unique."), List.of());
        });
        ComponentView before = componentView(component);
        applyComponent(component, request, code);
        component = components.saveAndFlush(component);
        ComponentView result = componentView(component);
        audit.record("PAYROLL_COMPONENT_UPDATED", "PayrollComponentType", id.toString(), before, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<PeriodView> periods() {
        return periods.findBySchoolIdOrderByStartDateDesc(TenantContext.get()).stream().map(this::periodView).toList();
    }

    @Transactional(readOnly = true)
    public PaymentOptionsView paymentOptions() {
        UUID schoolId = TenantContext.get();
        List<PaymentOptionView> channelViews = channels.findBySchoolIdOrderBySortOrderAscLabelFrAsc(schoolId).stream()
                .map(c -> new PaymentOptionView(c.getId(), c.getCode(), c.getLabelFr(), c.getLabelEn(),
                        c.isRequiresReference(), c.isEnabled(), c.getDebitAccountId())).toList();
        List<AccountOption> accountViews = accounts.findBySchoolIdAndActiveTrueOrderByCodeAsc(schoolId).stream()
                .filter(ChartOfAccount::isPostingAllowed)
                .map(a -> new AccountOption(a.getId(), a.getCode(), a.getNameFr(), a.getNameEn(), a.getAccountType(),
                        a.getCurrency() == null ? CURRENCY : a.getCurrency())).toList();
        List<TreasuryOption> treasuryViews = treasury.listAccountsForWorkflow().stream()
                .filter(value -> value.active() && CURRENCY.equals(value.currency()))
                .map(value -> new TreasuryOption(value.id(), value.chartAccountId(), value.displayName(),
                        value.kind(), value.currency(), value.balanceMinor())).toList();
        return new PaymentOptionsView(channelViews, accountViews, treasuryViews);
    }

    @Transactional
    public PeriodView createPeriod(PeriodRequest request) {
        UUID schoolId = TenantContext.get();
        validatePeriodDates(request);
        String code = request.code().trim();
        if (periods.findBySchoolIdAndCode(schoolId, code).isPresent()) {
            throw structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_PERIOD_CODE_EXISTS",
                    "Ce code de période de paie existe déjà.", Map.of("code", "Choisissez un code unique."), List.of());
        }
        AccountingPeriod accounting = accountingPeriods.require(request.accountingPeriodId());
        validateAccountingWindow(request, accounting);
        if (periods.findBySchoolIdOrderByStartDateDesc(schoolId).stream()
                .anyMatch(p -> "OPEN".equals(p.getStatus()) && overlaps(p.getStartDate(), p.getEndDate(), request.startDate(), request.endDate()))) {
            throw structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_PERIOD_OVERLAP",
                    "Une période de paie ouverte chevauche déjà ces dates.", Map.of("startDate", "Choisissez une plage libre."), List.of());
        }
        PayrollPeriod period = new PayrollPeriod();
        period.setSchoolId(schoolId);
        period.setCode(code);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setPaymentDate(request.paymentDate());
        period.setAccountingPeriodId(accounting.getId());
        period.setStatus("OPEN");
        period.setCreatedBy(currentUserId());
        period = periods.saveAndFlush(period);
        PeriodView result = periodView(period);
        audit.record("PAYROLL_PERIOD_CREATED", "PayrollPeriod", period.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public PeriodView updatePeriod(UUID id, PeriodRequest request) {
        PayrollPeriod period = requirePeriod(id);
        requireVersion(request.version(), period.getVersion(), "période de paie");
        if (!"OPEN".equals(period.getStatus())) throw ApiException.conflict("Une période de paie fermée est immuable.");
        validatePeriodDates(request);
        AccountingPeriod accounting = accountingPeriods.require(request.accountingPeriodId());
        validateAccountingWindow(request, accounting);
        periods.findBySchoolIdAndCode(TenantContext.get(), request.code().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw structured(org.springframework.http.HttpStatus.CONFLICT,
                    "PAYROLL_PERIOD_CODE_EXISTS", "Ce code de période de paie existe déjà.",
                    Map.of("code", "Choisissez un code unique."), List.of());
        });
        PeriodView before = periodView(period);
        period.setCode(request.code().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setPaymentDate(request.paymentDate());
        period.setAccountingPeriodId(accounting.getId());
        period = periods.saveAndFlush(period);
        PeriodView result = periodView(period);
        audit.record("PAYROLL_PERIOD_UPDATED", "PayrollPeriod", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public PeriodView closePeriod(UUID id, ActionRequest request) {
        PayrollPeriod period = requirePeriod(id);
        requireVersion(request.version(), period.getVersion(), "période de paie");
        if (!"OPEN".equals(period.getStatus())) throw ApiException.conflict("Cette période de paie est déjà fermée.");
        List<PayrollRun> openRuns = runs.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(r -> id.equals(r.getPayrollPeriodId()) && !Set.of("PAID", "VOID").contains(r.getStatus())).toList();
        if (!openRuns.isEmpty()) {
            throw blocked("PAYROLL_PERIOD_CLOSE_BLOCKED", "La période ne peut pas être fermée avant le paiement ou l'annulation de ses runs.",
                    openRuns.stream().map(r -> new ApiException.Blocker("PAYROLL_RUN", r.getId().toString(),
                            "Run " + r.getRunNumber() + " / " + r.getStatus(), "COMPLETE_OR_VOID_RUN")).toList());
        }
        PeriodView before = periodView(period);
        period.setStatus("CLOSED");
        period.setClosedBy(currentUserId());
        period.setClosedAt(Instant.now());
        period = periods.saveAndFlush(period);
        PeriodView result = periodView(period);
        audit.record("PAYROLL_PERIOD_CLOSED", "PayrollPeriod", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public PreviewView preview(RunRequest request) {
        PayrollPeriod period = requirePeriod(request.payrollPeriodId());
        RunSettings settings = settings(request.prorationMode(), request.defaultHours());
        List<Employee> selected = employeesForScope(request.employeeIds());
        List<PayrollComponentType> catalogue = components.findBySchoolIdAndActiveTrueOrderByCodeAsc(TenantContext.get());
        List<EligibilityView> views = new ArrayList<>();
        long gross = 0, deduction = 0, net = 0, employerCost = 0;
        int eligible = 0, exceptions = 0;
        List<BlockerView> blockers = new ArrayList<>();
        for (Employee employee : selected) {
            Candidate candidate = calculateCandidate(employee, period, settings, catalogue);
            EligibilityView view = eligibilityView(candidate);
            views.add(view);
            if (candidate.exceptionCode == null) {
                eligible++;
                gross += candidate.gross;
                deduction += candidate.deduction;
                net += candidate.net;
                employerCost += candidate.employerCost;
            } else {
                exceptions++;
                blockers.add(new BlockerView(candidate.exceptionCode, candidate.exceptionMessage,
                        "EMPLOYEE:" + employee.getId()));
            }
        }
        return new PreviewView(period.getId(), period.getCode(), period.getStartDate(), period.getEndDate(),
                settings.prorationMode, settings.defaultHours, selected.size(), eligible, exceptions,
                gross, deduction, net, employerCost, CURRENCY, views, blockers);
    }

    @Transactional(readOnly = true)
    public List<RunView> runs() {
        return runs.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream().map(this::runView).toList();
    }

    @Transactional
    public RunDetailView createRun(RunRequest request) {
        PayrollPeriod period = requirePeriod(request.payrollPeriodId());
        if (!"OPEN".equals(period.getStatus())) throw ApiException.conflict("Une période de paie fermée ne peut pas recevoir de nouveau run.");
        if (runs.findBySchoolIdAndPayrollPeriodIdAndStatusNot(TenantContext.get(), period.getId(), "VOID").isPresent()) {
            throw structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_RUN_EXISTS",
                    "Un run actif existe déjà pour cette période. Créez une version corrective après annulation contrôlée.",
                    Map.of("payrollPeriodId", "Utilisez le run existant."), List.of());
        }
        RunSettings settings = settings(request.prorationMode(), request.defaultHours());
        List<Employee> scope = employeesForScope(request.employeeIds());
        String runNoText = sequences.allocate("PAYROLL_RUN", period.getCode(), "PAY/" + period.getCode() + "/", 6);
        long runNumber = parseSequenceNumber(runNoText);
        PayrollRun run = new PayrollRun();
        run.setSchoolId(TenantContext.get());
        run.setPayrollPeriodId(period.getId());
        run.setRunNumber(runNumber);
        run.setStatus("DRAFT");
        run.setProrationMode(settings.prorationMode);
        run.setDefaultHours(settings.defaultHours);
        run.setEmployeeScopeJson(scope.stream().map(Employee::getId).map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(null));
        run.setSegregationEnabled(request.segregationEnabled() == null || request.segregationEnabled());
        run.setSourceEventKey("PAYROLL_RUN:" + period.getId() + ":" + runNumber);
        run.setCreatedBy(currentUserId());
        run = runs.saveAndFlush(run);
        RunDetailView result = detail(run);
        audit.record("PAYROLL_RUN_CREATED", "PayrollRun", run.getId().toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public RunDetailView detail(UUID id) {
        return detail(requireRun(id));
    }

    @Transactional
    public RunDetailView calculate(UUID id, String key) {
        requireKey(key, "Le calcul de paie doit être idempotent.");
        return idempotency.execute("finance-v2/payroll/calculate", key, new CommandKey(id), RunDetailView.class,
                () -> calculateNow(id, key));
    }

    @Transactional
    public RunDetailView calculateNow(UUID id, String key) {
        PayrollRun run = requireRun(id);
        if (!Set.of("DRAFT", "CALCULATED").contains(run.getStatus())) {
            throw ApiException.conflict("Seul un run brouillon ou déjà calculé peut être recalculé.");
        }
        List<EmployeePayroll> oldRows = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), id);
        Map<ManualKey, ManualSeed> manual = manualSeeds(oldRows);
        for (EmployeePayroll old : oldRows) payrollLines.deleteBySchoolIdAndEmployeePayrollId(TenantContext.get(), old.getId());
        payrollLines.flush();
        employeePayrolls.deleteAll(oldRows);
        employeePayrolls.flush();
        PayrollPeriod period = requirePeriod(run.getPayrollPeriodId());
        RunSettings settings = settings(run.getProrationMode(), run.getDefaultHours());
        List<PayrollComponentType> catalogue = components.findBySchoolIdAndActiveTrueOrderByCodeAsc(TenantContext.get());
        List<Employee> selected = employeesForScope(parseScope(run.getEmployeeScopeJson()));
        List<EmployeePayroll> rows = new ArrayList<>();
        for (Employee employee : selected) {
            Candidate candidate = calculateCandidate(employee, period, settings, catalogue);
            EmployeePayroll row = snapshot(candidate, run.getId());
            row = employeePayrolls.saveAndFlush(row);
            List<LineSeed> seeds = new ArrayList<>(candidate.lines);
            applyManualSeeds(seeds, manual, employee.getId(), catalogue);
            saveLines(row, seeds);
            recomputeEmployee(row);
            rows.add(row);
        }
        run.setPreviousSnapshotHash(run.getCalculationSnapshotHash());
        run.setCalculationSnapshotHash(hashRun(rows));
        run.setCalculationIdempotencyKey(key.trim());
        run.setCalculatedBy(currentUserId());
        run.setCalculatedAt(Instant.now());
        run.setStatus("CALCULATED");
        run.setSnapshotLocked(false);
        recalculateRunTotals(run, rows);
        run = runs.saveAndFlush(run);
        RunDetailView result = detail(run);
        audit.record("PAYROLL_CALCULATED", "PayrollRun", id.toString(), null, result,
                manual.isEmpty() ? null : "Les ajustements manuels ont été conservés lorsque le composant existe encore.");
        return result;
    }

    @Transactional
    public RunDetailView adjust(AdjustmentRequest request) {
        EmployeePayroll row = employeePayrolls.findByIdAndSchoolId(request.employeePayrollId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Ligne employé de paie"));
        PayrollRun run = requireRun(row.getPayrollRunId());
        requireVersion(request.version(), row.getVersion(), "ligne de paie");
        if (!Set.of("DRAFT", "CALCULATED").contains(run.getStatus())) throw ApiException.conflict("Les ajustements sont verrouillés après revue.");
        if (request.reason() == null || request.reason().trim().length() < 3) {
            throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYROLL_ADJUSTMENT_REASON_REQUIRED",
                    "Un motif détaillé est obligatoire pour un ajustement manuel.", Map.of("reason", "Saisissez au moins 3 caractères."), List.of());
        }
        String code = normalizeComponentCode(request.componentCode());
        PayrollComponentType component = components.findBySchoolIdAndCode(TenantContext.get(), code)
                .orElseThrow(() -> fieldNotFound("componentCode", "Composant de paie introuvable."));
        validateComponentAccountMapping(component, null);
        List<EmployeePayrollLine> lineRows = payrollLines.findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(TenantContext.get(), row.getId());
        EmployeePayrollLine line = lineRows.stream().filter(l -> code.equals(l.getComponentCode())).findFirst().orElse(null);
        if (line == null) {
            line = lineFrom(component, lineRows.size() + 1, request.amountMinor(), "MANUAL", request.reason());
            line.setSchoolId(TenantContext.get());
            line.setEmployeePayrollId(row.getId());
        } else {
            line.setAmountMinor(request.amountMinor());
            line.setSource("MANUAL");
            line.setReason(request.reason().trim());
        }
        payrollLines.saveAndFlush(line);
        recomputeEmployee(row);
        List<EmployeePayroll> all = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), run.getId());
        recalculateRunTotals(run, all);
        runs.saveAndFlush(run);
        audit.record("PAYROLL_LINE_ADJUSTED", "EmployeePayroll", row.getId().toString(), null,
                employeeView(row), request.reason());
        return detail(run);
    }

    @Transactional
    public RunDetailView review(UUID id, ActionRequest request) {
        PayrollRun run = requireRun(id);
        requireVersion(request.version(), run.getVersion(), "run de paie");
        if (!"CALCULATED".equals(run.getStatus())) throw ApiException.conflict("Le run doit être calculé avant la revue.");
        if (run.getExceptionCount() > 0) throw runBlockers(run, "PAYROLL_REVIEW_BLOCKED", "Corrigez les exceptions avant de soumettre le run à la revue.");
        RunDetailView before = detail(run);
        run.setStatus("REVIEWED");
        run.setReviewedBy(currentUserId());
        run.setReviewedAt(Instant.now());
        run = runs.saveAndFlush(run);
        RunDetailView result = detail(run);
        audit.record("PAYROLL_REVIEWED", "PayrollRun", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional
    public RunDetailView approve(UUID id, ActionRequest request) {
        PayrollRun run = requireRun(id);
        requireVersion(request.version(), run.getVersion(), "run de paie");
        if (!"REVIEWED".equals(run.getStatus())) throw ApiException.conflict("Le run doit être revu avant approbation.");
        if (run.getExceptionCount() > 0) throw runBlockers(run, "PAYROLL_APPROVAL_BLOCKED", "Les exceptions doivent être résolues avant approbation.");
        UUID user = currentUserId();
        if (run.isSegregationEnabled() && user != null && (user.equals(run.getCalculatedBy()) || user.equals(run.getReviewedBy()))) {
            throw ApiException.forbidden("La séparation des tâches interdit au calculateur ou au réviseur d'approuver ce run.");
        }
        List<EmployeePayroll> rows = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), id);
        List<JournalLineInput> journalLines = new ArrayList<>();
        ChartOfAccount payable = requireAccountByCode("2200", "Passif de paie", "LIABILITY", runDate(run));
        for (EmployeePayroll row : rows) {
            if (!"READY".equals(row.getStatus()) || row.getNetMinor() <= 0) {
                throw structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_ROW_NOT_POSTABLE",
                        "Une ligne de paie n'est pas payable.", Map.of("employeePayrollId", "Corrigez le salaire net et les exceptions."),
                        List.of(new ApiException.Blocker("EMPLOYEE_PAYROLL", row.getId().toString(), row.getEmployeeName(), "OPEN_PAYROLL_RUN")));
            }
            for (EmployeePayrollLine line : payrollLines.findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(TenantContext.get(), row.getId())) {
                ChartOfAccount mapped = accountForLine(line, runDate(run));
                if ("EARNING".equals(line.getComponentKind())) {
                    journalLines.add(new JournalLineInput(mapped.getId(), line.getAmountMinor(), 0, null, null, row.getEmployeeId(), null,
                            line.getComponentCode(), "Paie / charge " + row.getEmployeeCode()));
                } else if ("DEDUCTION".equals(line.getComponentKind())) {
                    journalLines.add(new JournalLineInput(mapped.getId(), 0, line.getAmountMinor(), null, null, row.getEmployeeId(), null,
                            line.getComponentCode(), "Paie / retenue " + row.getEmployeeCode()));
                } else {
                    ChartOfAccount expense = accountForLine(line, runDate(run));
                    journalLines.add(new JournalLineInput(expense.getId(), line.getAmountMinor(), 0, null, null, row.getEmployeeId(), null,
                            line.getComponentCode(), "Paie / coût employeur " + row.getEmployeeCode()));
                    if (line.getLiabilityAccountId() == null) throw mappingBlocker(line);
                    ChartOfAccount liability = requirePostingAccount(line.getLiabilityAccountId(), runDate(run), "Passif du composant " + line.getComponentCode());
                    journalLines.add(new JournalLineInput(liability.getId(), 0, line.getAmountMinor(), null, null, row.getEmployeeId(), null,
                            line.getComponentCode(), "Paie / contribution employeur " + row.getEmployeeCode()));
                }
            }
            journalLines.add(new JournalLineInput(payable.getId(), 0, row.getNetMinor(), null, null, row.getEmployeeId(), null,
                    null, "Dette salariale " + row.getEmployeeCode()));
        }
        AccountingPeriod accounting = accountingPeriods.requireOpenForDate(runDate(run));
        var draft = ledger.createDraftInternal(new JournalUpsert(runDate(run), "Provision de paie " + run.getRunNumber(), CURRENCY,
                accounting.getId(), "PAYROLL_ACCRUAL", run.getId().toString(), "PAYROLL_ACCRUAL:" + run.getId(), journalLines, null));
        var posted = ledger.postNowInternal(draft.id());
        run.setAccrualJournalId(posted.id());
        run.setStatus("APPROVED");
        run.setSnapshotLocked(true);
        run.setApprovedBy(user);
        run.setApprovedAt(Instant.now());
        run = runs.saveAndFlush(run);
        RunDetailView result = detail(run);
        audit.record("PAYROLL_APPROVED", "PayrollRun", id.toString(), null, result, request.reason());
        return result;
    }

    @Transactional
    public RunDetailView voidRun(UUID id, ActionRequest request, String key) {
        requireKey(key, "L'annulation du run de paie doit être idempotente.");
        return idempotency.execute("finance-v2/payroll/void", key, new CommandKey(id), RunDetailView.class,
                () -> voidRunNow(id, request, key));
    }

    @Transactional
    public RunDetailView voidRunNow(UUID id, ActionRequest request, String key) {
        PayrollRun run = requireRun(id);
        requireVersion(request.version(), run.getVersion(), "run de paie");
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 3) {
            throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYROLL_VOID_REASON_REQUIRED",
                    "Un motif détaillé est obligatoire pour annuler ou renverser un run.",
                    Map.of("reason", "Saisissez au moins 3 caractères."), List.of());
        }
        if ("VOID".equals(run.getStatus())) return detail(run);
        if (!Set.of("DRAFT", "CALCULATED", "REVIEWED", "APPROVED", "PAID").contains(run.getStatus())) {
            throw ApiException.conflict("Le run est dans un état qui ne permet pas une annulation contrôlée.");
        }
        RunDetailView before = detail(run);
        if ("APPROVED".equals(run.getStatus()) || "PAID".equals(run.getStatus())) {
            reversePostedPayrollJournal(run.getAccrualJournalId(), run, reason, key + ":accrual");
        }
        if ("PAID".equals(run.getStatus())) {
            reversePostedPayrollJournal(run.getPaymentJournalId(), run, reason, key + ":payment");
        }
        List<EmployeePayroll> payrollRows = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), id);
        Set<UUID> payrollRowIds = payrollRows.stream().map(EmployeePayroll::getId).collect(java.util.stream.Collectors.toSet());
        payrollRows.forEach(row -> {
            row.setStatus("VOID");
            employeePayrolls.saveAndFlush(row);
        });
        payslips.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(slip -> payrollRowIds.contains(slip.getEmployeePayrollId()) && "ISSUED".equals(slip.getStatus()))
                .forEach(slip -> {
                    slip.setStatus("VOIDED");
                    slip.setVoidedBy(currentUserId());
                    slip.setVoidedAt(Instant.now());
                    slip.setVoidReason(reason);
                    payslips.saveAndFlush(slip);
                    if (slip.getGeneratedDocumentId() != null) officialDocuments.revoke(slip.getGeneratedDocumentId(), new RevokeRequest(reason));
                });
        run.setStatus("VOID");
        run.setVoidedBy(currentUserId());
        run.setVoidedAt(Instant.now());
        run.setVoidReason(reason);
        run = runs.saveAndFlush(run);
        RunDetailView result = detail(run);
        audit.record("PAYROLL_VOIDED", "PayrollRun", id.toString(), before, result, reason);
        return result;
    }

    private void reversePostedPayrollJournal(UUID journalId, PayrollRun run, String reason, String key) {
        if (journalId == null) return;
        JournalView journal = ledger.detailInternal(journalId);
        if (!Set.of("POSTED", "REVERSED").contains(journal.status())) {
            throw blocked("PAYROLL_JOURNAL_NOT_POSTED", "Le journal de paie n'est pas posté et ne peut pas être renversé.",
                    List.of(new ApiException.Blocker("JOURNAL", journal.id().toString(), journal.number(), "OPEN_PAYROLL_JOURNAL")));
        }
        ledger.reverseNowInternal(journal.id(), new ReverseRequest(runDate(run), reason, journal.version()));
    }

    @Transactional
    public PayResultView pay(UUID id, PayRequest request, String key) {
        requireKey(key, "Le paiement de paie doit être idempotent.");
        return idempotency.execute("finance-v2/payroll/pay", key, request, PayResultView.class,
                () -> payNow(id, request, key));
    }

    @Transactional
    public PayResultView payNow(UUID id, PayRequest request, String key) {
        PayrollRun run = requireRun(id);
        requireVersion(request.version(), run.getVersion(), "run de paie");
        if (!"APPROVED".equals(run.getStatus())) throw ApiException.conflict("Seul un run approuvé peut être payé.");
        LocalDate paymentDate = request.paymentDate();
        AccountingPeriod accounting = accountingPeriods.requireOpenForDate(paymentDate);
        PaymentChannel channel = channels.findById(request.paymentChannelId()).filter(c -> TenantContext.get().equals(c.getSchoolId()))
                .orElseThrow(() -> ApiException.notFound("Canal de paiement"));
        if (!channel.isEnabled()) throw ApiException.conflict("Le canal de paiement est désactivé.");
        if (channel.isRequiresReference() && blank(request.reference()) == null) {
            throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYROLL_PAYMENT_REFERENCE_REQUIRED",
                    "Une référence est obligatoire pour ce canal.", Map.of("reference", "Saisissez la référence opérateur."), List.of());
        }
        if ("CASH".equalsIgnoreCase(channel.getCode()) && !cashierOpen()) {
            throw structured(org.springframework.http.HttpStatus.CONFLICT, "CASHIER_SESSION_REQUIRED",
                    "Une session de caisse ouverte est requise pour payer la paie en espèces.",
                    Map.of("paymentChannelId", "Ouvrez le tiroir de caisse avant de continuer."), List.of());
        }
        TreasuryService.TreasuryRecord treasuryRecord = treasury.requireActiveRecord(request.treasuryAccountId());
        ChartOfAccount paymentAccount = requirePostingAccount(treasuryRecord.chartAccountId(), paymentDate, "Compte de paiement");
        ChartOfAccount payable = requireAccountByCode("2200", "Passif de paie", "LIABILITY", paymentDate);
        List<EmployeePayroll> rows = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), id);
        List<JournalLineInput> journalLines = new ArrayList<>();
        List<EmployeePayroll> toPay = new ArrayList<>();
        List<PaymentResultView> results = new ArrayList<>();
        for (EmployeePayroll row : rows) {
            if (payments.existsBySchoolIdAndEmployeePayrollIdAndStatus(TenantContext.get(), row.getId(), "POSTED")) {
                results.add(new PaymentResultView(row.getId(), row.getEmployeeName(), "ALREADY_PAID", null, row.getNetMinor(),
                        "Un paiement posté existe déjà pour cette ligne.", null));
                continue;
            }
            if (row.getNetMinor() <= 0) {
                results.add(new PaymentResultView(row.getId(), row.getEmployeeName(), "BLOCKED", null, 0,
                        "Le net à payer doit être positif.", null));
                continue;
            }
            toPay.add(row);
            journalLines.add(new JournalLineInput(payable.getId(), row.getNetMinor(), 0, null, null, row.getEmployeeId(), null,
                    null, "Paiement paie " + row.getEmployeeCode()));
            journalLines.add(new JournalLineInput(paymentAccount.getId(), 0, row.getNetMinor(), null, null, row.getEmployeeId(), null,
                    null, "Paiement paie " + row.getEmployeeCode()));
        }
        UUID journalId = null;
        if (!toPay.isEmpty()) {
            var draft = ledger.createDraftInternal(new JournalUpsert(paymentDate, "Paiement paie " + run.getRunNumber(), CURRENCY,
                    accounting.getId(), "PAYROLL_PAYMENT", run.getId().toString(), "PAYROLL_PAYMENT:" + run.getId() + ":" + key,
                    journalLines, null));
            journalId = ledger.postNowInternal(draft.id()).id();
            for (EmployeePayroll row : toPay) {
                String ref = referenceFor(request, row);
                PayrollPayment payment = new PayrollPayment();
                payment.setSchoolId(TenantContext.get());
                payment.setEmployeePayrollId(row.getId());
                payment.setPaymentChannelId(channel.getId());
                payment.setChannelCode(channel.getCode().trim().toUpperCase(Locale.ROOT));
                payment.setPaymentAccountId(paymentAccount.getId());
                payment.setTreasuryAccountId(treasuryRecord.id());
                payment.setPaymentReference(ref);
                payment.setAmountMinor(row.getNetMinor());
                payment.setCurrency(CURRENCY);
                payment.setPaymentDate(paymentDate);
                payment.setStatus("POSTED");
                payment.setJournalEntryId(journalId);
                payment.setSourceEventKey("PAYROLL_PAYMENT:" + run.getId() + ":" + row.getId());
                payment.setIdempotencyKey(key.trim() + ":" + row.getId());
                payment.setPostedBy(currentUserId());
                payment.setPostedAt(Instant.now());
                payment = payments.saveAndFlush(payment);
                row.setStatus("PAID");
                employeePayrolls.saveAndFlush(row);
                results.add(new PaymentResultView(row.getId(), row.getEmployeeName(), "POSTED", ref, row.getNetMinor(),
                        "Paiement posté et journalisé.", payment.getId()));
            }
        }
        run.setPaymentJournalId(journalId);
        run.setPaidBy(currentUserId());
        run.setPaidAt(Instant.now());
        run.setStatus(toPay.isEmpty() && results.stream().noneMatch(r -> "BLOCKED".equals(r.status())) ? "PAID" : "PAID");
        run = runs.saveAndFlush(run);
        PayslipJobView job = generatePayslipsNow(run, "PAYSLIP_BATCH:" + run.getId() + ":" + key, null);
        long total = results.stream().filter(r -> "POSTED".equals(r.status())).mapToLong(PaymentResultView::amountMinor).sum();
        int failed = (int) results.stream().filter(r -> "BLOCKED".equals(r.status())).count();
        PayResultView result = new PayResultView(run.getId(), failed == 0 ? "PAID" : "PAID_WITH_BLOCKED_ROWS", total,
                results.size() - failed, failed, results, job);
        audit.record("PAYROLL_PAID", "PayrollRun", id.toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<PayslipView> payslips() {
        return payslips.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream().map(this::payslipView).toList();
    }

    @Transactional(readOnly = true)
    public List<PayslipView> selfPayslips() {
        requireSelfPayslipAccess();
        UUID employeeId = currentEmployeeId();
        if (employeeId == null) return List.of();
        Set<UUID> rowIds = new HashSet<>(employeePayrolls.findBySchoolIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.get(), employeeId)
                .stream().map(EmployeePayroll::getId).toList());
        return payslips.findBySchoolIdOrderByCreatedAtDesc(TenantContext.get()).stream()
                .filter(p -> rowIds.contains(p.getEmployeePayrollId()) && "ISSUED".equals(p.getStatus()))
                .map(this::payslipView).toList();
    }

    @Transactional(readOnly = true)
    public PayslipView payslip(UUID id, boolean selfOnly) {
        Payslip slip = requirePayslip(id);
        if (selfOnly) {
            requireSelfPayslipAccess();
            requireSelfOwnership(slip);
        }
        return payslipView(slip);
    }

    @Transactional(readOnly = true)
    public UUID payslipDocument(UUID id, boolean selfOnly) {
        Payslip slip = requirePayslip(id);
        if (selfOnly) {
            requireSelfPayslipAccess();
            requireSelfOwnership(slip);
        }
        if (!"ISSUED".equals(slip.getStatus()) || slip.getGeneratedDocumentId() == null) {
            throw ApiException.conflict("Ce bulletin n'est pas encore disponible en PDF.");
        }
        return slip.getGeneratedDocumentId();
    }

    @Transactional
    public PayslipView regeneratePayslip(UUID id, String key) {
        requireKey(key, "La régénération du bulletin doit être idempotente.");
        return idempotency.execute("finance-v2/payroll/payslips/regenerate", key, new CommandKey(id), PayslipView.class,
                () -> regenerateNow(id, key));
    }

    @Transactional
    public PayslipView regenerateNow(UUID id, String key) {
        Payslip previous = requirePayslip(id);
        if (!"ISSUED".equals(previous.getStatus())) throw ApiException.conflict("Seul un bulletin délivré peut être régénéré.");
        EmployeePayroll row = employeePayrolls.findByIdAndSchoolId(previous.getEmployeePayrollId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Ligne de paie"));
        PayrollRun run = requireRunForEmployee(row);
        PayrollPeriod period = requirePeriod(run.getPayrollPeriodId());
        int version = payslips.findBySchoolIdAndEmployeePayrollIdOrderByVersionNoDesc(TenantContext.get(), row.getId()).stream()
                .mapToInt(Payslip::getVersionNo).max().orElse(previous.getVersionNo()) + 1;
        Payslip replacement = new Payslip();
        replacement.setSchoolId(TenantContext.get());
        replacement.setEmployeePayrollId(row.getId());
        replacement.setVersionNo(version);
        replacement.setPayslipNumber(sequences.allocate("PAYSLIP", period.getCode(), "PSL/" + period.getCode() + "/", 6));
        replacement.setLocale("fr");
        replacement.setStatus("GENERATION_FAILED");
        replacement.setSnapshotHash(row.getSnapshotHash());
        replacement.setSourceEventKey("PAYSLIP:" + row.getId() + ":" + version);
        replacement.setIdempotencyKey(key.trim() + ":" + row.getId());
        replacement = payslips.saveAndFlush(replacement);
        try {
            GeneratedDocumentView document = officialDocuments.registerPdf("PAYSLIP", "Payslip", replacement.getId().toString(),
                    String.valueOf(version), "fr", "Bulletin de paie / Payslip " + replacement.getPayslipNumber(), "STAFF",
                    pdf.render(employeeView(row), periodView(period), schoolSnapshot(), replacement.getPayslipNumber(), row.getSnapshotHash(), "/verify/payslips/"),
                    "PAYSLIP_PDF:" + replacement.getId(), replacement.getPayslipNumber());
            replacement.setGeneratedDocumentId(document.id());
            replacement.setStatus("ISSUED");
            replacement.setIssuedBy(currentUserId());
            replacement.setIssuedAt(Instant.now());
            replacement = payslips.saveAndFlush(replacement);
            previous.setStatus("SUPERSEDED");
            previous.setSupersededById(replacement.getId());
            payslips.saveAndFlush(previous);
            if (previous.getGeneratedDocumentId() != null) officialDocuments.supersede(previous.getGeneratedDocumentId(), document.id(), "Régénération demandée");
        } catch (RuntimeException ex) {
            replacement.setGenerationError(shortError(ex));
            payslips.saveAndFlush(replacement);
            throw ex;
        }
        PayslipView result = payslipView(replacement);
        audit.record("PAYSLIP_REGENERATED", "Payslip", id.toString(), payslipView(previous), result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public PayslipJobView payslipJob(UUID id) {
        return payslipJobView(payslipJobs.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Job de bulletins")));
    }

    @Transactional(readOnly = true)
    public List<PayslipJobResultView> payslipJobResults(UUID id) {
        payslipJobs.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Job de bulletins"));
        return payslipJobResults.findBySchoolIdAndJobIdOrderByCreatedAtAsc(TenantContext.get(), id).stream()
                .map(r -> new PayslipJobResultView(r.getId(), r.getEmployeePayrollId(), r.getPayslipId(), r.getResultStatus(), r.getErrorDetail())).toList();
    }

    @Transactional
    public PayslipJobView retryPayslipJob(UUID id, String key) {
        requireKey(key, "La relance du job de bulletins doit être idempotente.");
        PayslipJob old = payslipJobs.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Job de bulletins"));
        List<UUID> failed = payslipJobResults.findBySchoolIdAndJobIdOrderByCreatedAtAsc(TenantContext.get(), id).stream()
                .filter(r -> "FAILED".equals(r.getResultStatus())).map(PayslipJobResult::getEmployeePayrollId).toList();
        PayrollRun run = requireRun(old.getPayrollRunId());
        return idempotency.execute("finance-v2/payroll/payslip-jobs/retry", key, new CommandKey(id), PayslipJobView.class,
                () -> generatePayslipsNow(run, key, failed));
    }

    @Transactional
    public PayslipJobView generatePayslips(UUID runId, String key) {
        requireKey(key, "La génération des bulletins doit être idempotente.");
        return idempotency.execute("finance-v2/payroll/payslip-jobs", key, new CommandKey(runId), PayslipJobView.class,
                () -> generatePayslipsNow(requireRun(runId), key, null));
    }

    @Transactional(readOnly = true)
    public EmployeeView employee(UUID id) {
        return employeeView(employeePayrolls.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Ligne employé de paie")));
    }

    private PayslipJobView generatePayslipsNow(PayrollRun run, String key, List<UUID> onlyEmployeeRows) {
        PayslipJob existing = payslipJobs.findBySchoolIdAndIdempotencyKey(TenantContext.get(), key).orElse(null);
        if (existing != null) return payslipJobView(existing);
        List<EmployeePayroll> all = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), run.getId()).stream()
                .filter(r -> "PAID".equals(r.getStatus())).toList();
        if (onlyEmployeeRows != null) {
            Set<UUID> allowed = new HashSet<>(onlyEmployeeRows);
            all = all.stream().filter(r -> allowed.contains(r.getId())).toList();
        }
        PayslipJob job = new PayslipJob();
        job.setSchoolId(TenantContext.get());
        job.setPayrollRunId(run.getId());
        job.setStatus("RUNNING");
        job.setTotalCount(all.size());
        job.setIdempotencyKey(key);
        job.setRequestedBy(currentUserId());
        job = payslipJobs.saveAndFlush(job);
        PayrollPeriod period = requirePeriod(run.getPayrollPeriodId());
        for (EmployeePayroll row : all) {
            PayslipJobResult result = new PayslipJobResult();
            result.setSchoolId(TenantContext.get());
            result.setJobId(job.getId());
            result.setEmployeePayrollId(row.getId());
            try {
                Payslip latest = payslips.findBySchoolIdAndEmployeePayrollIdOrderByVersionNoDesc(TenantContext.get(), row.getId())
                        .stream().findFirst().orElse(null);
                if (latest != null && "ISSUED".equals(latest.getStatus())) {
                    result.setResultStatus("ALREADY_EXISTS");
                    result.setPayslipId(latest.getId());
                } else {
                    Payslip slip = latest != null && "GENERATION_FAILED".equals(latest.getStatus()) ? latest : new Payslip();
                    if (slip.getId() == null) {
                        slip.setSchoolId(TenantContext.get());
                        slip.setEmployeePayrollId(row.getId());
                        slip.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
                        slip.setPayslipNumber(sequences.allocate("PAYSLIP", period.getCode(), "PSL/" + period.getCode() + "/", 6));
                        slip.setLocale("fr");
                        slip.setSourceEventKey("PAYSLIP:" + row.getId() + ":" + slip.getVersionNo());
                    }
                    slip.setStatus("GENERATION_FAILED");
                    slip.setSnapshotHash(row.getSnapshotHash());
                    slip.setIdempotencyKey(key + ":" + row.getId());
                    slip.setGenerationError(null);
                    slip = payslips.saveAndFlush(slip);
                    GeneratedDocumentView document = officialDocuments.registerPdf("PAYSLIP", "Payslip", slip.getId().toString(),
                            String.valueOf(slip.getVersionNo()), "fr", "Bulletin de paie / Payslip " + slip.getPayslipNumber(), "STAFF",
                            pdf.render(employeeView(row), periodView(period), schoolSnapshot(), slip.getPayslipNumber(), row.getSnapshotHash(), "/verify/payslips/"),
                            "PAYSLIP_PDF:" + slip.getId(), slip.getPayslipNumber());
                    slip.setGeneratedDocumentId(document.id());
                    slip.setStatus("ISSUED");
                    slip.setIssuedBy(currentUserId());
                    slip.setIssuedAt(Instant.now());
                    slip = payslips.saveAndFlush(slip);
                    result.setResultStatus("ISSUED");
                    result.setPayslipId(slip.getId());
                }
            } catch (RuntimeException ex) {
                result.setResultStatus("FAILED");
                result.setErrorDetail(shortError(ex));
            }
            payslipJobResults.saveAndFlush(result);
        }
        int issued = (int) payslipJobResults.findBySchoolIdAndJobIdOrderByCreatedAtAsc(TenantContext.get(), job.getId()).stream()
                .filter(r -> "ISSUED".equals(r.getResultStatus()) || "ALREADY_EXISTS".equals(r.getResultStatus())).count();
        int failed = job.getTotalCount() - issued;
        job.setIssuedCount(issued);
        job.setFailedCount(failed);
        job.setStatus(failed == 0 ? "COMPLETED" : "COMPLETED_WITH_FAILURES");
        job.setCompletedAt(Instant.now());
        if (failed > 0) job.setLastError("Certains bulletins nécessitent une relance; consultez les résultats détaillés.");
        job = payslipJobs.saveAndFlush(job);
        audit.record("PAYSLIP_JOB_COMPLETED", "PayslipJob", job.getId().toString(), null, payslipJobView(job), job.getLastError());
        return payslipJobView(job);
    }

    private Candidate calculateCandidate(Employee employee, PayrollPeriod period, RunSettings settings,
                                         List<PayrollComponentType> catalogue) {
        boolean dateEligible = employee.getHiredOn() == null || !employee.getHiredOn().isAfter(period.getEndDate());
        dateEligible &= employee.getExitedOn() == null || !employee.getExitedOn().isBefore(period.getStartDate());
        String mode = isHourly(employee) ? "HOURLY" : "MONTHLY";
        int hours = employee.getMonthlyHours() > 0 ? employee.getMonthlyHours() : settings.defaultHours;
        if (!employee.isActive()) return exceptionCandidate(employee, mode, hours, "INACTIVE_EMPLOYEE", "Cet employé inactif n'est pas éligible à la paie.");
        if (!dateEligible) return exceptionCandidate(employee, mode, hours, "NOT_ELIGIBLE_FOR_PERIOD", "Les dates d'embauche ou de sortie ne couvrent pas cette période.");
        if ("MONTHLY".equals(mode) && employee.getMonthlySalary() <= 0) {
            return exceptionCandidate(employee, mode, hours, "MISSING_MONTHLY_SALARY", "Le salaire mensuel XAF est manquant ou nul.");
        }
        if ("HOURLY".equals(mode) && employee.getHourlyRate() <= 0) {
            return exceptionCandidate(employee, mode, hours, "MISSING_HOURLY_RATE", "Le taux horaire XAF est manquant ou nul.");
        }
        if ("HOURLY".equals(mode) && hours <= 0) return exceptionCandidate(employee, mode, hours, "MISSING_APPROVED_HOURS", "Les heures approuvées sont manquantes.");
        String baseCode = "HOURLY".equals(mode) ? "HOURLY_WORK" : "BASE_SALARY";
        PayrollComponentType base = catalogue.stream().filter(c -> baseCode.equals(c.getCode()) && effective(c, period)).findFirst().orElse(null);
        if (base == null) return exceptionCandidate(employee, mode, hours, "COMPONENT_MISSING", "Le composant " + baseCode + " n'est pas actif pour cette période.");
        PayrollFormulaPolicy.Calculation formula = "HOURLY".equals(mode)
                ? PayrollFormulaPolicy.hourly(employee.getHourlyRate(), hours)
                : PayrollFormulaPolicy.monthly(employee.getMonthlySalary(), period.getStartDate(), period.getEndDate(), employee.getHiredOn(), employee.getExitedOn(), settings.prorationMode);
        LineSeed baseLine = new LineSeed(base, formula.quantity(), 0, formula.amountMinor(), "DEFAULT", null);
        List<LineSeed> lines = new ArrayList<>();
        lines.add(baseLine);
        long gross = formula.amountMinor(), deduction = 0, employer = 0;
        for (PayrollComponentType component : catalogue) {
            if (component.getCode().equals(baseCode) || !effective(component, period)) continue;
            long amount = componentAmount(component, gross, hours);
            if (amount <= 0) continue;
            lines.add(new LineSeed(component, component.getCalculationMode().equals("PERCENTAGE") ? 1 : hours,
                    component.getDefaultRateBps(), amount, "DEFAULT", null));
            if ("DEDUCTION".equals(component.getComponentKind())) deduction = Math.addExact(deduction, amount);
            else if ("EMPLOYER_CONTRIBUTION".equals(component.getComponentKind())) employer = Math.addExact(employer, amount);
            else gross = Math.addExact(gross, amount);
        }
        for (LineSeed line : lines) {
            if (missingComponentMapping(line.component)) {
                return candidateWithException(employee, mode, hours, formula.formula(), lines, gross, deduction,
                        gross - deduction, gross + employer, "ACCOUNT_MAPPING_MISSING",
                        "Le composant " + line.component.getCode() + " n'a pas tous ses comptes comptables configurés.");
            }
        }
        long net = gross - deduction;
        if (net <= 0) return candidateWithException(employee, mode, hours, formula.formula(), lines, gross, deduction, net, employer,
                "ZERO_OR_NEGATIVE_NET", "Le salaire net doit rester strictement positif.");
        return new Candidate(employee, mode, hours, formula.formula(), lines, gross, deduction, net, gross + employer, null, null);
    }

    private Candidate exceptionCandidate(Employee employee, String mode, int hours, String code, String message) {
        return candidateWithException(employee, mode, hours, null, List.of(), 0, 0, 0, 0, code, message);
    }

    private Candidate candidateWithException(Employee employee, String mode, int hours, String formula, List<LineSeed> lines,
                                             long gross, long deduction, long net, long employerCost, String code, String message) {
        return new Candidate(employee, mode, hours, formula, lines, gross, deduction, net, employerCost, code, message);
    }

    private long componentAmount(PayrollComponentType component, long gross, int hours) {
        return switch (component.getCalculationMode()) {
            case "PERCENTAGE" -> BigDecimal.valueOf(gross).multiply(BigDecimal.valueOf(component.getDefaultRateBps()))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
            case "HOURLY" -> Math.multiplyExact(component.getDefaultAmountMinor(), hours);
            default -> component.getDefaultAmountMinor();
        };
    }

    private void applyManualSeeds(List<LineSeed> seeds, Map<ManualKey, ManualSeed> manual, UUID employeeId,
                                  List<PayrollComponentType> catalogue) {
        for (Map.Entry<ManualKey, ManualSeed> entry : manual.entrySet()) {
            if (!employeeId.equals(entry.getKey().employeeId)) continue;
            LineSeed matching = seeds.stream().filter(s -> entry.getKey().componentCode.equals(s.component.getCode())).findFirst().orElse(null);
            if (matching != null) {
                seeds.set(seeds.indexOf(matching), new LineSeed(matching.component, matching.quantity, matching.rateBps,
                        entry.getValue().amountMinor, "MANUAL", entry.getValue().reason));
            } else {
                catalogue.stream().filter(c -> entry.getKey().componentCode.equals(c.getCode())).findFirst()
                        .ifPresent(c -> seeds.add(new LineSeed(c, 1, c.getDefaultRateBps(), entry.getValue().amountMinor, "MANUAL", entry.getValue().reason)));
            }
        }
    }

    private Map<ManualKey, ManualSeed> manualSeeds(List<EmployeePayroll> oldRows) {
        Map<ManualKey, ManualSeed> result = new HashMap<>();
        for (EmployeePayroll row : oldRows) {
            for (EmployeePayrollLine line : payrollLines.findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(TenantContext.get(), row.getId())) {
                if ("MANUAL".equals(line.getSource())) result.put(new ManualKey(row.getEmployeeId(), line.getComponentCode()),
                        new ManualSeed(line.getAmountMinor(), line.getReason()));
            }
        }
        return result;
    }

    private EmployeePayroll snapshot(Candidate c, UUID runId) {
        Employee employee = c.employee;
        EmployeePayroll row = new EmployeePayroll();
        row.setSchoolId(TenantContext.get());
        row.setPayrollRunId(runId);
        row.setEmployeeId(employee.getId());
        row.setEmployeeCode(employee.getCode());
        row.setEmployeeName(employee.getName());
        row.setEmployeeEmail(employee.getEmail());
        row.setEmploymentType(employee.getType());
        row.setHiredOnSnapshot(employee.getHiredOn());
        row.setExitedOnSnapshot(employee.getExitedOn());
        row.setEmploymentMode(c.mode);
        row.setMonthlySalaryMinor(employee.getMonthlySalary());
        row.setHourlyRateMinor(employee.getHourlyRate());
        row.setApprovedHours(c.hours);
        row.setEligible(c.exceptionCode == null);
        row.setStatus(c.exceptionCode == null ? "READY" : "EXCEPTION");
        row.setExceptionCode(c.exceptionCode);
        row.setExceptionMessage(c.exceptionMessage);
        row.setFormula(c.formula);
        row.setGrossMinor(c.gross);
        row.setDeductionMinor(c.deduction);
        row.setNetMinor(c.net);
        row.setEmployerCostMinor(c.employerCost);
        row.setSnapshotHash(hashEmployee(row, c.lines));
        return row;
    }

    private void saveLines(EmployeePayroll row, List<LineSeed> seeds) {
        int lineNo = 1;
        for (LineSeed seed : seeds) {
            EmployeePayrollLine line = lineFrom(seed.component, lineNo++, seed.amountMinor, seed.source, seed.reason);
            line.setSchoolId(TenantContext.get());
            line.setEmployeePayrollId(row.getId());
            line.setQuantity(seed.quantity);
            line.setRateBps(seed.rateBps);
            payrollLines.saveAndFlush(line);
        }
    }

    private EmployeePayrollLine lineFrom(PayrollComponentType component, int lineNo, long amount, String source, String reason) {
        EmployeePayrollLine line = new EmployeePayrollLine();
        line.setLineNo(lineNo);
        line.setComponentTypeId(component.getId());
        line.setComponentCode(component.getCode());
        line.setComponentNameFr(component.getNameFr());
        line.setComponentNameEn(component.getNameEn());
        line.setComponentKind(component.getComponentKind());
        line.setCalculationMode(component.getCalculationMode());
        line.setQuantity(1);
        line.setRateBps(component.getDefaultRateBps());
        line.setAmountMinor(amount);
        line.setSource(source);
        line.setReason(reason);
        line.setExpenseAccountId(component.getExpenseAccountId());
        line.setLiabilityAccountId(component.getLiabilityAccountId());
        return line;
    }

    private void recomputeEmployee(EmployeePayroll row) {
        List<EmployeePayrollLine> lines = payrollLines.findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(TenantContext.get(), row.getId());
        long gross = lines.stream().filter(l -> "EARNING".equals(l.getComponentKind())).mapToLong(EmployeePayrollLine::getAmountMinor).sum();
        long deduction = lines.stream().filter(l -> "DEDUCTION".equals(l.getComponentKind())).mapToLong(EmployeePayrollLine::getAmountMinor).sum();
        long employer = lines.stream().filter(l -> "EMPLOYER_CONTRIBUTION".equals(l.getComponentKind())).mapToLong(EmployeePayrollLine::getAmountMinor).sum();
        row.setGrossMinor(gross);
        row.setDeductionMinor(deduction);
        row.setNetMinor(gross - deduction);
        row.setEmployerCostMinor(gross + employer);
        if (row.getExceptionCode() == null && row.getNetMinor() <= 0) {
            row.setStatus("EXCEPTION");
            row.setExceptionCode("ZERO_OR_NEGATIVE_NET");
            row.setExceptionMessage("Le salaire net doit rester strictement positif.");
        } else if (row.getNetMinor() > 0 && "ZERO_OR_NEGATIVE_NET".equals(row.getExceptionCode())) {
            row.setStatus("READY");
            row.setExceptionCode(null);
            row.setExceptionMessage(null);
        }
        row.setSnapshotHash(hashEmployee(row, lines.stream().map(this::seed).toList()));
        employeePayrolls.saveAndFlush(row);
    }

    private LineSeed seed(EmployeePayrollLine line) {
        PayrollComponentType c = new PayrollComponentType();
        c.setId(line.getComponentTypeId()); c.setCode(line.getComponentCode()); c.setNameFr(line.getComponentNameFr());
        c.setNameEn(line.getComponentNameEn()); c.setComponentKind(line.getComponentKind()); c.setCalculationMode(line.getCalculationMode());
        c.setExpenseAccountId(line.getExpenseAccountId()); c.setLiabilityAccountId(line.getLiabilityAccountId());
        return new LineSeed(c, line.getQuantity(), line.getRateBps(), line.getAmountMinor(), line.getSource(), line.getReason());
    }

    private void recalculateRunTotals(PayrollRun run, List<EmployeePayroll> rows) {
        run.setEmployeeCount(rows.size());
        run.setExceptionCount((int) rows.stream().filter(r -> "EXCEPTION".equals(r.getStatus())).count());
        run.setGrossMinor(rows.stream().mapToLong(EmployeePayroll::getGrossMinor).sum());
        run.setDeductionMinor(rows.stream().mapToLong(EmployeePayroll::getDeductionMinor).sum());
        run.setNetMinor(rows.stream().mapToLong(EmployeePayroll::getNetMinor).sum());
        run.setEmployerCostMinor(rows.stream().mapToLong(EmployeePayroll::getEmployerCostMinor).sum());
        run.setCalculationSnapshotHash(hashRun(rows));
    }

    private String hashEmployee(EmployeePayroll row, List<LineSeed> lines) {
        StringBuilder value = new StringBuilder().append(row.getEmployeeId()).append('|').append(row.getEmployeeCode()).append('|')
                .append(row.getEmployeeName()).append('|').append(row.getEmploymentMode()).append('|').append(row.getMonthlySalaryMinor())
                .append('|').append(row.getHourlyRateMinor()).append('|').append(row.getApprovedHours()).append('|').append(row.getGrossMinor())
                .append('|').append(row.getDeductionMinor()).append('|').append(row.getNetMinor()).append('|').append(row.getEmployerCostMinor());
        lines.forEach(l -> value.append('|').append(l.component.getCode()).append(':').append(l.amountMinor).append(':').append(l.source));
        return sha256(value.toString());
    }

    private String hashRun(List<EmployeePayroll> rows) {
        return sha256(rows.stream().sorted(Comparator.comparing(EmployeePayroll::getEmployeeCode))
                .map(r -> r.getEmployeeId() + ":" + r.getSnapshotHash() + ":" + r.getStatus()).reduce((a, b) -> a + "|" + b).orElse("EMPTY"));
    }

    private PayslipJobView payslipJobView(PayslipJob job) {
        return new PayslipJobView(job.getId(), job.getPayrollRunId(), job.getStatus(), job.getTotalCount(), job.getIssuedCount(),
                job.getFailedCount(), job.getLastError(), job.getVersion());
    }

    private PayslipView payslipView(Payslip slip) {
        String status = slip.getGeneratedDocumentId() == null ? null : "ISSUED";
        EmployeePayroll row = employeePayrolls.findByIdAndSchoolId(slip.getEmployeePayrollId(), TenantContext.get()).orElse(null);
        return new PayslipView(slip.getId(), slip.getEmployeePayrollId(), row == null ? null : row.getEmployeeId(),
                row == null ? null : row.getEmployeeName(), slip.getPayslipNumber(), slip.getVersionNo(), slip.getLocale(), slip.getStatus(),
                slip.getGeneratedDocumentId(), status, slip.getSnapshotHash(), slip.getGenerationError(), slip.getVersion());
    }

    private RunDetailView detail(PayrollRun run) {
        List<EmployeeView> employeeViews = employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), run.getId())
                .stream().map(this::employeeView).toList();
        return new RunDetailView(runView(run), periodView(requirePeriod(run.getPayrollPeriodId())), employeeViews);
    }

    private EmployeeView employeeView(EmployeePayroll row) {
        List<LineView> lineViews = payrollLines.findBySchoolIdAndEmployeePayrollIdOrderByLineNoAsc(TenantContext.get(), row.getId()).stream()
                .map(this::lineView).toList();
        List<PaymentView> paymentViews = payments.findBySchoolIdAndEmployeePayrollIdOrderByCreatedAtDesc(TenantContext.get(), row.getId()).stream()
                .map(this::paymentView).toList();
        return new EmployeeView(row.getId(), row.getEmployeeId(), row.getEmployeeCode(), row.getEmployeeName(), row.getEmployeeEmail(),
                row.getEmploymentType(), row.getEmploymentMode(), row.getHiredOnSnapshot(), row.getExitedOnSnapshot(), row.getMonthlySalaryMinor(),
                row.getHourlyRateMinor(), row.getApprovedHours(), row.isEligible(), row.getStatus(), row.getExceptionCode(), row.getExceptionMessage(),
                row.getFormula(), row.getGrossMinor(), row.getDeductionMinor(), row.getNetMinor(), row.getEmployerCostMinor(), row.getSnapshotHash(),
                row.getVersion(), lineViews, paymentViews);
    }

    private LineView lineView(EmployeePayrollLine line) {
        return new LineView(line.getId(), line.getLineNo(), line.getComponentTypeId(), line.getComponentCode(), line.getComponentNameFr(),
                line.getComponentNameEn(), line.getComponentKind(), line.getCalculationMode(), line.getQuantity(), line.getRateBps(),
                line.getAmountMinor(), line.getSource(), line.getReason(), line.getExpenseAccountId(), line.getLiabilityAccountId(), line.getVersion());
    }

    private PaymentView paymentView(PayrollPayment payment) {
        return new PaymentView(payment.getId(), payment.getChannelCode(), payment.getPaymentReference(), payment.getAmountMinor(),
                payment.getCurrency(), payment.getPaymentDate(), payment.getStatus(), payment.getJournalEntryId(),
                payment.getTreasuryAccountId(), payment.getTreasuryAccountId() == null ? null
                        : treasury.displayNameForWorkflow(payment.getTreasuryAccountId()), payment.getVersion());
    }

    private RunView runView(PayrollRun run) {
        return new RunView(run.getId(), run.getPayrollPeriodId(), run.getRunNumber(), run.getStatus(), run.getProrationMode(), run.getDefaultHours(),
                run.isSegregationEnabled(), run.getEmployeeCount(), run.getExceptionCount(), run.getGrossMinor(), run.getDeductionMinor(), run.getNetMinor(),
                run.getEmployerCostMinor(), run.getCurrency(), run.getCalculationSnapshotHash(), run.getPreviousSnapshotHash(), run.isSnapshotLocked(),
                run.getAccrualJournalId(), run.getPaymentJournalId(), run.getCalculatedBy(), offset(run.getCalculatedAt()), run.getReviewedBy(), offset(run.getReviewedAt()),
                run.getApprovedBy(), offset(run.getApprovedAt()), run.getPaidBy(), offset(run.getPaidAt()), run.getVersion());
    }

    private ComponentView componentView(PayrollComponentType c) {
        return new ComponentView(c.getId(), c.getCode(), c.getNameFr(), c.getNameEn(), c.getComponentKind(), c.getCalculationMode(),
                c.getDefaultAmountMinor(), c.getDefaultRateBps(), c.getExpenseAccountId(), c.getLiabilityAccountId(), c.isActive(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.getVersion());
    }

    private PeriodView periodView(PayrollPeriod p) {
        return new PeriodView(p.getId(), p.getCode(), p.getStartDate(), p.getEndDate(), p.getPaymentDate(), p.getAccountingPeriodId(), p.getStatus(), p.getVersion());
    }

    private EligibilityView eligibilityView(Candidate c) {
        Employee e = c.employee;
        return new EligibilityView(e.getId(), e.getCode(), e.getName(), e.getType(), c.mode, e.getHiredOn(), e.getExitedOn(),
                e.getMonthlySalary(), e.getHourlyRate(), c.hours, e.isActive(), c.exceptionCode == null,
                c.exceptionCode == null ? "READY" : "EXCEPTION", c.exceptionCode, c.exceptionMessage, c.formula);
    }

    private List<Employee> employeesForScope(List<UUID> ids) {
        UUID schoolId = TenantContext.get();
        if (ids == null || ids.isEmpty()) return employees.findBySchoolId(schoolId);
        List<Employee> result = new ArrayList<>();
        for (UUID id : ids) result.add(employees.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> ApiException.notFound("Employé")));
        return result;
    }

    private List<UUID> parseScope(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(UUID::fromString).toList();
    }

    private PayrollRun requireRun(UUID id) { return runs.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Run de paie")); }
    private PayrollPeriod requirePeriod(UUID id) { return periods.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Période de paie")); }
    private Payslip requirePayslip(UUID id) { return payslips.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Bulletin de paie")); }
    private PayrollRun requireRunForEmployee(EmployeePayroll row) {
        return requireRun(jdbc.queryForObject("SELECT payroll_run_id FROM employee_payroll WHERE school_id=? AND id=?", UUID.class, TenantContext.get(), row.getId()));
    }

    private RunSettings settings(String proration, Integer hours) {
        String value = proration == null || proration.isBlank() ? "NONE" : proration.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NONE", "DAILY").contains(value)) throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PRORATION_MODE",
                "La politique de proratisation doit être NONE ou DAILY.", Map.of("prorationMode", "Choisissez NONE ou DAILY."), List.of());
        int safeHours = hours == null ? 0 : hours;
        if (safeHours < 0) throw fieldNotFound("defaultHours", "Les heures par défaut ne peuvent pas être négatives.");
        return new RunSettings(value, safeHours);
    }

    private void validatePeriodDates(PeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PAYROLL_PERIOD_DATES",
                "La date de fin doit être postérieure ou égale à la date de début.", Map.of("endDate", "Corrigez la plage de dates."), List.of());
        if (request.paymentDate().isBefore(request.startDate())) throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PAYROLL_PAYMENT_DATE",
                "La date de paiement doit appartenir à ou suivre la période.", Map.of("paymentDate", "Choisissez une date valide."), List.of());
    }

    private void validateAccountingWindow(PeriodRequest request, AccountingPeriod accounting) {
        if (!"OPEN".equals(accounting.getStatus())) throw ApiException.conflict("La période comptable liée est fermée.");
        if (request.startDate().isBefore(accounting.getStartDate()) || request.endDate().isAfter(accounting.getEndDate())
                || request.paymentDate().isBefore(accounting.getStartDate()) || request.paymentDate().isAfter(accounting.getEndDate())) {
            throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYROLL_PERIOD_OUTSIDE_ACCOUNTING",
                    "Les dates de paie doivent rester dans la période comptable liée.", Map.of("accountingPeriodId", "Choisissez une période compatible."), List.of());
        }
    }

    private void validateComponent(ComponentUpsert request, String code) {
        if (!code.matches("[A-Z][A-Z0-9_]{0,63}")) throw structured(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PAYROLL_COMPONENT_CODE",
                "Le code doit contenir des lettres majuscules, chiffres et underscores.", Map.of("code", "Exemple : TRANSPORT_ALLOWANCE."), List.of());
        if (!COMPONENT_KINDS.contains(request.componentKind().trim().toUpperCase(Locale.ROOT))) throw fieldNotFound("componentKind", "Nature de composant invalide.");
        if (!CALCULATION_MODES.contains(request.calculationMode().trim().toUpperCase(Locale.ROOT))) throw fieldNotFound("calculationMode", "Mode de calcul invalide.");
        if (request.defaultAmountMinor() < 0 || request.defaultRateBps() < 0 || request.defaultRateBps() > 10000) throw fieldNotFound("defaultRateBps", "Le taux doit être entre 0 et 10000 points de base.");
        if (request.effectiveFrom() != null && request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) throw fieldNotFound("effectiveTo", "La date de fin est invalide.");
        validateComponentAccountIds(request.expenseAccountId(), request.liabilityAccountId());
    }

    private void applyComponent(PayrollComponentType c, ComponentUpsert request, String code) {
        c.setCode(code); c.setNameFr(request.nameFr().trim()); c.setNameEn(request.nameEn().trim());
        c.setComponentKind(request.componentKind().trim().toUpperCase(Locale.ROOT)); c.setCalculationMode(request.calculationMode().trim().toUpperCase(Locale.ROOT));
        c.setDefaultAmountMinor(request.defaultAmountMinor()); c.setDefaultRateBps(request.defaultRateBps());
        c.setExpenseAccountId(request.expenseAccountId()); c.setLiabilityAccountId(request.liabilityAccountId());
        c.setEffectiveFrom(request.effectiveFrom()); c.setEffectiveTo(request.effectiveTo()); c.setActive(request.active() == null || request.active());
    }

    private void validateComponentAccountIds(UUID expense, UUID liability) {
        if (expense != null) requirePostingAccount(expense, null, "Compte de charge du composant");
        if (liability != null) requirePostingAccount(liability, null, "Compte de passif du composant");
    }

    private void validateComponentAccountMapping(PayrollComponentType c, LocalDate date) {
        if ("EARNING".equals(c.getComponentKind()) || "EMPLOYER_CONTRIBUTION".equals(c.getComponentKind())) {
            if (c.getExpenseAccountId() == null) throw mappingBlocker(c);
            requirePostingAccount(c.getExpenseAccountId(), date, "Compte de charge du composant " + c.getCode());
        }
        if ("DEDUCTION".equals(c.getComponentKind()) || "EMPLOYER_CONTRIBUTION".equals(c.getComponentKind())) {
            if (c.getLiabilityAccountId() == null) throw mappingBlocker(c);
            requirePostingAccount(c.getLiabilityAccountId(), date, "Compte de passif du composant " + c.getCode());
        }
    }

    private boolean missingComponentMapping(PayrollComponentType c) {
        return ("EARNING".equals(c.getComponentKind()) || "EMPLOYER_CONTRIBUTION".equals(c.getComponentKind()))
                && c.getExpenseAccountId() == null
                || ("DEDUCTION".equals(c.getComponentKind()) || "EMPLOYER_CONTRIBUTION".equals(c.getComponentKind()))
                && c.getLiabilityAccountId() == null;
    }

    private ChartOfAccount accountForLine(EmployeePayrollLine line, LocalDate date) {
        if ("DEDUCTION".equals(line.getComponentKind())) return requirePostingAccount(line.getLiabilityAccountId(), date, "Compte de passif du composant " + line.getComponentCode());
        return requirePostingAccount(line.getExpenseAccountId(), date, "Compte de charge du composant " + line.getComponentCode());
    }

    private ChartOfAccount requireAccountByCode(String code, String label, String expectedType, LocalDate date) {
        ChartOfAccount account = accounts.findBySchoolIdAndCode(TenantContext.get(), code).orElse(null);
        if (account == null) account = accounts.findBySchoolIdAndActiveTrueOrderByCodeAsc(TenantContext.get()).stream()
                .filter(a -> expectedType.equals(a.getAccountType())).findFirst().orElse(null);
        if (account == null) throw structured(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_MAPPING_MISSING",
                "Le compte " + label + " n'est pas configuré.", Map.of(), List.of(new ApiException.Blocker("ACCOUNT", null, label, "OPEN_CHART_OF_ACCOUNTS")));
        return requirePostingAccount(account.getId(), date, label);
    }

    private ChartOfAccount requirePostingAccount(UUID id, LocalDate date, String label) {
        if (id == null) throw structured(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_MAPPING_MISSING",
                label + " non configuré.", Map.of("accountId", "Choisissez un compte comptable."), List.of());
        ChartOfAccount account = accounts.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Compte comptable"));
        if (!account.isActive() || !account.isPostingAllowed()) throw structured(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_NOT_POSTABLE",
                label + " n'est pas postable.", Map.of("accountId", "Choisissez un compte actif et postable."), List.of());
        if (date != null && ((account.getEffectiveFrom() != null && date.isBefore(account.getEffectiveFrom()))
                || (account.getEffectiveTo() != null && date.isAfter(account.getEffectiveTo())))) throw structured(org.springframework.http.HttpStatus.CONFLICT,
                "ACCOUNT_NOT_EFFECTIVE", label + " n'est pas effectif à cette date.", Map.of("accountId", "Choisissez un compte effectif."), List.of());
        return account;
    }

    private ApiException mappingBlocker(PayrollComponentType c) {
        return structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_ACCOUNT_MAPPING_MISSING",
                "Le composant " + c.getCode() + " n'a pas tous ses comptes comptables.", Map.of(),
                List.of(new ApiException.Blocker("PAYROLL_COMPONENT", c.getId().toString(), c.getCode(), "CONFIGURE_COMPONENT_ACCOUNTS")));
    }

    private ApiException mappingBlocker(EmployeePayrollLine line) {
        return structured(org.springframework.http.HttpStatus.CONFLICT, "PAYROLL_ACCOUNT_MAPPING_MISSING",
                "Le composant " + line.getComponentCode() + " n'a pas tous ses comptes comptables.", Map.of(), List.of());
    }

    private boolean effective(PayrollComponentType c, PayrollPeriod p) {
        return c.isActive() && (c.getEffectiveFrom() == null || !c.getEffectiveFrom().isAfter(p.getEndDate()))
                && (c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(p.getStartDate()));
    }

    private LocalDate runDate(PayrollRun run) { return requirePeriod(run.getPayrollPeriodId()).getPaymentDate(); }
    private boolean isHourly(Employee e) { String type = e.getType() == null ? "" : e.getType().toLowerCase(Locale.ROOT); return type.contains("vac") || type.contains("hour"); }
    private boolean overlaps(LocalDate a, LocalDate b, LocalDate c, LocalDate d) { return !a.isAfter(d) && !b.isBefore(c); }
    private boolean cashierOpen() {
        UUID user = currentUserId();
        if (user == null) return false;
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cashier_session WHERE school_id=? AND cashier_user_id=? AND status='OPEN')", Boolean.class, TenantContext.get(), user);
    }
    private String referenceFor(PayRequest request, EmployeePayroll row) {
        String specific = request.employeeReferences() == null ? null : request.employeeReferences().get(row.getEmployeeId());
        String value = blank(specific);
        if (value == null) value = blank(request.reference());
        return value == null ? null : value + "/" + row.getEmployeeCode();
    }

    private void requireSelfPayslipAccess() {
        policy.require("PAYSLIP_VIEW_SELF", new PolicyResourceContext(
                TenantContext.get(), null, LocalDate.now(), null,
                null, null, null, null, null, null, null, null));
    }

    private void requireSelfOwnership(Payslip slip) {
        UUID employeeId = currentEmployeeId();
        EmployeePayroll row = employeePayrolls.findByIdAndSchoolId(slip.getEmployeePayrollId(), TenantContext.get()).orElseThrow(() -> ApiException.notFound("Ligne de paie"));
        if (employeeId == null || !employeeId.equals(row.getEmployeeId())) throw ApiException.forbidden("Ce bulletin n'appartient pas à votre compte.");
    }

    private UUID currentEmployeeId() {
        UUID user = currentUserId();
        if (user == null) return null;
        try { return jdbc.queryForObject("SELECT employee_id FROM app_user WHERE school_id=? AND id=?", UUID.class, TenantContext.get(), user); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    private PayrollPdfRenderer.SchoolSnapshot schoolSnapshot() {
        return jdbc.queryForObject("SELECT code,name,authority,address,city,country,phone,email FROM school WHERE id=?",
                (rs, n) -> new PayrollPdfRenderer.SchoolSnapshot(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), TenantContext.get());
    }

    private void requireKey(String key, String message) { if (key == null || key.isBlank()) throw fieldNotFound("idempotencyKey", message); }
    private static long parseSequenceNumber(String value) { try { return Long.parseLong(value.substring(value.lastIndexOf('/') + 1)); } catch (RuntimeException ex) { return Math.abs(value.hashCode()); } }
    private static OffsetDateTime offset(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private static String normalizeComponentCode(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String shortError(RuntimeException ex) { String message = ex.getMessage(); return message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(900, message.length())); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
    private static ApiException structured(org.springframework.http.HttpStatus status, String code, String message, Map<String, String> fields, List<ApiException.Blocker> blockers) { return ApiException.structured(status, code, message, fields, blockers); }
    private static ApiException fieldNotFound(String field, String message) { return structured(org.springframework.http.HttpStatus.BAD_REQUEST, "PAYROLL_FIELD_INVALID", message, Map.of(field, message), List.of()); }
    private static ApiException blocked(String code, String message, List<ApiException.Blocker> blockers) { return structured(org.springframework.http.HttpStatus.CONFLICT, code, message, Map.of(), blockers); }
    private ApiException runBlockers(PayrollRun run, String code, String message) { return blocked(code, message, employeePayrolls.findBySchoolIdAndPayrollRunIdOrderByEmployeeNameAsc(TenantContext.get(), run.getId()).stream().filter(r -> "EXCEPTION".equals(r.getStatus())).map(r -> new ApiException.Blocker("EMPLOYEE_PAYROLL", r.getId().toString(), r.getEmployeeName() + ": " + r.getExceptionMessage(), "OPEN_PAYROLL_ROW")).toList()); }
    private static void requireVersion(Long supplied, long current, String label) { if (supplied == null || supplied != current) throw ApiException.conflict("Le " + label + " a changé ailleurs. Rechargez-le avant de réessayer."); }

    private record RunSettings(String prorationMode, int defaultHours) {}
    private record CommandKey(UUID id) {}
    private record ManualKey(UUID employeeId, String componentCode) {}
    private record ManualSeed(long amountMinor, String reason) {}
    private record LineSeed(PayrollComponentType component, long quantity, int rateBps, long amountMinor, String source, String reason) {}
    private record Candidate(Employee employee, String mode, int hours, String formula, List<LineSeed> lines, long gross, long deduction, long net, long employerCost, String exceptionCode, String exceptionMessage) {}
}
