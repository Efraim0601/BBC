import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../core/auth.service';
import { I18nService } from '../core/i18n.service';

interface Mod {
  id: string;
  route: string;
  iconBg: string;
  color: string;
  svg: string;
  subFr: string;
  subEn: string;
}
interface Group { key: string; labelFr: string; labelEn: string; mods: Mod[]; }

const I = {
  users: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  building: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="4" y="2" width="16" height="20" rx="2"/><path d="M9 22v-4h6v4M9 6h.01M15 6h.01M9 10h.01M15 10h.01M9 14h.01M15 14h.01"/></svg>',
  book: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  fingerprint: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 11v3M7 8a8 8 0 0 1 10 0M5 12a10 10 0 0 1 14 0M9 14.5a4 4 0 0 1 6 0M9.5 19a8 8 0 0 0 5 0"/></svg>',
  shield: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  wallet: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 12V8H6a2 2 0 0 1 0-4h12v4"/><path d="M4 6v12a2 2 0 0 0 2 2h14v-4"/><path d="M18 12a2 2 0 0 0 0 4h4v-4z"/></svg>',
  calendar: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>',
  bell: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
  home: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/></svg>',
  chart: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>',
  settings: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
};

const GROUPS: Group[] = [
  { key: 'community', labelFr: 'Communauté', labelEn: 'Community', mods: [
    { id: 'students', route: '/students', iconBg: 'bg-rose-100 text-rose-700', color: 'from-rose-500 to-rose-700', svg: I.users, subFr: 'Élèves, parents, familles', subEn: 'Students, parents, families' },
    { id: 'hr', route: '/staff', iconBg: 'bg-violet-100 text-violet-700', color: 'from-violet-500 to-violet-700', svg: I.building, subFr: 'Personnel & ressources humaines', subEn: 'Staff & human resources' },
  ]},
  { key: 'education', labelFr: 'Pédagogie', labelEn: 'Education', mods: [
    { id: 'academic', route: '/academic', iconBg: 'bg-emerald-100 text-emerald-700', color: 'from-emerald-500 to-emerald-700', svg: I.book, subFr: 'Notes, bulletins, procès-verbaux', subEn: 'Grades, report cards, master sheets' },
    { id: 'presence', route: '/presence', iconBg: 'bg-amber-100 text-amber-700', color: 'from-amber-500 to-amber-700', svg: I.fingerprint, subFr: 'Empreinte digitale, SMS auto', subEn: 'Biometric, auto SMS' },
    { id: 'discipline', route: '/discipline', iconBg: 'bg-orange-100 text-orange-700', color: 'from-orange-500 to-orange-700', svg: I.shield, subFr: 'Incidents, SMS parents', subEn: 'Incidents, parent SMS' },
  ]},
  { key: 'operations', labelFr: 'Opérations', labelEn: 'Operations', mods: [
    { id: 'finance', route: '/finance', iconBg: 'bg-gold-50 text-gold-600', color: 'from-gold-400 to-gold-600', svg: I.wallet, subFr: 'Recettes, dépenses, débiteurs', subEn: 'Revenue, expenses, debtors' },
    { id: 'timetable', route: '/timetable', iconBg: 'bg-cyan-100 text-cyan-700', color: 'from-cyan-500 to-cyan-700', svg: I.calendar, subFr: 'Grilles, créneaux, conflits', subEn: 'Grids, slots, conflicts' },
    { id: 'events', route: '/events', iconBg: 'bg-pink-100 text-pink-700', color: 'from-pink-500 to-pink-700', svg: I.bell, subFr: 'Annonces & notifications parents', subEn: 'Announcements & parent alerts' },
  ]},
  { key: 'steering', labelFr: 'Pilotage', labelEn: 'Steering', mods: [
    { id: 'dashboard', route: '/dashboard', iconBg: 'bg-brand-100 text-brand-700', color: 'from-brand-500 to-brand-700', svg: I.home, subFr: "Vue d'ensemble · KPIs", subEn: 'Overview · KPIs' },
    { id: 'reports', route: '/reports', iconBg: 'bg-indigo-100 text-indigo-700', color: 'from-indigo-500 to-indigo-700', svg: I.chart, subFr: 'Analytique école entière', subEn: 'School-wide analytics' },
    { id: 'settings', route: '/settings', iconBg: 'bg-slate-100 text-slate-700', color: 'from-slate-500 to-slate-700', svg: I.settings, subFr: 'Configuration, rôles, lecteur', subEn: 'Config, roles, reader' },
  ]},
];

@Component({
  selector: 'bbc-apps-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fade-in min-h-full">
      <!-- Hero -->
      <div class="relative bg-gradient-to-br from-brand-700 via-brand-800 to-brand-900 text-white px-8 py-10 -mx-6 -mt-6 mb-8 overflow-hidden">
        <div class="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-gold-400/15 blur-3xl"></div>
        <div class="absolute -bottom-32 -left-16 w-96 h-96 rounded-full bg-brand-500/30 blur-3xl"></div>
        <div class="relative max-w-5xl">
          <div class="text-[11px] uppercase tracking-[0.2em] text-gold-300 font-bold mb-2">Bayo Bilingual Complex — SMS</div>
          <h1 class="font-display text-4xl font-bold leading-tight">
            {{ fr() ? 'Bonjour' : 'Hello' }} <span class="text-gold-300">{{ firstName() }}</span>.
          </h1>
          <p class="text-brand-100 text-sm mt-2 max-w-xl">
            {{ fr() ? 'Votre établissement, en un coup d’œil. Ouvrez un module pour commencer.' : 'Your school at a glance. Open a module to get started.' }}
          </p>
        </div>
      </div>

      <!-- Module groups -->
      <div class="space-y-8 pb-4 max-w-5xl">
        @for (g of visibleGroups(); track g.key) {
          <section>
            <div class="flex items-center gap-3 mb-3">
              <h2 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? g.labelFr : g.labelEn }}</h2>
              <div class="flex-1 h-px bg-slate-200"></div>
              <span class="text-[11px] text-slate-400 font-semibold">{{ g.mods.length }}</span>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4">
              @for (m of g.mods; track m.id) {
                <button (click)="open(m.route)"
                  class="group relative bg-white rounded-2xl border border-slate-100 shadow-card hover:shadow-pop transition-all hover:-translate-y-0.5 p-5 text-left overflow-hidden">
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
  private auth = inject(AuthService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  protected fr = () => this.i18n.lang() === 'fr';
  protected trust = (svg: string): SafeHtml => this.sanitizer.bypassSecurityTrustHtml(svg);

  protected firstName = computed(() => {
    const name = this.auth.user()?.displayName ?? '';
    return name.split(' ').slice(-1)[0] || name;
  });

  protected visibleGroups = computed(() => {
    const allowed = new Set(this.auth.user()?.modules ?? []);
    return GROUPS
      .map((g) => ({ ...g, mods: g.mods.filter((m) => allowed.has(m.id)) }))
      .filter((g) => g.mods.length > 0);
  });

  protected open(route: string): void {
    this.router.navigate([route]);
  }
}
