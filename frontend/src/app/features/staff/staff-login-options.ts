import { ChangeDetectionStrategy, Component, computed, inject, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../core/i18n.service';

export type StaffLoginMethod = 'email' | 'phone';

export function staffPhoneLogin(value: string | null | undefined): string | null {
  if (!value || !/^[+0-9][0-9\s().-]{5,24}$/.test(value.trim())) return null;
  let phone = value.trim().replace(/[\s().-]/g, '');
  if (/^[26][0-9]{8}$/.test(phone)) phone = '+237' + phone;
  else if (/^237[26][0-9]{8}$/.test(phone)) phone = '+' + phone;
  else if (phone.startsWith('00')) phone = '+' + phone.slice(2);
  return /^\+[1-9][0-9]{7,14}$/.test(phone) ? phone : null;
}

export function staffLoginIdentifier(method: StaffLoginMethod, email?: string | null, phone?: string | null): string | null {
  if (method === 'phone') return staffPhoneLogin(phone);
  const normalized = (email ?? '').trim().toLowerCase();
  return normalized.length <= 160 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized) ? normalized : null;
}

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
      @if (existing()) {
        <label class="block">
          <span class="text-sm font-semibold text-ink">{{ fr() ? 'Identifiant de connexion actuel' : 'Current sign-in identifier' }}</span>
          <input type="text" [value]="username()" readonly autocomplete="off"
            class="mt-1 w-full min-w-0 rounded-lg border border-slate-300 bg-slate-100 px-3 py-2.5 text-sm text-ink" />
          <span class="mt-1 block text-xs text-mute">{{ fr() ? 'La réinitialisation conserve cet identifiant.' : 'Resetting keeps this sign-in identifier.' }}</span>
        </label>
      } @else {
        <fieldset>
          <legend class="mb-2 text-sm font-semibold text-ink">{{ fr() ? 'L’employé se connectera avec' : 'The staff member will sign in with' }}</legend>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <label class="flex min-h-16 cursor-pointer items-start gap-3 rounded-xl border p-3"
              [class]="loginMethod() === 'email' ? 'border-brand-500 bg-brand-50' : 'border-slate-200 bg-white'">
              <input type="radio" name="staff-login-method" value="email" [ngModel]="loginMethod()"
                (ngModelChange)="choose($event)" class="mt-1 h-4 w-4 shrink-0 accent-teal-700" />
              <span><span class="block text-sm font-semibold text-ink">{{ fr() ? 'E-mail' : 'Email' }}</span>
                <span class="mt-1 block text-xs text-mute">{{ fr() ? 'L’adresse e-mail de sa fiche' : 'The email address on their profile' }}</span></span>
            </label>
            <label class="flex min-h-16 cursor-pointer items-start gap-3 rounded-xl border p-3"
              [class]="loginMethod() === 'phone' ? 'border-brand-500 bg-brand-50' : 'border-slate-200 bg-white'">
              <input type="radio" name="staff-login-method" value="phone" [ngModel]="loginMethod()"
                (ngModelChange)="choose($event)" class="mt-1 h-4 w-4 shrink-0 accent-teal-700" />
              <span><span class="block text-sm font-semibold text-ink">{{ fr() ? 'Numéro de téléphone' : 'Phone number' }}</span>
                <span class="mt-1 block text-xs text-mute">{{ fr() ? 'Aucun e-mail nécessaire' : 'No email needed' }}</span></span>
            </label>
          </div>
        </fieldset>
        <div class="rounded-xl border border-slate-200 bg-white p-3" aria-live="polite">
          <span class="block text-xs font-semibold text-mute">{{ fr() ? 'Identifiant à utiliser pour se connecter' : 'Sign-in identifier' }}</span>
          @if (identifier(); as value) {
            <strong class="mt-1 block break-all text-base text-ink">{{ value }}</strong>
          } @else {
            <span class="mt-1 block text-sm text-amber-800">{{ loginMethod() === 'phone'
              ? (fr() ? 'Renseignez le téléphone dans la fiche ci-dessus.' : 'Enter the phone number in the profile above.')
              : (fr() ? 'Renseignez un e-mail valide dans la fiche ci-dessus.' : 'Enter a valid email address in the profile above.') }}</span>
          }
        </div>
        @if (loginMethod() === 'phone') {
          <p class="text-xs leading-relaxed text-mute">{{ fr()
            ? 'Saisissez les 9 chiffres dans le champ téléphone. L’indicatif +237 est fixe et ajouté automatiquement.'
            : 'Enter the 9 digits in the phone field. The fixed +237 country code is added automatically.' }}</p>
        }
      }
      @if (existing() || loginMethod() === 'email') {
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
      }
      <p class="rounded-xl bg-brand-50 p-3 text-sm leading-relaxed text-brand-900">{{ fr()
        ? 'Le mot de passe créé sera affiché après l’enregistrement. Copiez les identifiants avant de fermer, puis transmettez-les vous-même à l’employé.'
        : 'The generated password will appear after saving. Copy the credentials before closing, then share them with the staff member yourself.' }}</p>
    </div>
  `,
})
export class StaffLoginOptionsComponent {
  private i18n = inject(I18nService);
  readonly email = input<string | null | undefined>('');
  readonly phone = input<string | null | undefined>('');
  readonly existing = input(false);
  readonly username = model('');
  readonly loginMethod = model<StaffLoginMethod>('email');
  readonly sendEmail = model(false);
  protected fr = () => this.i18n.lang() === 'fr';
  protected identifier = computed(() => staffLoginIdentifier(this.loginMethod(), this.email(), this.phone()));
  protected choose(method: StaffLoginMethod): void {
    this.loginMethod.set(method);
    if (method === 'phone') this.sendEmail.set(false);
  }
}
