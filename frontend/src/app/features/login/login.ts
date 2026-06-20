import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../../core/auth.service';
import { I18nService, Lang } from '../../core/i18n.service';

/** Demo accounts wired to the seeded backend users (password: "password"). */
interface DemoUser { username: string; name: string; title: string; initials: string; gradient: string; }

@Component({
  selector: 'bbc-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="h-screen w-screen flex bg-surface overflow-hidden">
      <!-- Left — brand panel -->
      <div class="hidden lg:flex w-1/2 relative overflow-hidden bg-gradient-to-br from-brand-700 to-brand-900 text-white">
        <div class="absolute -top-32 -right-24 w-[420px] h-[420px] rounded-full bg-gold-400/15 blur-3xl"></div>
        <div class="absolute bottom-0 -left-16 w-[380px] h-[380px] rounded-full bg-brand-500/30 blur-3xl"></div>
        <div class="absolute inset-0 opacity-[0.08]"
             style="background-image:radial-gradient(circle, white 1.2px, transparent 1.2px); background-size:24px 24px;"></div>

        <div class="relative z-10 flex flex-col justify-between p-12 w-full">
          <div class="flex items-center gap-4">
            <div class="w-14 h-14 bg-white rounded-xl p-1.5 shadow-pop shrink-0">
              <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
            </div>
            <div>
              <div class="text-[10px] uppercase tracking-[0.18em] text-gold-200 font-bold">République du Cameroun</div>
              <div class="font-display text-xl font-bold leading-tight">Bayo Bilingual Complex</div>
              <div class="text-xs text-brand-100">Maroua — {{ fr() ? 'Système de Gestion Scolaire' : 'School Management System' }}</div>
            </div>
          </div>

          <div class="max-w-md">
            <div class="text-[11px] uppercase tracking-[0.2em] text-gold-300 font-bold mb-3">{{ fr() ? 'Plateforme tout-en-un' : 'All-in-one platform' }}</div>
            <h1 class="font-display text-4xl font-bold leading-[1.15] mb-4">
              @if (fr()) { Un seul système.<br />Deux sous-systèmes.<br /><span class="text-gold-300">L'école entière.</span> }
              @else { One platform.<br />Two sub-systems.<br /><span class="text-gold-300">The whole school.</span> }
            </h1>
            <p class="text-brand-100 text-sm leading-relaxed mb-7">
              {{ fr()
                ? 'Gérez la scolarité, les finances, la présence biométrique et la communication parents — du Primaire au Secondaire, en Français comme en Anglais.'
                : 'Manage academics, finance, biometric attendance and parent communication — from Primary to Secondary, in French and English.' }}
            </p>
            <div class="grid grid-cols-2 gap-3">
              @for (f of features(); track f.label) {
                <div class="flex items-center gap-2.5 bg-white/10 backdrop-blur rounded-lg px-3 py-2 border border-white/15">
                  <div class="w-8 h-8 rounded-md bg-gold-400/20 text-gold-300 flex items-center justify-center shrink-0" [innerHTML]="f.icon"></div>
                  <span class="text-xs font-semibold text-white truncate">{{ f.label }}</span>
                </div>
              }
            </div>
          </div>

          <div class="flex items-center justify-between text-[11px] text-brand-200">
            <div>© 2026 Bayo Bilingual Complex — Maroua, Cameroun</div>
            <div>v 1.0.0</div>
          </div>
        </div>
      </div>

      <!-- Right — form -->
      <div class="flex-1 flex items-center justify-center p-6 lg:p-12 relative scroll-y">
        <div class="absolute top-6 right-6 flex items-center bg-slate-100 rounded-lg p-0.5">
          @for (l of langs; track l) {
            <button (click)="i18n.setLang(l)"
              class="px-3 h-8 text-xs font-bold rounded-md transition"
              [class]="i18n.lang() === l ? 'bg-white text-brand-700 shadow-sm' : 'text-mute hover:text-ink'">
              {{ l.toUpperCase() }}
            </button>
          }
        </div>

        <div class="w-full max-w-md">
          <div class="flex lg:hidden items-center gap-3 mb-8">
            <div class="w-12 h-12 bg-white rounded-lg p-1">
              <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
            </div>
            <div>
              <div class="font-display font-bold text-lg leading-tight">Bayo Bilingual Complex</div>
              <div class="text-xs text-mute">{{ fr() ? 'Système de Gestion Scolaire' : 'School Management System' }}</div>
            </div>
          </div>

          <div class="mb-7">
            <h2 class="text-[26px] font-bold text-ink leading-tight">{{ fr() ? 'Bon retour 👋' : 'Welcome back 👋' }}</h2>
            <p class="text-mute text-sm mt-1.5">{{ fr() ? 'Connectez-vous à votre espace BBC.' : 'Sign in to your BBC workspace.' }}</p>
          </div>

          <form (ngSubmit)="submit()" class="space-y-4">
            <div>
              <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">{{ i18n.t('username') }}</label>
              <div class="relative">
                <svg class="absolute left-3 top-1/2 -translate-y-1/2 text-mute" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z"/><path d="m22 6-10 7L2 6"/></svg>
                <input name="username" [(ngModel)]="username" autocomplete="username"
                  class="w-full h-11 pl-10 pr-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
              </div>
            </div>

            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="text-xs font-semibold text-mute uppercase tracking-wide">{{ i18n.t('password') }}</label>
                <button type="button" class="text-xs font-semibold text-brand-600 hover:underline">{{ fr() ? 'Oublié ?' : 'Forgot?' }}</button>
              </div>
              <div class="relative">
                <svg class="absolute left-3 top-1/2 -translate-y-1/2 text-mute" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                <input name="password" [type]="showPwd() ? 'text' : 'password'" [(ngModel)]="password" autocomplete="current-password"
                  class="w-full h-11 pl-10 pr-10 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono" />
                <button type="button" (click)="showPwd.set(!showPwd())" class="absolute right-3 top-1/2 -translate-y-1/2 text-mute hover:text-ink">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
              </div>
            </div>

            <div>
              <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-2">{{ fr() ? 'Comptes de démonstration' : 'Demo accounts' }}</label>
              <div class="grid grid-cols-3 gap-1.5">
                @for (u of demoUsers; track u.username) {
                  <button type="button" (click)="pick(u)"
                    class="px-2 py-2 rounded-lg text-xs font-bold border-2 transition text-left"
                    [class]="username === u.username ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-slate-300 bg-white'">
                    <div class="flex items-center gap-1.5">
                      <span class="w-6 h-6 rounded-full bg-gradient-to-br text-white flex items-center justify-center text-[10px] shrink-0" [class]="u.gradient">{{ u.initials }}</span>
                      <span class="truncate">{{ u.username }}</span>
                    </div>
                    <div class="text-[10px] font-semibold opacity-60 mt-1 truncate">{{ u.title }}</div>
                  </button>
                }
              </div>
            </div>

            @if (error()) {
              <div class="text-xs text-rose-600 bg-rose-50 rounded-lg px-3 py-2">{{ error() }}</div>
            }

            <button type="submit" [disabled]="loading()"
              class="w-full h-12 bg-brand-600 hover:bg-brand-700 text-white font-semibold rounded-lg transition disabled:opacity-60 flex items-center justify-center gap-2 mt-2">
              {{ loading() ? (fr() ? 'Connexion…' : 'Signing in…') : (fr() ? 'Se connecter' : 'Sign in') }}
              @if (!loading()) {
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m9 18 6-6-6-6"/></svg>
              }
            </button>

            <div class="bg-amber-50 border border-amber-100 rounded-lg p-3 flex items-start gap-2.5 mt-3">
              <svg class="text-amber-600 mt-0.5 shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              <div class="text-[12px] text-amber-900 leading-relaxed">
                <b>{{ fr() ? 'Démo :' : 'Demo:' }}</b> {{ fr() ? 'mot de passe' : 'password' }}
                <code class="bg-amber-100 px-1 py-0.5 rounded font-mono text-[11px]">password</code>.
                {{ fr() ? 'Choisissez un compte pour explorer son rôle.' : 'Pick an account to explore that role.' }}
              </div>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
})
export class LoginComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  protected readonly langs: Lang[] = ['fr', 'en'];
  protected username = 'principal';
  protected password = 'password';
  protected showPwd = signal(false);
  protected loading = signal(false);
  protected error = signal<string | null>(null);

  protected fr = () => this.i18n.lang() === 'fr';

  protected readonly demoUsers: DemoUser[] = [
    { username: 'principal', name: 'NGANOU Aïcha', title: 'Principal', initials: 'NA', gradient: 'from-gold-400 to-gold-600' },
    { username: 'econome', name: 'MBAH Junior', title: 'Économe', initials: 'MJ', gradient: 'from-gold-500 to-gold-700' },
    { username: 'parent1', name: 'Parent', title: 'Parent', initials: 'P1', gradient: 'from-orange-400 to-orange-600' },
  ];

  private icon(svg: string): SafeHtml { return this.sanitizer.bypassSecurityTrustHtml(svg); }

  protected features = () => [
    { label: this.fr() ? 'Présence biométrique' : 'Biometric attendance', icon: this.icon('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 11v3M7 8a8 8 0 0 1 10 0M5 12a10 10 0 0 1 14 0M9 14a4 4 0 0 1 6 0"/></svg>') },
    { label: this.fr() ? 'Recouvrement frais' : 'Fee collection', icon: this.icon('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></svg>') },
    { label: this.fr() ? 'Bulletins automatisés' : 'Auto report cards', icon: this.icon('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>') },
    { label: this.fr() ? 'SMS aux parents' : 'Parent SMS', icon: this.icon('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m22 2-7 20-4-9-9-4 20-7z"/></svg>') },
  ];

  protected pick(u: DemoUser): void {
    this.username = u.username;
    this.password = 'password';
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: (res) => this.router.navigate([res.user.role === 'parent' ? '/parent' : '/apps']),
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Connexion impossible');
        this.loading.set(false);
      },
    });
  }
}
