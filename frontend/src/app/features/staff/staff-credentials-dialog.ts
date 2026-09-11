import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { environment } from '../../../environments/environment';
import { AccountResult } from './staff.api';

export interface StaffCredentialSheet {
  employeeName: string;
  phone?: string | null;
  result: AccountResult;
}

/** Require an international number; do not guess which country a local number belongs to. */
export function staffWhatsappNumber(phone: string | null | undefined): string | null {
  const cleaned = (phone ?? '').trim().replace(/[\s().-]/g, '');
  const digits = cleaned.startsWith('+') ? cleaned.slice(1) : cleaned.startsWith('00') ? cleaned.slice(2) : '';
  return /^[1-9][0-9]{7,14}$/.test(digits) ? digits : null;
}

@Component({
  selector: 'bbc-staff-credentials-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
      <section role="dialog" aria-modal="true" aria-labelledby="staff-credentials-title"
        class="w-full max-w-lg max-h-[calc(100dvh-2rem)] overflow-y-auto overscroll-contain rounded-2xl bg-white p-5 shadow-pop sm:p-6">
        <div class="mb-5">
          <div class="mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-emerald-100 text-xl text-emerald-700" aria-hidden="true">✓</div>
          <h2 id="staff-credentials-title" class="text-xl font-bold text-ink">{{ fr() ? 'Identifiants prêts à partager' : 'Credentials ready to share' }}</h2>
          <p class="mt-1 break-words text-sm text-mute">{{ sheet().employeeName }}</p>
        </div>
        <div class="space-y-3 rounded-xl border border-brand-100 bg-brand-50/50 p-4">
          <label class="block"><span class="text-xs font-semibold text-mute">{{ fr() ? 'Page de connexion' : 'Sign-in page' }}</span>
            <input readonly [value]="loginUrl" class="mt-1 w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-ink" />
          </label>
          <label class="block"><span class="text-xs font-semibold text-mute">{{ identifierLabel() }}</span>
            <input readonly [value]="sheet().result.username" class="mt-1 w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2 font-mono text-base font-semibold text-ink" />
          </label>
          <label class="block"><span class="text-xs font-semibold text-mute">{{ fr() ? 'Nouveau mot de passe' : 'New password' }}</span>
            <input readonly [value]="sheet().result.password || ''" autocomplete="off" class="mt-1 w-full min-w-0 rounded-lg border border-slate-200 bg-white px-3 py-2 font-mono text-base font-semibold text-ink" />
          </label>
        </div>
        <p class="mt-3 text-xs leading-relaxed text-mute">{{ fr()
          ? 'Copiez ces identifiants avant de fermer. Le mot de passe ne sera plus visible dans la fiche employé ; une réinitialisation en créera un nouveau.'
          : 'Copy these credentials before closing. The password will not be visible in the employee profile; resetting creates a new one.' }}</p>
        <p class="mt-3 rounded-lg p-3 text-sm" role="status"
          [class]="sheet().result.emailRequested && !sheet().result.emailSent ? 'bg-amber-50 text-amber-800' : 'bg-slate-50 text-slate-700'">
          {{ sheet().result.emailSent ? (fr() ? 'L’e-mail a également été envoyé.' : 'The email was also sent.')
            : sheet().result.emailRequested ? (fr() ? 'L’e-mail n’a pas pu être envoyé. Les identifiants sont valides : partagez-les manuellement.' : 'Email delivery failed. These credentials work; share them manually.')
            : (fr() ? 'Aucun e-mail envoyé.' : 'No email sent.') }}
        </p>
        <div class="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
          <button type="button" (click)="copy()" class="min-h-11 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white">{{ copied() ? (fr() ? 'Copié !' : 'Copied!') : (fr() ? 'Copier les identifiants' : 'Copy credentials') }}</button>
          @if (whatsappUrl(); as url) {
            <a [href]="url" target="_blank" rel="noopener noreferrer" class="flex min-h-11 items-center justify-center rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-800">{{ fr() ? 'Ouvrir WhatsApp' : 'Open WhatsApp' }}</a>
          }
        </div>
        @if (whatsappUrl()) { <p class="mt-2 text-xs text-mute">{{ fr() ? 'WhatsApp ouvrira un message préparé. Vous choisissez quand l’envoyer.' : 'WhatsApp opens a prepared message. You choose when to send it.' }}</p> }
        @if (copyFailed()) {
          <label class="mt-3 block text-xs text-mute">{{ fr() ? 'Sélectionnez et copiez le texte ci-dessous.' : 'Select and copy the text below.' }}
            <textarea readonly rows="6" [value]="shareText()" class="mt-1 w-full rounded-lg border border-slate-300 p-3 text-sm"></textarea>
          </label>
        }
        <button type="button" (click)="closed.emit()" [disabled]="busy()" class="mt-5 min-h-11 w-full rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-ink disabled:opacity-50">{{ busy() ? (fr() ? 'Enregistrement en cours…' : 'Saving…') : (fr() ? 'Terminé' : 'Done') }}</button>
      </section>
    </div>
  `,
})
export class StaffCredentialsDialogComponent {
  private i18n = inject(I18nService);
  private document = inject(DOCUMENT);
  readonly sheet = input.required<StaffCredentialSheet>();
  readonly busy = input(false);
  readonly closed = output<void>();
  protected copied = signal(false);
  protected copyFailed = signal(false);
  protected identifierLabel = computed(() => this.sheet().result.username.startsWith('+')
    ? (this.fr() ? 'Téléphone de connexion' : 'Sign-in phone number')
    : this.sheet().result.username.includes('@') ? (this.fr() ? 'E-mail de connexion' : 'Sign-in email')
    : (this.fr() ? 'Identifiant' : 'Username'));
  protected fr = () => this.i18n.lang() === 'fr';
  protected loginUrl = environment.native ? 'https://bbcomplex.com/app/login' : new URL('login', this.document.baseURI).href;
  protected shareText = computed(() => [
    'BBC SMS — ' + this.sheet().employeeName,
    this.loginUrl,
    this.identifierLabel() + ': ' + this.sheet().result.username,
    (this.fr() ? 'Mot de passe : ' : 'Password: ') + (this.sheet().result.password ?? ''),
  ].join('\n'));
  protected whatsappUrl = computed(() => {
    const number = staffWhatsappNumber(this.sheet().result.username.startsWith('+') ? this.sheet().result.username : this.sheet().phone);
    return number ? `https://wa.me/${number}?text=${encodeURIComponent(this.shareText())}` : null;
  });
  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.shareText());
      this.copied.set(true);
      this.copyFailed.set(false);
    } catch {
      this.copyFailed.set(true);
    }
  }
}
