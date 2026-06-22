import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../core/auth.service';
import { I18nService } from '../core/i18n.service';
import { NAV_GROUPS } from '../core/nav-items';


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
    return NAV_GROUPS
      .map((g) => ({ ...g, mods: g.mods.filter((m) => allowed.has(m.id)) }))
      .filter((g) => g.mods.length > 0);
  });

  protected open(route: string): void {
    this.router.navigate([route]);
  }
}
