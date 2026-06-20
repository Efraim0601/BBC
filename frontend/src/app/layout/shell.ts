import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18nService, Lang } from '../core/i18n.service';

/** Odoo-style app shell: top bar (brand, language, profile) + routed page. */
@Component({
  selector: 'bbc-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink],
  template: `
    <div class="h-screen w-screen flex flex-col bg-surface text-ink">
      <header class="h-16 bg-brand-700 text-white px-4 flex items-center gap-3 shrink-0 shadow-sm">
        <a routerLink="/apps" class="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-white/10 transition shrink-0">
          <div class="w-8 h-8 bg-white rounded-md p-0.5 shrink-0">
            <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
          </div>
          <div class="text-left hidden md:block">
            <div class="font-display font-bold text-[14px] leading-tight">BBC SMS</div>
            <div class="text-[10px] text-gold-200">{{ user()?.schoolName || 'Bayo Bilingual Complex' }}</div>
          </div>
        </a>

        <div class="flex-1"></div>

        <div class="flex items-center bg-white/10 rounded-lg p-0.5">
          @for (l of langs; track l) {
            <button (click)="i18n.setLang(l)"
              class="px-2.5 h-7 text-[11px] font-bold rounded-md transition"
              [class]="i18n.lang() === l ? 'bg-white text-brand-700' : 'text-brand-100 hover:text-white'">
              {{ l.toUpperCase() }}
            </button>
          }
        </div>

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

      <main class="flex-1 scroll-y">
        <div class="px-6 py-6 min-h-full">
          <router-outlet />
        </div>
      </main>
    </div>
  `,
})
export class ShellComponent {
  protected auth = inject(AuthService);
  protected i18n = inject(I18nService);
  protected user = this.auth.user;
  protected langs: Lang[] = ['fr', 'en'];
}
