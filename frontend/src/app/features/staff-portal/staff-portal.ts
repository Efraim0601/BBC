import { Component, ChangeDetectionStrategy, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { I18nService, Lang } from '../../core/i18n.service';
import { StaffApi, StaffApplicationSubmit, StaffPortalMeta } from '../staff/staff.api';
import { IconComponent } from '../../core/ui';

@Component({
  selector: 'bbc-staff-portal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="min-h-screen bg-surface flex flex-col">
      <header class="border-b border-slate-200 bg-white">
        <div class="max-w-2xl mx-auto px-4 py-4 flex items-center justify-between gap-3">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-10 h-10 bg-white rounded-lg border border-slate-100 p-1 shrink-0">
              <img src="bbc-logo.png" alt="BBC" class="w-full h-full object-contain" />
            </div>
            <div class="min-w-0">
              <div class="text-[10px] uppercase tracking-wider text-mute font-bold">
                {{ fr() ? 'Inscription personnel' : 'Staff registration' }}
              </div>
              <div class="font-display font-bold text-ink truncate">
                {{ meta()?.schoolName || '…' }}
              </div>
            </div>
          </div>
          <div class="flex items-center bg-slate-100 rounded-lg p-0.5 shrink-0">
            @for (l of langs; track l) {
              <button type="button" (click)="i18n.setLang(l)"
                class="px-2.5 h-8 text-xs font-bold rounded-md transition"
                [class]="i18n.lang() === l ? 'bg-white text-brand-700 shadow-sm' : 'text-mute hover:text-ink'">
                {{ l.toUpperCase() }}
              </button>
            }
          </div>
        </div>
      </header>

      <main class="flex-1 px-4 py-8">
        <div class="max-w-2xl mx-auto">
          @if (loadError(); as err) {
            <div class="rounded-xl border border-rose-200 bg-rose-50 p-6 text-center">
              <div class="text-rose-700 font-semibold mb-2">{{ err }}</div>
              <a routerLink="/login" class="text-sm text-brand-700 font-semibold hover:underline">
                {{ fr() ? 'Retour à la connexion' : 'Back to login' }}
              </a>
            </div>
          } @else if (done()) {
            <div class="rounded-xl border border-emerald-200 bg-emerald-50 p-8 text-center space-y-3">
              <div class="mx-auto w-12 h-12 rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center">
                <bbc-icon name="check" [s]="22" />
              </div>
              <h1 class="font-display text-xl font-bold text-ink">
                {{ fr() ? 'Demande envoyée' : 'Application submitted' }}
              </h1>
              <p class="text-sm text-mute max-w-md mx-auto">
                {{ fr()
                  ? 'Vos informations ont été transmises à l’administration. Vous serez contacté(e) après validation.'
                  : 'Your details were sent to the school administration. You will be contacted after validation.' }}
              </p>
            </div>
          } @else if (meta()) {
            <div class="bg-white rounded-xl border border-slate-200 shadow-card p-6 md:p-8 space-y-6">
              <div>
                <h1 class="font-display text-2xl font-bold text-ink">
                  {{ fr() ? 'Créer ma fiche personnel' : 'Create my staff profile' }}
                </h1>
                <p class="text-sm text-mute mt-1">
                  {{ fr()
                    ? 'Remplissez vos informations. Un administrateur validera la demande avant la création définitive du compte.'
                    : 'Fill in your details. An administrator will validate the request before the account is finalized.' }}
                </p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label class="block md:col-span-2">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom complet' : 'Full name' }} *</span>
                  <input [(ngModel)]="draft.name" name="name" required
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sexe' : 'Sex' }}</span>
                  <select [(ngModel)]="draft.sex" name="sex"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="">—</option>
                    <option value="M">{{ fr() ? 'Masculin' : 'Male' }}</option>
                    <option value="F">{{ fr() ? 'Féminin' : 'Female' }}</option>
                  </select>
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Type de contrat' : 'Contract type' }}</span>
                  <select [(ngModel)]="draft.type" name="type"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="Permanent">{{ fr() ? 'Permanent' : 'Permanent' }}</option>
                    <option value="Vacataire">{{ fr() ? 'Vacataire' : 'Contractor' }}</option>
                  </select>
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">Email</span>
                  <input type="email" [(ngModel)]="draft.email" name="email"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                  <input [(ngModel)]="draft.phone" name="phone" placeholder="+237 6XX XX XX XX"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Classe (si PP)' : 'Form class (if any)' }}</span>
                  <input [(ngModel)]="draft.formClass" name="formClass" placeholder="6ème A"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Département souhaité' : 'Desired department' }}</span>
                  <input [(ngModel)]="draft.departmentHint" name="departmentHint"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block md:col-span-2">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Rôles / fonctions souhaités' : 'Desired roles / duties' }}</span>
                  <input [(ngModel)]="draft.desiredRoles" name="desiredRoles"
                    [placeholder]="fr() ? 'Ex. enseignant, surveillant…' : 'e.g. teacher, supervisor…'"
                    class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                </label>
                <label class="block md:col-span-2">
                  <span class="text-xs font-semibold text-ink">{{ fr() ? 'Notes / message' : 'Notes / message' }}</span>
                  <textarea [(ngModel)]="draft.notes" name="notes" rows="3"
                    class="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400"></textarea>
                </label>
              </div>

              <p class="text-[11px] text-mute">
                {{ fr() ? '* Nom obligatoire. Indiquez au moins un e-mail ou un téléphone.'
                        : '* Name required. Provide at least an e-mail or a phone number.' }}
              </p>

              @if (submitError(); as e) {
                <div class="text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div>
              }

              <button type="button" (click)="submit()" [disabled]="submitting() || !canSubmit()"
                class="w-full h-11 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                {{ submitting()
                    ? (fr() ? 'Envoi…' : 'Sending…')
                    : (fr() ? 'Soumettre ma candidature' : 'Submit my application') }}
              </button>
            </div>
          } @else {
            <div class="text-center text-mute text-sm py-16">{{ fr() ? 'Chargement…' : 'Loading…' }}</div>
          }
        </div>
      </main>
    </div>
  `,
})
export class StaffPortalComponent implements OnInit {
  protected i18n = inject(I18nService);
  private api = inject(StaffApi);
  private route = inject(ActivatedRoute);

  protected langs: Lang[] = ['fr', 'en'];
  protected fr = () => this.i18n.lang() === 'fr';

  protected meta = signal<StaffPortalMeta | null>(null);
  protected loadError = signal<string | null>(null);
  protected submitError = signal<string | null>(null);
  protected submitting = signal(false);
  protected done = signal(false);

  private slug = '';
  private token = '';

  protected draft: StaffApplicationSubmit = {
    name: '',
    sex: '',
    type: 'Permanent',
    email: '',
    phone: '',
    formClass: '',
    departmentHint: '',
    desiredRoles: '',
    notes: '',
  };

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug') || '';
    this.token = this.route.snapshot.queryParamMap.get('t') || '';
    if (!this.slug || !this.token) {
      this.loadError.set(this.fr()
        ? 'Lien invalide — demandez le lien d’inscription à l’administration.'
        : 'Invalid link — ask the school for the registration URL.');
      return;
    }
    this.api.portalMeta(this.slug, this.token).subscribe({
      next: (m) => this.meta.set(m),
      error: (e) => this.loadError.set(this.errMsg(e, true)),
    });
  }

  protected canSubmit(): boolean {
    return !!this.draft.name?.trim() && !!(this.draft.email?.trim() || this.draft.phone?.trim());
  }

  protected submit(): void {
    if (!this.canSubmit() || this.submitting()) return;
    this.submitting.set(true);
    this.submitError.set(null);
    const body: StaffApplicationSubmit = {
      name: this.draft.name.trim(),
      sex: this.draft.sex || undefined,
      type: this.draft.type || 'Permanent',
      email: this.draft.email?.trim() || undefined,
      phone: this.draft.phone?.trim() || undefined,
      formClass: this.draft.formClass?.trim() || undefined,
      departmentHint: this.draft.departmentHint?.trim() || undefined,
      desiredRoles: this.draft.desiredRoles?.trim() || undefined,
      notes: this.draft.notes?.trim() || undefined,
    };
    this.api.portalApply(this.slug, this.token, body).subscribe({
      next: () => { this.submitting.set(false); this.done.set(true); },
      error: (e) => { this.submitting.set(false); this.submitError.set(this.errMsg(e, false)); },
    });
  }

  private errMsg(e: unknown, loading: boolean): string {
    const fr = this.fr();
    if (e instanceof HttpErrorResponse) {
      const msg = e.error?.message;
      if (typeof msg === 'string' && msg) return msg;
      if (e.status === 0) return fr ? 'Connexion impossible.' : 'Connection failed.';
      return loading
        ? (fr ? 'Portail indisponible.' : 'Portal unavailable.')
        : (fr ? 'Envoi impossible.' : 'Could not submit.');
    }
    return fr ? 'Erreur.' : 'Error.';
  }
}
