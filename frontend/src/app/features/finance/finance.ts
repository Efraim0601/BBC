import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  FinanceApi, PaymentRequest, SituationView, ExpenseView, ExpenseRequest,
  FeeConfigView, FeeConfigUpdate, PaymentChannelView, PaymentChannelUpdate,
  StudentFeeStatementView, TrancheStatusView,
} from './finance.api';
import { StudentApi } from '../students/students.api';
import { ClassView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { FinanceSummary, PaymentView, Student } from '../../core/models';
import { downloadCsv, stampedName } from '../../core/csv';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  StatusPillComponent, TabsComponent, ChipFilterComponent, AreaChartComponent, Pt,
  ConfirmComponent,
} from '../../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

type Tab = 'payments' | 'debtors' | 'expenses' | 'fees' | 'channels';

@Component({
  selector: 'bbc-finance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, KpiComponent, PageHeaderComponent,
    EmptyComponent, StatusPillComponent, TabsComponent, ChipFilterComponent, AreaChartComponent,
    ConfirmComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header
        [title]="i18n.t('finance')"
        [subtitle]="canWrite
          ? (fr() ? 'Encaissements, frais, débiteurs, dépenses' : 'Payments, fees, debtors, expenses')
          : (fr() ? 'Consultation — accès lecture seule' : 'View only — read access')">
        <div right class="flex items-center gap-2">
          @if (!canWrite) {
            <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-1.5 rounded-lg">
              <bbc-icon name="eye" [s]="14" /> {{ fr() ? 'Lecture seule' : 'Read-only' }}
            </span>
          }
          <button (click)="exportCurrentTab()"
            class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
            <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
          </button>
          @if (canWrite) {
            <button (click)="openPayment()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouveau paiement' : 'New payment' }}
            </button>
          }
        </div>
      </bbc-page-header>

      <bbc-tabs [tabs]="tabs()" [value]="tab()" (change)="setTab($event)" />

      @switch (tab()) {
        @case ('payments') {
          <!-- KPIs -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
            <bbc-kpi tone="gold" icon="cash" [label]="i18n.t('revenue30')" [value]="money(revenue30())"
              [sub]="(summary()?.paymentsCount ?? 0) + (fr() ? ' encaissements' : ' payments')" />
            <bbc-kpi tone="bad" icon="wallet" [label]="i18n.t('expense30')" [value]="money(expense30())" />
            <bbc-kpi [tone]="balance30() >= 0 ? 'ok' : 'bad'" icon="trendUp"
              [label]="i18n.t('balance30')" [value]="money(balance30())"
              [sub]="fr() ? 'sur 30 jours' : 'over 30 days'" />
          </div>

          <!-- Revenue chart -->
          <bbc-card className="mb-5"
            [title]="fr() ? 'Revenus (30 jours)' : 'Revenue (30 days)'"
            [subtitle]="fr() ? 'Encaissements quotidiens (FCFA)' : 'Daily collections (FCFA)'">
            <div action class="text-xs text-mute">{{ fr() ? 'Total' : 'Total' }}: <span class="font-bold text-ink">{{ money(revenue30()) }}</span></div>
            @if (revData().length) {
              <bbc-area-chart [data]="revData()" [h]="240" />
            } @else {
              <bbc-empty icon="chart" [label]="fr() ? 'Aucune donnée' : 'No data'" />
            }
          </bbc-card>

          <!-- Payment history -->
          <bbc-card
            [title]="fr() ? 'Historique des encaissements' : 'Payment history'"
            [subtitle]="filtered().length + (fr() ? ' reçus' : ' receipts')">
            <div action>
              <bbc-chip-filter [allLabel]="fr() ? 'Tout' : 'All'" [value]="methodFilter()"
                (change)="methodFilter.set($event)" [options]="methodOptions()" />
            </div>
            @if (filtered().length === 0) {
              <bbc-empty icon="receipt" [label]="fr() ? 'Aucun paiement' : 'No payments'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm">
                  <thead class="border-y border-slate-100 bg-slate-50/50">
                    <tr class="text-[11px] uppercase tracking-wide text-mute">
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'N° reçu' : 'Receipt N°' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Élève' : 'Student' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Tranche' : 'Installment' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Méthode' : 'Method' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Date' : 'Date' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Montant' : 'Amount' }}</th>
                      <th class="text-right font-semibold py-2 pr-5">{{ fr() ? 'Actions' : 'Actions' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (p of filtered(); track p.id) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50 group">
                        <td class="py-2.5 pl-5 text-xs font-mono text-brand-600 font-semibold">{{ p.receiptNo }}</td>
                        <td class="py-2.5">
                          <div class="font-semibold text-ink">{{ p.studentName ?? (fr() ? 'Élève supprimé' : 'Deleted student') }}</div>
                          @if (p.matricule) {
                            <div class="text-[11px] text-mute font-mono">{{ p.matricule }}@if (p.className) { · {{ p.className }} }</div>
                          }
                        </td>
                        <td class="py-2.5">
                          @if (p.tranche) {
                            <span class="text-xs bg-brand-50 text-brand-700 px-2 py-0.5 rounded font-semibold">T{{ p.tranche }}</span>
                          } @else { <span class="text-mute">—</span> }
                        </td>
                        <td class="py-2.5">
                          <div class="text-ink">{{ methodLabel(p) }}</div>
                          @if (p.reference) {
                            <div class="text-[11px] text-mute font-mono">{{ p.reference }}</div>
                          }
                        </td>
                        <td class="py-2.5 text-mute">{{ p.paidOn }}</td>
                        <td class="py-2.5 text-right font-bold text-emerald-700">{{ money(p.amount) }}</td>
                        <td class="py-2.5 pr-5 text-right">
                          <div class="flex items-center justify-end gap-2 opacity-70 group-hover:opacity-100 transition">
                            <button (click)="viewReceipt(p)" class="text-mute hover:text-brand-600"
                              [title]="fr() ? 'Reçu' : 'Receipt'">
                              <bbc-icon name="receipt" [s]="14" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </bbc-card>
        }

        @case ('debtors') {
          <div class="mb-5 flex flex-col gap-3 rounded-xl2 border border-brand-100 bg-brand-50/60 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div class="text-sm font-bold text-brand-800">{{ fr() ? 'Comprendre cette vue' : 'Understanding this view' }}</div>
              <p class="mt-0.5 text-xs leading-relaxed text-brand-700">
                {{ fr()
                  ? 'Les montants attendus viennent des grilles de frais applicables aux élèves actifs. La liste affiche uniquement les soldes non réglés; les élèves soldés restent inclus dans le taux de recouvrement.'
                  : 'Expected amounts come from fee grids covering active students. The list shows outstanding balances only; fully paid students remain included in the recovery rate.' }}
              </p>
            </div>
            <button (click)="reloadDebtors()" [disabled]="debtorsLoading()"
              class="inline-flex h-9 shrink-0 items-center justify-center gap-2 rounded-lg border border-brand-200 bg-white px-3.5 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50">
              <bbc-icon name="refresh" [s]="15" /> {{ debtorsLoading() ? (fr() ? 'Actualisation…' : 'Refreshing…') : (fr() ? 'Actualiser' : 'Refresh') }}
            </button>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
            <bbc-kpi tone="bad" icon="wallet" [label]="fr() ? 'Total impayé' : 'Total outstanding'"
              [value]="money(debtTotal())"
              [sub]="debtors().length + (fr() ? ' débiteurs sur ' : ' debtors out of ') + situation().length" />
            <bbc-kpi tone="ok" icon="cash" [label]="fr() ? 'Affecté aux frais' : 'Applied to fees'"
              [value]="money(paidTotal())"
              [sub]="settledCount() + (fr()
                ? (settledCount() === 1 ? ' élève entièrement soldé' : ' élèves entièrement soldés')
                : (settledCount() === 1 ? ' student fully settled' : ' students fully settled'))" />
            <bbc-kpi tone="gold" icon="chart" [label]="fr() ? 'Taux de recouvrement' : 'Recovery rate'"
              [value]="recoveryPct() + '%'"
              [sub]="fr() ? 'payé ÷ frais attendus' : 'paid ÷ expected fees'" />
          </div>

          <bbc-card [title]="fr() ? 'Liste des débiteurs' : 'Debtors list'"
            [subtitle]="filteredDebtors().length + (fr() ? ' élèves avec un solde' : ' students with a balance')">
            <div action class="flex items-center gap-2">
              <button (click)="exportDebtors()" [disabled]="!filteredDebtors().length"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
              </button>
            </div>

            <div class="mb-4 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3 md:grid-cols-[1fr_1fr_1.4fr_auto] md:items-end">
              <label class="block">
                <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Classe' : 'Class' }}</span>
                <select [ngModel]="debtorClassFilter()" (ngModelChange)="debtorClassFilter.set($event || null)"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-ink focus:border-brand-400 focus:outline-none">
                  <option value="">{{ fr() ? 'Toutes les classes couvertes' : 'All covered classes' }}</option>
                  @for (name of debtorClassOptions(); track name) { <option [value]="name">{{ name }}</option> }
                </select>
              </label>
              <label class="block">
                <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Situation' : 'Status' }}</span>
                <select [ngModel]="debtorStatusFilter()" (ngModelChange)="debtorStatusFilter.set($event)"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-ink focus:border-brand-400 focus:outline-none">
                  <option value="all">{{ fr() ? 'Tous les soldes' : 'All outstanding' }}</option>
                  <option value="unpaid">{{ fr() ? 'Aucun versement' : 'No payment yet' }} ({{ unpaidCount() }})</option>
                  <option value="partial">{{ fr() ? 'Paiement partiel' : 'Partially paid' }} ({{ partialCount() }})</option>
                </select>
              </label>
              <label class="block">
                <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Élève' : 'Student' }}</span>
                <input [ngModel]="debtorQuery()" (ngModelChange)="debtorQuery.set($event)" name="debtorQuery"
                  [placeholder]="fr() ? 'Nom de l’élève…' : 'Student name…'"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none" />
              </label>
              <button (click)="clearDebtorFilters()" [disabled]="!hasDebtorFilters()"
                class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
                {{ fr() ? 'Effacer' : 'Clear' }}
              </button>
            </div>

            @if (debtorsLoading()) {
              <bbc-empty icon="wallet" [label]="fr() ? 'Chargement…' : 'Loading…'" />
            } @else if (debtorsError()) {
              <div class="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-4 py-3">
                {{ fr() ? 'Impossible de charger les débiteurs.' : 'Could not load debtors.' }}
                <button (click)="reloadDebtors()" class="font-bold underline ml-1">{{ fr() ? 'Réessayer' : 'Retry' }}</button>
              </div>
            } @else if (!situation().length) {
              <div class="rounded-xl border border-amber-200 bg-amber-50 px-4 py-5 text-sm text-amber-800">
                <div class="font-bold">{{ fr() ? 'Aucun élève couvert par une grille de frais' : 'No students are covered by a fee grid' }}</div>
                <p class="mt-1 text-xs">{{ fr() ? 'Créez une grille dans l’onglet Frais pour le niveau, le sous-système ou la classe concernés.' : 'Create a grid in the Fees tab for the relevant level, subsystem or class.' }}</p>
              </div>
            } @else if (!filteredDebtors().length) {
              <bbc-empty icon="wallet"
                [label]="hasDebtorFilters() ? (fr() ? 'Aucun débiteur ne correspond aux filtres' : 'No debtor matches these filters')
                                       : (fr() ? 'Aucun débiteur — tous les frais sont réglés' : 'No debtors — all fees are settled')" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm">
                  <thead class="border-y border-slate-100 bg-slate-50/50">
                    <tr class="text-[11px] uppercase tracking-wide text-mute">
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Élève' : 'Student' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Classe' : 'Class' }}</th>
                      <th class="text-left font-semibold py-2 w-40">{{ fr() ? 'Progression' : 'Progress' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Attendu' : 'Expected' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Payé' : 'Paid' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Solde' : 'Balance' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Statut' : 'Status' }}</th>
                      <th class="text-right font-semibold py-2 pr-5">{{ fr() ? 'Action' : 'Action' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (d of filteredDebtors(); track d.studentId) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50">
                        <td class="py-2.5 pl-5 font-semibold text-ink">{{ d.studentName }}</td>
                        <td class="py-2.5 text-mute">{{ d.className }}</td>
                        <td class="py-2.5">
                          <div class="flex items-center gap-2">
                            <div class="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                              <div class="h-full rounded-full transition-all"
                                [class]="d.progressPct >= 100 ? 'bg-emerald-500' : d.progressPct > 0 ? 'bg-gold-400' : 'bg-rose-400'"
                                [style.width.%]="d.progressPct"></div>
                            </div>
                            <span class="text-[11px] text-mute font-semibold w-9 text-right">{{ d.progressPct }}%</span>
                          </div>
                        </td>
                        <td class="py-2.5 text-right text-mute">{{ money(d.total) }}</td>
                        <td class="py-2.5 text-right text-emerald-700 font-semibold">{{ money(d.paid) }}</td>
                        <td class="py-2.5 text-right font-bold text-rose-600">{{ money(d.balance) }}</td>
                        <td class="py-2.5 text-right"><bbc-status-pill [status]="d.status"
                          [label]="d.status === 'partial' ? (fr() ? 'Partiel' : 'Partial') : (fr() ? 'Impayé' : 'Unpaid')" /></td>
                        <td class="py-2.5 pr-5 text-right">
                          @if (canWrite) {
                            <button (click)="openPaymentFor(d)"
                              class="inline-flex h-8 items-center gap-1.5 rounded-lg bg-brand-50 px-2.5 text-xs font-bold text-brand-700 hover:bg-brand-100">
                              <bbc-icon name="cash" [s]="13" /> {{ fr() ? 'Encaisser' : 'Collect' }}
                            </button>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </bbc-card>
        }

        @case ('expenses') {
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
            <bbc-kpi tone="bad" icon="wallet" [label]="i18n.t('expense30')" [value]="money(expense30())" />
            <bbc-kpi tone="neutral" icon="doc" [label]="fr() ? 'Dépenses enregistrées' : 'Recorded expenses'"
              [value]="expenses().length.toString()" />
            <bbc-kpi tone="gold" icon="chart" [label]="fr() ? 'Poste principal' : 'Top category'"
              [value]="topCategory()?.cat ?? '—'"
              [sub]="topCategory() ? money(topCategory()!.amount) : ''" />
          </div>

          @if (canWrite && expenseFormOpen()) {
            <bbc-card className="mb-5" [title]="fr() ? 'Nouvelle dépense' : 'New expense'">
              <form (ngSubmit)="saveExpense()" class="grid grid-cols-1 md:grid-cols-4 gap-3">
                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Date' : 'Date' }}</label>
                  <input type="date" name="spentOn" [(ngModel)]="expenseDraft.spentOn"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </div>
                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Catégorie' : 'Category' }}</label>
                  <select name="category" [(ngModel)]="expenseDraft.category"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    @for (c of expenseCategories; track c.value) {
                      <option [value]="c.value">{{ fr() ? c.fr : c.en }}</option>
                    }
                  </select>
                </div>
                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Libellé' : 'Label' }}</label>
                  <input name="label" [(ngModel)]="expenseDraft.label"
                    [placeholder]="fr() ? 'ex. Facture ENEO' : 'e.g. Power bill'"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </div>
                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Montant (FCFA)' : 'Amount (FCFA)' }}</label>
                  <input type="number" name="amount" [(ngModel)]="expenseDraft.amount" min="1"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </div>
                @if (expenseError()) {
                  <div class="md:col-span-4 text-xs text-rose-600 bg-rose-50 rounded-lg px-3 py-2">{{ expenseError() }}</div>
                }
                <div class="md:col-span-4 flex justify-end gap-2">
                  <button type="button" (click)="cancelExpense()"
                    class="h-9 px-4 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                    {{ i18n.t('cancel') }}
                  </button>
                  <button type="submit" [disabled]="expenseSaving()"
                    class="h-9 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60">
                    {{ i18n.t('save') }}
                  </button>
                </div>
              </form>
            </bbc-card>
          }

          <bbc-card [title]="fr() ? 'Journal des dépenses' : 'Expense log'"
            [subtitle]="filteredExpenses().length + (fr() ? ' dépenses' : ' expenses')">
            <div action class="flex items-center gap-2">
              <bbc-chip-filter [allLabel]="fr() ? 'Toutes' : 'All'" [value]="categoryFilter()"
                (change)="categoryFilter.set($event)" [options]="categoryOptions()" />
              <button (click)="exportExpenses()" [disabled]="!filteredExpenses().length"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
              </button>
              @if (canWrite && !expenseFormOpen()) {
                <button (click)="openExpense()"
                  class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                  <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle dépense' : 'New expense' }}
                </button>
              }
            </div>

            @if (expensesLoading()) {
              <bbc-empty icon="doc" [label]="fr() ? 'Chargement…' : 'Loading…'" />
            } @else if (expensesError()) {
              <div class="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-4 py-3">
                {{ fr() ? 'Impossible de charger les dépenses.' : 'Could not load expenses.' }}
                <button (click)="reloadExpenses()" class="font-bold underline ml-1">{{ fr() ? 'Réessayer' : 'Retry' }}</button>
              </div>
            } @else if (!filteredExpenses().length) {
              <bbc-empty icon="doc" [label]="fr() ? 'Aucune dépense enregistrée' : 'No expense recorded'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm">
                  <thead class="border-y border-slate-100 bg-slate-50/50">
                    <tr class="text-[11px] uppercase tracking-wide text-mute">
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Date' : 'Date' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Catégorie' : 'Category' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Libellé' : 'Label' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Montant' : 'Amount' }}</th>
                      @if (canWrite) { <th class="text-right font-semibold py-2 pr-5"></th> }
                    </tr>
                  </thead>
                  <tbody>
                    @for (e of filteredExpenses(); track e.id) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50 group">
                        <td class="py-2.5 pl-5 text-mute">{{ e.spentOn }}</td>
                        <td class="py-2.5">
                          <span class="text-xs bg-slate-100 text-slate-700 px-2 py-0.5 rounded font-semibold">{{ categoryLabel(e.category) }}</span>
                        </td>
                        <td class="py-2.5 font-semibold text-ink">{{ e.label }}</td>
                        <td class="py-2.5 text-right font-bold text-rose-600">{{ money(e.amount) }}</td>
                        @if (canWrite) {
                          <td class="py-2.5 pr-5 text-right">
                            <button (click)="confirmExpenseDel.set(e)"
                              class="text-mute hover:text-rose-600 opacity-0 group-hover:opacity-100 focus:opacity-100 transition"
                              [title]="fr() ? 'Supprimer' : 'Delete'">
                              <bbc-icon name="trash" [s]="14" />
                            </button>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr class="border-t-2 border-slate-200 font-bold">
                      <td class="py-2.5 pl-5" colspan="3">{{ fr() ? 'Total' : 'Total' }}</td>
                      <td class="py-2.5 text-right text-rose-700">{{ money(expenseTotal()) }}</td>
                      @if (canWrite) { <td></td> }
                    </tr>
                  </tfoot>
                </table>
              </div>
            }
          </bbc-card>
        }

        @case ('fees') {
          <bbc-card [title]="fr() ? 'Grille des frais' : 'Fee grid'"
            [subtitle]="fr() ? 'Montant annuel et tranches, par niveau' : 'Annual amount and installments, per level'">
            <div action>
              @if (canWrite && !feeFormOpen()) {
                <button (click)="openFee(null)"
                  class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                  <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle grille' : 'New grid' }}
                </button>
              }
            </div>

            @if (canWrite && feeFormOpen()) {
              <form (ngSubmit)="saveFee()" class="bg-slate-50 rounded-xl2 p-4 mb-4 space-y-3">
                <!-- Portée : grille du niveau, ou surcharge d'une classe précise -->
                <div class="inline-flex rounded-lg border border-slate-200 p-0.5 bg-white">
                  <button type="button" (click)="setFeeScope('level')" [disabled]="!!feeEditingId()"
                    class="h-8 px-3 text-xs font-semibold rounded-md disabled:opacity-60"
                    [class]="!feeDraft.classId ? 'bg-brand-600 text-white' : 'text-ink hover:bg-slate-50'">
                    {{ fr() ? 'Grille du niveau' : 'Level grid' }}
                  </button>
                  <button type="button" (click)="setFeeScope('class')" [disabled]="!!feeEditingId()"
                    class="h-8 px-3 text-xs font-semibold rounded-md disabled:opacity-60"
                    [class]="feeDraft.classId ? 'bg-brand-600 text-white' : 'text-ink hover:bg-slate-50'">
                    {{ fr() ? 'Surcharge par classe' : 'Per-class override' }}
                  </button>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                  @if (feeDraft.classId) {
                    <div class="md:col-span-2">
                      <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Classe' : 'Class' }} *</label>
                      <select name="feeClass" [ngModel]="feeDraft.classId" (ngModelChange)="onFeeClass($event)"
                        [disabled]="!!feeEditingId()"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-100">
                        @for (c of setupClasses(); track c.id) {
                          <option [ngValue]="c.id">{{ c.name }} · {{ c.sectionLabel }}</option>
                        }
                      </select>
                      <span class="text-[11px] text-mute mt-1 block">
                        {{ fr() ? 'Cette grille remplace celle du niveau pour les élèves de la classe.'
                                : 'This grid replaces the level grid for the students of that class.' }}
                      </span>
                    </div>
                  } @else {
                    <div>
                      <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Niveau' : 'Level' }} *</label>
                      <select name="level" [(ngModel)]="feeDraft.level" [disabled]="!!feeEditingId()"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-100">
                        <option value="maternelle">{{ fr() ? 'Maternelle' : 'Kindergarten' }}</option>
                        <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                        <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
                      </select>
                    </div>
                    <div>
                      <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Sous-système' : 'Sub-system' }}</label>
                      <select name="subsystem" [(ngModel)]="feeDraft.subsystem" [disabled]="!!feeEditingId()"
                        class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-100">
                        <option [ngValue]="null">{{ fr() ? 'Les deux' : 'Both' }}</option>
                        <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
                        <option value="EN">{{ fr() ? 'Anglophone' : 'English' }}</option>
                      </select>
                    </div>
                  }
                  <div>
                    <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Total annuel (FCFA)' : 'Annual total (FCFA)' }} *</label>
                    <input type="number" name="total" [(ngModel)]="feeDraft.total" min="1"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">
                    {{ fr() ? 'Tranches — libellé, montant et échéance' : 'Installments — label, amount and due date' }}
                  </label>
                  <div class="space-y-2">
                    @for (t of feeDraft.tranches; track $index) {
                      <div class="flex flex-wrap items-center gap-2">
                        <span class="text-[11px] font-bold text-mute w-6">{{ $index + 1 }}</span>
                        <input [ngModel]="t.label" (ngModelChange)="setTrancheField($index, 'label', $event)"
                          [name]="'trlabel' + $index" [placeholder]="fr() ? 'Libellé (T1, Rentrée…)' : 'Label (T1, Term 1…)'"
                          class="h-9 w-40 px-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                        <input type="number" min="0" [ngModel]="t.amount" (ngModelChange)="setTrancheField($index, 'amount', $event)"
                          [name]="'tramount' + $index" placeholder="0"
                          class="h-9 w-32 px-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                        <span class="text-[11px] text-mute">{{ fr() ? 'à payer avant le' : 'due by' }}</span>
                        <input type="date" [ngModel]="t.dueOn" (ngModelChange)="setTrancheField($index, 'dueOn', $event)"
                          [name]="'trdue' + $index"
                          class="h-9 px-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                        <button type="button" (click)="removeTranche($index)" class="text-mute hover:text-rose-600">
                          <bbc-icon name="x" [s]="14" />
                        </button>
                      </div>
                    }
                    <button type="button" (click)="addTranche()"
                      class="inline-flex items-center gap-1 h-9 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                      <bbc-icon name="plus" [s]="12" /> {{ fr() ? 'Ajouter une tranche' : 'Add an installment' }}
                    </button>
                  </div>
                  <p class="text-[11px] mt-1.5"
                    [class]="tranchesMatch() ? 'text-mute' : 'text-amber-700 font-semibold'">
                    {{ fr() ? 'Somme des tranches' : 'Installments sum' }} :
                    {{ money(tranchesSum()) }}
                    @if (!tranchesMatch()) {
                      — {{ fr() ? 'ne correspond pas au total annuel' : 'does not match the annual total' }}
                    }
                  </p>
                </div>

                @if (feeError()) {
                  <div class="text-xs text-rose-600 bg-rose-50 rounded-lg px-3 py-2">{{ feeError() }}</div>
                }
                <div class="flex justify-end gap-2">
                  <button type="button" (click)="cancelFee()"
                    class="h-9 px-4 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                    {{ i18n.t('cancel') }}
                  </button>
                  <button type="submit" [disabled]="feeSaving()"
                    class="h-9 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60">
                    {{ i18n.t('save') }}
                  </button>
                </div>
              </form>
            }

            @if (!feeConfigs().length) {
              <bbc-empty icon="wallet"
                [label]="fr() ? 'Aucune grille de frais définie' : 'No fee grid defined'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="w-full text-sm">
                  <thead class="border-y border-slate-100 bg-slate-50/50">
                    <tr class="text-[11px] uppercase tracking-wide text-mute">
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'S’applique à' : 'Applies to' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Sous-système' : 'Sub-system' }}</th>
                      <th class="text-left font-semibold py-2">{{ fr() ? 'Tranches' : 'Installments' }}</th>
                      <th class="text-right font-semibold py-2">{{ fr() ? 'Total annuel' : 'Annual total' }}</th>
                      @if (canWrite) { <th class="text-right font-semibold py-2 pr-5"></th> }
                    </tr>
                  </thead>
                  <tbody>
                    @for (c of feeConfigs(); track c.id) {
                      <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50 group">
                        <td class="py-2.5 pl-5 font-semibold text-ink">
                          @if (c.className) {
                            <span class="inline-flex items-center gap-1.5">
                              {{ c.className }}
                              <span class="text-[10px] uppercase tracking-wide bg-gold-50 text-gold-600 px-1.5 py-0.5 rounded font-bold">
                                {{ fr() ? 'classe' : 'class' }}
                              </span>
                            </span>
                          } @else {
                            {{ levelLabel(c.level) }}
                          }
                        </td>
                        <td class="py-2.5 text-mute">
                          {{ c.subsystem ? (c.subsystem === 'FR' ? 'Francophone' : (fr() ? 'Anglophone' : 'English')) : (fr() ? 'Les deux' : 'Both') }}
                        </td>
                        <td class="py-2.5">
                          <div class="flex flex-wrap gap-1">
                            @for (t of c.tranches; track $index) {
                              <span class="text-xs bg-brand-50 text-brand-700 px-2 py-0.5 rounded font-semibold"
                                [title]="t.dueOn ? (fr() ? 'À payer avant le ' : 'Due by ') + t.dueOn : ''">
                                {{ t.label }} · {{ money(t.amount) }}@if (t.dueOn) { <span class="font-normal opacity-70"> · {{ t.dueOn }}</span> }
                              </span>
                            } @empty { <span class="text-mute text-xs">—</span> }
                          </div>
                        </td>
                        <td class="py-2.5 text-right font-bold text-ink">{{ money(c.total) }}</td>
                        @if (canWrite) {
                          <td class="py-2.5 pr-5 text-right">
                            <button (click)="openFee(c)" class="text-mute hover:text-brand-600 opacity-0 group-hover:opacity-100 focus:opacity-100 transition"
                              [title]="fr() ? 'Modifier' : 'Edit'">
                              <bbc-icon name="edit" [s]="14" />
                            </button>
                            <button (click)="confirmFeeDel.set(c)" class="ml-2 text-mute hover:text-rose-600 opacity-0 group-hover:opacity-100 focus:opacity-100 transition"
                              [title]="fr() ? 'Supprimer' : 'Delete'">
                              <bbc-icon name="trash" [s]="14" />
                            </button>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <p class="text-[11px] text-mute mt-3">
                {{ fr()
                  ? 'Un élève suit la grille de sa classe si elle existe, sinon celle de son niveau. La grille détermine son solde, ses tranches et le blocage du bulletin pour dette.'
                  : 'A student follows their class grid when it exists, otherwise their level grid. The grid drives the balance, the installments and the debt-blocking of report cards.' }}
              </p>
            }
          </bbc-card>
        }

        @case ('channels') {
          <bbc-card [title]="fr() ? 'Moyens de paiement' : 'Payment methods'"
            [subtitle]="fr() ? 'Canaux acceptés par l’école et coordonnées communiquées aux parents'
                             : 'Channels the school accepts and the details shown to parents'">
            <div class="space-y-3">
              @for (c of channels(); track c.code) {
                <div class="rounded-xl2 border p-4"
                  [class]="c.enabled ? 'border-slate-200 bg-white' : 'border-slate-100 bg-slate-50/60'">
                  <div class="flex flex-wrap items-center gap-3">
                    <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
                      [class]="channelTone(c.code)">
                      <bbc-icon [name]="channelIcon(c.code)" [s]="18" />
                    </div>
                    <div class="min-w-0 flex-1">
                      <div class="font-semibold text-ink">{{ fr() ? c.labelFr : c.labelEn }}</div>
                      <div class="text-[11px] text-mute font-mono">{{ c.code }}</div>
                    </div>
                    @if (canWrite) {
                      <label class="inline-flex items-center gap-2 text-xs font-semibold text-ink">
                        <input type="checkbox" [ngModel]="c.enabled" (ngModelChange)="patchChannel(c, { enabled: $event })"
                          class="w-4 h-4 rounded border-slate-300 text-brand-600" />
                        {{ fr() ? 'Actif' : 'Enabled' }}
                      </label>
                      <label class="inline-flex items-center gap-2 text-xs font-semibold text-ink">
                        <input type="checkbox" [ngModel]="c.visibleToParents" (ngModelChange)="patchChannel(c, { visibleToParents: $event })"
                          class="w-4 h-4 rounded border-slate-300 text-brand-600" />
                        {{ fr() ? 'Visible des parents' : 'Shown to parents' }}
                      </label>
                      <label class="inline-flex items-center gap-2 text-xs font-semibold text-ink">
                        <input type="checkbox" [ngModel]="c.requiresReference" (ngModelChange)="patchChannel(c, { requiresReference: $event })"
                          class="w-4 h-4 rounded border-slate-300 text-brand-600" />
                        {{ fr() ? 'Référence obligatoire' : 'Reference required' }}
                      </label>
                      <button (click)="toggleChannelEdit(c.code)"
                        class="h-9 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                        {{ channelEdit() === c.code ? (fr() ? 'Fermer' : 'Close') : (fr() ? 'Coordonnées' : 'Details') }}
                      </button>
                    }
                  </div>

                  @if (c.accountRef && channelEdit() !== c.code) {
                    <div class="text-xs text-mute mt-2">
                      {{ fr() ? 'Compte' : 'Account' }} : <b class="text-ink font-mono">{{ c.accountRef }}</b>
                      @if (c.accountName) { · {{ c.accountName }} }
                    </div>
                  }

                  @if (canWrite && channelEdit() === c.code) {
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4 pt-4 border-t border-slate-100">
                      <label class="block">
                        <span class="text-xs font-semibold text-ink">{{ fr() ? 'Libellé (FR)' : 'Label (FR)' }}</span>
                        <input [(ngModel)]="channelDraft.labelFr"
                          class="mt-1 w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                      </label>
                      <label class="block">
                        <span class="text-xs font-semibold text-ink">{{ fr() ? 'Libellé (EN)' : 'Label (EN)' }}</span>
                        <input [(ngModel)]="channelDraft.labelEn"
                          class="mt-1 w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                      </label>
                      <label class="block">
                        <span class="text-xs font-semibold text-ink">
                          {{ c.code === 'MPGS' ? (fr() ? 'Identifiant marchand' : 'Merchant ID')
                             : c.code === 'TRANSFER' ? (fr() ? 'RIB / IBAN' : 'Bank account')
                             : (fr() ? 'Numéro à créditer' : 'Number to credit') }}
                        </span>
                        <input [(ngModel)]="channelDraft.accountRef" placeholder="+237 6XX XX XX XX"
                          class="mt-1 w-full h-10 px-3 text-sm rounded-lg border border-slate-200 font-mono focus:outline-none focus:border-brand-400" />
                      </label>
                      <label class="block">
                        <span class="text-xs font-semibold text-ink">{{ fr() ? 'Intitulé du compte' : 'Account name' }}</span>
                        <input [(ngModel)]="channelDraft.accountName" [placeholder]="fr() ? 'Bayo Bilingual Complex' : 'Bayo Bilingual Complex'"
                          class="mt-1 w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                      </label>
                      <label class="block md:col-span-2">
                        <span class="text-xs font-semibold text-ink">{{ fr() ? 'Instructions au parent (FR)' : 'Parent instructions (FR)' }}</span>
                        <textarea [(ngModel)]="channelDraft.instructionsFr" rows="2"
                          class="mt-1 w-full px-3 py-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400"></textarea>
                      </label>
                      <label class="block md:col-span-2">
                        <span class="text-xs font-semibold text-ink">{{ fr() ? 'Instructions au parent (EN)' : 'Parent instructions (EN)' }}</span>
                        <textarea [(ngModel)]="channelDraft.instructionsEn" rows="2"
                          class="mt-1 w-full px-3 py-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400"></textarea>
                      </label>
                    </div>
                    <div class="flex justify-end gap-2 mt-3">
                      <button (click)="channelEdit.set(null)"
                        class="h-9 px-4 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                        {{ i18n.t('cancel') }}
                      </button>
                      <button (click)="saveChannel(c)"
                        class="h-9 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                        {{ i18n.t('save') }}
                      </button>
                    </div>
                  }
                </div>
              } @empty {
                <bbc-empty icon="wallet" [label]="fr() ? 'Aucun moyen de paiement' : 'No payment method'" />
              }
            </div>

            @if (channelMsg(); as m) {
              <div class="mt-3 text-xs rounded-lg px-3 py-2"
                [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'">{{ m.text }}</div>
            }

            <p class="text-[11px] text-mute mt-4">
              {{ fr()
                ? 'Les canaux « visibles des parents » apparaissent dans le portail parent avec leurs coordonnées et instructions : c’est ainsi qu’une famille règle une tranche par Orange Money, MTN MoMo ou carte, puis communique la référence à l’économat.'
                : 'Channels marked “shown to parents” appear in the parent portal with their details and instructions: that is how a family settles an installment by Orange Money, MTN MoMo or card, then passes the reference to the bursary.' }}
            </p>
          </bbc-card>
        }
      }
    </div>

    @if (confirmFeeDel(); as cf) {
      <bbc-confirm
        [title]="fr() ? 'Supprimer cette grille ?' : 'Delete this grid?'"
        [body]="(cf.className
                  ? (fr() ? 'La classe ' + cf.className + ' repassera sur la grille de son niveau.'
                          : 'Class ' + cf.className + ' will fall back to its level grid.')
                  : (fr() ? 'Les élèves de ce niveau n’auront plus de grille de référence.'
                          : 'Students of this level will no longer have a reference grid.'))"
        [confirmLabel]="fr() ? 'Supprimer' : 'Delete'"
        [cancelLabel]="i18n.t('cancel')"
        (confirm)="removeFee(cf)" (cancel)="confirmFeeDel.set(null)" />
    }

    @if (confirmExpenseDel(); as ce) {
      <bbc-confirm
        [title]="(fr() ? 'Supprimer « ' : 'Delete “') + ce.label + (fr() ? ' » ?' : '”?')"
        [body]="(fr() ? 'Cette dépense de ' : 'This ') + money(ce.amount) + (fr() ? ' sera retirée du journal.' : ' expense will be removed from the log.')"
        [confirmLabel]="fr() ? 'Supprimer' : 'Delete'"
        [cancelLabel]="i18n.t('cancel')"
        (confirm)="removeExpense(ce)" (cancel)="confirmExpenseDel.set(null)" />
    }

    <!-- New payment modal -->
    @if (paymentOpen()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-black/40" (click)="closePayment()"></div>
        <div class="relative bg-white rounded-xl2 shadow-card w-full max-w-xl fade-in">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <div class="text-base font-semibold text-ink">{{ fr() ? 'Nouveau paiement' : 'New payment' }}</div>
            <button (click)="closePayment()" class="text-mute hover:text-ink"><bbc-icon name="x" [s]="18" /></button>
          </div>
          <div class="p-5 space-y-4">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Classe' : 'Class' }}</label>
                <select [ngModel]="payClass()" (ngModelChange)="onPayClass($event)"
                  class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                  <option value="">{{ fr() ? '— Choisir —' : '— Choose —' }}</option>
                  @for (c of setupClasses(); track c.id) {
                    <option [value]="c.name">{{ c.name }}</option>
                  }
                </select>
              </div>
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Élève' : 'Student' }}</label>
                <select [ngModel]="draft.studentId" (ngModelChange)="onPayStudent($event)" [disabled]="!payClass()"
                  class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-50">
                  <option value="">{{ fr() ? '— Choisir —' : '— Choose —' }}</option>
                  @for (s of payStudents(); track s.id) {
                    <option [value]="s.id">{{ s.name }} · {{ s.matricule }}</option>
                  }
                </select>
              </div>
            </div>

            <!-- Situation de l'élève : ce qui reste dû, tranche par tranche -->
            @if (statement(); as st) {
              <div class="rounded-xl2 bg-slate-50 border border-slate-100 p-3">
                <div class="flex items-center justify-between text-xs mb-2">
                  <span class="text-mute">
                    {{ fr() ? 'Grille' : 'Grid' }} :
                    <b class="text-ink">{{ st.gridSource === 'class' ? st.className : levelOfClass(st.className) }}</b>
                    @if (st.gridSource === 'class') {
                      <span class="ml-1 text-[10px] uppercase bg-gold-50 text-gold-600 px-1.5 py-0.5 rounded font-bold">{{ fr() ? 'classe' : 'class' }}</span>
                    }
                  </span>
                  <span class="text-mute">
                    {{ fr() ? 'Reste à payer' : 'Outstanding' }} :
                    <b [class]="st.balance > 0 ? 'text-rose-600' : 'text-emerald-700'">{{ money(st.balance) }}</b>
                  </span>
                </div>
                @if (st.balance <= 0) {
                  <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2.5 text-sm text-emerald-800">
                    <div class="font-bold">✓ {{ fr() ? 'Frais entièrement réglés' : 'Fees paid in full' }}</div>
                    <p class="mt-0.5 text-xs">{{ fr() ? 'Aucun nouveau paiement n’est attendu pour cette grille.' : 'No additional payment is expected for this fee grid.' }}</p>
                  </div>
                } @else if (st.tranches.length) {
                  <div class="flex flex-wrap gap-1.5">
                    @for (t of st.tranches; track t.index) {
                      <button (click)="pickTranche(t)" [disabled]="t.remaining <= 0"
                        class="text-[11px] px-2 py-1 rounded-lg border font-semibold transition"
                        [class]="draft.tranche === t.index ? 'border-brand-500 bg-brand-50 text-brand-700'
                                 : t.status === 'paid' ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                                 : t.overdue ? 'border-rose-200 bg-rose-50 text-rose-700'
                                 : 'border-slate-200 bg-white text-mute hover:border-brand-300 disabled:cursor-not-allowed'">
                        {{ t.label }} · {{ money(t.remaining || t.amount) }}
                        @if (t.status === 'paid') { ✓ } @else if (t.overdue) { ⚠ }
                      </button>
                    }
                  </div>
                  <p class="text-[11px] text-mute mt-2">
                    {{ fr() ? 'Cliquez une tranche pour pré-remplir le montant restant.'
                            : 'Click an installment to pre-fill the remaining amount.' }}
                  </p>
                } @else {
                  <p class="text-[11px] text-amber-700">
                    {{ fr() ? 'Aucune grille de frais ne couvre cet élève — définissez-la dans l’onglet Frais.'
                            : 'No fee grid covers this student — define one in the Fees tab.' }}
                  </p>
                }
              </div>
            }

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Montant' : 'Amount' }}</label>
                <div class="relative">
                  <input type="number" min="1" [max]="statement()?.balance ?? null" [(ngModel)]="draft.amount"
                    [disabled]="statement()?.balance === 0"
                    [class.border-rose-400]="!!paymentAmountProblem()"
                    class="w-full h-10 px-3 pr-16 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono disabled:bg-slate-100 disabled:text-mute" />
                  <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-mute font-semibold">FCFA</span>
                </div>
                @if (paymentAmountProblem(); as problem) {
                  <p class="mt-1 text-[11px] font-semibold text-rose-600">{{ problem }}</p>
                }
              </div>
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Date' : 'Date' }}</label>
                <input type="date" [(ngModel)]="draft.paidOn"
                  class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
              </div>
            </div>

            <div>
              <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Moyen de paiement' : 'Payment method' }}</label>
              <div class="flex flex-wrap gap-1.5">
                @for (c of activeChannels(); track c.code) {
                  <button (click)="pickChannel(c)"
                    class="h-10 px-3 text-[12px] font-semibold rounded-lg border transition"
                    [class]="draft.method === c.code ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                    {{ fr() ? c.labelFr : c.labelEn }}
                  </button>
                } @empty {
                  <span class="text-xs text-amber-700">
                    {{ fr() ? 'Aucun moyen de paiement actif — activez-en un dans l’onglet Moyens de paiement.'
                            : 'No active payment method — enable one in the Payment methods tab.' }}
                  </span>
                }
              </div>
            </div>

            @if (selectedChannel(); as ch) {
              @if (ch.requiresReference) {
                <div>
                  <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">
                    {{ fr() ? 'Référence de transaction' : 'Transaction reference' }} *
                  </label>
                  <input [(ngModel)]="draft.reference"
                    [placeholder]="fr() ? 'ID de la transaction communiqué par le parent' : 'Transaction ID provided by the parent'"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono" />
                  @if (ch.accountRef) {
                    <p class="text-[11px] text-mute mt-1">
                      {{ fr() ? 'Compte de l’école' : 'School account' }} : <b>{{ ch.accountRef }}</b>
                      @if (ch.accountName) { · {{ ch.accountName }} }
                    </p>
                  }
                </div>
              }
            }

            @if (payError(); as e) {
              <div class="text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div>
            }
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-100">
            <button (click)="closePayment()"
              class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              {{ i18n.t('cancel') }}
            </button>
            <button (click)="save()" [disabled]="!canSubmitPayment()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
              <bbc-icon name="receipt" [s]="16" /> {{ fr() ? 'Générer le reçu' : 'Generate receipt' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Receipt modal -->
    @if (receipt(); as r) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-black/40" (click)="receipt.set(null)"></div>
        <div class="relative bg-white rounded-xl2 shadow-card w-full max-w-md fade-in">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <div class="text-base font-semibold text-ink">{{ fr() ? 'Reçu' : 'Receipt' }}</div>
            <button (click)="receipt.set(null)" class="text-mute hover:text-ink"><bbc-icon name="x" [s]="18" /></button>
          </div>
          <div class="p-5">
            <div class="receipt-bg rounded-lg p-6 border border-slate-200">
              <div class="flex items-center gap-3 pb-4 border-b-2 border-brand-600">
                <div class="w-12 h-12 rounded-lg bg-brand-600 text-white flex items-center justify-center font-display font-bold text-lg shrink-0">B</div>
                <div class="flex-1 min-w-0">
                  <div class="font-display text-lg font-bold text-brand-700 leading-tight">Bayo Bilingual Complex</div>
                  <div class="text-[11px] text-mute">Maroua, Cameroun · Tél +237 6 99 00 00 00</div>
                </div>
                <div class="text-right">
                  <div class="text-[10px] uppercase tracking-wider text-gold-500 font-bold">{{ fr() ? 'Reçu' : 'Receipt' }}</div>
                  <div class="text-sm font-mono font-bold text-ink">{{ r.receiptNo }}</div>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4 my-5 text-sm">
                <div>
                  <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Payé par' : 'Paid by' }}</div>
                  <div class="font-semibold text-ink font-mono text-xs">{{ shortId(r.studentId) }}</div>
                </div>
                <div>
                  <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Date' : 'Date' }}</div>
                  <div class="font-semibold text-ink">{{ r.paidOn }}</div>
                  <div class="text-xs text-mute">{{ fr() ? 'Année' : 'Year' }} 2025-2026</div>
                </div>
              </div>

              <div class="bg-white border border-slate-200 rounded-lg overflow-hidden mb-4">
                <div class="flex items-center justify-between px-4 py-2.5 border-b border-slate-100">
                  <span class="text-sm font-semibold">
                    @if (r.tranche) { {{ fr() ? 'Tranche' : 'Installment' }} {{ r.tranche }} — }
                    {{ fr() ? 'Scolarité' : 'Tuition' }}
                  </span>
                  <span class="text-sm font-mono font-bold">{{ money(r.amount) }}</span>
                </div>
                <div class="flex items-center justify-between px-4 py-2.5 bg-gold-50">
                  <span class="text-sm font-bold text-brand-700">{{ fr() ? 'TOTAL PAYÉ' : 'TOTAL PAID' }}</span>
                  <span class="text-lg font-bold text-brand-700 font-mono">{{ money(r.amount) }}</span>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Méthode' : 'Method' }}</div>
                  <div class="font-semibold text-ink">{{ r.method }}</div>
                </div>
                <div>
                  <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Statut' : 'Status' }}</div>
                  <bbc-status-pill status="paid" />
                </div>
              </div>

              <div class="mt-6 pt-4 border-t border-slate-200 flex items-end justify-between">
                <div class="text-[10px] text-mute leading-relaxed">
                  {{ fr() ? 'Reçu généré électroniquement — valide sans signature.' : 'Electronically generated — valid without signature.' }}<br />
                  {{ fr() ? 'Conservez ce reçu pour vos archives.' : 'Keep this receipt for your records.' }}
                </div>
                <div class="text-right">
                  <div class="font-display text-gold-500 italic text-sm">Bayo</div>
                  <div class="text-[10px] text-mute">— {{ fr() ? 'Cachet école' : 'School stamp' }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-100">
            <button (click)="receipt.set(null)"
              class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              {{ i18n.t('cancel') }}
            </button>
            <button (click)="print()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class FinanceComponent {
  protected i18n = inject(I18nService);
  private api = inject(FinanceApi);
  private studentApi = inject(StudentApi);
  private auth = inject(AuthService);

  protected summary = signal<FinanceSummary | null>(null);
  protected rows = signal<PaymentView[]>([]);
  protected canWrite = this.auth.can('finance', 'write');

  protected tab = signal<Tab>('payments');
  protected methodFilter = signal<string | null>(null);
  protected paymentOpen = signal(false);
  protected receipt = signal<PaymentView | null>(null);
  protected draft: PaymentRequest = this.blank();

  protected setupClasses = signal<ClassView[]>([]);
  protected payClass = signal('');
  protected payStudents = signal<Student[]>([]);
  protected payError = signal<string | null>(null);
  /** Situation de l'élève sélectionné : sert à proposer la bonne tranche. */
  protected statement = signal<StudentFeeStatementView | null>(null);

  // --- moyens de paiement ---------------------------------------------------
  protected channels = signal<PaymentChannelView[]>([]);
  protected channelEdit = signal<string | null>(null);
  protected channelDraft: PaymentChannelUpdate = {};
  protected channelMsg = signal<{ ok: boolean; text: string } | null>(null);

  // --- debtors -------------------------------------------------------------
  /**
   * Holds /situation (every student), not /debtors (balances > 0 only): the KPIs need the
   * full expected total to state a recovery rate that agrees with the Reports module.
   * The list itself filters down to the debtors.
   */
  protected situation = signal<SituationView[]>([]);
  protected debtorsLoading = signal(false);
  protected debtorsError = signal(false);
  protected debtorQuery = signal('');
  protected debtorClassFilter = signal<string | null>(null);
  protected debtorStatusFilter = signal<'all' | 'unpaid' | 'partial'>('all');
  protected debtorsLoaded = false;

  // --- expenses ------------------------------------------------------------
  protected expenses = signal<ExpenseView[]>([]);
  protected expensesLoading = signal(false);
  protected expensesError = signal(false);
  protected categoryFilter = signal<string | null>(null);
  protected expenseFormOpen = signal(false);
  protected expenseSaving = signal(false);
  protected expenseError = signal<string | null>(null);
  protected confirmExpenseDel = signal<ExpenseView | null>(null);
  protected expenseDraft: ExpenseRequest = this.blankExpense();
  private expensesLoaded = false;

  protected readonly expenseCategories = [
    { value: 'salaires', fr: 'Salaires', en: 'Salaries' },
    { value: 'fournitures', fr: 'Fournitures', en: 'Supplies' },
    { value: 'energie', fr: 'Énergie', en: 'Energy' },
    { value: 'eau', fr: 'Eau', en: 'Water' },
    { value: 'maintenance', fr: 'Maintenance', en: 'Maintenance' },
    { value: 'transport', fr: 'Transport', en: 'Transport' },
    { value: 'cantine', fr: 'Cantine', en: 'Canteen' },
    { value: 'internet', fr: 'Internet', en: 'Internet' },
    { value: 'examens', fr: 'Examens', en: 'Exams' },
    { value: 'sport', fr: 'Sport', en: 'Sport' },
    { value: 'sante', fr: 'Santé', en: 'Health' },
    { value: 'divers', fr: 'Divers', en: 'Other' },
  ];

  // --- fee grid ------------------------------------------------------------
  protected feeConfigs = signal<FeeConfigView[]>([]);
  protected feeFormOpen = signal(false);
  protected feeSaving = signal(false);
  protected feeError = signal<string | null>(null);
  protected feeEditingId = signal<string | null>(null);
  protected feeDraft: FeeConfigUpdate = this.blankFee();
  protected confirmFeeDel = signal<FeeConfigView | null>(null);
  private feesLoaded = false;

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;

  protected revenue30 = computed(() => this.summary()?.totalRevenue30d ?? 0);
  protected expense30 = computed(() => this.summary()?.totalExpense30d ?? 0);
  protected balance30 = computed(() => this.summary()?.balance30d ?? 0);

  protected revData = computed<Pt[]>(() =>
    (this.summary()?.revenueSeries ?? []).map((d) => ({ label: d.date.slice(5), value: d.amount })));

  protected filtered = computed(() => {
    const m = this.methodFilter();
    return this.rows().filter((p) => !m || p.method === m);
  });

  // --- debtor derivations --------------------------------------------------
  protected debtors = computed(() => this.situation().filter((s) => s.balance > 0));
  protected filteredDebtors = computed(() => {
    const q = this.debtorQuery().trim().toLowerCase();
    const cls = this.debtorClassFilter();
    const status = this.debtorStatusFilter();
    let list = this.debtors();
    if (cls) list = list.filter((d) => d.className === cls);
    if (status !== 'all') list = list.filter((d) => d.status === status);
    if (q) list = list.filter((d) => (d.studentName ?? '').toLowerCase().includes(q));
    return list;
  });
  protected debtorClassOptions = computed(() =>
    [...new Set(this.situation().map((d) => d.className).filter((name): name is string => !!name))]
      .sort((a, b) => a.localeCompare(b, this.fr() ? 'fr' : 'en')),
  );
  protected debtTotal = computed(() => this.debtors().reduce((a, d) => a + d.balance, 0));
  /** School-wide, over every student — matches the Reports module's recovery rate. */
  protected paidTotal = computed(() => this.situation().reduce((a, d) => a + d.paid, 0));
  protected settledCount = computed(() => this.situation().filter((d) => d.balance === 0).length);
  protected unpaidCount = computed(() => this.debtors().filter((d) => d.status === 'unpaid').length);
  protected partialCount = computed(() => this.debtors().filter((d) => d.status === 'partial').length);
  protected recoveryPct = computed(() => {
    const expected = this.situation().reduce((a, d) => a + d.total, 0);
    return expected ? Math.round((this.paidTotal() / expected) * 100) : 0;
  });

  // --- expense derivations -------------------------------------------------
  protected filteredExpenses = computed(() => {
    const c = this.categoryFilter();
    return this.expenses().filter((e) => !c || e.category === c);
  });
  protected expenseTotal = computed(() => this.filteredExpenses().reduce((a, e) => a + e.amount, 0));
  protected categoryOptions = computed(() =>
    [...new Set(this.expenses().map((e) => e.category))]
      .sort()
      .map((c) => ({ value: c, label: this.categoryLabel(c) })),
  );
  protected topCategory = computed(() => {
    const by = new Map<string, number>();
    for (const e of this.expenses()) by.set(e.category, (by.get(e.category) ?? 0) + e.amount);
    let best: { cat: string; amount: number } | null = null;
    for (const [cat, amount] of by) if (!best || amount > best.amount) best = { cat: this.categoryLabel(cat), amount };
    return best;
  });

  // --- fee derivations -----------------------------------------------------
  protected tranchesSum = computed(() => (this.feeDraft.tranches ?? []).reduce((a, t) => a + (Number(t.amount) || 0), 0));
  protected tranchesMatch = computed(() => !this.tranchesSum() || this.tranchesSum() === Number(this.feeDraft.total));

  protected tabs = computed(() => [
    { id: 'payments', label: this.fr() ? 'Encaissements' : 'Payments' },
    { id: 'debtors', label: this.fr() ? 'Débiteurs' : 'Debtors' },
    { id: 'expenses', label: this.fr() ? 'Dépenses' : 'Expenses' },
    { id: 'fees', label: this.fr() ? 'Frais' : 'Fees' },
    { id: 'channels', label: this.fr() ? 'Moyens de paiement' : 'Payment methods' },
  ]);

  /** Canaux ouverts à l'encaissement ; le filtre de l'historique, lui, montre tout. */
  protected activeChannels = computed(() => this.channels().filter((c) => c.enabled));

  protected selectedChannel(): PaymentChannelView | null {
    return this.channels().find((c) => c.code === this.draft.method) ?? null;
  }

  protected methodOptions = computed(() =>
    this.channels().map((c) => ({ value: c.code, label: this.fr() ? c.labelFr : c.labelEn })));

  /** Un canal peut exiger une référence : le bouton reste inactif tant qu'elle manque. */
  protected canSubmitPayment(): boolean {
    if (!this.draft.studentId || !!this.paymentAmountProblem()) return false;
    const ch = this.selectedChannel();
    if (!ch) return false;
    return !ch.requiresReference || !!this.draft.reference?.trim();
  }

  protected paymentAmountProblem(): string | null {
    if (!this.draft.studentId) return null;
    const current = this.statement();
    if (!current) return this.fr() ? 'Chargement du solde en cours…' : 'Loading the balance…';
    if (current.balance <= 0) return this.fr()
      ? 'Les frais de cet élève sont déjà soldés.'
      : 'This student’s fees are already paid in full.';
    const amount = Number(this.draft.amount);
    if (!Number.isFinite(amount) || amount <= 0) return this.fr()
      ? 'Saisissez un montant supérieur à zéro.'
      : 'Enter an amount greater than zero.';
    if (amount > current.balance) return this.fr()
      ? `Le montant dépasse le solde restant de ${this.money(current.balance)}.`
      : `The amount exceeds the remaining balance of ${this.money(current.balance)}.`;
    return null;
  }

  protected hasDebtorFilters(): boolean {
    return !!this.debtorClassFilter() || this.debtorStatusFilter() !== 'all' || !!this.debtorQuery().trim();
  }

  protected clearDebtorFilters(): void {
    this.debtorClassFilter.set(null);
    this.debtorStatusFilter.set('all');
    this.debtorQuery.set('');
  }

  constructor() {
    this.reloadSummary();
    this.reloadPayments();
    this.api.context().subscribe({
      next: (context) => this.setupClasses.set(context.classes.map((c) => ({
        id: c.id,
        name: c.name,
        sectionId: c.code,
        sectionLabel: `${c.level} / ${c.subsystem}`,
        subsystem: c.subsystem,
        level: c.level,
        studentCount: 0,
        teacherCount: 0,
      }))),
      error: () => {},
    });
    // Les canaux servent dès l'onglet Encaissements (filtre, libellés, saisie).
    this.reloadChannels();
  }

  private reloadSummary(): void {
    this.api.summary().subscribe({ next: (s) => this.summary.set(s), error: () => {} });
  }
  private reloadPayments(): void {
    this.api.payments().subscribe({ next: (p) => this.rows.set(p), error: () => {} });
  }

  /** Tab data is fetched on first visit, so opening Finance costs one round-trip, not four. */
  protected setTab(id: string): void {
    const t = id as Tab;
    this.tab.set(t);
    if (t === 'debtors' && !this.debtorsLoaded) this.reloadDebtors();
    if (t === 'expenses' && !this.expensesLoaded) this.reloadExpenses();
    if (t === 'fees' && !this.feesLoaded) this.reloadFees();
  }

  // ------------------------------------------------------- moyens de paiement
  protected reloadChannels(): void {
    this.api.channels().subscribe({ next: (c) => this.channels.set(c), error: () => {} });
  }

  protected toggleChannelEdit(code: string): void {
    if (this.channelEdit() === code) { this.channelEdit.set(null); return; }
    const c = this.channels().find((x) => x.code === code);
    if (!c) return;
    this.channelDraft = {
      labelFr: c.labelFr, labelEn: c.labelEn,
      accountRef: c.accountRef ?? '', accountName: c.accountName ?? '',
      instructionsFr: c.instructionsFr ?? '', instructionsEn: c.instructionsEn ?? '',
    };
    this.channelMsg.set(null);
    this.channelEdit.set(code);
  }

  protected saveChannel(c: PaymentChannelView): void {
    this.api.updateChannel(c.code, this.channelDraft).subscribe({
      next: () => {
        this.channelEdit.set(null);
        this.channelMsg.set({ ok: true, text: this.fr() ? 'Moyen de paiement enregistré.' : 'Payment method saved.' });
        this.reloadChannels();
      },
      error: (e) => this.channelMsg.set({
        ok: false, text: e?.error?.message ?? (this.fr() ? 'Enregistrement impossible.' : 'Could not save.'),
      }),
    });
  }

  /** Bascule immédiate d'un interrupteur (actif, visible, référence obligatoire). */
  protected patchChannel(c: PaymentChannelView, patch: PaymentChannelUpdate): void {
    this.api.updateChannel(c.code, patch).subscribe({
      next: () => this.reloadChannels(),
      error: (e) => this.channelMsg.set({
        ok: false, text: e?.error?.message ?? (this.fr() ? 'Modification impossible.' : 'Update failed.'),
      }),
    });
  }

  protected channelIcon(code: string): string {
    return { CASH: 'cash', OM: 'phone', MOMO: 'phone', SARA: 'phone', MPGS: 'receipt', TRANSFER: 'wallet' }[code] ?? 'wallet';
  }

  protected channelTone(code: string): string {
    return {
      CASH: 'bg-emerald-50 text-emerald-700',
      OM: 'bg-orange-50 text-orange-700',
      MOMO: 'bg-gold-50 text-gold-600',
      SARA: 'bg-sky-50 text-sky-700',
      MPGS: 'bg-brand-50 text-brand-700',
      TRANSFER: 'bg-slate-100 text-slate-700',
    }[code] ?? 'bg-slate-100 text-slate-700';
  }

  protected methodLabel(p: PaymentView): string {
    return (this.fr() ? p.methodLabelFr : p.methodLabelEn) || p.method;
  }

  protected levelLabel(level: string): string {
    switch ((level || '').toLowerCase()) {
      case 'maternelle': return this.fr() ? 'Maternelle' : 'Kindergarten';
      case 'secondary': return this.fr() ? 'Secondaire' : 'Secondary';
      default: return this.fr() ? 'Primaire' : 'Primary';
    }
  }

  /** Niveau de la classe d'un élève, pour dire d'où vient la grille appliquée. */
  protected levelOfClass(className: string | null): string {
    const c = this.setupClasses().find((x) => x.name === className);
    return c ? this.levelLabel(c.level) : (this.fr() ? 'Niveau' : 'Level');
  }

  protected reloadDebtors(): void {
    this.debtorsLoaded = true;
    this.debtorsLoading.set(true);
    this.debtorsError.set(false);
    this.api.situation().subscribe({
      next: (d) => { this.situation.set(d); this.debtorsLoading.set(false); },
      error: () => { this.debtorsError.set(true); this.debtorsLoading.set(false); },
    });
  }

  protected reloadExpenses(): void {
    this.expensesLoaded = true;
    this.expensesLoading.set(true);
    this.expensesError.set(false);
    this.api.expenses().subscribe({
      next: (e) => { this.expenses.set(e); this.expensesLoading.set(false); },
      error: () => { this.expensesError.set(true); this.expensesLoading.set(false); },
    });
  }

  private reloadFees(): void {
    this.feesLoaded = true;
    this.api.feeConfig().subscribe({ next: (c) => this.feeConfigs.set(c), error: () => {} });
  }

  // --- expenses ------------------------------------------------------------
  protected categoryLabel(value: string): string {
    const c = this.expenseCategories.find((x) => x.value === value);
    return c ? (this.fr() ? c.fr : c.en) : value;
  }

  protected openExpense(): void {
    this.expenseDraft = this.blankExpense();
    this.expenseError.set(null);
    this.expenseFormOpen.set(true);
  }
  protected cancelExpense(): void {
    this.expenseFormOpen.set(false);
    this.expenseError.set(null);
  }

  protected saveExpense(): void {
    const d = this.expenseDraft;
    if (!d.spentOn || !d.label.trim() || !d.amount || d.amount <= 0) {
      this.expenseError.set(this.fr()
        ? 'Date, libellé et montant (> 0) sont obligatoires.'
        : 'Date, label and amount (> 0) are required.');
      return;
    }
    this.expenseSaving.set(true);
    this.api.addExpense({ ...d, label: d.label.trim() }).subscribe({
      next: () => {
        this.expenseSaving.set(false);
        this.expenseFormOpen.set(false);
        this.reloadExpenses();
        this.reloadSummary();   // the 30-day expense KPI moves with it
      },
      error: () => {
        this.expenseSaving.set(false);
        this.expenseError.set(this.fr() ? 'Enregistrement impossible.' : 'Could not save.');
      },
    });
  }

  protected removeExpense(e: ExpenseView): void {
    this.confirmExpenseDel.set(null);
    this.api.removeExpense(e.id).subscribe({
      next: () => { this.reloadExpenses(); this.reloadSummary(); },
      error: () => {},
    });
  }

  // --- fee grid ------------------------------------------------------------
  protected openFee(c: FeeConfigView | null): void {
    this.feeError.set(null);
    this.feeEditingId.set(c?.id ?? null);
    this.feeDraft = c
      ? {
          level: c.level, subsystem: c.subsystem, classId: c.classId, total: c.total,
          tranches: c.tranches.map((t) => ({ ...t })), items: c.items,
        }
      : this.blankFee();
    this.feeFormOpen.set(true);
  }
  protected cancelFee(): void {
    this.feeFormOpen.set(false);
    this.feeError.set(null);
  }

  /** Bascule entre grille de niveau et surcharge de classe. */
  protected setFeeScope(scope: 'level' | 'class'): void {
    if (scope === 'level') {
      this.feeDraft = { ...this.feeDraft, classId: null };
      return;
    }
    const first = this.setupClasses()[0];
    this.feeDraft = { ...this.feeDraft, classId: first?.id ?? null };
    if (first) this.onFeeClass(first.id);
  }

  /** Le niveau et le sous-système suivent la classe choisie : ils décrivent la même réalité. */
  protected onFeeClass(classId: string): void {
    const c = this.setupClasses().find((x) => x.id === classId);
    this.feeDraft = {
      ...this.feeDraft,
      classId,
      level: c?.level ?? this.feeDraft.level,
      subsystem: c?.subsystem ?? this.feeDraft.subsystem,
    };
  }

  protected addTranche(): void {
    const next = (this.feeDraft.tranches ?? []).length + 1;
    this.feeDraft = {
      ...this.feeDraft,
      tranches: [...(this.feeDraft.tranches ?? []), { label: 'T' + next, amount: 0, dueOn: null }],
    };
  }
  protected removeTranche(i: number): void {
    const t = [...(this.feeDraft.tranches ?? [])];
    t.splice(i, 1);
    this.feeDraft = { ...this.feeDraft, tranches: t };
  }
  protected setTrancheField(i: number, field: 'label' | 'amount' | 'dueOn', value: unknown): void {
    const t = [...(this.feeDraft.tranches ?? [])];
    const current = { ...t[i] };
    if (field === 'amount') current.amount = Number(value) || 0;
    else if (field === 'label') current.label = String(value ?? '');
    else current.dueOn = value ? String(value) : null;
    t[i] = current;
    this.feeDraft = { ...this.feeDraft, tranches: t };
  }

  protected saveFee(): void {
    const d = this.feeDraft;
    if (!d.level || !d.total || d.total <= 0) {
      this.feeError.set(this.fr() ? 'Niveau et total annuel (> 0) sont obligatoires.' : 'Level and annual total (> 0) are required.');
      return;
    }
    this.feeSaving.set(true);
    this.api.saveFeeConfig({ ...d, tranches: (d.tranches ?? []).filter((t) => t.amount > 0) }).subscribe({
      next: () => { this.feeSaving.set(false); this.feeFormOpen.set(false); this.reloadFees(); this.reloadDebtors(); },
      error: (e) => {
        this.feeSaving.set(false);
        // The server rejects a tranche sum that differs from the total — show its wording, not a generic one.
        this.feeError.set(e?.error?.message ?? (this.fr() ? 'Enregistrement impossible.' : 'Could not save.'));
      },
    });
  }

  protected removeFee(c: FeeConfigView): void {
    this.confirmFeeDel.set(null);
    this.api.deleteFeeConfig(c.id).subscribe({
      next: () => { this.reloadFees(); this.reloadDebtors(); },
      error: (e) => this.feeError.set(e?.error?.message ?? (this.fr() ? 'Suppression impossible.' : 'Delete failed.')),
    });
  }

  // --- exports -------------------------------------------------------------
  protected exportCurrentTab(): void {
    switch (this.tab()) {
      case 'payments': this.exportPayments(); break;
      case 'debtors': this.exportDebtors(); break;
      case 'expenses': this.exportExpenses(); break;
      case 'fees': this.exportFees(); break;
    }
  }

  protected exportPayments(): void {
    downloadCsv(
      stampedName('encaissements'),
      ['Recu', 'Eleve', 'Matricule', 'Classe', 'Tranche', 'Moyen', 'Reference', 'Date', 'Montant'],
      this.filtered().map((p) => [p.receiptNo, p.studentName, p.matricule, p.className,
        p.tranche ?? '', this.methodLabel(p), p.reference ?? '', p.paidOn, p.amount]),
    );
  }

  protected exportDebtors(): void {
    downloadCsv(
      stampedName('debiteurs'),
      ['Eleve', 'Classe', 'Attendu', 'Paye', 'Solde', 'Tranches payees', 'Statut', 'Progression %'],
      this.filteredDebtors().map((d) => [d.studentName, d.className, d.total, d.paid, d.balance,
        d.tranchesPaid, d.status, d.progressPct]),
    );
  }

  protected exportExpenses(): void {
    downloadCsv(
      stampedName('depenses'),
      ['Date', 'Categorie', 'Libelle', 'Montant'],
      this.filteredExpenses().map((e) => [e.spentOn, this.categoryLabel(e.category), e.label, e.amount]),
    );
  }

  protected exportFees(): void {
    downloadCsv(
      stampedName('grille-frais'),
      ['Portee', 'Niveau', 'Sous-systeme', 'Total annuel', 'Tranches'],
      this.feeConfigs().map((c) => [c.className ?? 'Niveau', c.level, c.subsystem ?? 'ALL', c.total,
        c.tranches.map((t) => `${t.label}=${t.amount}${t.dueOn ? '@' + t.dueOn : ''}`).join(' + ')]),
    );
  }

  protected openPayment(): void {
    this.draft = this.blank();
    // Premier canal actif par défaut — l'espèce n'est pas toujours acceptée.
    const first = this.activeChannels()[0];
    if (first) this.draft.method = first.code;
    this.payError.set(null);
    this.statement.set(null);
    this.payClass.set('');
    this.payStudents.set([]);
    this.paymentOpen.set(true);
  }

  protected openPaymentFor(debtor: SituationView): void {
    this.openPayment();
    this.payClass.set(debtor.className);
    this.studentApi.list(debtor.className).subscribe({
      next: (students) => {
        this.payStudents.set(students);
        if (students.some((student) => student.id === debtor.studentId)) this.onPayStudent(debtor.studentId);
      },
      error: () => {
        this.payStudents.set([]);
        this.payError.set(this.fr()
          ? 'Impossible de charger les élèves de cette classe.'
          : 'Could not load students for this class.');
      },
    });
  }

  protected closePayment(): void {
    this.paymentOpen.set(false);
  }

  protected onPayClass(name: string): void {
    this.payClass.set(name);
    this.draft.studentId = '';
    this.statement.set(null);
    this.payStudents.set([]);
    if (!name) return;
    this.studentApi.list(name).subscribe({ next: (r) => this.payStudents.set(r), error: () => this.payStudents.set([]) });
  }

  /** À la sélection d'un élève, on charge sa situation : grille, tranches et reste dû. */
  protected onPayStudent(studentId: string): void {
    this.draft.studentId = studentId;
    this.draft.amount = 0;
    this.draft.tranche = undefined;
    this.statement.set(null);
    this.payError.set(null);
    if (!studentId) return;
    this.api.statement(studentId).subscribe({
      next: (st) => {
        this.statement.set(st);
        const next = st.tranches.find((t) => t.remaining > 0);
        if (next) this.pickTranche(next);
      },
      error: () => this.statement.set(null),
    });
  }

  /** Pré-remplit le montant avec ce qui reste dû sur la tranche choisie. */
  protected pickTranche(t: TrancheStatusView): void {
    if (t.remaining <= 0) return;
    this.draft.tranche = t.index;
    this.draft.amount = t.remaining;
  }

  protected pickChannel(c: PaymentChannelView): void {
    this.draft.method = c.code;
    if (!c.requiresReference) this.draft.reference = null;
  }
  protected viewReceipt(p: PaymentView): void {
    this.receipt.set(p);
  }
  protected print(): void {
    window.print();
  }

  protected save(): void {
    if (!this.canSubmitPayment()) return;
    this.payError.set(null);
    this.api.recordPayment(this.draft).subscribe({
      next: (created) => {
        this.paymentOpen.set(false);
        this.draft = this.blank();
        this.statement.set(null);
        this.reloadSummary();
        this.reloadPayments();
        if (this.debtorsLoaded) this.reloadDebtors();
        if (created) this.receipt.set(created);
      },
      // Le serveur refuse un canal désactivé ou une référence manquante : son message est le plus précis.
      error: (e) => this.payError.set(e?.error?.message
        ?? (this.fr() ? 'Encaissement impossible.' : 'Could not record the payment.')),
    });
  }

  protected shortId(id: string): string {
    return id.length > 8 ? id.slice(0, 8) : id;
  }

  /**
   * Brouillon vide. Le canal est fixé ici à l'espèce et ajusté à l'ouverture de la
   * fenêtre : cette méthode sert à initialiser un champ de classe, donc avant que les
   * `computed` du composant n'existent.
   */
  private blank(): PaymentRequest {
    return {
      studentId: '', amount: 0, method: 'CASH', reference: null, tranche: 1,
      paidOn: new Date().toISOString().slice(0, 10),
    };
  }

  private blankExpense(): ExpenseRequest {
    return { spentOn: new Date().toISOString().slice(0, 10), category: 'fournitures', label: '', amount: 0 };
  }

  private blankFee(): FeeConfigUpdate {
    return {
      level: 'primary', subsystem: null, classId: null, total: 0,
      tranches: [
        { label: 'T1', amount: 0, dueOn: null },
        { label: 'T2', amount: 0, dueOn: null },
        { label: 'T3', amount: 0, dueOn: null },
      ],
      items: null,
    };
  }
}
