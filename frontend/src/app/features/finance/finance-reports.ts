import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  AccountingReport, CollectionsReport, DocumentsReport, ExpensesReport, FinanceReportApiError,
  FinanceReportsApi, PayrollReport, ReceivablesReport, ReconciliationReport, ReportContext,
  ReportEnvelope, ReportFilters, ReportMeta,
} from './finance-reports.api';

type ReportTab = 'receivables' | 'collections' | 'documents' | 'expenses' | 'payroll' | 'accounting' | 'reconciliation';

const today = () => new Date().toISOString().slice(0, 10);

@Component({
  selector: 'bbc-finance-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './finance-reports.scss',
  template: `
    <div class="reports-shell">
      <header class="reports-hero">
        <div>
          <div class="eyebrow">Finance / {{ fr() ? 'Rapports réconciliés' : 'Reconciled reporting' }} · XAF entier</div>
          <h1>{{ fr() ? 'Pilotage financier avec contexte' : 'Finance reporting with context' }}</h1>
          <p>{{ fr() ? 'Chaque chiffre indique sa session, sa période, sa fraîcheur et ses lignes sources.' : 'Every number states its session, date basis, freshness and source rows.' }}</p>
        </div>
        <div class="hero-actions">
          <span class="context-pill">{{ selectedSessionLabel() || (fr() ? 'Session non sélectionnée' : 'No session selected') }}</span>
          <button type="button" class="secondary-button" [disabled]="!hasContext()" (click)="exportOpen.set(true)">{{ fr() ? 'Exporter' : 'Export' }}</button>
        </div>
      </header>

      @if (error()) { <div class="state state-error" role="alert"><div><strong>{{ fr() ? 'Le rapport ne peut pas être chargé' : 'Report could not load' }}</strong><p>{{ error() }}</p></div><button type="button" class="text-button" (click)="load()">{{ fr() ? 'Réessayer' : 'Retry' }}</button></div> }
      @if (success()) { <div class="state state-success" role="status">{{ success() }}</div> }
      @if (loading()) { <div class="loading-state" aria-label="Loading"><span></span><span></span><span></span></div> }

      @if (!loading() && context(); as options) {
        <section class="context-panel" aria-label="Reporting context">
          <div class="context-heading"><div><div class="eyebrow">{{ fr() ? 'Contexte obligatoire' : 'Required context' }}</div><h2>{{ fr() ? 'Choisir la base des chiffres' : 'Choose the reporting basis' }}</h2><p>{{ fr() ? 'Les rapports ne mélangent pas les sessions. Les dates et l’as-of sont envoyés au serveur.' : 'Reports never mix sessions. Dates and as-of are sent to the server.' }}</p></div><span class="context-status" [class.ready]="hasContext()">{{ hasContext() ? (fr() ? 'Contexte prêt' : 'Context ready') : (fr() ? 'Bloqué' : 'Blocked') }}</span></div>
          <div class="filter-grid context-grid">
            <label>{{ fr() ? 'Session académique' : 'Academic session' }} *<select class="field" [(ngModel)]="filters.academicSessionId" (change)="sessionChanged()"><option value="">{{ fr() ? 'Choisir une session' : 'Choose a session' }}</option>@for (session of options.sessions; track session.id) { <option [value]="session.id">{{ session.code }} · {{ session.label }}{{ session.current ? (fr() ? ' · actuelle' : ' · current') : '' }}</option> }</select><small class="field-help">{{ fr() ? 'Sélection explicite requise avant tout KPI.' : 'Explicit selection is required before any KPI.' }}</small></label>
            <label>{{ fr() ? 'Du' : 'From' }} *<input type="date" class="field" [(ngModel)]="filters.fromDate"></label>
            <label>{{ fr() ? 'Au' : 'To' }} *<input type="date" class="field" [(ngModel)]="filters.toDate"></label>
            <label>{{ fr() ? 'Arrêté au' : 'As of' }} *<input type="date" class="field" [(ngModel)]="filters.asOfDate"></label>
            <label>{{ fr() ? 'Classe snapshot' : 'Snapshot class' }}<select class="field" [(ngModel)]="filters.classId"><option value="">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>@for (klass of options.classes; track klass.id) { <option [value]="klass.id">{{ klass.name }} · {{ klass.level }} · {{ klass.subsystem }}</option> }</select></label>
            <label>{{ fr() ? 'Niveau' : 'Level' }}<select class="field" [(ngModel)]="filters.level"><option value="">{{ fr() ? 'Tous les niveaux' : 'All levels' }}</option>@for (level of options.levels; track level) { <option [value]="level">{{ level }}</option> }</select></label>
            <label>{{ fr() ? 'Type de frais' : 'Fee type' }}<select class="field" [(ngModel)]="filters.feeTypeCode"><option value="">{{ fr() ? 'Tous les types' : 'All fee types' }}</option>@for (feeType of options.feeTypes; track feeType) { <option [value]="feeType">{{ feeType }}</option> }</select></label>
            <label>{{ fr() ? 'Canal' : 'Channel' }}<select class="field" [(ngModel)]="filters.channelCode"><option value="">{{ fr() ? 'Tous les canaux' : 'All channels' }}</option>@for (channel of options.channels; track channel) { <option [value]="channel">{{ channel }}</option> }</select></label>
          </div>
          <div class="filter-actions"><button type="button" class="primary-button" [disabled]="!hasContext() || loading()" (click)="applyFilters()">{{ fr() ? 'Appliquer le contexte' : 'Apply context' }}</button><button type="button" class="secondary-button" (click)="resetFilters()">{{ fr() ? 'Réinitialiser' : 'Reset' }}</button></div>
        </section>

        @if (!hasContext()) { <section class="blocked-state"><strong>{{ fr() ? 'Rapports bloqués jusqu’au choix d’une session' : 'Reports are blocked until a session is selected' }}</strong><p>{{ fr() ? 'Choisissez une session, une plage de dates et une date d’arrêté pour éviter des chiffres ambigus.' : 'Choose a session, date range and as-of date to avoid ambiguous figures.' }}</p></section> }
        @if (hasContext()) {
          <nav class="report-tabs" aria-label="Finance report groups">@for (item of tabs; track item.key) { <button type="button" [class.active-tab]="tab() === item.key" [class.locked-tab]="item.key === 'payroll' && !canPayroll()" (click)="setTab(item.key)">{{ fr() ? item.fr : item.en }}@if (item.key === 'payroll' && !canPayroll()) { <span aria-label="Payroll permission required">· 🔒</span> }</button> }</nav>
          <main class="report-workspace">
            @switch (tab()) {
              @case ('receivables') {
                @if (receivables(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Charges postées' : 'Posted charges' }}</div><h2>{{ fr() ? 'Créances réconciliées' : 'Reconciled receivables' }}</h2><p title="Billed = posted charge snapshots; collected = paid_minor; outstanding = charge outstanding_minor.">{{ fr() ? 'Facturé = charges postées · encaissé = paid_minor · restant = outstanding_minor.' : 'Billed = posted charges · collected = paid_minor · outstanding = outstanding_minor.' }}</p></div><span class="formula-chip">{{ report.data.balanced ? '✓ ' + (fr() ? 'Équation équilibrée' : 'Equation balanced') : '!' + (fr() ? 'Écart à traiter' : 'Mismatch') }}</span></div><div class="kpi-grid"><button class="metric-card" title="Billed total from posted student_charge.adjusted_amount_minor" (click)="focusSources()"><span>{{ fr() ? 'Facturé' : 'Billed' }}</span><b>{{ money(report.data.billedMinor) }}</b></button><button class="metric-card" title="Collected total from posted student_charge.paid_minor" (click)="focusSources()"><span>{{ fr() ? 'Encaissé' : 'Collected' }}</span><b>{{ money(report.data.collectedMinor) }}</b></button><button class="metric-card" title="Outstanding from posted charge snapshots" (click)="focusSources()"><span>{{ fr() ? 'Restant dû' : 'Outstanding' }}</span><b>{{ money(report.data.outstandingMinor) }}</b></button><button class="metric-card" title="Collected / billed × 100, same selected date basis"><span>{{ fr() ? 'Taux de recouvrement' : 'Recovery' }}</span><b>{{ report.data.recoveryPercentage }}%</b></button></div><div class="split-grid"><div><h3>{{ fr() ? 'Vieillissement au ' : 'Ageing as of ' }}{{ report.meta.asOfDate }}</h3><div class="ageing-list">@for (bucket of report.data.ageing; track bucket.bucket) { <button type="button" class="ageing-row" (click)="focusSources()"><span><b>{{ bucketLabel(bucket.bucket) }}</b><small>{{ bucket.installmentCount }} {{ fr() ? 'échéances' : 'installments' }}</small></span><strong>{{ money(bucket.amountMinor) }}</strong><i [style.width.%]="barWidth(bucket.amountMinor, report.data.outstandingMinor)"></i></button> }@empty { <div class="empty-inline">{{ fr() ? 'Aucune échéance ouverte.' : 'No open installments.' }}</div> }</div></div><div><h3>{{ fr() ? 'Performance des échéances' : 'Installment performance' }}</h3><div class="mini-list">@for (line of report.data.installmentPerformance; track line.label) { <div><span>{{ line.label }}<small>{{ line.installmentCount }} rows · {{ money(line.overdueMinor) }} overdue</small></span><b>{{ money(line.paidMinor) }} / {{ money(line.dueMinor) }}</b></div> }@empty { <div class="empty-inline">{{ fr() ? 'Aucune donnée.' : 'No data.' }}</div> }</div></div></div>@if (report.data.exceptions.length) { <div class="exception-box"><strong>{{ fr() ? 'Blocages de réconciliation' : 'Reconciliation blockers' }}</strong>@for (item of report.data.exceptions; track item.code) { <p><b>{{ item.code }}</b> · {{ item.message }}</p> }</div> }<div id="report-sources" class="table-wrap"><table><thead><tr><th>{{ fr() ? 'Charge / élève' : 'Charge / student' }}</th><th>{{ fr() ? 'Snapshot' : 'Snapshot' }}</th><th>{{ fr() ? 'Type' : 'Fee type' }}</th><th>{{ fr() ? 'Facturé' : 'Billed' }}</th><th>{{ fr() ? 'Restant' : 'Outstanding' }}</th></tr></thead><tbody>@for (row of report.data.rows; track row.sourceId) { <tr><td><strong>{{ row.studentName }}</strong><small>{{ row.studentId }} · {{ row.sessionCode || '—' }}</small></td><td>{{ row.classNameSnapshot || '—' }}<small>{{ row.levelSnapshot || '—' }} · {{ row.chargeDate }}</small></td><td><b>{{ row.feeTypeCode }}</b></td><td>{{ money(row.billedMinor) }}</td><td><strong>{{ money(row.outstandingMinor) }}</strong></td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucune charge postée pour ce contexte.' : 'No posted charges for this context.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('collections') {
                @if (collections(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Encaissements postés' : 'Posted collections' }}</div><h2>{{ fr() ? 'Collections & caisse' : 'Collections & cashier' }}</h2><p title="Payment equation: payment = allocations + remaining credit + refunds for non-reversed status.">{{ fr() ? 'Paiement = allocations + crédit restant + remboursements, hors renversements.' : 'Payment = allocations + remaining credit + refunds, excluding reversals.' }}</p></div><span class="formula-chip">{{ report.data.balanced ? '✓ Balanced' : '! Mismatch' }}</span></div><div class="kpi-grid"><button class="metric-card" (click)="focusSources()"><span>{{ fr() ? 'Paiements' : 'Payments' }}</span><b>{{ money(report.data.paymentTotalMinor) }}</b></button><button class="metric-card" (click)="focusSources()"><span>{{ fr() ? 'Affecté' : 'Allocated' }}</span><b>{{ money(report.data.allocatedMinor) }}</b></button><button class="metric-card" (click)="focusSources()"><span>{{ fr() ? 'Crédits' : 'Credits' }}</span><b>{{ money(report.data.remainingCreditMinor) }}</b></button><button class="metric-card"><span>{{ fr() ? 'Remboursé' : 'Refunded' }}</span><b>{{ money(report.data.refundedMinor) }}</b></button></div><div class="split-grid"><div><h3>{{ fr() ? 'Par canal' : 'By channel' }}</h3><div class="mini-list">@for (channel of report.data.channels; track channel.channel) { <div><span><b>{{ channel.channel }}</b><small>{{ channel.paymentCount }} payments · {{ money(channel.creditMinor) }} credit</small></span><strong>{{ money(channel.paymentMinor) }}</strong></div> }@empty { <div class="empty-inline">{{ fr() ? 'Aucun encaissement.' : 'No collections.' }}</div> }</div></div><div><h3>{{ fr() ? 'Variances caisse' : 'Cashier variances' }}</h3><div class="mini-list">@for (cashier of report.data.cashierVariances; track cashier.sourceId) { <div><span><b>{{ cashier.status }}</b><small>{{ cashier.openedOn }} · {{ cashier.cashierUserId }}</small></span><strong [class.danger-text]="cashier.varianceMinor !== 0">{{ money(cashier.varianceMinor) }}</strong></div> }@empty { <div class="empty-inline">{{ fr() ? 'Aucune session caisse.' : 'No cashier sessions.' }}</div> }</div></div></div>@if (report.data.exceptions.length) { <div class="exception-box"><strong>{{ fr() ? 'Écarts détectés' : 'Detected mismatches' }}</strong>@for (item of report.data.exceptions; track item.code) { <p><b>{{ item.code }}</b> · {{ item.message }}</p> }</div> }<div id="report-sources" class="table-wrap"><table><thead><tr><th>Student</th><th>Channel / status</th><th>Date</th><th>Received</th><th>Allocated / credit</th></tr></thead><tbody>@for (row of report.data.rows; track row.sourceId) { <tr><td><strong>{{ row.studentName }}</strong><small>{{ row.sourceId }} · {{ row.receiptNo || 'No receipt' }}</small></td><td>{{ row.channel }}<small>{{ row.status }} · {{ row.reference || 'No reference' }}</small></td><td>{{ row.paymentDate }}</td><td><strong>{{ money(row.amountMinor) }}</strong></td><td>{{ money(row.allocatedMinor) }}<small>{{ money(row.remainingCreditMinor) }} credit</small></td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucun paiement posté pour ce contexte.' : 'No posted payments for this context.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('documents') {
                @if (documents(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Documents financiers' : 'Finance documents' }}</div><h2>{{ fr() ? 'Factures & reçus' : 'Invoices & receipts' }}</h2><p>{{ fr() ? 'Statuts issus des snapshots immuables et de leurs dates d’émission.' : 'Statuses are read from immutable snapshots and issue dates.' }}</p></div></div><div class="kpi-grid"><div class="metric-card"><span>{{ fr() ? 'Factures' : 'Invoices' }}</span><b>{{ report.data.invoiceCount }}</b><small>{{ money(report.data.invoiceTotalMinor) }}</small></div><div class="metric-card"><span>{{ fr() ? 'Solde factures' : 'Invoice balance' }}</span><b>{{ money(report.data.invoiceOutstandingMinor) }}</b></div><div class="metric-card"><span>{{ fr() ? 'Reçus' : 'Receipts' }}</span><b>{{ report.data.receiptCount }}</b></div><div class="metric-card"><span>{{ fr() ? 'Reçu total' : 'Receipt total' }}</span><b>{{ money(report.data.receiptTotalMinor) }}</b></div></div><div class="chip-list">@for (status of report.data.statuses; track status.type + status.status) { <span class="status-chip"><b>{{ status.type }} · {{ status.status }}</b><small>{{ status.count }} · {{ money(status.amountMinor) }}</small></span> }</div><div class="table-wrap"><table><thead><tr><th>{{ fr() ? 'Document' : 'Document' }}</th><th>{{ fr() ? 'Étudiant / destinataire' : 'Student / recipient' }}</th><th>{{ fr() ? 'Date' : 'Date' }}</th><th>{{ fr() ? 'Montant' : 'Amount' }}</th><th>{{ fr() ? 'État' : 'Status' }}</th></tr></thead><tbody>@for (row of report.data.rows; track row.sourceId) { <tr><td><strong>{{ row.number }}</strong><small>{{ row.type }} · {{ row.sourceId }}</small></td><td>{{ row.studentName }}<small>{{ row.recipient }}</small></td><td>{{ row.issueDate }}</td><td><strong>{{ money(row.amountMinor) }}</strong><small>{{ money(row.outstandingMinor) }} balance</small></td><td><span class="status-badge">{{ row.status }}</span></td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucun document dans ce contexte.' : 'No documents in this context.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('expenses') {
                @if (expenses(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Dépenses' : 'Expenses' }}</div><h2>{{ fr() ? 'Dépenses & exceptions de source' : 'Expenses & source exceptions' }}</h2><p>{{ report.data.legacyAdapter ? (fr() ? 'Adaptateur legacy visible : les lignes historiques n’ont pas de statut POSTED propre.' : 'Legacy adapter visible: historical rows have no independent POSTED status.') : 'Posted source rows' }}</p></div><span class="formula-chip" [class.warn-chip]="report.data.legacyAdapter">{{ report.data.legacyAdapter ? 'LEGACY ADAPTER' : 'POSTED' }}</span></div><div class="kpi-grid"><div class="metric-card"><span>{{ fr() ? 'Dépenses postées' : 'Posted expenses' }}</span><b>{{ money(report.data.postedExpenseMinor) }}</b></div><div class="metric-card"><span>{{ fr() ? 'Lignes legacy' : 'Legacy rows' }}</span><b>{{ report.data.expenseCount }}</b></div></div>@if (report.data.exceptions.length) { <div class="exception-box">@for (item of report.data.exceptions; track item.code) { <p><b>{{ item.code }}</b> · {{ item.message }}</p> }</div> }<div class="table-wrap"><table><thead><tr><th>Source</th><th>{{ fr() ? 'Catégorie / libellé' : 'Category / label' }}</th><th>{{ fr() ? 'Date' : 'Date' }}</th><th>XAF</th><th>{{ fr() ? 'Base' : 'Basis' }}</th></tr></thead><tbody>@for (row of report.data.rows; track row.sourceId) { <tr><td>{{ row.sourceId }}</td><td><strong>{{ row.category }}</strong><small>{{ row.label }}</small></td><td>{{ row.spentOn }}</td><td><strong>{{ money(row.amountMinor) }}</strong></td><td>{{ row.status }}</td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucune dépense dans la période.' : 'No expenses in this period.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('payroll') {
                @if (!canPayroll()) { <section class="blocked-state"><strong>{{ fr() ? 'Rapport paie protégé' : 'Payroll report is protected' }}</strong><p>{{ fr() ? 'Le droit PAYROLL_VIEW est requis pour consulter les détails et les exports paie.' : 'PAYROLL_VIEW is required to see payroll details and exports.' }}</p></section> }
                @if (canPayroll() && payroll(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Confidentiel RH' : 'HR confidential' }}</div><h2>{{ fr() ? 'Paie : brut, retenues, net' : 'Payroll: gross, deductions, net' }}</h2><p>{{ fr() ? 'Vue agrégée protégée par PAYROLL_VIEW; les bulletins restent dans Finance / Paie.' : 'Aggregated view protected by PAYROLL_VIEW; payslips remain in Finance / Payroll.' }}</p></div><span class="formula-chip">PAYROLL_VIEW</span></div><div class="kpi-grid"><div class="metric-card"><span>Gross</span><b>{{ money(report.data.grossMinor) }}</b></div><div class="metric-card"><span>Deductions</span><b>{{ money(report.data.deductionMinor) }}</b></div><div class="metric-card"><span>Net</span><b>{{ money(report.data.netMinor) }}</b></div><div class="metric-card"><span>Paid</span><b>{{ money(report.data.paidMinor) }}</b></div></div><div class="table-wrap"><table><thead><tr><th>Run / period</th><th>Status</th><th>Employees</th><th>Gross</th><th>Net / paid</th></tr></thead><tbody>@for (row of report.data.runs; track row.sourceId) { <tr><td><strong>{{ row.periodCode }}</strong><small>{{ row.startDate }} → {{ row.endDate }} · {{ row.sourceId }}</small></td><td>{{ row.status }}</td><td>{{ row.employeeCount }}<small>{{ row.exceptionCount }} exceptions</small></td><td>{{ money(row.grossMinor) }}</td><td><strong>{{ money(row.netMinor) }}</strong><small>{{ money(row.paidMinor) }} paid</small></td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucun run payé/calculé.' : 'No calculated or paid runs.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('accounting') {
                @if (accounting(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'Journaux postés' : 'Posted journals' }}</div><h2>{{ fr() ? 'Balance, grand livre, résultat' : 'Trial balance, ledger, income statement' }}</h2><p>{{ fr() ? 'Débits = crédits est vérifié au as-of sélectionné.' : 'Debits = credits is checked at the selected as-of date.' }}</p></div><span class="formula-chip" [class.warn-chip]="!report.data.trialBalance.balanced">{{ report.data.trialBalance.balanced ? '✓ TB balanced' : '! TB mismatch' }}</span></div><div class="kpi-grid"><div class="metric-card"><span>Debits</span><b>{{ money(report.data.trialBalance.debitMinor) }}</b></div><div class="metric-card"><span>Credits</span><b>{{ money(report.data.trialBalance.creditMinor) }}</b></div><div class="metric-card"><span>{{ fr() ? 'Résultat' : 'Net income' }}</span><b>{{ money(report.data.incomeStatement.netMinor) }}</b></div><div class="metric-card"><span>{{ fr() ? 'Comptes' : 'Accounts' }}</span><b>{{ report.data.trialBalance.accountCount }}</b></div></div>@if (report.data.exceptions.length) { <div class="exception-box">@for (item of report.data.exceptions; track item.code) { <p><b>{{ item.code }}</b> · {{ item.message }}</p> }</div> }<div class="split-grid"><div><h3>{{ fr() ? 'Balance de vérification' : 'Trial balance' }}</h3><div class="table-wrap compact-table"><table><thead><tr><th>Code</th><th>Type</th><th>Debit</th><th>Credit</th></tr></thead><tbody>@for (row of report.data.trialBalance.rows; track row.accountId) { <tr><td><strong>{{ row.code }}</strong><small>{{ row.name }}</small></td><td>{{ row.type }}</td><td>{{ money(row.debitMinor) }}</td><td>{{ money(row.creditMinor) }}</td></tr> }</tbody></table></div></div><div><h3>{{ fr() ? 'Compte de résultat' : 'Income statement' }}</h3><div class="mini-list">@for (row of report.data.incomeStatement.rows; track row.accountId) { <div><span><b>{{ row.code }}</b><small>{{ row.name }} · {{ row.type }}</small></span><strong>{{ money(row.amountMinor) }}</strong></div> }@empty { <div class="empty-inline">{{ fr() ? 'Aucun compte de produit/charge.' : 'No revenue/expense accounts.' }}</div> }</div></div></div><h3>{{ fr() ? 'Grand livre source' : 'Source general ledger' }}</h3><div class="table-wrap"><table><thead><tr><th>Journal</th><th>Date</th><th>Compte</th><th>Debit</th><th>Credit</th><th>Running</th></tr></thead><tbody>@for (row of report.data.ledger; track row.sourceId + row.accountCode) { <tr><td><strong>{{ row.number }}</strong><small>{{ row.sourceType || '—' }} · {{ row.description }}</small></td><td>{{ row.entryDate }}</td><td>{{ row.accountCode }}</td><td>{{ money(row.debitMinor) }}</td><td>{{ money(row.creditMinor) }}</td><td>{{ money(row.runningBalanceMinor) }}</td></tr> }@empty { <tr><td colspan="6"><div class="empty-inline">{{ fr() ? 'Aucun journal posté dans la période.' : 'No posted journals in this period.' }}</div></td></tr> }</tbody></table></div></section> }
              }
              @case ('reconciliation') {
                @if (reconciliation(); as report) { <section class="report-card"><ng-container *ngTemplateOutlet="meta; context: {$implicit: report.meta}"></ng-container><div class="report-heading"><div><div class="eyebrow">{{ fr() ? 'File de contrôle' : 'Control queue' }}</div><h2>{{ fr() ? 'Exceptions de rapprochement' : 'Reconciliation exceptions' }}</h2><p>{{ fr() ? 'Les écarts restent visibles et portent leur action de correction.' : 'Mismatches remain visible with a corrective action.' }}</p></div><span class="formula-chip" [class.warn-chip]="report.data.openCount > 0">{{ report.data.openCount }} open</span></div><div class="kpi-grid"><div class="metric-card"><span>{{ fr() ? 'Ouverts' : 'Open' }}</span><b>{{ report.data.openCount }}</b></div><div class="metric-card"><span>{{ fr() ? 'Écart' : 'Mismatch' }}</span><b>{{ money(report.data.mismatchMinor) }}</b></div></div><div class="table-wrap"><table><thead><tr><th>Source</th><th>{{ fr() ? 'Attendu' : 'Expected' }}</th><th>{{ fr() ? 'Posté' : 'Actual' }}</th><th>{{ fr() ? 'État' : 'State' }}</th><th>{{ fr() ? 'Action' : 'Action' }}</th></tr></thead><tbody>@for (row of report.data.rows; track row.sourceId) { <tr><td><strong>{{ row.sourceType }}</strong><small>{{ row.sourceId }} · {{ row.sourceReference || '—' }}</small></td><td>{{ money(row.expectedMinor) }}</td><td>{{ money(row.actualMinor) }}</td><td><span class="status-badge">{{ row.state }}</span><small>{{ row.reason }}</small></td><td><a class="text-link" [href]="row.actionLink || '/finance/accounting'">{{ fr() ? 'Ouvrir' : 'Open' }}</a></td></tr> }@empty { <tr><td colspan="5"><div class="empty-inline">{{ fr() ? 'Aucune exception dans ce contexte.' : 'No exceptions in this context.' }}</div></td></tr> }</tbody></table></div></section> }
              }
            }
          </main>
        }
      }

      @if (exportOpen()) { <div class="drawer-backdrop" (click)="exportOpen.set(false)"><aside class="export-drawer" role="dialog" aria-modal="true" aria-label="Report export" (click)="$event.stopPropagation()"><div class="drawer-heading"><div><div class="eyebrow">{{ fr() ? 'Export serveur' : 'Server export' }}</div><h2>{{ fr() ? 'Télécharger ce rapport' : 'Download this report' }}</h2><p>{{ fr() ? 'Les filtres actuels et la date de génération seront inscrits dans le fichier.' : 'Current filters and generated time are included in the file.' }}</p></div><button type="button" class="icon-button" (click)="exportOpen.set(false)">×</button></div><div class="export-summary"><span>{{ fr() ? 'Rapport' : 'Report' }}<b>{{ reportTitle() }}</b></span><span>{{ fr() ? 'Session' : 'Session' }}<b>{{ selectedSessionLabel() || '—' }}</b></span><span>{{ fr() ? 'Lignes visibles' : 'Visible rows' }}<b>{{ currentRowCount() }}</b></span></div>@if (tab() === 'payroll') { <div class="warning-note">{{ fr() ? 'Données paie sensibles : le droit PAYROLL_VIEW et FINANCE_EXPORT sont contrôlés.' : 'Sensitive payroll data: PAYROLL_VIEW and FINANCE_EXPORT are enforced.' }}</div> }<label>{{ fr() ? 'Format' : 'File type' }}<select class="field" [(ngModel)]="exportFormat"><option value="csv">CSV</option><option value="pdf">PDF</option></select></label><div class="inline-actions"><button type="button" class="secondary-button" (click)="exportOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="primary-button" [disabled]="exportBusy() || !hasContext()" (click)="downloadExport()">{{ exportBusy() ? '…' : (fr() ? 'Générer le fichier' : 'Generate file') }}</button></div></aside></div> }
    </div>

    <ng-template #meta let-value><div class="report-meta"><span>{{ value.refreshStatus }} · {{ value.dataThrough | date:'medium' }}</span><span>{{ fr() ? 'Contexte' : 'Context' }}: {{ value.sessionCode || 'all dates' }} · {{ value.fromDate }} → {{ value.toDate }} · as-of {{ value.asOfDate }}</span><small title="Source basis">{{ value.sourceBasis }} · lag {{ value.lagSeconds }}s</small></div></ng-template>
  `,
})
export class FinanceReportsComponent implements OnInit {
  private readonly api = inject(FinanceReportsApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly fr = () => this.i18n.lang() === 'fr';
  protected readonly context = signal<ReportContext | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly exportOpen = signal(false);
  protected readonly exportBusy = signal(false);
  protected exportFormat: 'csv' | 'pdf' = 'csv';
  protected readonly tab = signal<ReportTab>('receivables');
  protected readonly receivables = signal<ReportEnvelope<ReceivablesReport> | null>(null);
  protected readonly collections = signal<ReportEnvelope<CollectionsReport> | null>(null);
  protected readonly documents = signal<ReportEnvelope<DocumentsReport> | null>(null);
  protected readonly expenses = signal<ReportEnvelope<ExpensesReport> | null>(null);
  protected readonly payroll = signal<ReportEnvelope<PayrollReport> | null>(null);
  protected readonly accounting = signal<ReportEnvelope<AccountingReport> | null>(null);
  protected readonly reconciliation = signal<ReportEnvelope<ReconciliationReport> | null>(null);
  protected readonly tabs: { key: ReportTab; fr: string; en: string }[] = [
    { key: 'receivables', fr: 'Créances', en: 'Receivables' },
    { key: 'collections', fr: 'Collections', en: 'Collections' },
    { key: 'documents', fr: 'Documents', en: 'Documents' },
    { key: 'expenses', fr: 'Dépenses', en: 'Expenses' },
    { key: 'payroll', fr: 'Paie', en: 'Payroll' },
    { key: 'accounting', fr: 'Comptabilité', en: 'Accounting' },
    { key: 'reconciliation', fr: 'Rapprochement', en: 'Reconciliation' },
  ];
  protected filters: ReportFilters = { academicSessionId: '', fromDate: '', toDate: '', asOfDate: '', classId: '', level: '', feeTypeCode: '', channelCode: '', status: '', limit: 500, offset: 0 };

  ngOnInit(): void {
    this.readUrlFilters();
    this.api.context().subscribe({ next: (options) => { this.context.set(options); this.seedSession(options); this.load(); }, error: (err) => this.fail(err) });
  }

  protected hasContext(): boolean { return !!this.filters.academicSessionId && !!this.filters.fromDate && !!this.filters.toDate && !!this.filters.asOfDate; }
  protected canPayroll(): boolean { return this.auth.can('hr', 'read'); }
  protected selectedSessionLabel(): string {
    const session = this.context()?.sessions.find(item => item.id === this.filters.academicSessionId);
    return session ? `${session.code} · ${session.label}` : '';
  }
  protected setTab(tab: ReportTab): void { this.tab.set(tab); this.error.set(null); this.load(); }
  protected sessionChanged(): void {
    const session = this.context()?.sessions.find(item => item.id === this.filters.academicSessionId);
    if (session) { this.filters.fromDate = session.startDate; this.filters.toDate = session.endDate; this.filters.asOfDate = session.endDate; }
  }
  protected applyFilters(): void { this.persistUrl(); this.load(); }
  protected resetFilters(): void {
    const current = this.context()?.sessions.find(item => item.current) || this.context()?.sessions[0];
    this.filters = { academicSessionId: current?.id || '', fromDate: current?.startDate || '', toDate: current?.endDate || '', asOfDate: current?.endDate || '', classId: '', level: '', feeTypeCode: '', channelCode: '', status: '', limit: 500, offset: 0 };
    this.persistUrl(); this.load();
  }
  protected load(): void {
    this.success.set(null); this.error.set(null);
    if (!this.hasContext()) { this.loading.set(false); return; }
    if (this.tab() === 'payroll' && !this.canPayroll()) { this.loading.set(false); return; }
    this.loading.set(true);
    const done = <T>(target: { set: (value: ReportEnvelope<T>) => void }) => ({ next: (value: ReportEnvelope<T>) => { target.set(value); this.loading.set(false); }, error: (err: unknown) => this.fail(err) });
    switch (this.tab()) {
      case 'receivables': this.api.receivables(this.filters).subscribe(done(this.receivables)); break;
      case 'collections': this.api.collections(this.filters).subscribe(done(this.collections)); break;
      case 'documents': this.api.documents(this.filters).subscribe(done(this.documents)); break;
      case 'expenses': this.api.expenses(this.filters).subscribe(done(this.expenses)); break;
      case 'payroll': this.api.payroll(this.filters).subscribe(done(this.payroll)); break;
      case 'accounting': this.api.accounting(this.filters).subscribe(done(this.accounting)); break;
      case 'reconciliation': this.api.reconciliation(this.filters).subscribe(done(this.reconciliation)); break;
    }
  }
  protected downloadExport(): void {
    this.exportBusy.set(true); this.error.set(null);
    this.api.export(this.tab(), this.filters, this.exportFormat).subscribe({ next: (blob) => { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `finance-${this.tab()}.${this.exportFormat}`; anchor.click(); URL.revokeObjectURL(url); this.exportBusy.set(false); this.exportOpen.set(false); this.success.set(this.fr() ? 'Export généré avec le contexte appliqué.' : 'Export generated with the applied context.'); }, error: (err) => { this.exportBusy.set(false); this.fail(err); } });
  }
  protected reportTitle(): string { return this.tabs.find(item => item.key === this.tab())?.[this.fr() ? 'fr' : 'en'] || this.tab(); }
  protected currentRowCount(): number {
    switch (this.tab()) { case 'receivables': return this.receivables()?.data.rows.length || 0; case 'collections': return this.collections()?.data.rows.length || 0; case 'documents': return this.documents()?.data.rows.length || 0; case 'expenses': return this.expenses()?.data.rows.length || 0; case 'payroll': return this.payroll()?.data.runs.length || 0; case 'accounting': return this.accounting()?.data.ledger.length || 0; case 'reconciliation': return this.reconciliation()?.data.rows.length || 0; }
  }
  protected money(value: number): string { return `${Math.round(value || 0).toLocaleString('fr-FR')} XAF`; }
  protected bucketLabel(bucket: string): string { return bucket === 'CURRENT' ? (this.fr() ? 'Courant' : 'Current') : bucket.replace('_', '–').replace('PLUS', '+'); }
  protected barWidth(value: number, total: number): number { return total <= 0 ? 0 : Math.min(100, Math.max(2, (value / total) * 100)); }
  protected focusSources(): void { document.getElementById('report-sources')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  private seedSession(options: ReportContext): void {
    if (!this.filters.academicSessionId) { const current = options.sessions.find(item => item.current) || options.sessions[0]; if (current) { this.filters.academicSessionId = current.id; this.filters.fromDate = this.filters.fromDate || current.startDate; this.filters.toDate = this.filters.toDate || current.endDate; this.filters.asOfDate = this.filters.asOfDate || current.endDate; } }
  }
  private readUrlFilters(): void {
    const query = this.route.snapshot.queryParamMap;
    for (const key of ['academicSessionId', 'fromDate', 'toDate', 'asOfDate', 'classId', 'level', 'feeTypeCode', 'channelCode', 'status'] as const) this.filters[key] = query.get(key) || '';
    const requested = query.get('report') as ReportTab | null; if (requested && this.tabs.some(item => item.key === requested)) this.tab.set(requested);
  }
  private persistUrl(): void { this.router.navigate([], { relativeTo: this.route, queryParams: { ...this.filters, report: this.tab() }, queryParamsHandling: 'merge', replaceUrl: true }); }
  private fail(err: unknown): void { this.loading.set(false); const response = err as HttpErrorResponse & { error?: FinanceReportApiError['error'] }; this.error.set(response?.error?.message || (err instanceof HttpErrorResponse && err.status === 403 ? (this.fr() ? 'Droit insuffisant pour ce rapport.' : 'Insufficient permission for this report.') : (this.fr() ? 'Vérifiez le contexte et réessayez.' : 'Check the context and retry.'))); }
}
