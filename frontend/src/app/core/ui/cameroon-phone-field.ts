import { ChangeDetectionStrategy, Component, inject, input, model } from '@angular/core';
import { I18nService } from '../i18n.service';
import { cameroonNationalInput, isOtherCountryPhone } from '../cameroon-phone';

@Component({
  selector: 'bbc-cameroon-phone-field',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  template: `
    <label [for]="fieldId()" class="mb-1.5 block text-xs font-semibold text-ink">{{ label() }}</label>
    @if (otherCountry(value())) {
      <input [id]="fieldId()" type="tel" [value]="value()" readonly
        class="h-11 w-full min-w-0 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm" />
      <p class="mt-1 text-xs text-mute">{{ fr() ? 'Le numéro existant est conservé.' : 'The existing number is preserved.' }}</p>
      <button type="button" (click)="value.set('')" class="mt-1 text-xs font-semibold text-brand-700 underline">
        {{ fr() ? 'Remplacer par un numéro camerounais' : 'Replace with a Cameroon number' }}
      </button>
    } @else {
      <div class="flex h-11 min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white focus-within:border-brand-400">
        <span class="flex shrink-0 select-none items-center border-r border-slate-200 bg-slate-100 px-3 text-sm font-semibold text-slate-600"
          aria-hidden="true">+237</span>
        <input [id]="fieldId()" type="tel" inputmode="numeric" [autocomplete]="autocomplete()"
          [value]="national(value())" (input)="onInput($event)" [required]="required()"
          [attr.aria-describedby]="fieldId() + '-hint'" placeholder="6XX XX XX XX" pattern="[26][0-9]{8}"
          class="h-full w-full min-w-0 flex-1 border-0 bg-transparent px-3 text-sm text-ink outline-none" />
      </div>
      <p [id]="fieldId() + '-hint'" class="mt-1 text-xs text-mute">{{ fr()
        ? 'Saisissez les 9 chiffres. L’indicatif +237 est fixe et ajouté automatiquement.'
        : 'Enter the 9 digits. The fixed +237 country code is added automatically.' }}</p>
    }
  `,
})
export class CameroonPhoneFieldComponent {
  private i18n = inject(I18nService);
  readonly value = model<string | null | undefined>('');
  readonly label = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly autocomplete = input('tel-national');
  readonly required = input(false);
  protected fr = () => this.i18n.lang() === 'fr';
  protected national = cameroonNationalInput;
  protected otherCountry = isOtherCountryPhone;

  protected onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = cameroonNationalInput(input.value);
    // A pasted country code is removed from the editable portion, never duplicated.
    if (input.value !== digits) input.value = digits;
    this.value.set(digits ? '+237' + digits : '');
  }
}
