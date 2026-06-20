import { Component, ChangeDetectionStrategy, inject, signal, computed, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { AttendanceApi } from './attendance.api';
import { RealtimeService } from '../../core/realtime.service';
import { I18nService } from '../../core/i18n.service';
import { AttendanceView } from '../../core/models';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, StatusPillComponent, ChipFilterComponent,
} from '../../core/ui';

/**
 * Live attendance board. Loads today's board, then reacts to AttendanceView
 * events pushed over WebSocket (a badge scan upserts a row in real time).
 */
@Component({
  selector: 'bbc-attendance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, StatusPillComponent, ChipFilterComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('presence')" [subtitle]="fr() ? 'Lecteur d’empreintes digitales' : 'Fingerprint reader'">
        <div right class="flex items-center gap-2">
          <span class="inline-flex items-center gap-1.5 h-9 px-3 text-[11px] font-semibold uppercase tracking-wider rounded-lg bg-emerald-50 text-emerald-700">
            <span class="dot beat" style="background:#10b981"></span> {{ fr() ? 'En ligne' : 'Online' }}
          </span>
          <div class="text-sm text-mute hidden sm:block">{{ todayLabel() }}</div>
        </div>
      </bbc-page-header>

      <!-- Top: live reader card + KPIs -->
      <div class="grid grid-cols-12 gap-4 mb-5">
        <!-- Fingerprint reader visual -->
        <div class="col-span-12 lg:col-span-5">
          <div class="relative overflow-hidden rounded-xl2 shadow-card p-6 text-white h-full"
            style="background:linear-gradient(135deg,#1B3A5C,#12283f)">
            <div class="absolute -top-10 -right-10 w-48 h-48 rounded-full bg-gold-400/10 blur-2xl"></div>
            <div class="absolute -bottom-12 -left-8 w-40 h-40 rounded-full bg-gold-400/5 blur-2xl"></div>

            <div class="flex items-center gap-2 text-[11px] uppercase tracking-wider font-semibold relative" style="color:#E2C05A">
              <span class="dot beat" style="background:#34d399"></span>
              {{ fr() ? 'En ligne' : 'Online' }} · {{ fr() ? 'Lecteur d’empreintes' : 'Fingerprint reader' }}
            </div>

            <div class="flex items-center gap-5 mt-5 relative">
              <div class="w-24 h-24 rounded-full fp-ring fp-pulse flex items-center justify-center shrink-0 bg-white text-brand-700">
                <bbc-icon name="fingerprint" [s]="56" [sw]="1.5" />
              </div>
              <div class="min-w-0 flex-1">
                @if (lastScan(); as ls) {
                  <div class="fade-in">
                    <div class="text-[11px] uppercase tracking-wider" style="color:#E2C05A">
                      {{ fr() ? 'Vient de scanner' : 'Just scanned' }}
                    </div>
                    <div class="text-[19px] font-bold mt-0.5 truncate">{{ ls.studentName }}</div>
                    <div class="text-sm mt-0.5" style="color:#c7d6e6">{{ ls.className }} · {{ ls.checkInTime || '—' }}</div>
                    <div class="mt-2">
                      <span class="text-[11px] font-bold px-2.5 py-1 rounded-full" [class]="scanBadge(ls.status)">
                        @if (ls.status === 'late') {
                          {{ label('late').toUpperCase() }} +{{ ls.lateMinutes }} min
                        } @else {
                          {{ label(ls.status).toUpperCase() }}
                        }
                      </span>
                    </div>
                  </div>
                } @else {
                  <div>
                    <div class="text-[11px] uppercase tracking-wider" style="color:#E2C05A">{{ fr() ? 'Statut' : 'Status' }}</div>
                    <div class="text-[19px] font-bold mt-0.5">{{ fr() ? 'En attente de pointage…' : 'Waiting for scan…' }}</div>
                  </div>
                }
              </div>
            </div>

            <div class="grid grid-cols-3 gap-2 mt-6 relative">
              <div class="rounded-lg p-3 border border-white/10 bg-white/5">
                <div class="text-[10px] uppercase tracking-wider" style="color:#9db4ca">{{ i18n.t('present') }}</div>
                <div class="text-[22px] font-bold text-emerald-300">{{ present() }}</div>
              </div>
              <div class="rounded-lg p-3 border border-white/10 bg-white/5">
                <div class="text-[10px] uppercase tracking-wider" style="color:#9db4ca">{{ i18n.t('late') }}</div>
                <div class="text-[22px] font-bold text-amber-300">{{ late() }}</div>
              </div>
              <div class="rounded-lg p-3 border border-white/10 bg-white/5">
                <div class="text-[10px] uppercase tracking-wider" style="color:#9db4ca">{{ i18n.t('absent') }}</div>
                <div class="text-[22px] font-bold text-rose-300">{{ absent() }}</div>
              </div>
            </div>

            <div class="mt-5 pt-5 border-t border-white/10 flex items-center justify-between text-xs relative" style="color:#c7d6e6">
              <span>{{ fr() ? 'Date' : 'Date' }}: <b class="text-white">{{ today }}</b></span>
              <span>{{ total() }} {{ fr() ? 'scans aujourd’hui' : 'scans today' }}</span>
            </div>
          </div>
        </div>

        <!-- KPIs + live ticker -->
        <div class="col-span-12 lg:col-span-7 grid grid-cols-2 gap-4">
          <bbc-kpi tone="ok" icon="check" [label]="fr() ? 'Taux de présence' : 'Attendance rate'"
            [value]="rate() + '%'" [sub]="(present() + late()) + '/' + total() + ' ' + (fr() ? 'élèves' : 'students')" />
          <bbc-kpi tone="warn" icon="clock" [label]="fr() ? 'Retards du jour' : 'Lates today'"
            [value]="late()" [sub]="fr() ? 'cf. liste ci-dessous' : 'see list below'" />
          <bbc-kpi tone="bad" icon="bell" [label]="fr() ? 'Absents du jour' : 'Absent today'"
            [value]="absent()" [sub]="fr() ? 'SMS auto envoyés' : 'Auto SMS sent'" />
          <bbc-kpi tone="neutral" icon="users" [label]="fr() ? 'Total scans' : 'Total scans'"
            [value]="total()" [sub]="fr() ? 'enregistrés' : 'recorded'" />

          <bbc-card className="col-span-2" [title]="fr() ? 'Scans en direct' : 'Live scans'"
            [subtitle]="fr() ? 'Derniers pointages enregistrés par le lecteur' : 'Latest scans recorded by the reader'">
            <div class="space-y-1.5 max-h-[240px] overflow-y-auto">
              @for (r of liveFeed(); track r.studentId) {
                <div class="flex items-center gap-3 px-3 py-2 rounded-lg"
                  [class.row-new]="r.studentId === newId()" [class.bg-slate-50]="$even">
                  <div class="text-[11px] font-mono text-mute w-12 shrink-0">{{ r.checkInTime || '—' }}</div>
                  <bbc-avatar [name]="r.studentName" [hue]="200" [size]="32" />
                  <div class="flex-1 min-w-0">
                    <div class="text-sm font-semibold text-ink truncate">{{ r.studentName }}</div>
                    <div class="text-xs text-mute">{{ r.className }} · {{ r.matricule }}</div>
                  </div>
                  <bbc-status-pill [status]="r.status" [label]="label(r.status)" />
                  @if (r.status === 'late') { <span class="text-xs font-bold text-amber-700">+{{ r.lateMinutes }}m</span> }
                </div>
              } @empty {
                <bbc-empty icon="clock" [label]="fr() ? 'En attente de pointages…' : 'Waiting for scans…'" />
              }
            </div>
          </bbc-card>
        </div>
      </div>

      <!-- Today's presence journal -->
      <bbc-card [title]="fr() ? 'Journal de présence du jour' : 'Today’s presence journal'"
        [subtitle]="fr() ? 'Tous les élèves, filtres par classe et statut' : 'All students, filter by class & status'">
        <div action class="relative">
          <span class="absolute left-2.5 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
          <input [value]="search()" (input)="search.set($any($event.target).value)"
            [placeholder]="fr() ? 'Rechercher…' : 'Search…'"
            class="h-8 pl-8 pr-3 text-xs rounded-lg border border-slate-200 bg-white w-44 focus:outline-none focus:border-brand-400" />
        </div>

        <div class="flex items-center gap-2 mb-3 flex-wrap">
          <span class="text-xs font-semibold text-mute uppercase mr-1">{{ fr() ? 'Classe' : 'Class' }}:</span>
          <bbc-chip-filter [allLabel]="fr() ? 'Toutes' : 'All'" [value]="classFilter()"
            (change)="classFilter.set($event)" [options]="classOptions()" />
        </div>
        <div class="flex items-center gap-2 mb-3 flex-wrap">
          <span class="text-xs font-semibold text-mute uppercase mr-1">{{ fr() ? 'Statut' : 'Status' }}:</span>
          <bbc-chip-filter [allLabel]="fr() ? 'Tous' : 'All'" [value]="statusFilter()"
            (change)="statusFilter.set($event)" [options]="statusOptions()" />
        </div>

        <div class="overflow-x-auto -mx-5">
          <table class="w-full text-sm">
            <thead class="border-y border-slate-100 bg-slate-50/50">
              <tr class="text-[11px] uppercase tracking-wide text-mute">
                <th class="text-left font-semibold py-2 pl-5">{{ fr() ? 'Matricule' : 'ID' }}</th>
                <th class="text-left font-semibold py-2">{{ fr() ? 'Nom' : 'Name' }}</th>
                <th class="text-left font-semibold py-2">{{ fr() ? 'Classe' : 'Class' }}</th>
                <th class="text-left font-semibold py-2">{{ fr() ? 'Heure scan' : 'Scan time' }}</th>
                <th class="text-left font-semibold py-2">{{ fr() ? 'Statut' : 'Status' }}</th>
                <th class="text-left font-semibold py-2">{{ fr() ? 'Source' : 'Source' }}</th>
                <th class="text-right font-semibold py-2 pr-5">{{ fr() ? 'Retard' : 'Lateness' }}</th>
              </tr>
            </thead>
            <tbody>
              @for (r of tableRows(); track r.studentId) {
                <tr class="border-b border-slate-50 last:border-0 hover:bg-slate-50/50"
                  [class.row-new]="r.studentId === newId()">
                  <td class="py-2.5 pl-5 text-xs font-mono text-mute">{{ r.matricule }}</td>
                  <td class="py-2.5">
                    <div class="flex items-center gap-2.5">
                      <bbc-avatar [name]="r.studentName" [hue]="200" [size]="28" />
                      <span class="font-semibold text-ink">{{ r.studentName }}</span>
                    </div>
                  </td>
                  <td class="py-2.5 text-mute">{{ r.className }}</td>
                  <td class="py-2.5 font-mono text-xs">{{ r.checkInTime || '—' }}</td>
                  <td class="py-2.5"><bbc-status-pill [status]="r.status" [label]="label(r.status)" /></td>
                  <td class="py-2.5 text-mute text-xs">{{ r.source }}</td>
                  <td class="py-2.5 pr-5 text-right font-semibold text-amber-700">{{ r.lateMinutes ? '+' + r.lateMinutes + ' min' : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="7"><bbc-empty icon="search" [label]="fr() ? 'Aucun résultat' : 'No results'" /></td></tr>
              }
            </tbody>
          </table>
        </div>
      </bbc-card>
    </div>
  `,
})
export class AttendanceComponent implements OnDestroy {
  protected i18n = inject(I18nService);
  private api = inject(AttendanceApi);
  private realtime = inject(RealtimeService);
  private sub?: Subscription;

  protected fr = () => this.i18n.lang() === 'fr';
  protected today = new Date().toISOString().slice(0, 10);
  protected records = signal<Map<string, AttendanceView>>(new Map());
  protected newId = signal<string | null>(null);
  protected lastScan = signal<AttendanceView | null>(null);
  protected search = signal('');
  protected classFilter = signal<string | null>(null);
  protected statusFilter = signal<string | null>(null);

  protected todayLabel = computed(() =>
    new Date().toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB',
      { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' }));

  /** All records sorted by name (used for stats & the journal). */
  protected sorted = computed(() =>
    [...this.records().values()].sort((a, b) => a.studentName.localeCompare(b.studentName)));

  protected present = computed(() => this.count('present'));
  protected late = computed(() => this.count('late'));
  protected absent = computed(() => this.count('absent'));
  protected total = computed(() => this.records().size);
  protected rate = computed(() => {
    const tot = this.total();
    return tot ? Math.round(((this.present() + this.late()) / tot) * 100) : 0;
  });

  /** Live ticker — scanned rows (have a check-in time) newest first. */
  protected liveFeed = computed(() =>
    [...this.records().values()]
      .filter((r) => !!r.checkInTime)
      .sort((a, b) => (b.checkInTime || '').localeCompare(a.checkInTime || ''))
      .slice(0, 12));

  protected classOptions = computed(() =>
    [...new Set(this.sorted().map((r) => r.className))].slice(0, 12).map((c) => ({ value: c, label: c })));

  protected statusOptions = computed(() => [
    { value: 'present', label: this.label('present') },
    { value: 'late', label: this.label('late') },
    { value: 'absent', label: this.label('absent') },
  ]);

  protected tableRows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const cls = this.classFilter();
    const st = this.statusFilter();
    return this.sorted().filter((r) => {
      if (cls && r.className !== cls) return false;
      if (st && r.status !== st) return false;
      if (q && !r.studentName.toLowerCase().includes(q) && !r.matricule.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  constructor() {
    this.api.board().subscribe((b) => {
      const map = new Map<string, AttendanceView>();
      b.records.forEach((r) => map.set(r.studentId, r));
      this.records.set(map);
    });
    // Live push: every badge scan / manual mark arrives here.
    this.sub = this.realtime.watch<AttendanceView>('attendance').subscribe((ev) => {
      this.records.update((m) => {
        const next = new Map(m);
        next.set(ev.studentId, ev);
        return next;
      });
      if (ev.checkInTime) this.lastScan.set(ev);
      this.newId.set(ev.studentId);
    });
  }

  private count(status: string): number {
    return this.sorted().filter((r) => r.status === status).length;
  }

  protected label(s: string): string {
    return { present: this.i18n.t('present'), late: this.i18n.t('late'), absent: this.i18n.t('absent') }[s] ?? s;
  }

  protected scanBadge(s: string): string {
    return {
      present: 'bg-emerald-400 text-emerald-900',
      late: 'bg-amber-400 text-amber-900',
      absent: 'bg-rose-400 text-rose-900',
    }[s] ?? 'bg-slate-200 text-slate-700';
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
