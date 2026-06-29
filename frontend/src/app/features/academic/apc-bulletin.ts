import { Component, ChangeDetectionStrategy, inject, input, computed } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { ScopeService } from '../../core/scope.service';
import { AuthService } from '../../core/auth.service';
import { BulletinView } from './academic.api';
import { apcFramework } from './apc-framework';

/**
 * Competency-based (APC) report card for Maternelle & Primaire, rendered to match the
 * official BBC templates (Francophone / Anglophone). Marks cells are left blank so the
 * sheet is a faithful, printable form; richer mark entry is a later iteration. Identity
 * (student, class) comes from the shared {@link BulletinView}.
 */
@Component({
  selector: 'bbc-apc-bulletin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="apc bg-white text-black mx-auto" style="max-width: 900px;">
      <!-- Header -->
      <div class="text-center border-2 border-black p-3">
        <div class="text-[11px] font-semibold uppercase">
          {{ fr() ? 'République du Cameroun · Paix – Travail – Patrie'
                  : 'Republic of Cameroon · Peace – Work – Fatherland' }}
        </div>
        <div class="font-bold text-lg uppercase">{{ schoolName() }}</div>
        <div class="text-[11px] uppercase">{{ fr() ? 'Maroua' : 'Maroua' }}</div>
        <div class="font-bold text-sm uppercase mt-1">
          {{ fr() ? 'Bulletin de compétences' : 'Competency report card' }}
          — {{ fr() ? 'Année scolaire' : 'School year' }} {{ schoolYear }}
        </div>
      </div>

      <!-- Identity -->
      <table class="w-full border-2 border-t-0 border-black text-[12px]">
        <tr>
          <td class="border border-black px-2 py-1"><b>{{ fr() ? "Nom de l'élève" : 'Student name' }}:</b> {{ view().studentName }}</td>
          <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Classe' : 'Class' }}:</b> {{ view().className }}</td>
        </tr>
        <tr>
          <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Enseignant titulaire' : 'Class teacher' }}:</b> ____________________</td>
          <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Effectif' : 'Enrolment' }}:</b> {{ view().classSize || '____' }}</td>
        </tr>
      </table>

      <!-- Competencies -->
      @for (c of fw().competencies; track c.code) {
        <table class="w-full border-2 border-t-0 border-black text-[11px]">
          <thead>
            <tr class="bg-slate-100">
              <th colspan="3" class="border border-black px-2 py-1 text-left uppercase">
                {{ c.code }} — {{ c.title }} ({{ c.max }} {{ fr() ? 'pts' : 'mks' }})
              </th>
              @for (t of fw().terms; track t) {
                <th colspan="2" class="border border-black px-2 py-1 text-center">{{ t }}</th>
              }
            </tr>
            <tr class="text-[10px] uppercase">
              <th class="border border-black px-2 py-0.5 text-left">{{ fr() ? 'Sous-compétence' : 'Sub-competency' }}</th>
              <th class="border border-black px-2 py-0.5 text-left">{{ fr() ? 'Évaluation' : 'Evaluation' }}</th>
              <th class="border border-black px-1 py-0.5">{{ fr() ? 'Max' : 'Max' }}</th>
              @for (t of fw().terms; track t) {
                <th class="border border-black px-1 py-0.5">{{ fr() ? 'Note' : 'Mark' }}</th>
                <th class="border border-black px-1 py-0.5">{{ fr() ? 'Cote' : 'Grade' }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (s of c.subs; track s.code) {
              @for (e of s.evals; track e.label; let first = $first) {
                <tr>
                  @if (first) {
                    <td class="border border-black px-2 py-0.5 align-top" [attr.rowspan]="s.evals.length">
                      <b>{{ s.code }}</b> {{ s.title }} ({{ s.max }})
                    </td>
                  }
                  <td class="border border-black px-2 py-0.5">{{ e.label }}</td>
                  <td class="border border-black px-1 py-0.5 text-center">{{ e.max }}</td>
                  @for (t of fw().terms; track t) {
                    <td class="border border-black px-1 py-0.5">&nbsp;</td>
                    <td class="border border-black px-1 py-0.5">&nbsp;</td>
                  }
                </tr>
              }
              <tr class="font-bold bg-slate-50">
                <td class="border border-black px-2 py-0.5 text-right" colspan="2">{{ fr() ? 'Total' : 'Total' }} {{ s.code }}</td>
                <td class="border border-black px-1 py-0.5 text-center">{{ s.max }}</td>
                @for (t of fw().terms; track t) {
                  <td class="border border-black px-1 py-0.5">&nbsp;</td>
                  <td class="border border-black px-1 py-0.5">&nbsp;</td>
                }
              </tr>
            }
          </tbody>
        </table>
      }

      <!-- Grand total + summary -->
      <table class="w-full border-2 border-t-0 border-black text-[12px]">
        <tr class="font-bold">
          <td class="border border-black px-2 py-1">{{ fr() ? 'TOTAL GÉNÉRAL' : 'GRAND TOTAL' }}</td>
          <td class="border border-black px-2 py-1 text-center">_____ / {{ fw().grandTotal }}</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Moyenne' : 'Average' }}: _____ / 20</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Rang' : 'Rank' }}: {{ view().rank || '__' }}/{{ view().classSize || '__' }}</td>
        </tr>
      </table>

      <!-- Appreciation + signatures -->
      <table class="w-full border-2 border-t-0 border-black text-[11px]">
        <tr>
          <td class="border border-black px-2 py-3 align-top" style="height:48px;">
            <b>{{ fr() ? 'Appréciation générale' : 'General remarks' }}:</b>
            {{ view().generalAppreciation }}
          </td>
        </tr>
        <tr>
          <td class="border border-black px-2 py-3">
            <div class="grid grid-cols-3 gap-4 text-center">
              <div>{{ fr() ? "Visa de l'enseignant" : 'Class teacher' }}<br />______________</div>
              <div>{{ fr() ? 'Visa du Directeur' : 'Head teacher' }}<br />______________</div>
              <div>{{ fr() ? 'Visa du parent' : 'Parent / guardian' }}<br />______________</div>
            </div>
          </td>
        </tr>
      </table>

      @if (view().financiallyBlocked) {
        <div class="mt-2 text-[11px] font-semibold text-rose-700 print:text-black">
          ⚠ {{ fr() ? 'Bulletin verrouillé — solde de frais impayé.' : 'Report card locked — outstanding fee balance.' }}
        </div>
      }
    </div>
  `,
  styles: [`
    .apc table { border-collapse: collapse; }
    @media print {
      .apc { max-width: 100% !important; }
    }
  `],
})
export class ApcBulletinComponent {
  private i18n = inject(I18nService);
  private scope = inject(ScopeService);
  private auth = inject(AuthService);

  readonly view = input.required<BulletinView>();

  protected fr = () => this.i18n.lang() === 'fr';
  protected readonly schoolYear = new Date().getFullYear() + '-' + (new Date().getFullYear() + 1);

  protected fw = computed(() => apcFramework(this.scope.scope()?.subsystem ?? 'EN'));
  protected schoolName = computed(() => this.auth.user()?.schoolName || 'Bayo Bilingual Complex');
}
