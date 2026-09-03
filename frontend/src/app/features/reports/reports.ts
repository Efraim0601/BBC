import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  DonutComponent, BarChartComponent, AreaChartComponent, Pt, Slice, BarGroup,
  ListPaginationComponent, paginateRows,
} from '../../core/ui';
import {
  ReportApi,
  FinanceReport,
  AttendanceRow,
  Demographics,
} from './reports.api';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

const PALETTE = ['#1B3A5C', '#D4A843', '#2D5586', '#E2C05A', '#7E9CBA', '#94701D', '#16A34A', '#DC2626'];

@Component({
  selector: 'bbc-reports',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, KpiComponent, PageHeaderComponent,
    EmptyComponent, DonutComponent, BarChartComponent, AreaChartComponent, ListPaginationComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('reports')"
        [subtitle]="fr() ? 'Analytique complète — école entière' : 'Full analytics — whole school'">
        <div right class="flex items-center gap-2">
          <button (click)="print()"
            class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
            <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
          </button>
          <button (click)="exportCsv()"
            class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
            <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter' : 'Export' }}
          </button>
        </div>
      </bbc-page-header>

      <!-- Financial KPIs -->
      <div class="responsive-kpi-grid grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
        <bbc-kpi tone="ok" icon="cash" [label]="fr() ? 'Recettes' : 'Revenue'"
          [value]="money(finance()?.totalRevenue ?? 0)" />
        <bbc-kpi tone="bad" icon="wallet" [label]="fr() ? 'Dépenses' : 'Expenses'"
          [value]="money(finance()?.totalExpense ?? 0)" />
        <bbc-kpi tone="neutral" icon="trendUp" [label]="fr() ? 'Solde net' : 'Net balance'"
          [value]="money(finance()?.balance ?? 0)" />
        <bbc-kpi tone="gold" icon="chart" [label]="fr() ? 'Taux de recouvrement' : 'Recovery rate'"
          [value]="(finance()?.recoveryRate ?? 0) + '%'" />
      </div>

      <div class="grid grid-cols-12 gap-4 mb-5">
        <!-- Financial breakdown -->
        <bbc-card className="col-span-12 lg:col-span-8"
          [title]="fr() ? 'Bilan financier' : 'Financial report'"
          [subtitle]="fr() ? 'Recettes vs dépenses (FCFA)' : 'Revenue vs expenses (FCFA)'">
          <div action class="text-xs text-mute">
            {{ fr() ? 'Solde' : 'Balance' }}:
            <span class="font-bold" [class]="(finance()?.balance ?? 0) >= 0 ? 'text-emerald-700' : 'text-rose-700'">{{ money(finance()?.balance ?? 0) }}</span>
          </div>
          @if (finance()) {
            <bbc-bar-chart [data]="financeBars()" [h]="220" />
            <div class="flex items-center justify-center gap-5 mt-3 text-xs">
              <span class="flex items-center gap-1.5"><span class="w-2.5 h-2.5 rounded-sm" style="background:#16A34A"></span><span class="text-mute">{{ fr() ? 'Recettes' : 'Revenue' }}</span></span>
              <span class="flex items-center gap-1.5"><span class="w-2.5 h-2.5 rounded-sm" style="background:#DC2626"></span><span class="text-mute">{{ fr() ? 'Dépenses' : 'Expenses' }}</span></span>
              <span class="flex items-center gap-1.5"><span class="w-2.5 h-2.5 rounded-sm" style="background:#1B3A5C"></span><span class="text-mute">{{ fr() ? 'Solde' : 'Balance' }}</span></span>
            </div>
          } @else {
            <bbc-empty icon="cash" [label]="fr() ? 'Aucune donnée financière' : 'No financial data'" />
          }
        </bbc-card>

        <!-- Demographics by sex -->
        <bbc-card className="col-span-12 lg:col-span-4"
          [title]="fr() ? 'Démographie — Sexe' : 'Demographics — Gender'"
          [subtitle]="(demographics()?.total ?? 0) + (fr() ? ' élèves' : ' students')">
          @if (sexData().length) {
            <bbc-donut [data]="sexData()" [total]="demographics()?.total ?? 0"
              [centerLabel]="fr() ? 'élèves' : 'students'" />
          } @else {
            <bbc-empty icon="users" [label]="fr() ? 'Aucune donnée' : 'No data'" />
          }
        </bbc-card>

        <!-- Demographics by level -->
        <bbc-card className="col-span-12 lg:col-span-6"
          [title]="fr() ? 'Démographie — Niveau' : 'Demographics — Level'"
          [subtitle]="fr() ? 'Effectifs par niveau' : 'Enrollment per level'">
          @if (levelBars().length) {
            <bbc-bar-chart [data]="levelBars()" [h]="220" />
            <div class="grid grid-cols-2 gap-1.5 mt-3 text-xs">
              @for (kv of entries(demographics()?.byLevel ?? {}); track kv[0]; let i = $index) {
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-sm shrink-0" [style.background]="color(i)"></span>
                  <span class="text-mute flex-1 truncate">{{ kv[0] }}</span>
                  <span class="font-semibold text-ink">{{ kv[1] }}</span>
                </div>
              }
            </div>
          } @else {
            <bbc-empty icon="users" [label]="fr() ? 'Aucune donnée' : 'No data'" />
          }
        </bbc-card>

        <!-- Demographics by subsystem -->
        <bbc-card className="col-span-12 lg:col-span-6"
          [title]="fr() ? 'Démographie — Sous-système' : 'Demographics — Subsystem'"
          [subtitle]="fr() ? 'Répartition par sous-système' : 'Split by subsystem'">
          @if (subsystemData().length) {
            <bbc-donut [data]="subsystemData()" [total]="demographics()?.total ?? 0"
              [centerLabel]="fr() ? 'élèves' : 'students'" />
          } @else {
            <bbc-empty icon="users" [label]="fr() ? 'Aucune donnée' : 'No data'" />
          }
        </bbc-card>
      </div>

      <!-- Monthly attendance -->
      <bbc-card className="col-span-12 mb-5"
        [title]="fr() ? 'Présence mensuelle' : 'Monthly attendance'"
        [subtitle]="fr() ? 'Taux de présence par élève' : 'Attendance rate per student'">
        <div action class="flex items-center gap-2">
          <input type="month" [ngModel]="month()" (ngModelChange)="month.set($event)"
            class="h-9 px-3 rounded-lg border border-slate-200 text-sm" />
          <button (click)="loadAttendance()"
            class="inline-flex items-center gap-1.5 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
            <bbc-icon name="calendar" [s]="16" /> {{ fr() ? 'Charger' : 'Load' }}
          </button>
        </div>

        @if (attendance().length) {
          @if (attTrend().length) {
            <div class="mb-4">
              <bbc-area-chart [data]="attTrend()" [h]="180" color="#1B3A5C" stroke="#142C46" />
            </div>
          }
          <div class="mb-4 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3 md:grid-cols-[1.5fr_1fr_auto] md:items-end">
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Recherche' : 'Search' }}</span>
              <div class="relative">
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
                <input [ngModel]="attendanceQuery()" (ngModelChange)="setAttendanceQuery($event)"
                  [placeholder]="fr() ? 'Élève ou classe…' : 'Student or class…'"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 text-sm focus:border-brand-400 focus:outline-none" />
              </div>
            </label>
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Classe' : 'Class' }}</span>
              <select [ngModel]="attendanceClass()" (ngModelChange)="setAttendanceClass($event)"
                class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
                <option value="">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>
                @for (name of attendanceClasses(); track name) { <option [value]="name">{{ name }}</option> }
              </select>
            </label>
            <button type="button" (click)="clearAttendanceFilters()" [disabled]="!attendanceQuery().trim() && !attendanceClass()"
              class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
              {{ fr() ? 'Effacer' : 'Clear' }}
            </button>
          </div>
          @if (!filteredAttendance().length) {
            <bbc-empty icon="calendar" [label]="fr() ? 'Aucun élève ne correspond aux filtres' : 'No student matches these filters'" />
          } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-[11px] uppercase tracking-wide text-mute border-b border-slate-100">
                  <th class="text-left font-semibold py-2">{{ fr() ? 'Élève' : 'Student' }}</th>
                  <th class="text-left font-semibold py-2">{{ fr() ? 'Classe' : 'Class' }}</th>
                  <th class="text-right font-semibold py-2">{{ i18n.t('present') }}</th>
                  <th class="text-right font-semibold py-2">{{ i18n.t('late') }}</th>
                  <th class="text-right font-semibold py-2">{{ i18n.t('absent') }}</th>
                  <th class="text-right font-semibold py-2">{{ fr() ? 'Taux' : 'Rate' }}</th>
                </tr>
              </thead>
              <tbody>
                @for (r of pagedAttendance(); track r.studentId) {
                  <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50">
                    <td class="py-2.5 font-medium text-ink">{{ r.studentName }}</td>
                    <td class="py-2.5 text-mute">{{ r.className }}</td>
                    <td class="py-2.5 text-right text-emerald-700 font-medium">{{ r.present }}</td>
                    <td class="py-2.5 text-right text-amber-700 font-medium">{{ r.late }}</td>
                    <td class="py-2.5 text-right text-rose-700 font-medium">{{ r.absent }}</td>
                    <td class="py-2.5 text-right font-bold" [class]="rateClass(r.rate)">{{ r.rate }}%</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <bbc-list-pagination class="mt-3 block" [total]="filteredAttendance().length" [page]="attendancePage()"
            [pageSize]="attendancePageSize()" [language]="fr() ? 'fr' : 'en'"
            (pageChange)="attendancePage.set($event)" (pageSizeChange)="setAttendancePageSize($event)" />
          }
        } @else {
          <bbc-empty icon="calendar" [label]="fr() ? 'Aucune donnée de présence' : 'No attendance data'" />
        }
      </bbc-card>
    </div>
  `,
})
export class ReportsComponent {
  protected i18n = inject(I18nService);
  private api = inject(ReportApi);

  protected finance = signal<FinanceReport | null>(null);
  protected demographics = signal<Demographics | null>(null);
  protected attendance = signal<AttendanceRow[]>([]);
  protected month = signal<string>(this.currentMonth());
  protected attendanceQuery = signal('');
  protected attendanceClass = signal('');
  protected attendancePage = signal(1);
  protected attendancePageSize = signal(25);

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;
  protected color = (i: number) => PALETTE[i % PALETTE.length];

  constructor() {
    this.api.finance().subscribe({ next: (f) => this.finance.set(f), error: () => {} });
    this.api.demographics().subscribe({ next: (d) => this.demographics.set(d), error: () => {} });
    this.loadAttendance();
  }

  protected loadAttendance(): void {
    this.attendancePage.set(1);
    this.api.attendanceMonthly(this.month()).subscribe({ next: (rows) => this.attendance.set(rows), error: () => {} });
  }

  protected financeBars = computed<BarGroup[]>(() => {
    const f = this.finance();
    if (!f) return [];
    return [
      { label: this.fr() ? 'Recettes' : 'Revenue', segments: [{ value: f.totalRevenue, color: '#16A34A' }] },
      { label: this.fr() ? 'Dépenses' : 'Expenses', segments: [{ value: f.totalExpense, color: '#DC2626' }] },
      { label: this.fr() ? 'Solde' : 'Balance', segments: [{ value: Math.max(f.balance, 0), color: '#1B3A5C' }] },
    ];
  });

  protected sexData = computed<Slice[]>(() =>
    this.slices(this.demographics()?.bySex ?? {}, { M: '#1B3A5C', F: '#D4A843' }));

  protected subsystemData = computed<Slice[]>(() =>
    this.slices(this.demographics()?.bySubsystem ?? {}));

  protected levelBars = computed<BarGroup[]>(() =>
    this.entries(this.demographics()?.byLevel ?? {}).map((kv, i) => ({
      label: kv[0],
      segments: [{ value: kv[1], color: this.color(i) }],
    })));

  protected attTrend = computed<Pt[]>(() => {
    const rows = this.attendance();
    if (!rows.length) return [];
    return rows.slice(0, 30).map((r) => ({ label: r.className, value: r.rate }));
  });

  protected attendanceClasses = computed(() => [...new Set(this.attendance().map((row) => row.className).filter(Boolean))].sort());
  protected filteredAttendance = computed(() => {
    const q = this.attendanceQuery().trim().toLowerCase();
    const cls = this.attendanceClass();
    return this.attendance().filter((row) => (!cls || row.className === cls)
      && (!q || `${row.studentName} ${row.className}`.toLowerCase().includes(q)));
  });
  protected pagedAttendance = computed(() => paginateRows(this.filteredAttendance(), this.attendancePage(), this.attendancePageSize()));

  protected setAttendanceQuery(value: string): void { this.attendanceQuery.set(value); this.attendancePage.set(1); }
  protected setAttendanceClass(value: string): void { this.attendanceClass.set(value || ''); this.attendancePage.set(1); }
  protected setAttendancePageSize(value: number): void { this.attendancePageSize.set(value); this.attendancePage.set(1); }
  protected clearAttendanceFilters(): void { this.attendanceQuery.set(''); this.attendanceClass.set(''); this.attendancePage.set(1); }

  protected entries(o: Record<string, number>): [string, number][] {
    return Object.entries(o);
  }

  private slices(o: Record<string, number>, colors: Record<string, string> = {}): Slice[] {
    return this.entries(o).map(([name, value], i) => ({
      name,
      value,
      color: colors[name] ?? this.color(i),
    }));
  }

  protected rateClass(rate: number): string {
    if (rate < 60) return 'text-rose-600';
    if (rate < 80) return 'text-amber-600';
    return 'text-emerald-600';
  }

  protected print(): void {
    window.print();
  }

  protected exportCsv(): void {
    const rows = this.attendance();
    if (!rows.length) return;
    const head = ['Student', 'Class', 'Present', 'Late', 'Absent', 'Rate'];
    const lines = [head.join(',')].concat(
      rows.map((r) => [r.studentName, r.className, r.present, r.late, r.absent, r.rate]
        .map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')),
    );
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `attendance-${this.month()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private currentMonth(): string {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    return `${y}-${m}`;
  }
}
