import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceApi, PaymentRequest } from './finance.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { FinanceSummary, PaymentView } from '../../core/models';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  StatusPillComponent, TabsComponent, ChipFilterComponent, AreaChartComponent, Pt,
} from '../../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

@Component({
  selector: 'bbc-finance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, KpiComponent, PageHeaderComponent,
    EmptyComponent, StatusPillComponent, TabsComponent, ChipFilterComponent, AreaChartComponent,
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
          <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
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
                        <td class="py-2.5 font-semibold text-ink font-mono text-xs">{{ shortId(p.studentId) }}</td>
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
          <bbc-card [title]="fr() ? 'Liste des débiteurs' : 'Debtors list'"
            [subtitle]="fr() ? 'Soldes impayés par élève' : 'Outstanding balances per student'">
            <bbc-empty icon="wallet"
              [label]="fr() ? 'Données débiteurs non disponibles' : 'Debtor data not available'" />
          </bbc-card>
        }

        @case ('expenses') {
          <bbc-card [title]="fr() ? 'Journal des dépenses' : 'Expense log'"
            [subtitle]="i18n.t('expense30') + ' : ' + money(expense30())">
            <bbc-empty icon="doc"
              [label]="fr() ? 'Détail des dépenses non disponible' : 'Expense detail not available'" />
          </bbc-card>
        }
      }
    </div>

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

  protected tab = signal<'payments' | 'debtors' | 'expenses'>('payments');
  protected methodFilter = signal<string | null>(null);
  protected paymentOpen = signal(false);
  protected receipt = signal<PaymentView | null>(null);
  protected draft: PaymentRequest = this.blank();
  protected methods = ['Espèces', 'Mobile Money', 'Virement'];

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

  protected tabs = computed(() => [
    { id: 'payments', label: this.fr() ? 'Encaissements' : 'Payments' },
    { id: 'debtors', label: this.fr() ? 'Débiteurs' : 'Debtors' },
    { id: 'expenses', label: this.fr() ? 'Dépenses' : 'Expenses' },
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

  protected setTab(id: string): void {
    this.tab.set(id as 'payments' | 'debtors' | 'expenses');
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
}
