import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  FinanceApi, PaymentRequest, SituationView, ExpenseView, ExpenseRequest,
  FeeConfigView, FeeConfigUpdate,
} from './finance.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { FinanceSummary, PaymentView } from '../../core/models';
import { downloadCsv, stampedName } from '../../core/csv';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  StatusPillComponent, TabsComponent, ChipFilterComponent, AreaChartComponent, Pt,
  ConfirmComponent,
} from '../../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

type Tab = 'payments' | 'debtors' | 'expenses' | 'fees';

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
                (change)="methodFilter.set($event)"
                [options]="[
                  { value: 'Espèces', label: fr() ? 'Espèces' : 'Cash' },
                  { value: 'Mobile Money', label: 'Mobile Money' },
                  { value: 'Virement', label: fr() ? 'Virement' : 'Transfer' }
                ]" />
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
                        <td class="py-2.5 text-mute">{{ p.method }}</td>
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
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
            <bbc-kpi tone="bad" icon="wallet" [label]="fr() ? 'Total impayé' : 'Total outstanding'"
              [value]="money(debtTotal())"
              [sub]="debtors().length + (fr() ? ' élèves concernés' : ' students concerned')" />
            <bbc-kpi tone="ok" icon="cash" [label]="fr() ? 'Déjà encaissé' : 'Collected'"
              [value]="money(paidTotal())"
              [sub]="fr() ? 'toutes classes confondues' : 'across all classes'" />
            <bbc-kpi tone="gold" icon="chart" [label]="fr() ? 'Taux de recouvrement' : 'Recovery rate'"
              [value]="recoveryPct() + '%'"
              [sub]="situation().length + (fr() ? ' élèves au total' : ' students in total')" />
          </div>

          <bbc-card [title]="fr() ? 'Liste des débiteurs' : 'Debtors list'"
            [subtitle]="filteredDebtors().length + (fr() ? ' élèves avec un solde' : ' students with a balance')">
            <div action class="flex items-center gap-2">
              <input [ngModel]="debtorQuery()" (ngModelChange)="debtorQuery.set($event)" name="debtorQuery"
                [placeholder]="fr() ? 'Rechercher un élève, une classe…' : 'Search a student, a class…'"
                class="h-9 w-56 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
              <button (click)="exportDebtors()" [disabled]="!filteredDebtors().length"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
              </button>
            </div>

            @if (debtorsLoading()) {
              <bbc-empty icon="wallet" [label]="fr() ? 'Chargement…' : 'Loading…'" />
            } @else if (debtorsError()) {
              <div class="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-4 py-3">
                {{ fr() ? 'Impossible de charger les débiteurs.' : 'Could not load debtors.' }}
                <button (click)="reloadDebtors()" class="font-bold underline ml-1">{{ fr() ? 'Réessayer' : 'Retry' }}</button>
              </div>
            } @else if (!filteredDebtors().length) {
              <bbc-empty icon="wallet"
                [label]="debtorQuery() ? (fr() ? 'Aucun résultat' : 'No results')
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
                      <th class="text-right font-semibold py-2 pr-5">{{ fr() ? 'Statut' : 'Status' }}</th>
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
                        <td class="py-2.5 pr-5 text-right"><bbc-status-pill [status]="d.status" /></td>
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
                <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <div>
                    <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Niveau' : 'Level' }} *</label>
                    <select name="level" [(ngModel)]="feeDraft.level" [disabled]="!!feeEditingId()"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-100">
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
                  <div>
                    <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Total annuel (FCFA)' : 'Annual total (FCFA)' }} *</label>
                    <input type="number" name="total" [(ngModel)]="feeDraft.total" min="1"
                      class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </div>
                </div>

                <div>
                  <label class="block text-xs font-semibold text-mute mb-1">{{ fr() ? 'Tranches (FCFA)' : 'Installments (FCFA)' }}</label>
                  <div class="flex flex-wrap items-center gap-2">
                    @for (t of feeDraft.tranches; track $index) {
                      <div class="flex items-center gap-1">
                        <span class="text-[11px] font-bold text-mute">T{{ $index + 1 }}</span>
                        <input type="number" min="0" [ngModel]="t" (ngModelChange)="setTranche($index, $event)"
                          [name]="'tranche' + $index"
                          class="h-9 w-28 px-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                        <button type="button" (click)="removeTranche($index)" class="text-mute hover:text-rose-600">
                          <bbc-icon name="x" [s]="14" />
                        </button>
                      </div>
                    }
                    <button type="button" (click)="addTranche()"
                      class="inline-flex items-center gap-1 h-9 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                      <bbc-icon name="plus" [s]="12" /> {{ fr() ? 'Ajouter' : 'Add' }}
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
                      <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Niveau' : 'Level' }}</th>
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
                          {{ c.level === 'primary' ? (fr() ? 'Primaire' : 'Primary') : (fr() ? 'Secondaire' : 'Secondary') }}
                        </td>
                        <td class="py-2.5 text-mute">
                          {{ c.subsystem ? (c.subsystem === 'FR' ? 'Francophone' : (fr() ? 'Anglophone' : 'English')) : (fr() ? 'Les deux' : 'Both') }}
                        </td>
                        <td class="py-2.5">
                          <div class="flex flex-wrap gap-1">
                            @for (t of c.tranches; track $index) {
                              <span class="text-xs bg-brand-50 text-brand-700 px-2 py-0.5 rounded font-semibold">
                                T{{ $index + 1 }} · {{ money(t) }}
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
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <p class="text-[11px] text-mute mt-3">
                {{ fr()
                  ? 'La grille sert de référence au solde de chaque élève et au blocage des bulletins pour dette.'
                  : 'The grid drives each student’s balance and the debt-blocking of report cards.' }}
              </p>
            }
          </bbc-card>
        }
      }
    </div>

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
            <div>
              <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Élève (matricule / ID)' : 'Student (ID)' }}</label>
              <input [(ngModel)]="draft.studentId"
                [placeholder]="fr() ? 'Matricule ou identifiant élève…' : 'Student ID…'"
                class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono" />
            </div>

            <div>
              <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Tranche' : 'Installment' }}</label>
              <div class="grid grid-cols-3 gap-2">
                @for (n of [1, 2, 3]; track n) {
                  <button (click)="draft.tranche = n"
                    class="h-10 text-xs font-semibold rounded-lg border transition"
                    [class]="draft.tranche === n ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                    T{{ n }}
                  </button>
                }
              </div>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Montant' : 'Amount' }}</label>
                <div class="relative">
                  <input type="number" [(ngModel)]="draft.amount"
                    class="w-full h-10 px-3 pr-16 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono" />
                  <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-mute font-semibold">FCFA</span>
                </div>
              </div>
              <div>
                <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ fr() ? 'Méthode' : 'Method' }}</label>
                <div class="grid grid-cols-3 gap-1.5">
                  @for (m of methods; track m) {
                    <button (click)="draft.method = m"
                      class="h-10 text-[11px] font-semibold rounded-lg border transition px-1"
                      [class]="draft.method === m ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                      {{ m === 'Espèces' ? (fr() ? 'Espèces' : 'Cash') : m === 'Virement' ? (fr() ? 'Virement' : 'Transfer') : 'MoMo' }}
                    </button>
                  }
                </div>
              </div>
            </div>
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-100">
            <button (click)="closePayment()"
              class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              {{ i18n.t('cancel') }}
            </button>
            <button (click)="save()" [disabled]="!draft.studentId || !draft.amount"
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
  private auth = inject(AuthService);

  protected summary = signal<FinanceSummary | null>(null);
  protected rows = signal<PaymentView[]>([]);
  protected canWrite = this.auth.can('finance', 'write');

  protected tab = signal<Tab>('payments');
  protected methodFilter = signal<string | null>(null);
  protected paymentOpen = signal(false);
  protected receipt = signal<PaymentView | null>(null);
  protected draft: PaymentRequest = this.blank();
  protected methods = ['Espèces', 'Mobile Money', 'Virement'];

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
  private debtorsLoaded = false;

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
    const list = this.debtors();
    if (!q) return list;
    return list.filter(
      (d) => (d.studentName ?? '').toLowerCase().includes(q) || (d.className ?? '').toLowerCase().includes(q),
    );
  });
  protected debtTotal = computed(() => this.debtors().reduce((a, d) => a + d.balance, 0));
  /** School-wide, over every student — matches the Reports module's recovery rate. */
  protected paidTotal = computed(() => this.situation().reduce((a, d) => a + d.paid, 0));
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
  protected tranchesSum = computed(() => (this.feeDraft.tranches ?? []).reduce((a, t) => a + (Number(t) || 0), 0));
  protected tranchesMatch = computed(() => !this.tranchesSum() || this.tranchesSum() === Number(this.feeDraft.total));

  protected tabs = computed(() => [
    { id: 'payments', label: this.fr() ? 'Encaissements' : 'Payments' },
    { id: 'debtors', label: this.fr() ? 'Débiteurs' : 'Debtors' },
    { id: 'expenses', label: this.fr() ? 'Dépenses' : 'Expenses' },
    { id: 'fees', label: this.fr() ? 'Frais' : 'Fees' },
  ]);

  constructor() {
    this.reloadSummary();
    this.reloadPayments();
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
      ? { level: c.level, subsystem: c.subsystem, total: c.total, tranches: [...c.tranches], items: c.items }
      : this.blankFee();
    this.feeFormOpen.set(true);
  }
  protected cancelFee(): void {
    this.feeFormOpen.set(false);
    this.feeError.set(null);
  }
  protected addTranche(): void {
    this.feeDraft = { ...this.feeDraft, tranches: [...(this.feeDraft.tranches ?? []), 0] };
  }
  protected removeTranche(i: number): void {
    const t = [...(this.feeDraft.tranches ?? [])];
    t.splice(i, 1);
    this.feeDraft = { ...this.feeDraft, tranches: t };
  }
  protected setTranche(i: number, v: number): void {
    const t = [...(this.feeDraft.tranches ?? [])];
    t[i] = Number(v) || 0;
    this.feeDraft = { ...this.feeDraft, tranches: t };
  }

  protected saveFee(): void {
    const d = this.feeDraft;
    if (!d.level || !d.total || d.total <= 0) {
      this.feeError.set(this.fr() ? 'Niveau et total annuel (> 0) sont obligatoires.' : 'Level and annual total (> 0) are required.');
      return;
    }
    this.feeSaving.set(true);
    this.api.saveFeeConfig({ ...d, tranches: (d.tranches ?? []).filter((t) => t > 0) }).subscribe({
      next: () => { this.feeSaving.set(false); this.feeFormOpen.set(false); this.reloadFees(); },
      error: (e) => {
        this.feeSaving.set(false);
        // The server rejects a tranche sum that differs from the total — show its wording, not a generic one.
        this.feeError.set(e?.error?.message ?? (this.fr() ? 'Enregistrement impossible.' : 'Could not save.'));
      },
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
      ['Recu', 'Eleve', 'Matricule', 'Classe', 'Tranche', 'Methode', 'Date', 'Montant'],
      this.filtered().map((p) => [p.receiptNo, p.studentName, p.matricule, p.className,
        p.tranche ?? '', p.method, p.paidOn, p.amount]),
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
      ['Niveau', 'Sous-systeme', 'Total annuel', 'Tranches'],
      this.feeConfigs().map((c) => [c.level, c.subsystem ?? 'ALL', c.total, c.tranches.join(' + ')]),
    );
  }

  protected openPayment(): void {
    this.draft = this.blank();
    this.paymentOpen.set(true);
  }
  protected closePayment(): void {
    this.paymentOpen.set(false);
  }
  protected viewReceipt(p: PaymentView): void {
    this.receipt.set(p);
  }
  protected print(): void {
    window.print();
  }

  protected save(): void {
    if (!this.draft.studentId || !this.draft.amount) return;
    this.api.recordPayment(this.draft).subscribe((created) => {
      this.paymentOpen.set(false);
      this.draft = this.blank();
      this.reloadSummary();
      this.reloadPayments();
      if (created) this.receipt.set(created);
    });
  }

  protected shortId(id: string): string {
    return id.length > 8 ? id.slice(0, 8) : id;
  }

  private blank(): PaymentRequest {
    return { studentId: '', amount: 0, method: 'Espèces', tranche: 1 };
  }

  private blankExpense(): ExpenseRequest {
    return { spentOn: new Date().toISOString().slice(0, 10), category: 'fournitures', label: '', amount: 0 };
  }

  private blankFee(): FeeConfigUpdate {
    return { level: 'primary', subsystem: null, total: 0, tranches: [0, 0, 0], items: null };
  }
}
