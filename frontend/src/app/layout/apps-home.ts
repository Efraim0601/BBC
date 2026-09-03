import { Component, ChangeDetectionStrategy, inject, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../core/auth.service';
import { I18nService } from '../core/i18n.service';
import { NAV_GROUPS, MOD_BY_ID, RECENT_MODS_KEY, Mod } from '../core/nav-items';
import { StudentApi } from '../features/students/students.api';
import { AttendanceApi } from '../features/attendance/attendance.api';
import { FinanceApi } from '../features/finance/finance.api';
import { KpiComponent } from '../core/ui';

const fmtMoney = (n: number) => `${Math.round(n).toLocaleString('fr-FR')} FCFA`;

/**
 * Home screen — a true landing page, not a mirror of the sidebar.
 * Leads with an at-a-glance KPI strip and a "resume where you left off" row
 * (things the sidebar can't show); the full module catalogue lives at the
 * bottom as a discovery surface.
 */
@Component({
  selector: 'bbc-apps-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiComponent],
  template: `
    <div class="fade-in min-h-full">
      <!-- Hero -->
      <div class="relative bg-gradient-to-br from-brand-700 via-brand-800 to-brand-900 text-white px-6 sm:px-8 py-8 sm:py-10 -mx-4 sm:-mx-6 -mt-4 sm:-mt-6 mb-6 sm:mb-8 overflow-hidden">
        <div class="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-gold-400/15 blur-3xl"></div>
        <div class="absolute -bottom-32 -left-16 w-96 h-96 rounded-full bg-brand-500/30 blur-3xl"></div>
        <div class="relative max-w-5xl flex flex-wrap items-end justify-between gap-4">
          <div>
            <div class="text-[11px] uppercase tracking-[0.2em] text-gold-300 font-bold mb-2">Bayo Bilingual Complex — SMS</div>
            <h1 class="font-display text-3xl sm:text-4xl font-bold leading-tight">
              {{ greeting() }} <span class="text-gold-300">{{ firstName() }}</span>.
            </h1>
            <p class="text-brand-100 text-sm mt-2 max-w-xl">{{ todayLabel() }}</p>
          </div>
          @if (auth.can('dashboard', 'read')) {
            <button (click)="open('/dashboard')"
              class="inline-flex items-center gap-2 h-10 px-4 rounded-lg bg-white/10 hover:bg-white/20 border border-white/15 text-sm font-semibold transition">
              <span [innerHTML]="trust(icon('dashboard'))" class="[&>svg]:w-[18px] [&>svg]:h-[18px]"></span>
              {{ fr() ? 'Tableau de bord' : 'Dashboard' }}
            </button>
          }
        </div>
      </div>

      <div class="max-w-5xl">
        <!-- At-a-glance KPIs -->
        @if (showKpis()) {
          <div class="responsive-kpi-grid grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
            @if (auth.canModuleOrAction('students', 'STUDENT_DIRECTORY_VIEW')) {
              <bbc-kpi tone="neutral" icon="users" [label]="i18n.t('students')" [value]="studentCount()" [sub]="subsystemSub()" />
            }
            @if (auth.can('finance', 'read')) {
              <bbc-kpi tone="gold" icon="cash" [label]="i18n.t('revenue30')" [value]="money(revenue30())" />
            }
            @if (auth.can('presence', 'read')) {
              <bbc-kpi tone="ok" icon="fingerprint" [label]="i18n.t('present')" [value]="attRate() + '%'"
                [sub]="late() + ' ' + i18n.t('late').toLowerCase()" />
            }
            @if (auth.can('finance', 'read')) {
              <bbc-kpi tone="neutral" icon="wallet" [label]="fr() ? 'Solde (30 j)' : 'Balance (30d)'" [value]="money(balance30())" />
            }
          </div>
        }

        <!-- Resume where you left off -->
        @if (recentMods().length) {
          <section class="mb-8">
            <div class="flex items-center gap-3 mb-3">
              <h2 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? 'Reprendre' : 'Resume' }}</h2>
              <div class="flex-1 h-px bg-slate-200"></div>
            </div>
            <div class="flex flex-wrap gap-2.5">
              @for (m of recentMods(); track m.id) {
                <button (click)="open(m.route)"
                  class="group inline-flex items-center gap-2.5 h-11 pl-2.5 pr-4 rounded-xl bg-white border border-slate-100 shadow-card hover:shadow-pop hover:-translate-y-0.5 transition">
                  <span class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 [&>svg]:w-[18px] [&>svg]:h-[18px]" [class]="m.iconBg" [innerHTML]="trust(m.svg)"></span>
                  <span class="text-sm font-semibold text-ink">{{ i18n.moduleLabel(m.id) }}</span>
                </button>
              }
            </div>
          </section>
        }
      </div>

      <!-- Full module catalogue (discovery) -->
      <div class="space-y-8 pb-4 max-w-5xl">
        <div class="flex items-center gap-3">
          <h2 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? 'Tous les modules' : 'All modules' }}</h2>
          <div class="flex-1 h-px bg-slate-200"></div>
        </div>
        @for (g of visibleGroups(); track g.key) {
          <section>
            <div class="flex items-center gap-3 mb-3">
              <h3 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? g.labelFr : g.labelEn }}</h3>
              <div class="flex-1 h-px bg-slate-100"></div>
              <span class="text-[11px] text-slate-400 font-semibold">{{ g.mods.length }}</span>
            </div>

            <div class="module-catalog-grid grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4">
              @for (m of g.mods; track m.id) {
                <button (click)="open(m.route)"
                  class="group relative min-h-11 bg-white rounded-2xl border border-slate-100 shadow-card hover:shadow-pop transition-all hover:-translate-y-0.5 p-4 sm:p-5 text-left overflow-hidden">
                  <div class="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-gradient-to-br opacity-0 group-hover:opacity-10 blur-2xl transition" [class]="m.color"></div>
                  <div class="flex items-start gap-3 relative">
                    <div class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 group-hover:scale-110 transition" [class]="m.iconBg" [innerHTML]="trust(m.svg)"></div>
                  </div>
                  <div class="mt-4 relative">
                    <div class="font-display text-[17px] font-bold text-ink leading-tight">{{ i18n.moduleLabel(m.id) }}</div>
                    <div class="text-xs text-mute mt-1 leading-relaxed h-8">{{ fr() ? m.subFr : m.subEn }}</div>
                  </div>
                  <div class="absolute bottom-3 right-3 text-mute group-hover:text-brand-600 transition opacity-0 group-hover:opacity-100">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 18 6-6-6-6"/></svg>
                  </div>
                </button>
              }
            </div>
          </section>
        }
      </div>
    </div>
  `,
})
export class AppsHomeComponent {
  protected i18n = inject(I18nService);
  protected auth = inject(AuthService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);
  private studentApi = inject(StudentApi);
  private attendanceApi = inject(AttendanceApi);
  private financeApi = inject(FinanceApi);

  protected fr = () => this.i18n.lang() === 'fr';
  protected money = fmtMoney;
  protected trust = (svg: string): SafeHtml => this.sanitizer.bypassSecurityTrustHtml(svg);
  protected icon = (id: string): string => MOD_BY_ID[id]?.svg ?? '';

  protected studentCount = signal(0);
  protected frStudents = signal(0);
  protected revenue30 = signal(0);
  protected balance30 = signal(0);
  protected present = signal(0);
  protected late = signal(0);
  protected absent = signal(0);

  protected firstName = computed(() => {
    const name = this.auth.user()?.displayName ?? '';
    return name.split(' ').slice(-1)[0] || name;
  });

  protected greeting = computed(() => {
    const h = new Date().getHours();
    if (this.fr()) return h < 18 ? 'Bonjour' : 'Bonsoir';
    return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
  });

  protected todayLabel = computed(() =>
    new Date().toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB',
      { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' }));

  protected showKpis = () => this.auth.canModuleOrAction('students', 'STUDENT_DIRECTORY_VIEW') || this.auth.can('finance', 'read') || this.auth.can('presence', 'read');

  protected subsystemSub = computed(() => `${this.frStudents()} FR · ${this.studentCount() - this.frStudents()} EN`);

  protected attRate = computed(() => {
    const tot = this.present() + this.late() + this.absent();
    return tot ? Math.round(((this.present() + this.late()) / tot) * 100) : 0;
  });

  protected visibleGroups = computed(() => {
    const allowed = new Set(this.auth.user()?.modules ?? []);
    return NAV_GROUPS
      .map((g) => ({ ...g, mods: g.mods.filter((m) => this.moduleVisible(m.id, allowed)) }))
      .filter((g) => g.mods.length > 0);
  });

  private moduleVisible(id: string, allowed: Set<string>): boolean {
    const scopedAction: Record<string, string> = {
      coursebook: 'COURSEBOOK_VIEW',
      events: 'EVENTS_VIEW',
      messages: 'MESSAGES_VIEW',
    };
    if (scopedAction[id]) {
      return ['ALLOW', 'CONTEXT_REQUIRED'].includes(this.auth.actionState(scopedAction[id]));
    }
    return allowed.has(id)
      || (id === 'access-control' && this.auth.canAction('PERMISSION_VIEW'))
      || (id === 'students' && this.auth.canModuleOrAction('students', 'STUDENT_DIRECTORY_VIEW'));
  }

  /** Recently opened modules the user still has access to. */
  protected recentMods = computed<Mod[]>(() => {
    const allowed = new Set(this.auth.user()?.modules ?? []);
    let ids: string[] = [];
    try { ids = JSON.parse(localStorage.getItem(RECENT_MODS_KEY) || '[]'); } catch { /* ignore */ }
    return ids.map((id) => MOD_BY_ID[id]).filter((m): m is Mod => !!m && (allowed.has(m.id) || (m.id === 'access-control' && this.auth.canAction('PERMISSION_VIEW'))));
  });

  constructor() {
    if (this.auth.canModuleOrAction('students', 'STUDENT_DIRECTORY_VIEW')) {
      this.studentApi.list().subscribe({
        next: (r) => {
          this.studentCount.set(r.length);
          this.frStudents.set(r.filter((s) => (s.subsystem || '').toUpperCase().startsWith('F')).length);
        },
        error: () => {},
      });
    }
    if (this.auth.can('finance', 'read')) {
      this.financeApi.summary().subscribe({
        next: (s) => { this.revenue30.set(s.totalRevenue30d); this.balance30.set(s.balance30d); },
        error: () => {},
      });
    }
    if (this.auth.can('presence', 'read')) {
      this.attendanceApi.board().subscribe({
        next: (b) => { this.present.set(b.present); this.late.set(b.late); this.absent.set(b.absent); },
        error: () => {},
      });
    }
  }

  protected open(route: string): void {
    this.router.navigate([route]);
  }
}
