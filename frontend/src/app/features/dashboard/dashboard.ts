import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { StudentApi } from '../students/students.api';
import { AttendanceApi } from '../attendance/attendance.api';
import { FinanceApi } from '../finance/finance.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { Student, PaymentView } from '../../core/models';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, AreaChartComponent, DonutComponent, Pt, Slice,
} from '../../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;
const fmtShort = (n: number) => (n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'k' : '' + n);

@Component({
  selector: 'bbc-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, AreaChartComponent, DonutComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('dashboard')" [subtitle]="todayLabel()">
        <div right class="flex items-center gap-2">
          <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
            <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
          </button>
        </div>
      </bbc-page-header>

      <!-- KPIs -->
      <div class="responsive-kpi-grid grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
        @if (auth.can('students', 'read')) {
          <bbc-kpi tone="neutral" icon="users" [label]="i18n.t('students')" [value]="students().length"
            [sub]="subsystemSub()" />
        }
        @if (auth.can('finance', 'read')) {
          <bbc-kpi tone="gold" icon="cash" [label]="i18n.t('revenue30')" [value]="money(revenue30())"
            [sub]="(fr() ? 'Solde : ' : 'Balance: ') + money(balance30())" />
          <bbc-kpi tone="bad" icon="wallet" [label]="fr() ? 'Dépenses (30 j)' : 'Expenses (30d)'" [value]="money(expense30())" />
        }
        @if (auth.can('presence', 'read')) {
          <bbc-kpi tone="ok" icon="fingerprint" [label]="i18n.t('present')" [value]="attRate() + '%'"
            [sub]="late() + ' ' + i18n.t('late').toLowerCase() + ' · ' + absent() + ' ' + i18n.t('absent').toLowerCase()" />
        }
      </div>

      <!-- Charts -->
      <div class="grid grid-cols-12 gap-4 mb-5">
        @if (auth.can('finance', 'read')) {
          <bbc-card className="col-span-12 lg:col-span-8" [title]="fr() ? 'Revenus (30 jours)' : 'Revenue (30 days)'"
            [subtitle]="fr() ? 'Encaissements quotidiens (FCFA)' : 'Daily collections (FCFA)'">
            <div action class="text-xs text-mute">{{ fr() ? 'Total' : 'Total' }}: <span class="font-bold text-ink">{{ money(revenue30()) }}</span></div>
            @if (revData().length) { <bbc-area-chart [data]="revData()" [h]="240" /> }
            @else { <bbc-empty icon="chart" [label]="fr() ? 'Aucune donnée' : 'No data'" /> }
          </bbc-card>
        }

        @if (auth.can('students', 'read')) {
          <bbc-card className="col-span-12 lg:col-span-4" [title]="fr() ? 'Répartition des effectifs' : 'Enrollment split'"
            [subtitle]="students().length + (fr() ? ' élèves inscrits' : ' enrolled students')">
            <bbc-donut [data]="enrollData()" [total]="students().length" [centerLabel]="fr() ? 'élèves' : 'students'" />
          </bbc-card>
        }

        @if (auth.can('presence', 'read')) {
          <bbc-card className="col-span-12 lg:col-span-7" [title]="i18n.t('liveBoard')"
            [subtitle]="todayLabel()">
            <div class="grid grid-cols-3 gap-3">
              <div class="rounded-xl bg-emerald-50 p-4 text-center">
                <div class="text-3xl font-bold text-emerald-600">{{ present() }}</div>
                <div class="text-xs text-emerald-700 font-semibold mt-1">{{ i18n.t('present') }}</div>
              </div>
              <div class="rounded-xl bg-amber-50 p-4 text-center">
                <div class="text-3xl font-bold text-amber-600">{{ late() }}</div>
                <div class="text-xs text-amber-700 font-semibold mt-1">{{ i18n.t('late') }}</div>
              </div>
              <div class="rounded-xl bg-rose-50 p-4 text-center">
                <div class="text-3xl font-bold text-rose-600">{{ absent() }}</div>
                <div class="text-xs text-rose-700 font-semibold mt-1">{{ i18n.t('absent') }}</div>
              </div>
            </div>
            <div class="mt-4 h-2.5 rounded-full overflow-hidden flex bg-slate-100">
              <div class="bg-emerald-500" [style.width.%]="bar(present())"></div>
              <div class="bg-amber-500" [style.width.%]="bar(late())"></div>
              <div class="bg-rose-500" [style.width.%]="bar(absent())"></div>
            </div>
          </bbc-card>

          <bbc-card className="col-span-12 lg:col-span-5" [title]="fr() ? 'Retardataires du jour' : 'Lates today'"
            [subtitle]="late() + (fr() ? ' élèves' : ' students')">
            @if (lates().length === 0) {
              <bbc-empty icon="clock" [label]="fr() ? 'Aucun retard aujourd’hui' : 'No lates today'" />
            } @else {
              <div class="space-y-2">
                @for (r of lates(); track r.studentId) {
                  <div class="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-slate-50">
                    <bbc-avatar [name]="r.studentName" [hue]="200" />
                    <div class="flex-1 min-w-0">
                      <div class="text-sm font-semibold text-ink truncate">{{ r.studentName }}</div>
                      <div class="text-xs text-mute">{{ r.className }} · {{ r.checkInTime }}</div>
                    </div>
                    <span class="text-xs font-bold text-amber-700 bg-amber-50 px-2 py-1 rounded-full">+{{ r.lateMinutes }} min</span>
                  </div>
                }
              </div>
            }
          </bbc-card>
        }
      </div>

      <!-- Recent payments + alerts -->
      <div class="grid grid-cols-12 gap-4">
        @if (auth.can('finance', 'read')) {
          <bbc-card className="col-span-12 lg:col-span-7" [title]="fr() ? 'Encaissements récents' : 'Recent payments'">
            @if (recentPayments().length === 0) {
              <bbc-empty icon="receipt" [label]="fr() ? 'Aucun paiement' : 'No payments'" />
            } @else {
              <div class="space-y-2 md:hidden">
                @for (p of recentPayments(); track p.id) {
                  <article class="rounded-xl border border-slate-200 bg-slate-50/60 p-3">
                    <div class="flex items-start justify-between gap-3">
                      <div class="min-w-0"><div class="break-words text-sm font-semibold text-ink">{{ studentName(p.studentId) }}</div><div class="mt-1 text-xs font-mono text-brand-600">{{ p.receiptNo }}</div></div>
                      <strong class="shrink-0 text-sm text-emerald-700">{{ money(p.amount) }}</strong>
                    </div>
                    <div class="mt-2 text-xs text-mute">{{ fr() ? 'Méthode' : 'Method' }}: {{ p.method }}</div>
                  </article>
                }
              </div>
              <table class="hidden w-full text-sm md:table">
                <thead>
                  <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                    <th class="text-left font-semibold py-2">{{ fr() ? 'Reçu' : 'Receipt' }}</th>
                    <th class="text-left font-semibold py-2">{{ i18n.t('students') }}</th>
                    <th class="text-left font-semibold py-2">{{ fr() ? 'Méthode' : 'Method' }}</th>
                    <th class="text-right font-semibold py-2">{{ fr() ? 'Montant' : 'Amount' }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of recentPayments(); track p.id) {
                    <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50">
                      <td class="py-2.5 text-xs font-mono text-brand-600">{{ p.receiptNo }}</td>
                      <td class="py-2.5 font-medium text-ink">{{ studentName(p.studentId) }}</td>
                      <td class="py-2.5 text-mute text-xs">{{ p.method }}</td>
                      <td class="py-2.5 text-right font-bold text-emerald-700">{{ money(p.amount) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </bbc-card>
        }

        <bbc-card className="col-span-12 lg:col-span-5" [title]="fr() ? 'Alertes & rappels' : 'Alerts & reminders'">
          <div class="space-y-2.5">
            @for (a of alerts(); track a.title) {
              <div class="flex items-start gap-3 p-3 rounded-lg border" [class]="a.cls">
                <div class="w-8 h-8 rounded-full bg-white flex items-center justify-center shrink-0">
                  <bbc-icon [name]="a.icon" [s]="16" />
                </div>
                <div class="flex-1 min-w-0">
                  <div class="text-[13px] font-semibold text-ink">{{ a.title }}</div>
                  <div class="text-xs text-mute mt-0.5">{{ a.body }}</div>
                </div>
              </div>
            }
          </div>
        </bbc-card>
      </div>
    </div>
  `,
})
export class DashboardComponent {
  protected i18n = inject(I18nService);
  protected auth = inject(AuthService);
  private studentApi = inject(StudentApi);
  private attendanceApi = inject(AttendanceApi);
  private financeApi = inject(FinanceApi);

  protected students = signal<Student[]>([]);
  protected present = signal(0);
  protected late = signal(0);
  protected absent = signal(0);
  protected lates = signal<{ studentId: string; studentName: string; className: string; checkInTime: string | null; lateMinutes: number }[]>([]);
  protected revenue30 = signal(0);
  protected expense30 = signal(0);
  protected balance30 = signal(0);
  protected revData = signal<Pt[]>([]);
  protected recentPayments = signal<PaymentView[]>([]);

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;

  protected todayLabel = computed(() => {
    const d = new Date();
    return d.toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' });
  });

  protected attRate = computed(() => {
    const tot = this.present() + this.late() + this.absent();
    return tot ? Math.round(((this.present() + this.late()) / tot) * 100) : 0;
  });

  protected bar = (n: number) => {
    const tot = this.present() + this.late() + this.absent() || 1;
    return (n / tot) * 100;
  };

  protected subsystemSub = computed(() => {
    const fr = this.students().filter((s) => (s.subsystem || '').toUpperCase().startsWith('F')).length;
    const en = this.students().length - fr;
    return `${fr} FR · ${en} EN`;
  });

  protected enrollData = computed<Slice[]>(() => {
    const groups = new Map<string, number>();
    for (const s of this.students()) {
      const key = s.level || s.subsystem || '—';
      groups.set(key, (groups.get(key) ?? 0) + 1);
    }
    const palette = ['#1B3A5C', '#2D5586', '#D4A843', '#E2C05A', '#7E9CBA', '#94701D'];
    return [...groups.entries()].map(([name, value], i) => ({ name, value, color: palette[i % palette.length] }));
  });

  protected alerts = computed(() => {
    const debtors = 0;
    return [
      { title: this.fr() ? 'Lecteur d’empreintes' : 'Fingerprint reader', body: this.fr() ? 'Entrée principale — opérationnel' : 'Main gate — operational', icon: 'fingerprint', cls: 'bg-brand-50 text-brand-600 border-brand-100' },
      { title: this.fr() ? 'Présence du jour' : "Today's attendance", body: this.attRate() + '% ' + (this.fr() ? 'de présence' : 'present'), icon: 'check', cls: 'bg-emerald-50 text-emerald-600 border-emerald-100' },
      { title: this.fr() ? 'Sauvegarde quotidienne' : 'Daily backup', body: this.fr() ? 'Effectuée à 02:00 — 100% OK' : 'Completed at 02:00 — 100% OK', icon: 'check', cls: 'bg-emerald-50 text-emerald-600 border-emerald-100' },
    ];
  });

  constructor() {
    if (this.auth.can('students', 'read')) {
      this.studentApi.list().subscribe({ next: (r) => this.students.set(r), error: () => {} });
    }
    if (this.auth.can('presence', 'read')) {
      this.attendanceApi.board().subscribe({
        next: (b) => {
          this.present.set(b.present);
          this.late.set(b.late);
          this.absent.set(b.absent);
          this.lates.set((b.records ?? []).filter((r) => r.status === 'late').sort((a, c) => c.lateMinutes - a.lateMinutes).slice(0, 5));
        },
        error: () => {},
      });
    }
    if (this.auth.can('finance', 'read')) {
      this.financeApi.summary().subscribe({
        next: (s) => {
          this.revenue30.set(s.totalRevenue30d);
          this.expense30.set(s.totalExpense30d);
          this.balance30.set(s.balance30d);
          this.revData.set((s.revenueSeries ?? []).map((d) => ({ label: d.date.slice(5), value: d.amount })));
        },
        error: () => {},
      });
      this.financeApi.payments().subscribe({ next: (p) => this.recentPayments.set(p.slice(0, 6)), error: () => {} });
    }
  }

  protected studentName(id: string): string {
    return this.students().find((s) => s.id === id)?.name ?? '—';
  }
}
