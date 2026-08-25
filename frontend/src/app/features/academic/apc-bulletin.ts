import { Component, ChangeDetectionStrategy, inject, input, computed } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { BulletinView } from './academic.api';

/**
 * Primary and nursery report card. Its rows always come from the calculated bulletin
 * payload so the printed subjects, coefficients and marks match the class curriculum
 * and grade-entry screens.
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
          <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Enseignant titulaire' : 'Class teacher' }}:</b> {{ view().classMasterName || '—' }}</td>
          <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Effectif' : 'Enrolment' }}:</b> {{ view().classSize || '____' }}</td>
        </tr>
      </table>

      <!-- Subjects configured for this class and calculated for this milestone -->
      <table class="w-full border-2 border-t-0 border-black text-[11px]">
        <thead>
          <tr class="bg-slate-100 text-[10px] uppercase">
            <th class="border border-black px-2 py-1 text-left">{{ fr() ? 'Matière' : 'Subject' }}</th>
            @for (periodCode of periodColumns(); track periodCode) {
              <th class="border border-black px-2 py-1 text-center">{{ periodCode }}</th>
            }
            <th class="border border-black px-2 py-1 text-center">{{ fr() ? 'Note' : 'Mark' }}</th>
            <th class="border border-black px-2 py-1 text-center">{{ fr() ? 'Coef.' : 'Coef.' }}</th>
            <th class="border border-black px-2 py-1 text-center">{{ fr() ? 'Total' : 'Total' }}</th>
            <th class="border border-black px-2 py-1 text-left">{{ fr() ? 'Appréciation' : 'Appreciation' }}</th>
          </tr>
        </thead>
        <tbody>
          @for (line of view().lines; track line.subjectCode) {
            <tr>
              <td class="border border-black px-2 py-1">
                <b>{{ line.subjectLabel }}</b>
                <span class="block text-[9px] text-slate-600">{{ line.subjectCode }}</span>
              </td>
              @for (periodCode of periodColumns(); track periodCode) {
                <td class="border border-black px-2 py-1 text-center">{{ formatMark(periodMark(line, periodCode)) }}</td>
              }
              <td class="border border-black px-2 py-1 text-center font-bold">{{ formatMark(line.mark) }}</td>
              <td class="border border-black px-2 py-1 text-center">{{ line.coef }}</td>
              <td class="border border-black px-2 py-1 text-center">{{ formatMark(line.weighted) }}</td>
              <td class="border border-black px-2 py-1">{{ line.teacherRemark || appreciation(line.mark) }}</td>
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="periodColumns().length + 5" class="border border-black px-2 py-6 text-center text-slate-600">
                {{ fr() ? 'Aucune matière configurée pour cette classe et cette période.' : 'No subjects are configured for this class and period.' }}
              </td>
            </tr>
          }
        </tbody>
      </table>

      <!-- Grand total + summary -->
      <table class="w-full border-2 border-t-0 border-black text-[12px]">
        <tr class="font-bold">
          <td class="border border-black px-2 py-1">{{ fr() ? 'TOTAL GÉNÉRAL' : 'GRAND TOTAL' }}</td>
          <td class="border border-black px-2 py-1 text-center">{{ formatMark(weightedTotal()) }}</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Moyenne' : 'Average' }}: {{ formatMark(view().average) }} / 20</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Rang' : 'Rank' }}: {{ view().rank || '__' }}/{{ view().classSize || '__' }}</td>
        </tr>
      </table>

      @if (view().attendance; as attendance) {
        <table class="w-full border-2 border-t-0 border-black text-[11px]">
          <tr class="bg-slate-100 font-bold">
            <td class="border border-black px-2 py-1" colspan="6">{{ fr() ? 'ASSIDUITÉ' : 'ATTENDANCE' }}</td>
          </tr>
          <tr>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Appels finalisés' : 'Finalized calls' }}</b><br />{{ attendance.finalizedSessions }}</td>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Présences' : 'Present' }}</b><br />{{ attendance.presentCount }}</td>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Absences' : 'Absent' }}</b><br />{{ attendance.absentCount }}</td>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Justifiées' : 'Excused' }}</b><br />{{ attendance.excusedCount }}</td>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Retards' : 'Late' }}</b><br />{{ attendance.lateCount }} · {{ attendance.lateMinutes }} min</td>
            <td class="border border-black px-2 py-1"><b>{{ fr() ? 'Heures d’absence' : 'Absence hours' }}</b><br />{{ attendance.justifiedAbsenceHours + attendance.unjustifiedAbsenceHours }}</td>
          </tr>
        </table>
      }

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
  private auth = inject(AuthService);

  readonly view = input.required<BulletinView>();

  // A bilingual pupil receives one report per programme. The document language
  // follows that programme, independently from the operator's UI language.
  protected fr = computed(() => (this.view().subsystem ?? 'FR').toUpperCase() !== 'EN');
  protected readonly schoolYear = new Date().getFullYear() + '-' + (new Date().getFullYear() + 1);

  protected periodColumns = computed(() => Array.from(new Set(
    this.view().lines.flatMap(line => (line.periodMarks ?? []).map(mark => mark.periodCode)),
  )));
  protected weightedTotal = computed(() => this.view().lines.reduce(
    (sum, line) => sum + (line.weighted ?? 0), 0,
  ));
  protected schoolName = computed(() => this.auth.user()?.schoolName || 'Bayo Bilingual Complex');

  protected periodMark(line: BulletinView['lines'][number], periodCode: string): number | null {
    return line.periodMarks?.find(mark => mark.periodCode === periodCode)?.mark ?? null;
  }

  protected formatMark(value: number | null | undefined): string {
    return value == null ? '—' : Number(value).toFixed(2);
  }

  protected appreciation(mark: number | null): string {
    if (mark == null) return '';
    if (mark >= 16) return this.fr() ? 'Très bien' : 'Very good';
    if (mark >= 14) return this.fr() ? 'Bien' : 'Good';
    if (mark >= 12) return this.fr() ? 'Assez bien' : 'Fairly good';
    if (mark >= 10) return this.fr() ? 'Passable' : 'Pass';
    return this.fr() ? 'À renforcer' : 'Needs improvement';
  }
}
