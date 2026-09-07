import { ChangeDetectionStrategy, Component, inject, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';

export function validStaffUsername(value: string): boolean {
  return !value.trim() || /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$/.test(value.trim());
}

@Component({
  selector: 'bbc-staff-login-options',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-4">
      <label class="block">
        <span class="text-sm font-semibold text-ink">{{ fr() ? 'Identifiant de connexion' : 'Login username' }}</span>
        <input type="text" [ngModel]="username()" (ngModelChange)="username.set($event)"
          [readonly]="existing()" maxlength="64" autocapitalize="none" spellcheck="false" autocomplete="off"
          [attr.aria-invalid]="!existing() && !validUsername(username())"
          [placeholder]="fr() ? 'Automatique si vous laissez vide' : 'Generated automatically if left blank'"
          class="mt-1 w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-ink readonly:bg-slate-100" />
        <span class="mt-1 block text-xs text-mute">
          {{ existing() ? (fr() ? 'L’identifiant actuel est conservé.' : 'The current username is kept.')
            : (fr() ? '3 à 64 caractères : lettres, chiffres, points, tirets ou underscores.' : '3–64 characters: letters, numbers, dots, hyphens or underscores.') }}
        </span>
        @if (!existing() && !validUsername(username())) {
          <span class="mt-1 block text-xs font-semibold text-rose-700" role="alert">{{ fr() ? 'Vérifiez le format de l’identifiant.' : 'Check the username format.' }}</span>
        }
      </label>
      <label class="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-3">
        <input type="checkbox" [ngModel]="sendEmail() && !!email()?.trim()" (ngModelChange)="sendEmail.set($event)"
          [disabled]="!email()?.trim()" class="mt-0.5 h-5 w-5 shrink-0 rounded border-slate-300 text-brand-600" />
        <span class="min-w-0">
          <span class="block text-sm font-semibold text-ink">{{ fr() ? 'Envoyer aussi les identifiants par e-mail' : 'Also email the credentials' }}</span>
          <span class="mt-1 block break-words text-xs text-mute">{{ email()?.trim()
            ? (fr() ? 'Envoi à ' + email() + ' uniquement si vous cochez cette option.' : 'Send to ' + email() + ' only when you select this option.')
            : (fr() ? 'Facultatif. Ajoutez un e-mail à la fiche pour utiliser cette option.' : 'Optional. Add an email address to use this option.') }}</span>
        </span>
      </label>
      <p class="text-xs leading-relaxed text-mute">{{ fr()
        ? 'L’identifiant et le mot de passe seront affichés après l’enregistrement. Vous pourrez les copier ou les partager vous-même par WhatsApp.'
        : 'The username and password will appear after saving. You can copy them or share them yourself through WhatsApp.' }}</p>
    </div>
  `,
})
export class StaffLoginOptionsComponent {
  private i18n = inject(I18nService);
  readonly email = input<string | null | undefined>('');
  readonly existing = input(false);
  readonly username = model('');
  readonly sendEmail = model(false);
  protected fr = () => this.i18n.lang() === 'fr';
  protected validUsername = validStaffUsername;
}
