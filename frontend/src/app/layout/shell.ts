import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../core/auth.service';
import { ScopeService } from '../core/scope.service';
import { I18nService, Lang } from '../core/i18n.service';
import { NAV_GROUPS, RECENT_MODS_KEY } from '../core/nav-items';

const NAV_COLLAPSE_KEY = 'bbc.nav.collapsed';

/** App shell: top bar (brand, language, profile) + persistent left sidebar + routed page. */
@Component({
  selector: 'bbc-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="h-screen w-screen flex flex-col bg-surface text-ink">
      <header class="h-16 bg-brand-700 text-white px-3 sm:px-4 flex items-center gap-2 sm:gap-3 shrink-0 shadow-sm z-30">
        <button (click)="toggle()" title="{{ fr() ? 'Replier le menu' : 'Toggle menu' }}"
          class="w-9 h-9 rounded-lg hover:bg-white/10 transition flex items-center justify-center shrink-0">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h18M3 6h18M3 18h18"/></svg>
        </button>
        <a routerLink="/apps" class="flex items-center gap-2.5 px-1 sm:px-2 py-1.5 rounded-lg hover:bg-white/10 transition shrink-0">
          <div class="w-8 h-8 bg-white rounded-md p-0.5 shrink-0">
            <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
          </div>
          <div class="text-left hidden md:block">
            <div class="font-display font-bold text-[14px] leading-tight">BBC SMS</div>
            <div class="text-[10px] text-gold-200">{{ user()?.schoolName || 'Bayo Bilingual Complex' }}</div>
          </div>
        </a>

        <div class="flex-1"></div>

        @if (scopeLabel(); as sl) {
          <a routerLink="/parcours" title="{{ fr() ? 'Changer de parcours' : 'Switch parcours' }}"
            class="hidden sm:flex items-center gap-2 h-8 px-3 rounded-lg bg-white/10 hover:bg-white/20 transition text-[11px] font-bold">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18M3 12h18M3 17h18"/></svg>
            <span>{{ sl }}</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m6 9 6 6 6-6"/></svg>
          </a>
        } @else {
          <a routerLink="/parcours" title="{{ fr() ? 'Choisir un parcours' : 'Choose a parcours' }}"
            class="hidden sm:flex items-center gap-2 h-8 px-3 rounded-lg bg-white/10 hover:bg-white/20 transition text-[11px] font-bold">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18M3 12h18M3 17h18"/></svg>
            <span>{{ fr() ? 'Tous les parcours' : 'All parcours' }}</span>
          </a>
        }

        <div class="flex items-center bg-white/10 rounded-lg p-0.5">
          @for (l of langs; track l) {
            <button (click)="i18n.setLang(l)"
              class="px-2.5 h-7 text-[11px] font-bold rounded-md transition"
              [class]="i18n.lang() === l ? 'bg-white text-brand-700' : 'text-brand-100 hover:text-white'">
              {{ l.toUpperCase() }}
            </button>
          }
        </div>

        <a href="/guide/" target="_blank" rel="noopener"
          title="{{ fr() ? 'Guide utilisateur' : 'User guide' }}"
          class="hidden sm:inline-flex items-center gap-1.5 h-8 px-3 rounded-lg bg-white/10 hover:bg-white/20 transition text-[11px] font-bold">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>
          {{ fr() ? 'Aide' : 'Help' }}
        </a>

        <div class="flex items-center gap-2.5 pl-2">
          <div class="w-9 h-9 rounded-full bg-gradient-to-br from-gold-400 to-gold-600 text-white flex items-center justify-center font-bold text-xs">
            {{ user()?.initials }}
          </div>
          <div class="text-left hidden md:block">
            <div class="text-xs font-bold leading-tight">{{ user()?.displayName }}</div>
            <div class="text-[10px] text-brand-200">{{ user()?.role }}</div>
          </div>
          <button (click)="auth.logout()" class="ml-1 text-[11px] text-brand-100 hover:text-white underline">
            {{ i18n.t('signOut') }}
          </button>
        </div>
      </header>

      <div class="flex-1 flex min-h-0 relative">
        <!-- Mobile backdrop -->
        @if (mobileOpen()) {
          <div class="fixed inset-0 top-16 bg-black/40 z-30 lg:hidden fade-in" (click)="closeMobile()"></div>
        }

        <!-- Sidebar: persistent on desktop, off-canvas drawer on mobile -->
        <aside [class]="asideClass()">
          <nav class="py-3">
            <a routerLink="/apps" routerLinkActive="bg-brand-50 text-brand-700"
              [routerLinkActiveOptions]="{ exact: true }" (click)="closeMobile()"
              class="group flex items-center gap-3 mx-2 px-3 h-10 rounded-lg text-sm font-semibold text-mute hover:bg-slate-50 hover:text-ink transition relative"
              [title]="fr() ? 'Toutes les applications' : 'All apps'">
              <span class="w-5 h-5 shrink-0 flex items-center justify-center">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
              </span>
              @if (!iconOnly()) { <span class="truncate">{{ fr() ? 'Applications' : 'Apps' }}</span> }
            </a>

            @for (g of visibleGroups(); track g.key) {
              <div class="mt-3">
                @if (!iconOnly()) {
                  <div class="px-5 mb-1 text-[10px] uppercase tracking-[0.14em] text-slate-400 font-bold">{{ fr() ? g.labelFr : g.labelEn }}</div>
                } @else {
                  <div class="mx-3 my-2 h-px bg-slate-100"></div>
                }
                @for (m of g.mods; track m.id) {
                  <a [routerLink]="m.route" routerLinkActive="!bg-brand-50 !text-brand-700 font-semibold" (click)="closeMobile()"
                    class="flex items-center gap-3 mx-2 px-3 h-10 rounded-lg text-sm font-medium text-slate-600 hover:bg-slate-50 hover:text-ink transition"
                    [title]="i18n.moduleLabel(m.id)">
                    <span class="w-5 h-5 shrink-0 flex items-center justify-center [&>svg]:w-[18px] [&>svg]:h-[18px]" [innerHTML]="trust(m.svg)"></span>
                    @if (!iconOnly()) { <span class="truncate">{{ i18n.moduleLabel(m.id) }}</span> }
                  </a>
                }
              </div>
            }
          </nav>
        </aside>

        <main class="flex-1 scroll-y min-w-0">
          <div class="px-4 sm:px-6 py-4 sm:py-6 min-h-full">
            <router-outlet />
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ShellComponent {
  protected auth = inject(AuthService);
  protected i18n = inject(I18nService);
  private scope = inject(ScopeService);
  private sanitizer = inject(DomSanitizer);
  private router = inject(Router);

  protected user = this.auth.user;
  protected langs: Lang[] = ['fr', 'en'];
  protected fr = () => this.i18n.lang() === 'fr';
  protected trust = (svg: string): SafeHtml => this.sanitizer.bypassSecurityTrustHtml(svg);

  /** Compact label of the active parcours shown in the header (e.g. "Primaire · FR"). */
  protected scopeLabel = computed(() => {
    const s = this.scope.scope();
    if (!s) return null;
    const lvl = s.level === 'maternelle' ? (this.fr() ? 'Maternelle' : 'Kindergarten')
      : s.level === 'secondary' ? (this.fr() ? 'Secondaire' : 'Secondary')
      : (this.fr() ? 'Primaire' : 'Primary');
    return `${lvl} · ${s.subsystem}`;
  });

  protected collapsed = signal(localStorage.getItem(NAV_COLLAPSE_KEY) === '1');

  /** Off-canvas drawer state (mobile only). */
  protected mobileOpen = signal(false);
  /** Reactive viewport flag — true below the `lg` breakpoint (1024px). */
  protected isMobile = signal(typeof window !== 'undefined' && window.innerWidth < 1024);

  /** Icon-only nav: only on desktop when collapsed; the mobile drawer always shows labels. */
  protected iconOnly = computed(() => !this.isMobile() && this.collapsed());

  /** Sidebar classes: in-flow on desktop, fixed translate-X drawer on mobile. */
  protected asideClass = computed(() => {
    const base =
      'bg-white border-r border-slate-200 flex flex-col scroll-y transition-all duration-200 ' +
      'fixed top-16 bottom-0 left-0 z-40 w-64 ' +
      'lg:static lg:top-0 lg:z-auto lg:shrink-0 lg:translate-x-0 ';
    const mobile = this.mobileOpen() ? 'translate-x-0 shadow-pop ' : '-translate-x-full ';
    const desktopWidth = this.collapsed() ? 'lg:w-16' : 'lg:w-60';
    return base + mobile + desktopWidth;
  });

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => this.isMobile.set(window.innerWidth < 1024));
    }
    // Close the mobile drawer + record the visited module on every navigation.
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) {
        this.mobileOpen.set(false);
        this.recordRecent(e.urlAfterRedirects);
      }
    });
  }

  /** Persist the most-recently opened modules (most recent first, max 4) for the home screen. */
  private recordRecent(url: string): void {
    const mod = NAV_GROUPS.flatMap((g) => g.mods).find((m) => url.startsWith(m.route));
    if (!mod) return;
    try {
      const prev: string[] = JSON.parse(localStorage.getItem(RECENT_MODS_KEY) || '[]');
      const next = [mod.id, ...prev.filter((id) => id !== mod.id)].slice(0, 4);
      localStorage.setItem(RECENT_MODS_KEY, JSON.stringify(next));
    } catch { /* ignore malformed storage */ }
  }

  protected visibleGroups = computed(() => {
    const allowed = new Set(this.auth.user()?.modules ?? []);
    return NAV_GROUPS
      .map((g) => ({ ...g, mods: g.mods.filter((m) => allowed.has(m.id)) }))
      .filter((g) => g.mods.length > 0);
  });

  protected toggle(): void {
    // On mobile the hamburger opens/closes the drawer; on desktop it collapses the rail.
    if (this.isMobile()) {
      this.mobileOpen.update((v) => !v);
      return;
    }
    const next = !this.collapsed();
    this.collapsed.set(next);
    localStorage.setItem(NAV_COLLAPSE_KEY, next ? '1' : '0');
  }

  protected closeMobile(): void {
    if (this.mobileOpen()) this.mobileOpen.set(false);
  }
}
