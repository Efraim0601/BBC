import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  AccountingPeriodOption, FinancePayrollApi, PayrollAccountOption, PayrollApiError, PayrollComponent,
  PayrollEmployee, PayrollPaymentOption, PayrollPeriod, PayrollPreview, PayrollRun, PayrollRunDetail,
  PayrollTreasuryOption, Payslip, PayslipJob, PayslipJobResult,
} from './finance-payroll.api';

type PayrollTab = 'runs' | 'new' | 'components' | 'payslips';

const today = () => new Date().toISOString().slice(0, 10);
const newKey = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

@Component({
  selector: 'bbc-finance-payroll',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './finance-payroll.scss',
  template: `
    <div class="payroll-shell">
      <header class="payroll-hero">
        <div>
          <div class="eyebrow">Finance / {{ fr() ? 'Paie opérationnelle' : 'Operational payroll' }} <span>· XAF entier</span></div>
          <h1>{{ fr() ? 'Runs de paie & bulletins' : 'Payroll runs & payslips' }}</h1>
          <p>{{ fr() ? 'Calculez, faites relire, approuvez puis payez avec un instantané immuable.' : 'Calculate, review, approve and pay from an immutable snapshot.' }}</p>
        </div>
        <div class="hero-actions">
          <span class="context-pill">{{ canWrite() ? (fr() ? 'Droits opérationnels' : 'Operational access') : (fr() ? 'Lecture seule' : 'Read only') }}</span>
          <a routerLink="/staff" class="secondary-button">{{ fr() ? 'Salaires dans Staff' : 'Salary summary in Staff' }}</a>
        </div>
      </header>

      <nav class="payroll-tabs" aria-label="Payroll sections">
        @for (item of tabs; track item.key) {
          <button type="button" [class.active-tab]="tab() === item.key" (click)="setTab(item.key)">
            {{ fr() ? item.fr : item.en }}
          </button>
        }
      </nav>

      @if (error()) {
        <div class="state state-error" role="alert"><div><strong>{{ fr() ? 'Impossible de charger la paie' : 'Payroll could not load' }}</strong><p>{{ error() }}</p></div><button type="button" class="text-button" (click)="load()">{{ fr() ? 'Réessayer' : 'Retry' }}</button></div>
      }
      @if (success()) { <div class="state state-success" role="status">{{ success() }}</div> }
      @if (loading()) { <div class="loading-state" aria-label="Loading"><span></span><span></span><span></span></div> }

      @if (!loading() && !error()) {
        @switch (tab()) {
          @case ('runs') {
            <section class="workspace-card">
              <div class="section-header"><div><div class="eyebrow">{{ fr() ? 'Cycle de contrôle' : 'Control cycle' }}</div><h2>{{ fr() ? 'Périodes et runs' : 'Periods and runs' }}</h2><p>{{ fr() ? 'Chaque ligne indique le snapshot, les exceptions et le total net.' : 'Each row shows the snapshot, exceptions and net total.' }}</p></div><button type="button" class="primary-button" [disabled]="!canWrite()" (click)="setTab('new')">+ {{ fr() ? 'Nouveau run' : 'New run' }}</button></div>
              @if (!runs().length) { <div class="empty-state"><strong>{{ fr() ? 'Aucun run de paie' : 'No payroll runs yet' }}</strong><p>{{ fr() ? 'Créez une période puis lancez le premier calcul.' : 'Create a period and start the first calculation.' }}</p><button type="button" class="primary-button" [disabled]="!canWrite()" (click)="setTab('new')">{{ fr() ? 'Commencer' : 'Start first run' }}</button></div> }
              @else {
                <div class="run-list">
                  @for (run of runs(); track run.id) {
                    <button type="button" class="run-row" [class.selected-row]="selectedRun()?.run?.id === run.id" (click)="selectRun(run)">
                      <span class="run-main"><strong>{{ periodLabel(run.payrollPeriodId) }}</strong><small>Run #{{ run.runNumber }} · {{ run.calculatedAt || (fr() ? 'Non calculé' : 'Not calculated') }}</small></span>
                      <span class="status-badge" [class]="statusClass(run.status)">{{ run.status }}</span>
                      <span class="run-metric"><small>{{ fr() ? 'Employés' : 'Employees' }}</small><b>{{ run.employeeCount }}</b></span>
                      <span class="run-metric" [class.warn]="run.exceptionCount > 0"><small>{{ fr() ? 'Exceptions' : 'Exceptions' }}</small><b>{{ run.exceptionCount }}</b></span>
                      <span class="run-metric money"><small>{{ fr() ? 'Net' : 'Net' }}</small><b>{{ money(run.netMinor) }}</b></span>
                    </button>
                  }
                </div>
              }
            </section>
            @if (selectedRun(); as detail) {
              <section class="workspace-card detail-card">
                <div class="section-header sticky-header"><div><div class="eyebrow">{{ detail.period.code }} · {{ detail.period.startDate }} → {{ detail.period.endDate }}</div><h2>Run #{{ detail.run.runNumber }} <span class="status-badge" [class]="statusClass(detail.run.status)">{{ detail.run.status }}</span></h2><p>{{ fr() ? 'Le calcul est verrouillé après approbation. Les paiements et bulletins restent liés à ce snapshot.' : 'Calculation locks after approval. Payments and payslips remain linked to this snapshot.' }}</p></div><button type="button" class="secondary-button" (click)="selectedRun.set(null)">{{ fr() ? 'Fermer' : 'Close' }}</button></div>
                <div class="totals-grid"><div><span>Gross / Brut</span><b>{{ money(detail.run.grossMinor) }}</b></div><div><span>Deductions / Retenues</span><b>{{ money(detail.run.deductionMinor) }}</b></div><div class="total-highlight"><span>Net / Net à payer</span><b>{{ money(detail.run.netMinor) }}</b></div><div><span>Employer cost / Coût employeur</span><b>{{ money(detail.run.employerCostMinor) }}</b></div></div>
                @if (detail.run.exceptionCount > 0) { <div class="blocked-box"><strong>{{ detail.run.exceptionCount }} {{ fr() ? 'exception(s) bloque(nt) la revue' : 'exception(s) block review' }}</strong><p>{{ fr() ? 'Corrigez les données source ou ouvrez une ligne pour son motif précis.' : 'Fix source data or open a row for its exact reason.' }}</p></div> }
                <div class="action-bar">
                  @if (detail.run.status === 'DRAFT' || detail.run.status === 'CALCULATED') { <button type="button" class="primary-button" [disabled]="busy() || !canWrite()" (click)="calculate(detail.run)">{{ fr() ? 'Calculer / recalculer' : 'Calculate / recalculate' }}</button> }
                  @if (detail.run.status === 'CALCULATED') { <button type="button" class="secondary-button" [disabled]="busy() || !canWrite()" (click)="review(detail.run)">{{ fr() ? 'Soumettre à la revue' : 'Submit for review' }}</button> }
                  @if (detail.run.status === 'REVIEWED') { <button type="button" class="primary-button" [disabled]="busy() || !canWrite()" (click)="approve(detail.run)">{{ fr() ? 'Approuver & journaliser' : 'Approve & accrue' }}</button> }
                  @if (detail.run.status === 'APPROVED') { <button type="button" class="primary-button" [disabled]="busy() || !canWrite()" (click)="openPay()">{{ fr() ? 'Payer le run' : 'Pay run' }}</button> }
                  @if (detail.run.status === 'PAID') { <button type="button" class="secondary-button" [disabled]="busy()" (click)="loadPayslips()">{{ fr() ? 'Voir les bulletins' : 'View payslips' }}</button> }
                  @if (detail.run.status !== 'VOID' && canWrite()) { <button type="button" class="secondary-button void-button" [disabled]="busy()" (click)="openVoid()">{{ fr() ? 'Annuler / renverser' : 'Void / reverse' }}</button> }
                  @if (voidOpen()) { <div class="void-panel" role="dialog" aria-label="Payroll void confirmation"><strong>{{ fr() ? 'Annulation contrôlée' : 'Controlled void' }}</strong><p>{{ fr() ? 'Un motif est requis. Les journaux déjà postés seront renversés; les snapshots historiques restent conservés.' : 'A reason is required. Posted journals will be reversed; historical snapshots remain retained.' }}</p><label>{{ fr() ? 'Motif obligatoire' : 'Required reason' }} *<textarea class="field" rows="2" [(ngModel)]="voidReason"></textarea></label><div class="inline-actions"><button type="button" class="secondary-button" (click)="voidOpen.set(false)">{{ fr() ? 'Fermer' : 'Cancel' }}</button><button type="button" class="primary-button" [disabled]="busy() || voidReason.trim().length < 3" (click)="voidRun(detail.run)">{{ fr() ? 'Confirmer l’annulation' : 'Confirm void' }}</button></div></div> }
                  @if (detail.run.calculationSnapshotHash) { <span class="hash-note">Snapshot {{ detail.run.calculationSnapshotHash.slice(0, 12) }}…</span> }
                </div>
              @if (payOpen()) { <div class="pay-panel"><h3>{{ fr() ? 'Paiement maker-safe' : 'Maker-safe payment' }}</h3><p>{{ fr() ? 'Le compte sélectionné est le compte bancaire ou la caisse réellement débitée.' : 'Select the actual bank or cash account that will be debited.' }}</p><div class="form-grid"><label>Channel / Canal *<select class="field" [(ngModel)]="payChannelId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (channel of paymentOptions()?.channels || []; track channel.id) { <option [value]="channel.id">{{ channel.code }} · {{ fr() ? channel.labelFr : channel.labelEn }}{{ channel.requiresReference ? ' · ref.' : '' }}</option> }</select></label><label>{{ fr() ? 'Compte de trésorerie débité' : 'Treasury account debited' }} *<select class="field" [(ngModel)]="payAccountId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (account of paymentOptions()?.treasuryAccounts || []; track account.id) { <option [value]="account.id">{{ account.displayName }} · {{ account.balanceMinor.toLocaleString('fr-FR') }} XAF</option> }</select></label><label>{{ fr() ? 'Date comptable' : 'Accounting date' }} *<input type="date" class="field" [(ngModel)]="payDate"></label><label>{{ fr() ? 'Référence globale' : 'Global reference' }} *<input class="field" [(ngModel)]="payReference" placeholder="PAYROLL-2026-08"></label></div><div class="inline-actions"><button type="button" class="secondary-button" (click)="payOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="primary-button" [disabled]="busy() || !payChannelId || !payAccountId || !payReference.trim()" (click)="pay(detail.run)">{{ fr() ? 'Confirmer le paiement' : 'Confirm payment' }}</button></div></div> }
                <div class="employee-table-wrap"><table class="employee-table"><thead><tr><th>{{ fr() ? 'Employé' : 'Employee' }}</th><th>{{ fr() ? 'Formule' : 'Formula' }}</th><th>{{ fr() ? 'Composants' : 'Components' }}</th><th>Net XAF</th><th>{{ fr() ? 'État' : 'State' }}</th><th></th></tr></thead><tbody>@for (employee of detail.employees; track employee.id) {<tr [class.exception-row]="employee.status === 'EXCEPTION'"><td><strong>{{ employee.employeeName }}</strong><small>{{ employee.employeeCode }} · {{ employee.employmentMode }}</small></td><td><span class="formula">{{ employee.formula || employee.exceptionMessage || '—' }}</span></td><td>{{ employee.lines.length }}</td><td class="money">{{ money(employee.netMinor) }}</td><td><span class="status-badge" [class]="statusClass(employee.status)">{{ employee.status }}</span></td><td><button type="button" class="text-button" (click)="selectEmployee(employee)">{{ fr() ? 'Ouvrir' : 'Open' }}</button></td></tr>}</tbody></table></div>
                @if (!detail.employees.length) { <div class="empty-state compact"><strong>{{ fr() ? 'Aucune ligne dans ce run' : 'No employee lines in this run' }}</strong></div> }
              </section>
            }
            @if (selectedEmployee(); as employee) { <aside class="drawer-card"><div class="section-header"><div><div class="eyebrow">{{ employee.employeeCode }}</div><h2>{{ employee.employeeName }}</h2><p>{{ employee.employmentMode }} · {{ employee.formula || employee.exceptionMessage || '—' }}</p></div><button type="button" class="secondary-button" (click)="selectedEmployee.set(null)">{{ fr() ? 'Fermer' : 'Close' }}</button></div><div class="drawer-total"><span>{{ fr() ? 'Net calculé' : 'Calculated net' }}</span><strong>{{ money(employee.netMinor) }}</strong></div><h3>{{ fr() ? 'Composants et ajustement' : 'Components and adjustment' }}</h3>@for (line of employee.lines; track line.id) { <div class="component-line"><span><b>{{ line.componentCode }}</b><small>{{ fr() ? line.componentNameFr : line.componentNameEn }} · {{ line.source }}</small></span><strong>{{ money(line.amountMinor) }}</strong></div> }<div class="adjust-box"><label>{{ fr() ? 'Composant à ajuster' : 'Component to adjust' }} *<select class="field" [(ngModel)]="adjustComponent"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (component of components(); track component.id) { <option [value]="component.code">{{ component.code }} · {{ fr() ? component.nameFr : component.nameEn }}</option> }</select></label><label>{{ fr() ? 'Nouveau montant XAF' : 'New amount XAF' }} *<input type="number" min="0" step="1" class="field" [(ngModel)]="adjustAmount"></label><label>{{ fr() ? 'Motif obligatoire' : 'Required reason' }} *<textarea class="field" rows="2" [(ngModel)]="adjustReason"></textarea></label><button type="button" class="primary-button" [disabled]="busy() || !canWrite() || !adjustComponent || adjustAmount < 0 || adjustReason.trim().length < 3" (click)="adjust(employee)">{{ fr() ? 'Enregistrer l’ajustement' : 'Save adjustment' }}</button></div></aside> }
          }
          @case ('new') {
            <section class="workspace-card wizard-card"><div class="section-header"><div><div class="eyebrow">{{ fr() ? 'Assistant en 4 étapes' : 'Four-step wizard' }}</div><h2>{{ fr() ? 'Préparer un run de paie' : 'Prepare a payroll run' }}</h2><p>{{ fr() ? 'La prévisualisation montre les employés éligibles et chaque blocage avant écriture.' : 'Preview eligibility and every blocker before writing anything.' }}</p></div><span class="step-badge">1 · 2 · 3 · 4</span></div><div class="form-grid"><label>{{ fr() ? 'Période de paie' : 'Payroll period' }} *<select class="field" [(ngModel)]="newPeriodId"><option value="">{{ fr() ? 'Choisir une période ouverte' : 'Choose an open period' }}</option>@for (period of periods(); track period.id) { <option [value]="period.id" [disabled]="period.status !== 'OPEN'">{{ period.code }} · {{ period.startDate }} → {{ period.endDate }}{{ period.status !== 'OPEN' ? ' · CLOSED' : '' }}</option> }</select></label><label>{{ fr() ? 'Proratisation' : 'Proration' }}<select class="field" [(ngModel)]="newProration"><option value="NONE">NONE · {{ fr() ? 'mois complet' : 'full month' }}</option><option value="DAILY">DAILY · {{ fr() ? 'jours actifs' : 'active days' }}</option></select></label><label>{{ fr() ? 'Heures par défaut' : 'Default hours' }}<input type="number" min="0" class="field" [(ngModel)]="newHours"><small>{{ fr() ? 'Les employés horaires utilisent leurs heures configurées, sinon cette valeur.' : 'Hourly employees use their configured hours, otherwise this value.' }}</small></label><label class="check-field"><input type="checkbox" [(ngModel)]="newSegregation"><span>{{ fr() ? 'Séparation des tâches obligatoire' : 'Segregation of duties required' }}<small>{{ fr() ? 'Le calculateur/réviseur ne pourra pas approuver.' : 'Calculator/reviewer cannot approve.' }}</small></span></label></div><div class="scope-note"><strong>{{ fr() ? 'Périmètre employés' : 'Employee scope' }}</strong><span>{{ fr() ? 'Tous les employés de l’établissement (le filtrage avancé reste côté données RH).' : 'All employees in the school (advanced filtering remains in HR data).' }}</span></div>@if (preview(); as result) {<div class="preview-card"><div class="preview-heading"><div><h3>{{ fr() ? 'Prévisualisation' : 'Preview' }}</h3><p>{{ result.employeeCount }} {{ fr() ? 'employés' : 'employees' }} · {{ result.eligibleCount }} {{ fr() ? 'éligibles' : 'eligible' }} · {{ result.exceptionCount }} {{ fr() ? 'exceptions' : 'exceptions' }}</p></div><strong>{{ money(result.netMinor) }}</strong></div><div class="preview-metrics"><span>Gross {{ money(result.grossMinor) }}</span><span>Deductions {{ money(result.deductionMinor) }}</span><span>Employer cost {{ money(result.employerCostMinor) }}</span></div>@if (result.blockers.length) {<div class="blocked-box"><strong>{{ fr() ? 'Blocages à résoudre avant calcul' : 'Blockers before calculation' }}</strong><ul>@for (blocker of result.blockers; track blocker.actionLink + blocker.code) { <li>{{ blocker.code }} · {{ blocker.message }}</li> }</ul></div>}<div class="employee-table-wrap"><table class="employee-table"><thead><tr><th>{{ fr() ? 'Employé' : 'Employee' }}</th><th>{{ fr() ? 'Éligibilité' : 'Eligibility' }}</th><th>{{ fr() ? 'Formule' : 'Formula' }}</th></tr></thead><tbody>@for (employee of result.employees; track employee.employeeId) {<tr><td>{{ employee.employeeName }} <small>{{ employee.employeeCode }}</small></td><td><span class="status-badge" [class]="employee.eligible ? 'status-ready' : 'status-exception'">{{ employee.eligible ? 'READY' : employee.exceptionCode }}</span></td><td>{{ employee.formula || employee.exceptionMessage || '—' }}</td></tr>}</tbody></table></div></div>}<div class="inline-actions"><button type="button" class="secondary-button" [disabled]="busy() || !newPeriodId" (click)="previewRun()">{{ fr() ? 'Prévisualiser' : 'Preview' }}</button><button type="button" class="primary-button" [disabled]="busy() || !newPeriodId || !preview() || preview()!.exceptionCount > 0 || !canWrite()" (click)="createRun()">{{ busy() ? '…' : (fr() ? 'Créer puis calculer' : 'Create & calculate') }}</button></div></section>
            @if (!periods().length) { <section class="workspace-card empty-state"><strong>{{ fr() ? 'Aucune période de paie' : 'No payroll period' }}</strong><p>{{ fr() ? 'Créez une période dans le formulaire ci-dessous avant de calculer.' : 'Create a period in the form below before calculating.' }}</p></section> }
            <section class="workspace-card period-form"><h2>{{ fr() ? 'Créer une période' : 'Create period' }}</h2><div class="form-grid"><label>Code *<input class="field" [(ngModel)]="periodCode" placeholder="2026-08"></label><label>{{ fr() ? 'Début' : 'Start' }} *<input type="date" class="field" [(ngModel)]="periodStart"></label><label>{{ fr() ? 'Fin' : 'End' }} *<input type="date" class="field" [(ngModel)]="periodEnd"></label><label>{{ fr() ? 'Date de paiement' : 'Payment date' }} *<input type="date" class="field" [(ngModel)]="periodPayment"></label><label>{{ fr() ? 'Période comptable' : 'Accounting period' }} *<select class="field" [(ngModel)]="periodAccountingId"><option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>@for (period of accountingPeriods(); track period.id) { <option [value]="period.id">{{ period.code }} · {{ period.startDate }} → {{ period.endDate }} · {{ period.status }}</option> }</select></label></div><button type="button" class="primary-button" [disabled]="busy() || !canWrite() || !periodCode.trim() || !periodStart || !periodEnd || !periodPayment || !periodAccountingId" (click)="createPeriod()">{{ fr() ? 'Créer la période' : 'Create period' }}</button></section>
          }
          @case ('components') {
            <section class="workspace-card"><div class="section-header"><div><div class="eyebrow">{{ fr() ? 'Catalogue configurable' : 'Configurable catalogue' }}</div><h2>{{ fr() ? 'Composants de paie' : 'Payroll components' }}</h2><p>{{ fr() ? 'Aucun taux fiscal n’est hard-codé. Les mappings comptables manquants bloquent la revue.' : 'No tax formula is hard-coded. Missing accounting mappings block review.' }}</p></div><button type="button" class="primary-button" [disabled]="!canWrite()" (click)="componentFormOpen.set(!componentFormOpen())">+ {{ fr() ? 'Nouveau composant' : 'New component' }}</button></div>@if (!components().length) {<div class="empty-state"><strong>{{ fr() ? 'Catalogue vide' : 'Catalogue is empty' }}</strong></div>} @else {<div class="component-grid">@for (component of components(); track component.id) {<article class="component-card"><div class="component-card-top"><strong>{{ component.code }}</strong><span class="status-badge" [class]="component.active ? 'status-ready' : 'status-muted'">{{ component.active ? 'ACTIVE' : 'INACTIVE' }}</span></div><h3>{{ fr() ? component.nameFr : component.nameEn }}</h3><p>{{ component.componentKind }} · {{ component.calculationMode }}</p><div class="component-value">{{ component.calculationMode === 'PERCENTAGE' ? (component.defaultRateBps / 100) + '%' : money(component.defaultAmountMinor) }}</div><small>{{ component.expenseAccountId ? 'Expense mapped' : 'Expense mapping missing' }} · {{ component.liabilityAccountId ? 'Liability mapped' : 'Liability mapping missing' }}</small><button type="button" class="text-button" (click)="editComponent(component)">{{ fr() ? 'Modifier' : 'Edit' }}</button></article>}</div> }@if (componentFormOpen()) {<div class="form-panel"><h3>{{ editingComponent ? (fr() ? 'Modifier le composant' : 'Edit component') : (fr() ? 'Nouveau composant' : 'New component') }}</h3><div class="form-grid"><label>Code *<input class="field" [class.invalid]="componentErrors['code']" [(ngModel)]="componentDraft.code" placeholder="TRANSPORT_ALLOWANCE"><small class="field-error">{{ componentErrors['code'] }}</small></label><label>{{ fr() ? 'Nom français' : 'French name' }} *<input class="field" [(ngModel)]="componentDraft.nameFr"><small class="field-error">{{ componentErrors['nameFr'] }}</small></label><label>{{ fr() ? 'Nom anglais' : 'English name' }} *<input class="field" [(ngModel)]="componentDraft.nameEn"></label><label>Kind / Nature *<select class="field" [(ngModel)]="componentDraft.componentKind"><option value="EARNING">EARNING</option><option value="DEDUCTION">DEDUCTION</option><option value="EMPLOYER_CONTRIBUTION">EMPLOYER_CONTRIBUTION</option></select></label><label>Mode *<select class="field" [(ngModel)]="componentDraft.calculationMode"><option value="FIXED">FIXED</option><option value="PERCENTAGE">PERCENTAGE</option><option value="HOURLY">HOURLY</option><option value="MANUAL">MANUAL</option></select></label><label>{{ fr() ? 'Montant XAF' : 'Amount XAF' }}<input type="number" min="0" class="field" [(ngModel)]="componentDraft.defaultAmountMinor"></label><label>{{ fr() ? 'Taux points de base' : 'Rate basis points' }}<input type="number" min="0" max="10000" class="field" [(ngModel)]="componentDraft.defaultRateBps"></label><label>{{ fr() ? 'Compte charge' : 'Expense account' }}<input class="field" [(ngModel)]="componentDraft.expenseAccountId" placeholder="UUID from accounting"></label><label>{{ fr() ? 'Compte passif' : 'Liability account' }}<input class="field" [(ngModel)]="componentDraft.liabilityAccountId" placeholder="UUID from accounting"></label><label>{{ fr() ? 'Effectif du' : 'Effective from' }}<input type="date" class="field" [(ngModel)]="componentDraft.effectiveFrom"></label><label>{{ fr() ? 'Effectif au' : 'Effective to' }}<input type="date" class="field" [(ngModel)]="componentDraft.effectiveTo"></label></div><p class="help-note">{{ fr() ? 'Les comptes seront remplacés par des sélecteurs de catalogue dans la prochaine UI. Utilisez la page Comptabilité pour obtenir les mappings validés.' : 'Account selectors will move to the accounting catalogue in the next UI pass. Use Accounting for validated mappings.' }}</p><div class="inline-actions"><button type="button" class="secondary-button" (click)="componentFormOpen.set(false)">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" class="primary-button" [disabled]="busy() || !canWrite()" (click)="saveComponent()">{{ fr() ? 'Enregistrer' : 'Save' }}</button></div></div>}</section>
          }
          @case ('payslips') {
            <section class="workspace-card"><div class="section-header"><div><div class="eyebrow">{{ fr() ? 'Documents server-side' : 'Server-side documents' }}</div><h2>{{ fr() ? 'Bulletins de paie' : 'Payslips' }}</h2><p>{{ fr() ? 'Chaque PDF est lié au snapshot, au hash et à l’historique de version.' : 'Each PDF is linked to its snapshot, hash and version history.' }}</p></div><button type="button" class="secondary-button" (click)="loadPayslips()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div>@if (!payslips().length) {<div class="empty-state"><strong>{{ fr() ? 'Aucun bulletin délivré' : 'No issued payslips' }}</strong><p>{{ fr() ? 'Les bulletins apparaîtront après un paiement réussi.' : 'Payslips appear after successful payment.' }}</p></div>} @else {<div class="employee-table-wrap"><table class="employee-table"><thead><tr><th>{{ fr() ? 'Numéro' : 'Number' }}</th><th>{{ fr() ? 'Employé' : 'Employee' }}</th><th>{{ fr() ? 'Version' : 'Version' }}</th><th>{{ fr() ? 'État' : 'Status' }}</th><th>{{ fr() ? 'Actions' : 'Actions' }}</th></tr></thead><tbody>@for (slip of payslips(); track slip.id) {<tr><td><strong>{{ slip.payslipNumber }}</strong><small>{{ slip.snapshotHash.slice(0, 12) }}…</small></td><td>{{ slip.employeeName || '—' }}</td><td>v{{ slip.versionNo }}</td><td><span class="status-badge" [class]="statusClass(slip.status)">{{ slip.status }}</span></td><td class="inline-actions"><button type="button" class="text-button" [disabled]="slip.status !== 'ISSUED'" (click)="downloadPayslip(slip)">{{ fr() ? 'Télécharger' : 'Download' }}</button><button type="button" class="text-button" [disabled]="busy() || slip.status !== 'ISSUED' || !canWrite()" (click)="regenerate(slip)">{{ fr() ? 'Nouvelle version' : 'Regenerate' }}</button></td></tr>}</tbody></table></div> }</section>
          }
        }
      }
    </div>
  `,
})
export class FinancePayrollComponent implements OnInit {
  private readonly api = inject(FinancePayrollApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  protected readonly fr = () => this.i18n.lang() === 'fr';
  protected readonly tabs: { key: PayrollTab; fr: string; en: string }[] = [
    { key: 'runs', fr: 'Périodes & runs', en: 'Periods & runs' },
    { key: 'new', fr: 'Nouveau run', en: 'New run' },
    { key: 'components', fr: 'Composants', en: 'Components' },
    { key: 'payslips', fr: 'Bulletins', en: 'Payslips' },
  ];
  protected readonly tab = signal<PayrollTab>('runs');
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly components = signal<PayrollComponent[]>([]);
  protected readonly periods = signal<PayrollPeriod[]>([]);
  protected readonly accountingPeriods = signal<AccountingPeriodOption[]>([]);
  protected readonly paymentOptions = signal<{ channels: PayrollPaymentOption[]; accounts: PayrollAccountOption[]; treasuryAccounts: PayrollTreasuryOption[] } | null>(null);
  protected readonly runs = signal<PayrollRun[]>([]);
  protected readonly selectedRun = signal<PayrollRunDetail | null>(null);
  protected readonly selectedEmployee = signal<PayrollEmployee | null>(null);
  protected readonly preview = signal<PayrollPreview | null>(null);
  protected readonly payslips = signal<Payslip[]>([]);
  protected readonly job = signal<PayslipJob | null>(null);
  protected readonly jobResults = signal<PayslipJobResult[]>([]);
  protected payOpen = signal(false);
  protected voidOpen = signal(false);
  protected componentFormOpen = signal(false);
  protected editingComponent: PayrollComponent | null = null;
  protected componentErrors: Record<string, string> = {};
  protected componentDraft: Partial<PayrollComponent> = this.emptyComponent();

  protected newPeriodId = '';
  protected newProration = 'NONE';
  protected newHours = 0;
  protected newSegregation = true;
  protected periodCode = '';
  protected periodStart = today().slice(0, 8) + '01';
  protected periodEnd = today();
  protected periodPayment = today();
  protected periodAccountingId = '';
  protected payChannelId = '';
  protected payAccountId = '';
  protected payDate = today();
  protected payReference = '';
  protected voidReason = '';
  protected adjustComponent = '';
  protected adjustAmount = 0;
  protected adjustReason = '';

  ngOnInit(): void { this.load(); }

  protected canWrite(): boolean { return this.auth.can('finance', 'write') || this.auth.can('hr', 'write'); }
  protected setTab(tab: PayrollTab): void { this.tab.set(tab); this.success.set(null); if (tab === 'payslips' && !this.payslips().length) this.loadPayslips(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    forkJoin({ components: this.api.components(), periods: this.api.periods(), accountingPeriods: this.api.accountingPeriods(), options: this.api.paymentOptions(), runs: this.api.runs(), payslips: this.api.payslips() }).subscribe({
      next: (value) => { this.components.set(value.components); this.periods.set(value.periods); this.accountingPeriods.set(value.accountingPeriods); this.paymentOptions.set(value.options); this.runs.set(value.runs); this.payslips.set(value.payslips); this.loading.set(false); },
      error: (err: unknown) => { this.loading.set(false); this.error.set(this.apiError(err)); },
    });
  }
  protected periodLabel(id: string): string { return this.periods().find(p => p.id === id)?.code || 'Payroll period'; }
  protected money(value: number): string { return `${Math.round(value || 0).toLocaleString('fr-FR')} XAF`; }
  protected statusClass(status: string): string { return ['READY', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAID', 'ISSUED', 'COMPLETED'].includes(status) ? 'status-ready' : ['EXCEPTION', 'GENERATION_FAILED', 'FAILED', 'VOID'].includes(status) ? 'status-exception' : 'status-muted'; }
  protected selectRun(run: PayrollRun): void { this.busy.set(true); this.api.detail(run.id).subscribe({ next: detail => { this.selectedRun.set(detail); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected selectEmployee(employee: PayrollEmployee): void { this.selectedEmployee.set(employee); this.adjustComponent = employee.lines[0]?.componentCode || ''; this.adjustAmount = employee.lines[0]?.amountMinor || 0; this.adjustReason = ''; }
  protected previewRun(): void { if (!this.newPeriodId) return; this.runRequest().subscribe({ next: value => { this.preview.set(value); this.error.set(null); }, error: err => this.error.set(this.apiError(err)) }); }
  protected createRun(): void {
    if (!this.preview() || this.preview()!.exceptionCount > 0) return;
    this.busy.set(true); this.api.createRun(this.runBody()).subscribe({
      next: detail => this.api.calculate(detail.run.id, newKey('payroll-calculate')).subscribe({ next: calculated => { this.selectedRun.set(calculated); this.runs.update(rows => [calculated.run, ...rows.filter(r => r.id !== calculated.run.id)]); this.success.set(this.fr() ? 'Run créé et calculé. La revue peut commencer.' : 'Run created and calculated. Review can begin.'); this.tab.set('runs'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }),
      error: err => { this.busy.set(false); this.error.set(this.apiError(err)); },
    });
  }
  protected review(run: PayrollRun): void { this.action(run, 'review'); }
  protected approve(run: PayrollRun): void { this.action(run, 'approve'); }
  private action(run: PayrollRun, type: 'review' | 'approve'): void {
    this.busy.set(true); const request = type === 'review' ? this.api.review(run.id, run.version, 'Validated by payroll workspace') : this.api.approve(run.id, run.version, 'Approved after maker-checker review');
    request.subscribe({ next: detail => { this.selectedRun.set(detail); this.replaceRun(detail.run); this.success.set(type === 'review' ? (this.fr() ? 'Run soumis à la revue.' : 'Run submitted for review.') : (this.fr() ? 'Run approuvé et journal d’accrual posté.' : 'Run approved and accrual journal posted.')); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } });
  }
  protected calculate(run: PayrollRun): void { this.busy.set(true); this.api.calculate(run.id, newKey('payroll-calculate')).subscribe({ next: detail => { this.selectedRun.set(detail); this.replaceRun(detail.run); this.success.set(this.fr() ? 'Calcul recalculé; les ajustements manuels compatibles sont conservés.' : 'Recalculated; compatible manual adjustments were retained.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected openPay(): void { this.payOpen.set(true); this.payDate = today(); this.payReference = `PAYROLL-${this.payDate.slice(0, 7)}`; const firstChannel = this.paymentOptions()?.channels.find(c => c.enabled); if (firstChannel) this.payChannelId = firstChannel.id; const firstAccount = this.paymentOptions()?.treasuryAccounts.find(a => a.kind === 'BANK') || this.paymentOptions()?.treasuryAccounts[0]; if (firstAccount) this.payAccountId = firstAccount.id; }
  protected openVoid(): void { this.voidOpen.set(true); this.voidReason = ''; }
  protected voidRun(run: PayrollRun): void { if (this.voidReason.trim().length < 3) return; this.busy.set(true); this.api.voidRun(run.id, run.version, this.voidReason, newKey('payroll-void')).subscribe({ next: detail => { this.selectedRun.set(detail); this.replaceRun(detail.run); this.voidOpen.set(false); this.success.set(this.fr() ? 'Run annulé; les journaux postés ont été renversés et les snapshots sont conservés.' : 'Run voided; posted journals were reversed and snapshots were retained.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected pay(run: PayrollRun): void { this.busy.set(true); this.api.pay(run.id, { paymentChannelId: this.payChannelId, treasuryAccountId: this.payAccountId, paymentDate: this.payDate, reference: this.payReference, employeeReferences: {}, version: run.version }, newKey('payroll-pay')).subscribe({ next: result => { this.payOpen.set(false); this.replaceRun({ ...run, status: 'PAID', paymentJournalId: run.paymentJournalId }); this.success.set(this.fr() ? `Paiement posté: ${result.paidCount} ligne(s). Les bulletins sont en génération server-side.` : `Payment posted: ${result.paidCount} row(s). Server-side payslips are generating.`); this.loadPayslips(); if (this.selectedRun()) this.selectRun(run); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected adjust(employee: PayrollEmployee): void { const detail = this.selectedRun(); if (!detail) return; this.busy.set(true); this.api.adjust({ employeePayrollId: employee.id, componentCode: this.adjustComponent, amountMinor: Math.round(this.adjustAmount), reason: this.adjustReason, version: employee.version }).subscribe({ next: updated => { this.selectedRun.set(updated); this.replaceRun(updated.run); this.selectedEmployee.set(updated.employees.find(row => row.id === employee.id) || null); this.success.set(this.fr() ? 'Ajustement enregistré avec motif et recalcul du net.' : 'Adjustment saved with reason and net recalculated.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected createPeriod(): void { this.busy.set(true); this.api.createPeriod({ code: this.periodCode, startDate: this.periodStart, endDate: this.periodEnd, paymentDate: this.periodPayment, accountingPeriodId: this.periodAccountingId }).subscribe({ next: period => { this.periods.update(rows => [period, ...rows]); this.newPeriodId = period.id; this.success.set(this.fr() ? 'Période de paie créée.' : 'Payroll period created.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected editComponent(component: PayrollComponent): void { this.editingComponent = component; this.componentDraft = { ...component }; this.componentErrors = {}; this.componentFormOpen.set(true); }
  protected saveComponent(): void { this.componentErrors = {}; const draft = this.componentDraft; if (!draft.code?.trim()) this.componentErrors['code'] = this.fr() ? 'Code obligatoire.' : 'Code is required.'; if (!draft.nameFr?.trim()) this.componentErrors['nameFr'] = this.fr() ? 'Nom obligatoire.' : 'Name is required.'; if (Object.keys(this.componentErrors).length) return; this.busy.set(true); const request = this.editingComponent ? this.api.updateComponent(this.editingComponent.id, draft) : this.api.createComponent(draft); request.subscribe({ next: component => { this.components.update(rows => this.editingComponent ? rows.map(row => row.id === component.id ? component : row) : [component, ...rows]); this.componentFormOpen.set(false); this.editingComponent = null; this.componentDraft = this.emptyComponent(); this.success.set(this.fr() ? 'Composant enregistré.' : 'Component saved.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  protected loadPayslips(): void { this.api.payslips().subscribe({ next: rows => this.payslips.set(rows), error: err => this.error.set(this.apiError(err)) }); }
  protected downloadPayslip(slip: Payslip): void { this.api.payslipPdf(slip.id).subscribe({ next: blob => this.download(blob, `${slip.payslipNumber}.pdf`), error: err => this.error.set(this.apiError(err)) }); }
  protected regenerate(slip: Payslip): void { this.busy.set(true); this.api.regeneratePayslip(slip.id, newKey('payslip-regenerate')).subscribe({ next: value => { this.payslips.update(rows => [value, ...rows.filter(row => row.id !== slip.id)]); this.success.set(this.fr() ? 'Nouvelle version du bulletin délivrée.' : 'New payslip version issued.'); this.busy.set(false); }, error: err => { this.busy.set(false); this.error.set(this.apiError(err)); } }); }
  private runBody(): Record<string, unknown> { return { payrollPeriodId: this.newPeriodId, employeeIds: [], prorationMode: this.newProration, defaultHours: Math.max(0, Math.round(this.newHours)), segregationEnabled: this.newSegregation }; }
  private runRequest() { return this.api.preview(this.runBody()); }
  private replaceRun(run: PayrollRun): void { this.runs.update(rows => rows.map(row => row.id === run.id ? run : row)); }
  private download(blob: Blob, filename: string): void { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url); }
  private emptyComponent(): Partial<PayrollComponent> { return { code: '', nameFr: '', nameEn: '', componentKind: 'EARNING', calculationMode: 'FIXED', defaultAmountMinor: 0, defaultRateBps: 0, expenseAccountId: null, liabilityAccountId: null, active: true, effectiveFrom: null, effectiveTo: null }; }
  private apiError(error: unknown): string { const response = error as HttpErrorResponse; const body = (response?.error || {}) as PayrollApiError['error']; const detail = body?.message || (error as { message?: string })?.message || (this.fr() ? 'Erreur inattendue.' : 'Unexpected error.'); const blockers = body?.blockers?.map(item => item.label).filter(Boolean).join(' · '); const correlation = body?.correlationId ? ` (${body.correlationId})` : ''; return `${detail}${blockers ? ` · ${blockers}` : ''}${correlation}`; }
}
