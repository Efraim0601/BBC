import { Component, ChangeDetectionStrategy, inject, input, computed } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { ScopeService } from '../../core/scope.service';
import { AuthService } from '../../core/auth.service';
import { BulletinView } from './academic.api';
import { apcFramework, ApcFramework, ApcTerm } from './apc-framework';

/**
 * Bulletin de compétences (APC) du primaire et de la maternelle, calqué sur les
 * modèles officiels de l'établissement — un par sous-système et par barème.
 *
 * <p>Le barème vient de la CLASSE de l'élève, pas du parcours actif : francophone et
 * anglophone changent de barème en cours de cycle, et le mode « Tous les parcours »
 * n'a de toute façon aucun sous-système à offrir.
 *
 * <p>Les colonnes de notes restent vides : la feuille est un formulaire imprimable
 * conforme. La saisie des notes par compétence, elle, reste à faire — les notes
 * persistées aujourd'hui sont celles du bulletin classique, par matière et séquence.
 */
@Component({
  selector: 'bbc-apc-bulletin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="apc bg-white text-black mx-auto" style="max-width: 1100px;">
      <!-- En-tête officiel -->
      <div class="text-center border-2 border-black p-3">
        <div class="text-[11px] font-semibold uppercase">
          {{ fr() ? 'République du Cameroun · Paix – Travail – Patrie'
                  : 'Republic of Cameroon · Peace – Work – Fatherland' }}
        </div>
        <div class="font-bold text-lg uppercase">{{ schoolName() }}</div>
        <div class="font-bold text-sm uppercase mt-1">
          {{ fr() ? 'Bulletin de compétences' : 'Competency report card' }}
          — {{ fr() ? 'Année scolaire' : 'School year' }} {{ schoolYear }}
        </div>
        <div class="text-[11px] mt-0.5">{{ fw().label }} · {{ fr() ? 'Total' : 'Total' }} /{{ fw().grandTotal }}</div>
      </div>

      <!-- Identité -->
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

      <!-- Une table par compétence -->
      @for (c of fw().competencies; track c.code) {
        <table class="w-full border-2 border-t-0 border-black text-[10px]">
          <thead>
            <!-- Ligne 1 : intitulé de la compétence, puis les trimestres -->
            <tr class="bg-slate-100">
              <th colspan="3" class="border border-black px-2 py-1 text-left uppercase">
                {{ c.code }} — {{ c.title }} ({{ c.max }} {{ fr() ? 'pts' : 'mks' }})
              </th>
              @for (t of fw().slots.terms; track t.label.en) {
                <th [attr.colspan]="termSpan(t)" class="border border-black px-2 py-1 text-center">
                  {{ fr() ? t.label.fr : t.label.en }}
                </th>
              }
              @if (fw().slots.annualTotal) {
                <th class="border border-black px-1 py-1 text-center">{{ fr() ? 'Total an.' : 'An. total' }}</th>
                <th [attr.colspan]="fw().slots.scale.length" class="border border-black px-1 py-1 text-center">
                  {{ fr() ? 'Appréciation annuelle' : 'Annual appreciation' }}
                </th>
              }
            </tr>
            <!-- Ligne 2 : les colonnes de saisie (UA1…UA8 ou mois 1…8) -->
            <tr class="text-[9px] uppercase">
              <th class="border border-black px-2 py-0.5 text-left">{{ fr() ? 'Sous-compétence' : 'Sub-competency' }}</th>
              <th class="border border-black px-2 py-0.5 text-left">{{ fr() ? 'Évaluation' : 'Evaluation' }}</th>
              <th class="border border-black px-1 py-0.5">Max</th>
              @for (t of fw().slots.terms; track t.label.en) {
                @for (s of t.slots; track s) {
                  <th class="border border-black px-1 py-0.5 text-center" [attr.colspan]="fw().slots.gradePerSlot ? 2 : 1">{{ s }}</th>
                }
                @if (t.total) {
                  <th class="border border-black px-1 py-0.5 text-center">{{ fr() ? 'Tot.' : 'Total' }}</th>
                }
              }
              @if (fw().slots.annualTotal) {
                <th class="border border-black px-1 py-0.5"></th>
                @for (lv of fw().slots.scale; track lv.code) {
                  <th class="border border-black px-1 py-0.5 text-center" [title]="lv.title">{{ lv.code }}</th>
                }
              }
            </tr>
            <!-- Ligne 3 : au francophone, chaque unité porte une note ET une cote -->
            @if (fw().slots.gradePerSlot) {
              <tr class="text-[9px]">
                <th class="border border-black" colspan="3"></th>
                @for (t of fw().slots.terms; track t.label.en) {
                  @for (s of t.slots; track s) {
                    <th class="border border-black px-1 py-0.5 text-center font-normal">{{ fr() ? 'Note' : 'Mark' }}</th>
                    <th class="border border-black px-1 py-0.5 text-center font-normal">{{ fr() ? 'Cote' : 'Grade' }}</th>
                  }
                }
              </tr>
            }
          </thead>
          <tbody>
            @for (s of c.subs; track s.code) {
              @for (e of s.evals; track e.label; let first = $first) {
                <tr [class]="s.alternative ? 'bg-slate-50/60' : ''">
                  @if (first) {
                    <td class="border border-black px-2 py-0.5 align-top" [attr.rowspan]="s.evals.length + 1">
                      <b>{{ s.code }}</b> {{ s.title }} ({{ s.max }})
                      @if (s.alternative) {
                        <div class="text-[9px] italic">
                          {{ fr() ? 'Alternative — ne se cumule pas' : 'Alternative — not cumulative' }}
                        </div>
                      }
                    </td>
                  }
                  <td class="border border-black px-2 py-0.5">{{ e.label }}</td>
                  <td class="border border-black px-1 py-0.5 text-center">{{ e.max }}</td>
                  @for (col of dataColumns(); track $index) {
                    <td class="border border-black px-1 py-0.5">&nbsp;</td>
                  }
                </tr>
              }
              <!-- Total de la sous-compétence -->
              <tr class="font-bold bg-slate-50">
                <td class="border border-black px-2 py-0.5 text-right">{{ fr() ? 'Total' : 'Total' }}</td>
                <td class="border border-black px-1 py-0.5 text-center">{{ s.max }}</td>
                @for (col of dataColumns(); track $index) {
                  <td class="border border-black px-1 py-0.5">&nbsp;</td>
                }
              </tr>
            }
          </tbody>
        </table>
      }

      <!-- Total général -->
      <table class="w-full border-2 border-t-0 border-black text-[12px]">
        <tr class="font-bold">
          <td class="border border-black px-2 py-1">{{ fr() ? 'TOTAL GÉNÉRAL' : 'GRAND TOTAL' }}</td>
          <td class="border border-black px-2 py-1 text-center">_____ / {{ fw().grandTotal }}</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Moyenne' : 'Average' }}: _____ / 20</td>
          <td class="border border-black px-2 py-1">{{ fr() ? 'Rang' : 'Rank' }}: {{ view().rank || '__' }}/{{ view().classSize || '__' }}</td>
        </tr>
      </table>

      <!-- Légende de l'échelle officielle (modèle anglophone) -->
      @if (fw().slots.scale.length) {
        <table class="w-full border-2 border-t-0 border-black text-[10px]">
          <tr>
            @for (lv of fw().slots.scale; track lv.code) {
              <td class="border border-black px-2 py-1"><b>{{ lv.code }}</b> — {{ lv.label }} · {{ lv.title }}</td>
            }
          </tr>
        </table>
      }

      <!-- Appréciation et visas -->
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
    .apc table { border-collapse: collapse; table-layout: fixed; width: 100%; }
    .apc td, .apc th { word-break: break-word; }
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

  /** La classe décide du barème ; le parcours actif ne sert que de repli. */
  protected fw = computed<ApcFramework>(() =>
    apcFramework(this.scope.scope()?.subsystem, this.view().className));

  protected schoolName = computed(() => this.auth.user()?.schoolName || 'Bayo Bilingual Complex');

  /** Largeur d'un trimestre en colonnes : ses unités (×2 si cotées) plus son total. */
  protected termSpan(term: ApcTerm): number {
    return term.slots.length * (this.fw().slots.gradePerSlot ? 2 : 1) + (term.total ? 1 : 0);
  }

  /** Les cellules à laisser vides sur chaque ligne, dans l'ordre du modèle. */
  protected dataColumns = computed(() => {
    const s = this.fw().slots;
    let n = 0;
    for (const t of s.terms) n += this.termSpan(t);
    if (s.annualTotal) n += 1 + s.scale.length;
    return Array.from({ length: n });
  });
}
