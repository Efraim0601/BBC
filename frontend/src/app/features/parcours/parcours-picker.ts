import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../../core/auth.service';
import { ScopeService } from '../../core/scope.service';
import { I18nService, Lang } from '../../core/i18n.service';
import { Parcours } from '../../core/models';

type Lvl = Parcours['level'];

/**
 * Post-login parcours picker: choose a parcours (Maternelle / Primaire / Secondaire)
 * then a section (Francophone / Anglophone). The choice becomes the global scope that
 * compartmentalises the whole app. Limited to the user's allowed parcours when restricted.
 */
@Component({
  selector: 'bbc-parcours-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen w-screen bg-surface flex flex-col">
      <!-- Top bar — logo stays where the navbar normally sits -->
      <header class="h-16 bg-brand-700 text-white px-4 flex items-center gap-3 shrink-0 shadow-sm">
        <div class="flex items-center gap-2.5 px-2 py-1.5 shrink-0">
          <div class="w-8 h-8 bg-white rounded-md p-0.5 shrink-0">
            <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
          </div>
          <div class="text-left hidden md:block">
            <div class="font-display font-bold text-[14px] leading-tight">BBC SMS</div>
            <div class="text-[10px] text-gold-200">{{ schoolName() }}</div>
          </div>
        </div>

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

        <button (click)="signOut()" class="ml-1 text-[11px] text-brand-100 hover:text-white underline">
          {{ fr() ? 'Déconnexion' : 'Sign out' }}
        </button>
      </header>

      <div class="flex-1 flex items-center justify-center p-6 scroll-y">
      <div class="w-full max-w-3xl">
        <div class="mb-8">
          <h1 class="font-display text-2xl font-bold text-ink leading-tight">
            {{ step() === 'level'
              ? (fr() ? 'Choisissez un parcours' : 'Choose a parcours')
              : (fr() ? 'Choisissez la section' : 'Choose the section') }}
          </h1>
          <p class="text-mute text-sm mt-0.5">
            {{ fr() ? 'Chaque parcours dispose de ses propres données et bulletins.'
                    : 'Each parcours has its own data and report cards.' }}
          </p>
        </div>

        @if (step() === 'level') {
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            @for (lv of levels(); track lv.value) {
              <button (click)="pickLevel(lv.value)"
                class="group text-left rounded-2xl border border-slate-200 bg-white p-6 hover:border-brand-400 hover:shadow-pop transition">
                <div class="w-12 h-12 rounded-xl flex items-center justify-center mb-4 {{ lv.bg }}" [innerHTML]="lv.icon"></div>
                <div class="font-display font-bold text-lg text-ink">{{ lv.label }}</div>
                <div class="text-xs text-mute mt-1">{{ lv.sub }}</div>
              </button>
            }
          </div>
          @if (canSeeAll()) {
            <button (click)="commitAll()"
              class="mt-6 w-full text-left rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5 hover:border-brand-400 hover:bg-white transition">
              <div class="font-display font-bold text-ink">{{ fr() ? 'Tous les parcours' : 'All parcours' }}</div>
              <div class="text-xs text-mute mt-1">
                {{ fr()
                  ? 'Afficher les données de tous les niveaux et sous-systèmes (recommandé pour l’administration).'
                  : 'Show data from every level and subsystem (recommended for administration).' }}
              </div>
            </button>
          }
        } @else {
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            @for (sec of sections(); track sec.value) {
              <button (click)="pickSection(sec.value)"
                class="group text-left rounded-2xl border border-slate-200 bg-white p-6 hover:border-brand-400 hover:shadow-pop transition">
                <div class="font-display font-bold text-lg text-ink">{{ sec.label }}</div>
                <div class="text-xs text-mute mt-1">{{ sec.sub }}</div>
              </button>
            }
          </div>
          <button (click)="step.set('level')" class="mt-6 text-sm font-semibold text-brand-600 hover:underline">
            ← {{ fr() ? 'Retour aux parcours' : 'Back to parcours' }}
          </button>
        }
      </div>
      </div>
    </div>
  `,
})
export class ParcoursPickerComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private scope = inject(ScopeService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  protected readonly langs: Lang[] = ['fr', 'en'];
  protected fr = () => this.i18n.lang() === 'fr';
  protected schoolName = computed(() => this.auth.user()?.schoolName || 'Bayo Bilingual Complex');
  protected signOut(): void { this.auth.logout(); }

  protected step = signal<'level' | 'section'>('level');
  private chosenLevel = signal<Lvl | null>(null);

  /** The parcours the user is allowed to see; empty allow-list = all parcours. */
  private allowed = computed(() => this.auth.user()?.allowedParcours ?? []);

  protected levels = computed(() => {
    const all = [
      { value: 'maternelle' as Lvl, label: this.fr() ? 'Maternelle' : 'Kindergarten', sub: this.fr() ? 'Cycle préscolaire' : 'Pre-school cycle', bg: 'bg-pink-100 text-pink-700', icon: this.svg('M12 2a5 5 0 0 1 5 5c0 3-5 9-5 9S7 10 7 7a5 5 0 0 1 5-5z') },
      { value: 'primary' as Lvl, label: this.fr() ? 'Primaire' : 'Primary', sub: this.fr() ? 'SIL → CM2 / Class 1 → 6' : 'SIL → CM2 / Class 1 → 6', bg: 'bg-emerald-100 text-emerald-700', icon: this.svg('M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z') },
      { value: 'secondary' as Lvl, label: this.fr() ? 'Secondaire' : 'Secondary', sub: this.fr() ? '6ème → Tle / Form 1 → U6' : 'Form 1 → Upper Sixth', bg: 'bg-brand-100 text-brand-700', icon: this.svg('M22 10v6M2 10l10-5 10 5-10 5z M6 12v5c0 1 2 3 6 3s6-2 6-3v-5') },
    ];
    return all.filter((l) => this.levelAllowed(l.value));
  });

  protected sections = computed(() => {
    const lvl = this.chosenLevel();
    const all = [
      { value: 'FR' as const, label: this.fr() ? 'Francophone' : 'Francophone', sub: this.fr() ? 'Sous-système français' : 'French sub-system' },
      { value: 'EN' as const, label: this.fr() ? 'Anglophone' : 'English', sub: this.fr() ? 'Sous-système anglais' : 'English sub-system' },
    ];
    return all.filter((s) => this.sectionAllowed(lvl, s.value));
  });

  private levelAllowed(level: Lvl): boolean {
    const a = this.allowed();
    return a.length === 0 || a.some((p) => p.level === level);
  }
  private sectionAllowed(level: Lvl | null, subsystem: 'FR' | 'EN'): boolean {
    const a = this.allowed();
    if (a.length === 0) return true;
    return a.some((p) => p.level === level && p.subsystem === subsystem);
  }

  protected pickLevel(level: Lvl): void {
    this.chosenLevel.set(level);
    // If only one section is allowed for this level, skip straight to the app.
    const secs = this.sections();
    if (secs.length === 1) { this.commit(level, secs[0].value); return; }
    this.step.set('section');
  }

  protected pickSection(subsystem: 'FR' | 'EN'): void {
    const level = this.chosenLevel();
    if (level) this.commit(level, subsystem);
  }

  private commit(level: Lvl, subsystem: 'FR' | 'EN'): void {
    this.scope.set({ level, subsystem });
    this.router.navigate(['/apps']);
  }

  /** Global-scope users may browse without a parcours filter. Keep the empty-list fallback for legacy API payloads. */
  protected canSeeAll = computed(() => {
    const user = this.auth.user();
    if (!user) return false;
    return user.parcoursScopeMode === 'GLOBAL'
      || (user.parcoursScopeMode == null && (user.allowedParcours ?? []).length === 0);
  });

  protected commitAll(): void {
    this.scope.setAll();
    this.router.navigate(['/apps']);
  }

  private svg(path: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(
      `<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${path}"/></svg>`);
  }
}
