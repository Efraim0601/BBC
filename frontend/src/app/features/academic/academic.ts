import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { StudentApi } from '../students/students.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { AcademicApi, BulletinView, PvView, GradeEntryView, ReportCardInputsView, ReportCardInputRow, ReportCardInputUpsert, BulletinBatchJobView, BulletinBatchItemView } from './academic.api';
import { FoundationApi, AcademicReportingPeriodView, GeneratedDocumentView } from '../../core/foundation.api';
import { AuthService } from '../../core/auth.service';
import { ScopeService } from '../../core/scope.service';
import { I18nService } from '../../core/i18n.service';
import { Student } from '../../core/models';
import { ApcBulletinComponent } from './apc-bulletin';
import { PhotoApi } from '../../core/photo.api';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, TabsComponent,
} from '../../core/ui';

type Mode = 'bulletin' | 'grade-entry' | 'inputs' | 'pv' | 'batch';

const cleanDisplay = (value: string | null | undefined): string => {
  if (!value) return value ?? '';
  return value
    .replace(/\u00c3\u0192\u00c2\u00a9/g, '\u00e9')
    .replace(/\u00c3\u0192\u00c2\u00a8/g, '\u00e8')
    .replace(/\u00c3\u0192\u00c2\u00aa/g, '\u00ea')
    .replace(/\u00c3\u0192\u00c2\u00a0/g, '\u00e0')
    .replace(/\u00c3\u0192\u00c2\u00a2/g, '\u00e2')
    .replace(/\u00c3\u0192\u00c2\u00a7/g, '\u00e7')
    .replace(/\u00c3\u0192\u00c2\u00b4/g, '\u00f4')
    .replace(/\u00c3\u0192\u00c2\u00bb/g, '\u00fb')
    .replace(/\u00c3\u0192\u00c2\u00af/g, '\u00ef')
    .replace(/\u00c3\u0192\u00c2\u00b7/g, '\u00b7')
    .replace(/\u00c3\u0192\u00c2\u00a0/g, ' ')
    .replace(/\u00c3\u0083\u00c2\u2030/g, '\u00c9')
    .replace(/\u00c3\u0083\u00c2\u00a9/g, '\u00e9')
    .replace(/\u00c3\u0083\u00c2\u00a8/g, '\u00e8')
    .replace(/\u00c3\u0083\u00c2\u00aa/g, '\u00ea')
    .replace(/\u00c3\u0083\u00c2\u00a0/g, '\u00e0')
    .replace(/\u00c3\u0083\u00c2\u00a2/g, '\u00e2')
    .replace(/\u00c3\u0083\u00c2\u00a7/g, '\u00e7')
    .replace(/\u00c3\u0083\u00c2\u00b4/g, '\u00f4')
    .replace(/\u00c3\u0083\u00c2\u00bb/g, '\u00fb')
    .replace(/\u00c3\u0083\u00c2\u00af/g, '\u00ef')
    .replace(/\u00c3\u0082\u00c2\u00b7/g, '\u00b7')
    .replace(/\u00c3\u0082\u00c2\u00a0/g, ' ')
    .replace(/\u00c3\u2030/g, '\u00c9')
    .replace(/\u00c3\u00a9/g, '\u00e9')
    .replace(/\u00c3\u00a8/g, '\u00e8')
    .replace(/\u00c3\u00aa/g, '\u00ea')
    .replace(/\u00c3\u00a0/g, '\u00e0')
    .replace(/\u00c3\u00a2/g, '\u00e2')
    .replace(/\u00c3\u00a7/g, '\u00e7')
    .replace(/\u00c3\u00b4/g, '\u00f4')
    .replace(/\u00c3\u00bb/g, '\u00fb')
    .replace(/\u00c3\u00af/g, '\u00ef')
    .replace(/\u00c2\u00b7/g, '\u00b7')
    .replace(/\u00c2\u00a0/g, ' ');
};

const appreciation = (avg: number, fr: boolean): string => {
  if (avg >= 16) return fr ? 'Excellent' : 'Excellent';
  if (avg >= 14) return fr ? 'Très bien' : 'Very good';
  if (avg >= 12) return fr ? 'Bien' : 'Good';
  if (avg >= 10) return fr ? 'Assez bien' : 'Fair';
  if (avg >= 8) return fr ? 'Passable' : 'Pass';
  return fr ? 'Insuffisant' : 'Insufficient';
};

@Component({
  selector: 'bbc-academic',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, DatePipe, IconComponent, CardComponent, PageHeaderComponent,
    EmptyComponent, AvatarComponent, TabsComponent, ApcBulletinComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('academic')"
        [subtitle]="fr() ? 'Saisie des notes, bulletins, procès-verbaux' : 'Grade entry, report cards, master sheets'">
        <div right class="flex items-center gap-2 print:hidden">
          @if (mode() === 'bulletin' && selectedClass() && classStudents().length) {
            <button (click)="printAllBulletins()" [disabled]="bulkBusy()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white disabled:opacity-50">
              <bbc-icon name="download" [s]="16" />
              {{ fr() ? 'Tous les bulletins de la classe' : 'All class report cards' }}
            </button>
          }
          @if (mode() === 'bulletin' && bulletin()) {
            <button (click)="print()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
            </button>
          }
          @if (mode() === 'pv' && pv()) {
            <button (click)="print()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
            </button>
          }
        </div>
      </bbc-page-header>

      @if (notice(); as n) {
        <div class="mb-4 flex items-start gap-3 rounded-lg border px-4 py-3 text-sm" [class]="n.ok ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'" role="status">
          <div class="flex-1 font-semibold">{{ n.text }}</div>
          <button type="button" (click)="notice.set(null)" class="shrink-0 text-current opacity-70 hover:opacity-100" [attr.aria-label]="fr() ? 'Fermer le message' : 'Dismiss message'">×</button>
        </div>
      }

      <bbc-tabs [tabs]="tabs()" [value]="mode()" (change)="setMode($any($event))" />

      @if (mode() === 'grade-entry') {
        <div class="mb-4 rounded-xl border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-950 leading-relaxed print:hidden">
          <div class="font-bold">{{ fr() ? 'Saisie des notes : votre feuille de travail' : 'Grade entry: your work sheet' }}</div>
          <div class="mt-1 text-brand-900">{{ fr() ? '1. Choisissez la classe · 2. Choisissez la période · 3. Choisissez la matière · 4. Saisissez une note pour chaque élève · 5. Enregistrez ou envoyez à la direction.' : '1. Choose the class · 2. Choose the period · 3. Choose the subject · 4. Enter one mark for each student · 5. Save or send it to management.' }}</div>
        </div>
      }

      <!-- Toolbar: class + sequence -->
      <bbc-card className="mb-5 print:hidden">
        <div class="flex items-start gap-4 flex-wrap">
          <div class="flex-1 min-w-[240px]">
            <div class="text-xs font-semibold text-mute uppercase mb-2">{{ mode() === 'grade-entry' ? (fr() ? '1. Classe' : '1. Class') : (fr() ? 'Classe' : 'Class') }}</div>
            <select [ngModel]="selectedClass()" (ngModelChange)="onClassChange($event)"
              class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white text-ink focus:outline-none focus:border-brand-400 font-semibold">
              <option value="">{{ fr() ? '— Choisir une classe —' : '— Pick a class —' }}</option>
              @for (c of classes(); track c.id) {
                <option [value]="c.name">{{ c.name }}</option>
              }
            </select>
          </div>
          @if (reportingPeriods().length) {
            <div class="flex-1 min-w-[220px]">
              <div class="text-xs font-semibold text-mute uppercase mb-2">{{ mode() === 'grade-entry' ? (fr() ? '2. Période de notation' : '2. Grading period') : (fr() ? 'Jalon académique' : 'Academic milestone') }}</div>
              <select [ngModel]="selectedReportingPeriodId()" (ngModelChange)="onReportingPeriodChange($event)"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white text-ink focus:outline-none focus:border-brand-400 font-semibold">
                @for (p of reportingPeriods(); track p.id) { <option [value]="p.id">{{ p.code }} · {{ display(p.label) }}</option> }
              </select>
              @if (mode() === 'grade-entry') { <div class="mt-1 text-xs text-mute">{{ fr() ? 'La période sur laquelle les notes seront enregistrées.' : 'The period these marks will be recorded for.' }}</div> }
            </div>
          }
          @if (mode() === 'grade-entry' && gradeEntry(); as entry) {
            <div class="flex-1 min-w-[240px]">
              <div class="text-xs font-semibold text-mute uppercase mb-2">{{ fr() ? '3. Matière' : '3. Subject' }}</div>
              <select [ngModel]="selectedGradeSubjectCode()" (ngModelChange)="onGradeSubjectChange($event)"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white text-ink focus:outline-none focus:border-brand-400 font-semibold">
                @for (s of entry.availableSubjects; track s.code) {
                  <option [value]="s.code">{{ display(s.label) }} · {{ fr() ? 'coefficient' : 'coefficient' }} {{ s.coefficient }}</option>
                }
              </select>
              <div class="mt-1 text-xs text-mute">{{ fr() ? 'Les élèves et les évaluations apparaîtront ci-dessous.' : 'The students and assessments appear below.' }}</div>
            </div>
          }
          @if (mode() === 'pv') {
            <div class="flex flex-col justify-end shrink-0">
              <div class="text-xs font-semibold text-transparent uppercase mb-2">.</div>
              <button (click)="loadPv()" [disabled]="!selectedClass()"
                class="inline-flex items-center gap-2 h-10 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-lg text-sm font-semibold disabled:opacity-50">
                <bbc-icon name="doc" [s]="16" /> {{ fr() ? 'Charger le PV' : 'Load master sheet' }}
              </button>
            </div>
          }
        </div>
      </bbc-card>

      <!-- ============ TEACHER GRADE ENTRY ============ -->
      @if (mode() === 'grade-entry') {
        @if (!selectedClass()) {
          <bbc-card className="border-brand-200">
            <div class="flex items-start gap-3">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-100 text-brand-700 font-bold">1</div>
              <div><h2 class="text-lg font-bold text-ink">{{ fr() ? 'Commencer une feuille de notes' : 'Start a grade sheet' }}</h2><p class="mt-1 text-sm text-mute">{{ fr() ? 'Choisissez une classe ci-dessus. La période et la matière vous permettront ensuite de voir exactement les élèves à noter.' : 'Choose a class above. The period and subject will then show you exactly which students need marks.' }}</p></div>
            </div>
          </bbc-card>
        } @else if (gradeEntryError(); as error) {
          <bbc-card className="border-rose-200 bg-rose-50/40">
            <div class="flex items-start gap-3" role="alert">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-700 font-bold">!</div>
              <div class="flex-1"><h2 class="text-lg font-bold text-rose-950">{{ fr() ? 'Cette feuille ne peut pas être ouverte' : 'This grade sheet cannot be opened' }}</h2><p class="mt-1 text-sm text-rose-900">{{ error }}</p><p class="mt-2 text-xs text-rose-800">{{ fr() ? 'Vérifiez la classe, la période, l’affectation de la matière et l’enseignant responsable, puis réessayez.' : 'Check the class, period, subject assignment, and responsible teacher, then try again.' }}</p><button type="button" (click)="loadGradeEntry()" class="mt-3 h-9 px-3 rounded-lg bg-white border border-rose-300 text-rose-800 text-sm font-semibold hover:bg-rose-100">{{ fr() ? 'Réessayer' : 'Try again' }}</button></div>
            </div>
          </bbc-card>
        } @else if (!gradeEntry()) {
          <bbc-card><bbc-empty icon="doc" [label]="fr() ? 'Préparation de la feuille de notes…' : 'Preparing the grade sheet…'" /></bbc-card>
        } @else {
          @if (gradeEntry(); as entry) {
            <bbc-card className="mb-4">
              <div class="flex flex-wrap items-start gap-3 justify-between">
                <div>
                  <div class="text-xs font-semibold uppercase tracking-wide text-brand-700">{{ fr() ? 'Feuille de notes' : 'Grade sheet' }}</div>
                  <h2 class="text-xl font-bold text-ink mt-1">{{ display(entry.subjectLabel) }}</h2>
                  <div class="text-sm text-mute mt-1">{{ entry.className }} · {{ selectedReportingPeriodCode() }} · {{ fr() ? 'coefficient' : 'coefficient' }} {{ entry.coefficient }}</div>
                  <div class="text-xs text-mute mt-1">{{ fr() ? 'Enseignant responsable :' : 'Responsible teacher:' }} {{ display(entry.teacherName) || (fr() ? 'Non configuré' : 'Not configured') }}</div>
                </div>
                <span class="px-2.5 py-1 rounded-full text-xs font-bold uppercase"
                  [class]="entry.packetStatus === 'ACCEPTED' ? 'bg-emerald-100 text-emerald-700' : entry.packetStatus === 'SUBMITTED' ? 'bg-blue-100 text-blue-700' : entry.packetStatus === 'RETURNED' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-800'">
                  {{ gradePacketStatusLabel(entry.packetStatus) }}
                </span>
              </div>
              <div class="mt-4 rounded-lg border border-brand-200 bg-brand-50 px-3 py-3 text-sm text-brand-950"><strong>{{ fr() ? 'Votre tâche :' : 'Your task:' }}</strong> {{ fr() ? 'saisissez la note prévue pour chaque élève. Enregistrez pour garder un brouillon ; envoyez ensuite la feuille à la direction.' : 'enter the required mark for each student. Save to keep a draft; then send the sheet to management.' }}</div>
              <div class="mt-4 grid grid-cols-2 md:grid-cols-3 gap-3">
                <div class="rounded-lg border border-slate-200 bg-slate-50 p-3"><div class="text-[11px] uppercase text-mute font-semibold">{{ fr() ? 'Élèves à noter' : 'Students to grade' }}</div><div class="text-xl font-bold text-ink mt-1">{{ entry.totalStudents }}</div></div>
                <div class="rounded-lg border border-slate-200 bg-slate-50 p-3"><div class="text-[11px] uppercase text-mute font-semibold">{{ fr() ? 'Saisie complète' : 'Completed' }}</div><div class="text-xl font-bold text-emerald-700 mt-1">{{ entry.completedStudents }}/{{ entry.totalStudents }}</div></div>
                <div class="rounded-lg border border-slate-200 bg-slate-50 p-3"><div class="text-[11px] uppercase text-mute font-semibold">{{ fr() ? 'Colonnes de notes' : 'Mark columns' }}</div><div class="text-xl font-bold text-ink mt-1">{{ entry.assessments.length }}</div></div>
              </div>
              @if (entry.blockers.length) {
                <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-950" role="status">
                  <div class="font-bold">{{ fr() ? 'Il reste des champs à compléter avant l’envoi' : 'Some fields still need to be completed before sending' }}</div>
                  <ul class="mt-1 list-disc pl-5">@for (blocker of entry.blockers; track blocker) { <li>{{ gradeBlockerLabel(blocker) }}</li> }</ul>
                </div>
              } @else if (entry.totalStudents > 0 && entry.assessments.length > 0) {
                <div class="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{{ fr() ? 'Toutes les notes obligatoires sont renseignées. Vous pouvez enregistrer ou envoyer la feuille.' : 'All required marks are entered. You can save or send the sheet.' }}</div>
              }
              <div class="mt-4 text-sm text-mute">{{ fr() ? 'Saisissez la note sur le barème affiché (par exemple /20). Pour un élève absent ou dispensé, choisissez le statut correspondant au lieu de saisir une note.' : 'Enter the mark using the scale shown (for example /20). For an absent or exempt student, choose the matching status instead of entering a mark.' }}</div>
            </bbc-card>
            @if (gradeEntry(); as entry) {
            <bbc-card className="mb-4 border-brand-200 bg-brand-50/40">
              <div class="flex items-start gap-3">
                <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-brand-700 font-bold">✓</div>
                <div>
                  <div class="font-bold text-ink">{{ fr() ? 'Note à saisir' : 'Mark to enter' }}</div>
                  <div class="mt-1 text-sm text-mute">{{ fr() ? 'Saisissez une note pour chaque élève sur le barème affiché. Les détails techniques sont gérés automatiquement par le système.' : 'Enter one mark for each student using the scale shown. Technical details are handled automatically by the system.' }}</div>
                </div>
              </div>
              @if (entry.assessments.length === 1) {
                <div class="mt-3 flex items-center justify-between gap-3 rounded-lg border border-brand-200 bg-white px-3 py-2 text-sm">
                  <span class="font-semibold text-ink">{{ selectedReportingPeriodCode() }} · {{ display(entry.subjectLabel) }}</span>
                  <span class="text-mute">{{ fr() ? 'sur' : 'out of' }} {{ entry.assessments[0].maxScore }}</span>
                </div>
              } @else if (entry.assessments.length > 1) {
                <div class="mt-3 rounded-lg border border-brand-200 bg-white px-3 py-2 text-sm text-mute">{{ fr() ? entry.assessments.length + ' colonnes de notes sont prévues pour cette matière.' : entry.assessments.length + ' mark columns are expected for this subject.' }}</div>
              } @else {
                <div class="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">{{ fr() ? 'La feuille n’est pas encore prête : aucune colonne de note n’est configurée pour cette matière.' : 'This sheet is not ready yet: no mark column is configured for this subject.' }}</div>
              }
            </bbc-card>
            }
            <bbc-card className="overflow-hidden">
              <div class="flex items-start justify-between gap-3 flex-wrap px-5 pt-5">
                <div><h2 class="text-lg font-bold text-ink">{{ fr() ? '4. Saisir les notes' : '4. Enter marks' }}</h2><p class="mt-1 text-sm text-mute">{{ fr() ? 'Une ligne = un élève. Remplissez chaque colonne obligatoire.' : 'One row = one student. Complete every required column.' }}</p></div>
                @if (entry.assessments.length) { <div class="rounded-lg bg-slate-50 border border-slate-200 px-3 py-2 text-sm font-semibold text-ink">{{ entry.completedStudents }} / {{ entry.totalStudents }} {{ fr() ? 'élève(s) complet(s)' : 'student(s) complete' }}</div> }
              </div>
              @if (entry.assessments.length) {
              <div class="overflow-x-auto mt-4">
                <table class="min-w-[880px] w-full text-sm">
                  <thead class="bg-brand-50 border-y-2 border-brand-600">
                    <tr class="text-brand-700 font-bold uppercase text-[10px]">
                      <th class="text-left py-3 pl-5 sticky left-0 bg-brand-50 z-10 min-w-[220px]">{{ fr() ? 'Élève' : 'Student' }}</th>
                      @for (a of entry.assessments; track a.id) {
                        <th class="text-center py-3 px-2 min-w-[160px]"><span class="normal-case text-sm">{{ gradeAssessmentLabel(entry, a) }}</span><div class="font-normal normal-case">{{ fr() ? 'Note sur' : 'Mark out of' }} {{ a.maxScore }}</div><div class="font-normal normal-case text-mute">{{ a.mandatory ? (fr() ? 'Obligatoire' : 'Required') : (fr() ? 'Facultatif' : 'Optional') }}</div></th>
                      }
                      <th class="text-left py-3 px-2 min-w-[240px]">{{ fr() ? 'Appréciation (facultatif)' : 'Comment (optional)' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of entry.students; track row.studentId) {
                      <tr class="border-b border-slate-100 align-top">
                        <td class="py-3 pl-5 sticky left-0 bg-white z-10">
                          <div class="font-semibold text-ink">{{ row.studentName }}</div><div class="text-[11px] text-mute font-mono">{{ row.matricule }}</div>
                        </td>
                        @for (cell of row.values; track cell.assessmentId; let i = $index) {
                          <td class="p-2">
                            <div class="mb-1 text-[11px] text-mute">{{ fr() ? 'Note /' : 'Mark /' }} {{ entry.assessments[i].maxScore }}</div>
                            <input type="number" min="0" [max]="entry.assessments[i].maxScore" step="0.01" [ngModel]="cell.mark" (ngModelChange)="updateGradeMark(row.studentId, i, $event)"
                              [attr.aria-label]="(fr() ? 'Note de ' : 'Mark for ') + row.studentName + ' — ' + (gradeAssessmentLabel(entry, entry.assessments[i]))"
                              [disabled]="entry.packetStatus === 'SUBMITTED' || entry.packetStatus === 'ACCEPTED' || entry.packetStatus === 'LOCKED'"
                              class="w-full h-10 px-2 text-center rounded-md border border-slate-300 bg-white text-base font-semibold focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100" placeholder="—" />
                            <select [ngModel]="cell.valueStatus" (ngModelChange)="updateGradeStatus(row.studentId, i, $event)"
                              [disabled]="entry.packetStatus === 'SUBMITTED' || entry.packetStatus === 'ACCEPTED' || entry.packetStatus === 'LOCKED'"
                              class="w-full h-8 mt-1 px-2 text-xs rounded border border-slate-200 bg-white text-mute">
                              <option value="SCORED">{{ fr() ? 'Note saisie' : 'Mark entered' }}</option><option value="ABSENT">{{ fr() ? 'Absent' : 'Absent' }}</option><option value="EXEMPT">{{ fr() ? 'Dispensé' : 'Exempt' }}</option><option value="MISSING">{{ fr() ? 'À compléter' : 'To complete' }}</option>
                            </select>
                          </td>
                        }
                        <td class="p-2"><textarea rows="2" [ngModel]="row.comment" (ngModelChange)="updateGradeComment(row.studentId, $event)" [disabled]="entry.packetStatus === 'SUBMITTED' || entry.packetStatus === 'ACCEPTED' || entry.packetStatus === 'LOCKED'" maxlength="500" class="w-full px-2 py-2 rounded-md border border-slate-300 text-sm resize-y focus:outline-none focus:border-brand-500 disabled:bg-slate-100" [placeholder]="fr() ? 'Facultatif : remarque sur le travail…' : 'Optional: comment on the work…'"></textarea></td>
                      </tr>
                    } @empty {
                      <tr><td [attr.colspan]="entry.assessments.length + 2" class="p-8 text-center text-mute">{{ fr() ? 'Aucun élève actif dans cette classe pour la session.' : 'No active student in this class for the session.' }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
              } @else {
                <div class="mx-5 mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-950">{{ fr() ? 'La saisie ne peut pas commencer tant qu’une évaluation n’est pas configurée pour cette matière.' : 'Mark entry cannot start until an assessment is configured for this subject.' }}</div>
              }
              <div class="flex flex-wrap gap-2 items-center mt-5 pt-4 border-t border-slate-100 print:hidden">
                <div class="flex-1 min-w-[260px] text-xs text-mute">{{ entry.packetStatus === 'SUBMITTED' ? (fr() ? 'Cette feuille est en attente de vérification par la direction.' : 'This sheet is waiting for management review.') : entry.packetStatus === 'ACCEPTED' ? (fr() ? 'Cette feuille a été acceptée et est verrouillée.' : 'This sheet was accepted and is locked.') : (fr() ? 'Enregistrer = garder votre travail. Envoyer à la direction = demander la vérification.' : 'Save = keep your work. Send to management = request review.') }}</div>
                @if (canWrite && (entry.packetStatus === 'DRAFT' || entry.packetStatus === 'RETURNED')) {
                  <button type="button" (click)="saveGradeEntry()" [disabled]="gradeBusy()" class="h-10 px-4 rounded-lg border border-slate-300 text-sm font-semibold text-ink hover:bg-slate-50 disabled:opacity-50">{{ gradeBusy() ? '…' : (fr() ? 'Enregistrer sans envoyer' : 'Save without sending') }}</button>
                  <button type="button" (click)="submitGradeEntry()" [disabled]="gradeBusy() || entry.blockers.length > 0 || !entry.assessments.length" [title]="entry.blockers.length ? (fr() ? 'Complétez les champs indiqués avant l’envoi.' : 'Complete the highlighted fields before sending.') : ''" class="h-10 px-4 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50">{{ fr() ? 'Envoyer à la direction' : 'Send to management' }}</button>
                }
                @if (canReview() && entry.packetStatus === 'SUBMITTED') {
                  <button type="button" (click)="reviewGradeEntry('RETURN')" [disabled]="gradeBusy()" class="h-10 px-4 rounded-lg border border-rose-200 text-rose-700 text-sm font-semibold hover:bg-rose-50 disabled:opacity-50">{{ fr() ? 'Retourner pour correction' : 'Return for correction' }}</button>
                  <button type="button" (click)="reviewGradeEntry('ACCEPT')" [disabled]="gradeBusy()" class="h-10 px-4 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50">{{ fr() ? 'Accepter la feuille' : 'Accept the sheet' }}</button>
                }
              </div>
            </bbc-card>
          }
        }
      }

      <!-- ============ ATTENDANCE / COUNCIL INPUTS ============ -->
      @if (mode() === 'inputs') {
        @if (!selectedClass()) {
          <bbc-card><bbc-empty icon="users" [label]="fr() ? 'Choisissez une classe pour saisir l’assiduité et le conseil.' : 'Pick a class to enter attendance and council inputs.'" /></bbc-card>
        } @else if (!reportInputs()) {
          <bbc-card><bbc-empty icon="doc" [label]="fr() ? 'Chargement des fiches…' : 'Loading input sheets…'" /></bbc-card>
        } @else {
          @if (reportInputs(); as inputs) {
            <bbc-card className="mb-4">
              <div class="flex items-start justify-between gap-3 flex-wrap">
                <div>
                  <div class="text-lg font-bold text-ink">{{ fr() ? 'Assiduité et conseil de classe' : 'Attendance and class council' }}</div>
                  <div class="text-sm text-mute mt-1">{{ inputs.className }} · {{ inputs.reportingPeriodCode }} · {{ inputs.reportingPeriodLabel }}</div>
                </div>
                <div class="text-xs text-mute max-w-xl">{{ fr() ? 'Les chiffres de présence sont calculés à partir des appels finalisés. Les corrections et décisions sont conservées en brouillon, soumises puis approuvées avant d’entrer dans le bulletin.' : 'Attendance totals come from finalized calls. Corrections and decisions are drafted, submitted, and approved before they enter a report card.' }}</div>
              </div>
            </bbc-card>
            <div class="space-y-4">
              @for (row of inputs.rows; track row.studentId) {
                <bbc-card>
                  <div class="flex items-start justify-between gap-3 flex-wrap border-b border-slate-100 pb-3">
                    <div>
                      <div class="font-bold text-ink">{{ row.studentName }}</div>
                      <div class="text-xs text-mute font-mono mt-0.5">{{ row.matricule }}</div>
                    </div>
                    <div class="flex items-center gap-2 text-xs flex-wrap">
                      <span class="rounded-full bg-slate-100 px-2 py-1 font-semibold">{{ row.attendance?.finalizedSessions ?? 0 }} {{ fr() ? 'appels' : 'calls' }}</span>
                      <span class="rounded-full bg-rose-50 text-rose-700 px-2 py-1 font-semibold">{{ row.attendance?.absentCount ?? 0 }} {{ fr() ? 'abs.' : 'abs.' }}</span>
                      <span class="rounded-full bg-amber-50 text-amber-800 px-2 py-1 font-semibold">{{ row.attendance?.lateMinutes ?? 0 }} min</span>
                      @if (row.attendanceAdjustment) { <span class="rounded-full px-2 py-1 font-semibold" [class]="row.attendanceAdjustment.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-700' : row.attendanceAdjustment.status === 'SUBMITTED' ? 'bg-blue-50 text-blue-700' : 'bg-amber-50 text-amber-800'">{{ row.attendanceAdjustment.status }}</span> }
                      @if (row.conduct) { <span class="rounded-full px-2 py-1 font-semibold" [class]="row.conduct.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-700' : row.conduct.status === 'SUBMITTED' ? 'bg-blue-50 text-blue-700' : 'bg-amber-50 text-amber-800'">{{ row.conduct.status }}</span> }
                    </div>
                  </div>
                  <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 mt-4">
                    <section class="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div class="font-bold text-sm text-ink">{{ fr() ? 'Correction des heures d’absence' : 'Absence-hours correction' }}</div>
                      <div class="text-xs text-mute mt-1">{{ fr() ? 'Valeurs ajoutées aux appels finalisés après approbation.' : 'Values added to finalized calls after approval.' }}</div>
                      <div class="grid grid-cols-3 gap-2 mt-3">
                        <label class="field-label"><span>{{ fr() ? 'Justifiées (h)' : 'Justified (h)' }}</span><input type="number" min="0" step="0.25" [ngModel]="inputDraft(row.studentId).justifiedAbsenceHours" (ngModelChange)="updateInput(row.studentId, { justifiedAbsenceHours: +$event })" class="field" /></label>
                        <label class="field-label"><span>{{ fr() ? 'Non justifiées (h)' : 'Unjustified (h)' }}</span><input type="number" min="0" step="0.25" [ngModel]="inputDraft(row.studentId).unjustifiedAbsenceHours" (ngModelChange)="updateInput(row.studentId, { unjustifiedAbsenceHours: +$event })" class="field" /></label>
                        <label class="field-label"><span>{{ fr() ? 'Retards (min)' : 'Late (min)' }}</span><input type="number" min="0" [ngModel]="inputDraft(row.studentId).lateMinutes" (ngModelChange)="updateInput(row.studentId, { lateMinutes: +$event })" class="field" /></label>
                      </div>
                      <label class="field-label mt-2"><span>{{ fr() ? 'Motif (obligatoire)' : 'Reason (required)' }}</span><input [ngModel]="inputDraft(row.studentId).reason" (ngModelChange)="updateInput(row.studentId, { reason: $event })" maxlength="500" class="field" [class.border-rose-400]="!inputDraft(row.studentId).reason" placeholder="Ex. Certificat médical" /></label>
                      @if (!inputDraft(row.studentId).reason.trim()) { <div class="mt-1 text-xs font-semibold text-rose-600">{{ fr() ? 'Le motif est obligatoire avant l’enregistrement ou la soumission.' : 'A reason is required before saving or submitting.' }}</div> }
                      <label class="field-label mt-2"><span>{{ fr() ? 'Référence de preuve' : 'Evidence reference' }}</span><input [ngModel]="inputDraft(row.studentId).evidenceReference" (ngModelChange)="updateInput(row.studentId, { evidenceReference: $event })" maxlength="240" class="field" placeholder="Ex. CERT-2026-001" /></label>
                    </section>
                    <section class="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div class="font-bold text-sm text-ink">{{ fr() ? 'Travail, conduite et décision du conseil' : 'Work, conduct and council decision' }}</div>
                      <div class="grid grid-cols-2 md:grid-cols-3 gap-2 mt-3 text-xs">
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).workWarning" (ngModelChange)="updateInput(row.studentId, { workWarning: $event })" /> {{ fr() ? 'Avert. travail' : 'Work warning' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).workBlame" (ngModelChange)="updateInput(row.studentId, { workBlame: $event })" /> {{ fr() ? 'Blâme travail' : 'Work blame' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).conductWarning" (ngModelChange)="updateInput(row.studentId, { conductWarning: $event })" /> {{ fr() ? 'Avert. conduite' : 'Conduct warning' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).conductBlame" (ngModelChange)="updateInput(row.studentId, { conductBlame: $event })" /> {{ fr() ? 'Blâme conduite' : 'Conduct blame' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).honorRoll" (ngModelChange)="updateInput(row.studentId, { honorRoll: $event })" /> {{ fr() ? 'Tableau honneur' : 'Honor roll' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).encouragement" (ngModelChange)="updateInput(row.studentId, { encouragement: $event })" /> {{ fr() ? 'Encouragement' : 'Encouragement' }}</label>
                        <label class="flex items-center gap-2"><input type="checkbox" [ngModel]="inputDraft(row.studentId).congratulations" (ngModelChange)="updateInput(row.studentId, { congratulations: $event })" /> {{ fr() ? 'Félicitations' : 'Congratulations' }}</label>
                      </div>
                      <div class="grid grid-cols-2 gap-2 mt-3">
                        <label class="field-label"><span>{{ fr() ? 'Exclusion (jours)' : 'Exclusion (days)' }}</span><input type="number" min="0" [ngModel]="inputDraft(row.studentId).exclusionDays" (ngModelChange)="updateInput(row.studentId, { exclusionDays: +$event })" class="field" /></label>
                        <label class="field-label"><span>{{ fr() ? 'Code de décision' : 'Decision code' }}</span><input [ngModel]="inputDraft(row.studentId).decisionCode" (ngModelChange)="updateInput(row.studentId, { decisionCode: $event })" class="field" placeholder="PROMOTE / REPEAT / REVIEW" /></label>
                      </div>
                      <label class="field-label mt-2"><span>{{ fr() ? 'Observation du conseil' : 'Council observation' }}</span><textarea rows="2" [ngModel]="inputDraft(row.studentId).councilObservation" (ngModelChange)="updateInput(row.studentId, { councilObservation: $event })" maxlength="4000" class="field resize-y" placeholder="{{ fr() ? 'Observation imprimée sur le bulletin…' : 'Observation printed on the report card…' }}"></textarea></label>
                    </section>
                  </div>
                  <div class="flex flex-wrap justify-end gap-2 mt-4 pt-3 border-t border-slate-100">
                    <button (click)="saveReportInput(row)" [disabled]="inputBusy() === row.studentId" class="h-9 px-3 rounded-lg border border-slate-300 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">{{ inputBusy() === row.studentId ? '…' : (fr() ? 'Enregistrer le brouillon' : 'Save draft') }}</button>
                    @if (!row.conduct || !['SUBMITTED','APPROVED','LOCKED'].includes(row.conduct.status) || !row.attendanceAdjustment || !['SUBMITTED','APPROVED'].includes(row.attendanceAdjustment.status)) {
                      <button (click)="submitReportInput(row)" [disabled]="inputBusy() === row.studentId" class="h-9 px-3 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">{{ fr() ? 'Soumettre à la revue' : 'Submit for review' }}</button>
                    }
                    @if (canReview() && ((row.conduct?.status === 'SUBMITTED') || (row.attendanceAdjustment?.status === 'SUBMITTED'))) {
                      <button (click)="requestInputReview(row, 'RETURN')" [disabled]="inputBusy() === row.studentId" class="h-9 px-3 rounded-lg border border-rose-200 text-rose-700 text-sm font-semibold">{{ fr() ? 'Retourner' : 'Return' }}</button>
                      <button (click)="requestInputReview(row, 'APPROVE')" [disabled]="inputBusy() === row.studentId" class="h-9 px-3 rounded-lg bg-emerald-600 text-white text-sm font-semibold">{{ fr() ? 'Approuver' : 'Approve' }}</button>
                    }
                  </div>
                </bbc-card>
              } @empty {
                <bbc-card><bbc-empty icon="users" [label]="fr() ? 'Aucun élève actif dans cette classe.' : 'No active students in this class.'" /></bbc-card>
              }
            </div>
          }
        }
      }

      <!-- ============ BULLETIN ============ -->
      @if (mode() === 'bulletin') {
        @if (!selectedClass()) {
          <bbc-card>
            <bbc-empty icon="users"
              [label]="fr() ? 'Choisissez d’abord une classe pour afficher ses élèves.' : 'Pick a class first to list its students.'" />
          </bbc-card>
        } @else {
          <div class="grid grid-cols-12 gap-4">
            <!-- Class roster -->
            <bbc-card className="col-span-12 lg:col-span-4 print:hidden"
              [title]="selectedClass()"
              [subtitle]="classStudents().length + (fr() ? ' élèves' : ' students')">
              <input [ngModel]="studentQuery()" (ngModelChange)="studentQuery.set($event)"
                [placeholder]="fr() ? 'Rechercher…' : 'Search…'"
                class="w-full h-9 px-3 mb-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              <div class="space-y-1 max-h-[32rem] overflow-y-auto pr-1">
                @for (s of filteredClassStudents(); track s.id) {
                  <button type="button" (click)="onStudentChange(s.id)"
                    class="w-full flex items-center gap-2.5 p-2 rounded-lg text-left transition"
                    [class]="selectedStudentId() === s.id ? 'bg-brand-50 border border-brand-200' : 'hover:bg-slate-50 border border-transparent'">
                    <bbc-avatar [name]="s.name" [hue]="s.photoHue" />
                    <div class="flex-1 min-w-0">
                      <div class="text-sm font-semibold text-ink truncate">{{ s.name }}</div>
                      <div class="text-[11px] text-mute font-mono">{{ s.matricule }}</div>
                    </div>
                  </button>
                } @empty {
                  <bbc-empty icon="users" [label]="fr() ? 'Aucun élève' : 'No students'" />
                }
              </div>
            </bbc-card>

            <!-- Single bulletin -->
            <div class="col-span-12 lg:col-span-8">
              @if (bulletin(); as b) {
                @if (isApc()) {
                  <bbc-card className="overflow-x-auto"><bbc-apc-bulletin [view]="b" /></bbc-card>
                } @else {
                  <bbc-card className="overflow-hidden">
                    <div class="bg-white rounded-xl2 overflow-hidden -m-5 print:m-0">
                      <div class="bg-gradient-to-br from-brand-700 to-brand-800 text-white p-6 relative overflow-hidden">
                        <div class="absolute -top-8 -right-8 w-40 h-40 rounded-full bg-gold-400/15 blur-2xl"></div>
                        <div class="flex items-start gap-4 relative">
                          <div class="w-14 h-14 bg-white/95 rounded-lg flex items-center justify-center shrink-0 text-brand-700">
                            <bbc-icon name="cap" [s]="30" />
                          </div>
                          <div class="flex-1 min-w-0">
                            <div class="text-[10px] uppercase tracking-wider text-gold-200 font-semibold">
                              République du Cameroun · {{ fr() ? 'MINESEC' : 'MoSEd' }}
                            </div>
                            <div class="font-display text-xl font-bold leading-tight">Bayo Bilingual Complex</div>
                            <div class="text-sm text-brand-100 mt-0.5">Maroua</div>
                            <div class="text-xs text-gold-200 mt-2 font-semibold">
                              {{ (fr() ? 'BULLETIN' : 'REPORT CARD') }} — {{ (fr() ? 'SÉQUENCE ' : 'SEQ. ') + b.sequence }}
                            </div>
                          </div>
                          <div class="flex flex-col items-end gap-2">
                            @if (b.state === 'PUBLISHED') {
                              <div class="bg-emerald-500 text-white text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                                <bbc-icon name="eye" [s]="12" [sw]="3" /> {{ fr() ? 'Publié aux parents' : 'Published to parents' }}
                              </div>
                            } @else if (b.state === 'PREVIEW') {
                              <div class="bg-blue-100 text-blue-800 text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                                <bbc-icon name="eye" [s]="12" [sw]="3" /> {{ fr() ? 'Aperçu lecture seule' : 'Read-only preview' }}
                              </div>
                            } @else if (b.validated) {
                              <div class="bg-emerald-500 text-white text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                                <bbc-icon name="check" [s]="12" [sw]="3" /> {{ fr() ? 'Validé' : 'Validated' }}
                              </div>
                            } @else {
                              <div class="bg-amber-400 text-amber-900 text-[10px] font-bold uppercase px-2 py-1 rounded">
                                {{ fr() ? 'En attente' : 'Awaiting' }}
                              </div>
                            }
                            @if (b.financiallyBlocked) {
                              <div class="bg-rose-500 text-white text-[10px] font-bold uppercase px-2 py-1 rounded flex items-center gap-1">
                                <bbc-icon name="alertTri" [s]="12" /> {{ fr() ? 'Bloqué' : 'Blocked' }}
                              </div>
                            }
                          </div>
                        </div>
                          <div class="flex items-center gap-5 mt-5 relative">
                          <bbc-avatar [name]="b.studentName" [hue]="210" [size]="56" [photoUrl]="studentPhotoUrl()" />
                          <div class="flex-1 min-w-0">
                            <div class="text-[10px] uppercase tracking-wider text-gold-200">{{ fr() ? 'Nom' : 'Name' }}</div>
                            <div class="text-lg font-bold truncate">{{ b.studentName }}</div>
                            <div class="text-xs text-brand-100">{{ b.className }}</div>
                          </div>
                          <div class="text-right">
                            <div class="text-[10px] uppercase tracking-wider text-gold-200">{{ fr() ? 'Rang' : 'Rank' }}</div>
                            <div class="font-bold text-2xl text-gold-300">{{ b.rank }}<span class="text-sm text-brand-100">/{{ b.classSize }}</span></div>
                          </div>
                        </div>
                      </div>

                      <div class="p-6">
                        @if (b.state === 'PREVIEW') {
                          <div class="mb-5 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900">
                            <div class="font-bold">{{ fr() ? 'Aperçu en lecture seule' : 'Read-only preview' }}</div>
                            <div class="mt-1">{{ fr() ? 'Aucune version de bulletin n’a été créée. Les données affichées proviennent du calcul courant et ne modifient pas le dossier.' : 'No report-card version was created. The displayed data is a current calculation and does not change the record.' }}</div>
                          </div>
                        }
                        @if (b.complete === false && b.blockers?.length) {
                          <div class="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                            <div class="font-bold">{{ fr() ? 'Bulletin non complet' : 'Report card is incomplete' }}</div>
                            <ul class="mt-1 list-disc pl-5 font-normal">@for (blocker of b.blockers; track blocker) { <li>{{ blocker }}</li> }</ul>
                          </div>
                        }
                        @if (b.financiallyBlocked) {
                          <div class="mb-5 flex items-center gap-3 px-4 py-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm font-semibold">
                            <bbc-icon name="alertTri" [s]="18" />
                            {{ fr() ? 'Bulletin verrouillé — solde de frais impayé' : 'Report card locked — outstanding fee balance' }}
                          </div>
                        }

                        <table class="w-full text-sm">
                          <thead>
                            <tr class="border-b-2 border-brand-600 text-[11px] uppercase text-brand-700 font-bold">
                              <th class="text-left py-2">{{ fr() ? 'Matière' : 'Subject' }}</th>
                              <th class="text-center py-2 w-16">{{ fr() ? 'Coef' : 'Coef' }}</th>
                              <th class="text-center py-2 w-20">{{ fr() ? 'Note' : 'Mark' }}</th>
                              <th class="text-center py-2 w-24">{{ fr() ? 'Pondéré' : 'Weighted' }}</th>
                              <th class="text-left py-2 min-w-[160px]">{{ fr() ? 'Appréciation' : 'Appreciation' }}</th>
                            </tr>
                          </thead>
                          <tbody>
                            @for (l of b.lines; track l.subjectCode) {
                              <tr class="border-b border-slate-100">
                                <td class="py-2.5 font-semibold text-ink">{{ l.subjectLabel }} @if (l.subjectGroupLabel) { <div class="text-[10px] font-normal uppercase tracking-wide text-brand-600">{{ l.subjectGroupLabel }}</div> } @if (l.teacherName) { <div class="text-[10px] font-normal text-mute">{{ fr() ? 'Prof. ' : 'Teacher: ' }}{{ l.teacherName }}</div> } @if (l.periodMarks?.length) { <div class="text-[10px] font-normal text-mute mt-0.5">@for (part of (l.periodMarks ?? []); track part.periodCode) { {{ part.periodCode }}: {{ part.mark }} @if (!$last) { · } }</div> }</td>
                                <td class="py-2.5 text-center text-mute">{{ l.coef }}</td>
                                <td class="py-2.5 text-center font-bold"
                                  [class]="l.mark < 10 ? 'text-rose-700' : l.mark < 14 ? 'text-ink' : 'text-emerald-700'">{{ l.mark }}</td>
                                <td class="py-2.5 text-center font-mono text-ink">{{ l.weighted }}</td>
                                <td class="py-2.5 pr-2 text-xs italic text-mute">{{ l.teacherRemark || appr(l.mark) }}</td>
                              </tr>
                            } @empty {
                              <tr><td colspan="5" class="py-10"><bbc-empty icon="doc" [label]="fr() ? 'Aucune note' : 'No marks'" /></td></tr>
                            }
                          </tbody>
                        </table>

                        @if (b.groupStats?.length) {
                          <div class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-2">
                            @for (group of (b.groupStats ?? []); track group.code) {
                              <div class="rounded-lg border border-brand-100 bg-brand-50/50 px-3 py-2">
                                <div class="flex items-center justify-between gap-2 text-xs font-semibold text-brand-900">
                                  <span>{{ group.label || group.code }}</span><span>{{ group.average }}/20</span>
                                </div>
                                <div class="text-[11px] text-mute mt-1">
                                  {{ fr() ? 'Total pondéré' : 'Weighted total' }}: {{ group.total }} · {{ fr() ? 'Coef' : 'Coef' }}: {{ group.coefficient }} · {{ group.subjectCount }} {{ fr() ? 'matières' : 'subjects' }}
                                </div>
                              </div>
                            }
                          </div>
                        }

                        <div class="grid grid-cols-3 gap-3 mt-5">
                          <div class="rounded-lg px-3 py-2.5 ring-2 ring-gold-300"
                            [class]="b.average < 10 ? 'bg-rose-50 text-rose-700' : b.average < 14 ? 'bg-slate-50 text-ink' : 'bg-emerald-50 text-emerald-700'">
                            <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moyenne' : 'Average' }}</div>
                            <div class="text-lg font-bold">{{ b.average }}/20</div>
                          </div>
                          <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                            <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Rang' : 'Rank' }}</div>
                            <div class="text-lg font-bold">{{ b.rank }}/{{ b.classSize }}</div>
                          </div>
                          <div class="rounded-lg px-3 py-2.5 bg-slate-50 text-ink">
                            <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moy. classe' : 'Class avg' }}</div>
                            <div class="text-lg font-bold">{{ b.classAverage }}/20</div>
                          </div>
                        </div>
                        @if (b.attendance; as attendance) {
                          <div class="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
                            <div class="text-[10px] uppercase tracking-wider text-mute font-semibold">{{ fr() ? 'Présence sur la période' : 'Attendance for period' }}</div>
                            <div class="grid grid-cols-2 md:grid-cols-5 gap-3 mt-2 text-sm">
                              <div><div class="text-xs text-mute">{{ fr() ? 'Séances finalisées' : 'Finalized sessions' }}</div><div class="font-bold">{{ attendance.finalizedSessions }}</div></div>
                              <div><div class="text-xs text-mute">{{ fr() ? 'Absences' : 'Absences' }}</div><div class="font-bold text-rose-700">{{ attendance.absentCount }}</div></div>
                              <div><div class="text-xs text-mute">{{ fr() ? 'Justifiées' : 'Excused' }}</div><div class="font-bold">{{ attendance.excusedCount }}</div></div>
                              <div><div class="text-xs text-mute">{{ fr() ? 'Retards' : 'Late' }}</div><div class="font-bold">{{ attendance.lateCount }} · {{ attendance.lateMinutes }} min</div></div>
                              <div><div class="text-xs text-mute">{{ fr() ? 'Heures ajustées' : 'Adjusted hours' }}</div><div class="font-bold">{{ attendance.adjustedJustifiedHours + attendance.adjustedUnjustifiedHours }}</div></div>
                            </div>
                          </div>
                        }

                        <div class="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div class="border border-slate-200 rounded-lg p-4">
                            <div class="flex items-center justify-between mb-2">
                              <div class="text-[10px] uppercase tracking-wider text-mute font-semibold">
                                {{ fr() ? 'Appréciation générale' : 'General appreciation' }}
                              </div>
                              @if (canWrite && b.state !== 'PREVIEW' && !b.validated && !b.financiallyBlocked) {
                                <bbc-icon name="edit" [s]="12" />
                              }
                            </div>
                            @if (canWrite && b.state !== 'PREVIEW' && !b.validated && !b.financiallyBlocked) {
                              <textarea [ngModel]="appreciationDraft()" (ngModelChange)="appreciationDraft.set($event)"
                                rows="3" [placeholder]="fr() ? 'Saisissez l’appréciation générale…' : 'Enter overall appreciation…'"
                                class="w-full p-2 text-sm rounded border border-slate-200 italic resize-none focus:outline-none focus:border-brand-400 print:hidden"></textarea>
                            } @else {
                              <div class="text-sm text-ink italic">
                                "{{ b.generalAppreciation || (appr(b.average) + (fr() ? '. Continue ainsi.' : '. Keep it up.')) }}"
                              </div>
                            }
                          </div>
                          <div class="border border-slate-200 rounded-lg p-4">
                            <div class="text-[10px] uppercase tracking-wider text-mute font-semibold">
                              {{ fr() ? 'Visa du Principal' : 'Principal signature' }}
                            </div>
                            <div class="text-sm text-ink italic mt-1.5">
                              {{ b.validated ? (fr() ? 'Bulletin validé. Encouragements.' : 'Validated. Encouragements.') : (fr() ? 'En attente de validation.' : 'Awaiting validation.') }}
                            </div>
                            <div class="text-[11px] text-mute mt-3">— {{ fr() ? 'Cachet' : 'Seal' }}</div>
                          </div>
                        </div>

                        <div class="mt-5 flex items-center gap-2 pt-4 border-t border-slate-100 print:hidden">
                          @if (b.financiallyBlocked) {
                            <div class="flex-1 flex items-center gap-2 text-xs text-rose-700">
                              <bbc-icon name="alertTri" [s]="16" />
                              {{ fr() ? 'Bulletin bloqué — frais impayés' : 'Blocked — outstanding fees' }}
                            </div>
                          } @else {
                            <div class="flex-1"></div>
                          }
                          @if (canWrite && b.state === 'PREVIEW' && !b.financiallyBlocked) {
                            <button (click)="createBulletinDraft(b)" [disabled]="bulletinBusy()"
                              class="inline-flex items-center gap-2 h-10 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-lg text-sm font-semibold disabled:opacity-50">
                              <bbc-icon name="doc" [s]="16" /> {{ bulletinBusy() ? '…' : (fr() ? 'Créer le brouillon' : 'Create draft') }}
                            </button>
                          }
                          @if (canWrite && !!b.id && b.state !== 'PREVIEW' && !b.validated && !b.financiallyBlocked) {
                            <button (click)="validate(b)"
                              class="inline-flex items-center gap-2 h-10 px-4 bg-gold-500 hover:bg-gold-600 text-white rounded-lg text-sm font-semibold">
                              <bbc-icon name="check" [s]="16" [sw]="2.5" /> {{ fr() ? 'Valider le bulletin' : 'Validate report card' }}
                            </button>
                          }
                          @if (canWrite && b.state === 'VALIDATED' && !b.financiallyBlocked) {
                            <button (click)="requestPublication(b)"
                              class="inline-flex items-center gap-2 h-10 px-4 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-sm font-semibold">
                              <bbc-icon name="eye" [s]="16" /> {{ fr() ? 'Publier aux parents' : 'Publish to parents' }}
                            </button>
                          }
                          @if (canWrite && (b.state === 'VALIDATED' || b.state === 'PUBLISHED') && !b.financiallyBlocked) {
                            <button (click)="generateOfficialDocument(b)" [disabled]="officialDocumentBusy()"
                              class="inline-flex items-center gap-2 h-10 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-lg text-sm font-semibold disabled:opacity-50">
                              <bbc-icon name="download" [s]="16" /> {{ officialDocumentBusy() ? '…' : (fr() ? 'Générer le PDF officiel' : 'Generate official PDF') }}
                            </button>
                          }
                          <button (click)="print()" [disabled]="b.financiallyBlocked"
                            class="inline-flex items-center gap-2 h-10 px-4 bg-white border border-slate-200 text-ink hover:bg-slate-50 rounded-lg text-sm font-semibold disabled:opacity-40">
                            <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
                          </button>
                        </div>
                      </div>
                    </div>
                  </bbc-card>
                }
              } @else {
                <bbc-card>
                  <bbc-empty icon="doc"
                    [label]="fr() ? 'Cliquez sur un élève de la classe pour afficher son bulletin.' : 'Click a student in the class to open their report card.'" />
                </bbc-card>
              }
            </div>
          </div>
        }

        <!-- Bulk print stack (hidden on screen, visible when printing) -->
        @if (bulkBulletins().length) {
          <div class="hidden print:block">
            @for (b of bulkBulletins(); track b.studentId) {
              <div class="break-after-page p-6">
                <div class="text-xs font-semibold text-mute uppercase mb-1">{{ b.className }} · {{ fr() ? 'Séquence' : 'Seq' }} {{ b.sequence }}</div>
                <div class="text-xl font-bold text-ink mb-1">{{ b.studentName }}</div>
                <div class="text-sm text-mute mb-4">{{ fr() ? 'Rang' : 'Rank' }} {{ b.rank }}/{{ b.classSize }} · {{ fr() ? 'Moyenne' : 'Average' }} {{ b.average }}/20</div>
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b-2 border-brand-600 text-[11px] uppercase text-brand-700 font-bold">
                      <th class="text-left py-2">{{ fr() ? 'Matière' : 'Subject' }}</th>
                      <th class="text-center py-2">Coef</th>
                      <th class="text-center py-2">{{ fr() ? 'Note' : 'Mark' }}</th>
                      <th class="text-center py-2">{{ fr() ? 'Pondéré' : 'Weighted' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (l of b.lines; track l.subjectCode) {
                      <tr class="border-b border-slate-100">
                        <td class="py-1.5 font-semibold">{{ l.subjectLabel }}</td>
                        <td class="py-1.5 text-center">{{ l.coef }}</td>
                        <td class="py-1.5 text-center font-bold">{{ l.mark }}</td>
                        <td class="py-1.5 text-center font-mono">{{ l.weighted }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
                <div class="mt-4 text-sm italic">
                  {{ b.generalAppreciation || appr(b.average) }}
                </div>
              </div>
            }
          </div>
        }
      }

      <!-- ============ PV ============ -->
      @if (mode() === 'batch') {
        @if (!selectedClass()) {
          <bbc-card><bbc-empty icon="users" [label]="fr() ? 'Choisissez une classe pour générer les bulletins.' : 'Pick a class to generate report cards.'" /></bbc-card>
        } @else {
          <bbc-card className="mb-4">
            <div class="flex items-start justify-between gap-4 flex-wrap">
              <div>
                <div class="text-lg font-bold text-ink">{{ fr() ? 'Génération de la classe' : 'Class report-card generation' }}</div>
                <div class="text-sm text-mute mt-1">{{ selectedClass() }} · {{ selectedReportingPeriodCode() || selectedReportingPeriodId() }}</div>
                <div class="text-xs text-mute mt-2 max-w-2xl">{{ fr() ? 'La génération se fait élève par élève. Les bulletins validés ou publiés sont exportés; les lignes bloquées expliquent exactement ce qui manque et peuvent être relancées après correction.' : 'Generation runs one student at a time. Validated or published report cards are exported; blocked rows explain what is missing and can be retried after correction.' }}</div>
              </div>
              <button (click)="startBatchGeneration()" [disabled]="batchBusy() || !selectedClassId() || !selectedReportingPeriodId()"
                class="inline-flex items-center gap-2 h-10 px-4 rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold disabled:opacity-50">
                <bbc-icon name="download" [s]="16" /> {{ batchBusy() ? (fr() ? 'Lancement…' : 'Starting…') : (fr() ? 'Lancer la génération' : 'Start generation') }}
              </button>
            </div>
            @if (batchJob(); as job) {
              <div class="mt-5 rounded-xl border border-brand-100 bg-brand-50/50 p-4">
                <div class="flex items-center justify-between gap-3 flex-wrap">
                  <div class="flex items-center gap-2"><span class="font-bold text-ink">{{ fr() ? 'Lot' : 'Job' }} {{ job.id.slice(0, 8) }}</span><span class="px-2 py-1 rounded-full text-[11px] font-bold" [class]="batchStatusClass(job.status)">{{ batchStatusLabel(job.status) }}</span></div>
                  <div class="text-xs text-mute">{{ job.processedItems }}/{{ job.totalItems }} {{ fr() ? 'traités' : 'processed' }}</div>
                </div>
                <div class="h-2 rounded-full bg-white border border-brand-100 overflow-hidden mt-3"><div class="h-full bg-brand-600 transition-all" [style.width.%]="job.progressPercent"></div></div>
                <div class="grid grid-cols-2 md:grid-cols-5 gap-2 mt-4 text-xs">
                  <div><span class="text-mute">{{ fr() ? 'Réussis' : 'Published' }}</span><div class="font-bold text-emerald-700">{{ job.publishedItems }}</div></div>
                  <div><span class="text-mute">{{ fr() ? 'Bloqués' : 'Blocked' }}</span><div class="font-bold text-amber-700">{{ job.blockedItems }}</div></div>
                  <div><span class="text-mute">{{ fr() ? 'Erreurs' : 'Errors' }}</span><div class="font-bold text-rose-700">{{ job.errorItems }}</div></div>
                  <div><span class="text-mute">{{ fr() ? 'Avancement' : 'Progress' }}</span><div class="font-bold text-ink">{{ job.progressPercent }}%</div></div>
                  <div><span class="text-mute">{{ fr() ? 'Archive' : 'Archive' }}</span><div class="font-bold text-ink">{{ job.archiveAvailable ? (job.archiveSizeBytes || 0) + ' B' : '—' }}</div></div>
                </div>
                @if (job.lastError) { <div class="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800">{{ job.lastError }}</div> }
                <div class="flex flex-wrap gap-2 mt-4">
                  @if (job.archiveAvailable) { <button (click)="downloadBatchArchive(job)" class="h-9 px-3 rounded-lg bg-emerald-600 text-white text-sm font-semibold">{{ fr() ? 'Télécharger l’archive' : 'Download archive' }}</button> }
                  @if ((job.blockedItems + job.errorItems) > 0 && job.status !== 'RUNNING' && job.status !== 'QUEUED') { <button (click)="retryBatch(job)" [disabled]="batchBusy()" class="h-9 px-3 rounded-lg border border-amber-300 bg-amber-50 text-amber-800 text-sm font-semibold">{{ fr() ? 'Relancer les lignes en échec' : 'Retry failed rows' }}</button> }
                </div>
              </div>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead><tr class="border-b-2 border-slate-200 text-left text-[11px] uppercase text-mute"><th class="py-2">{{ fr() ? 'Élève' : 'Student' }}</th><th class="py-2">{{ fr() ? 'État' : 'Status' }}</th><th class="py-2">{{ fr() ? 'Tentatives' : 'Attempts' }}</th><th class="py-2">{{ fr() ? 'Message' : 'Message' }}</th></tr></thead>
                  <tbody>@for (item of batchItems(); track item.id) { <tr class="border-b border-slate-100"><td class="py-2 font-semibold text-ink">{{ item.studentName }}</td><td class="py-2"><span class="px-2 py-1 rounded-full text-[11px] font-semibold" [class]="batchItemStatusClass(item.status)">{{ batchItemStatusLabel(item.status) }}</span></td><td class="py-2">{{ item.attempts }}</td><td class="py-2 text-xs text-mute">{{ item.error || item.fileName || '—' }}</td></tr> } @empty { <tr><td colspan="4" class="py-5 text-center text-mute">{{ fr() ? 'Aucune ligne dans ce lot.' : 'No rows in this job.' }}</td></tr> }</tbody>
                </table>
              </div>
            }
          </bbc-card>
          @if (batchJobs().length) {
            <bbc-card title="{{ fr() ? 'Historique des générations' : 'Generation history' }}" subtitle="{{ fr() ? 'Les archives restent disponibles après la fin du traitement.' : 'Archives remain available after processing completes.' }}">
              <div class="space-y-2">@for (job of batchJobs(); track job.id) { <button type="button" (click)="selectBatchJob(job)" class="w-full flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2 text-left hover:border-brand-300 hover:bg-brand-50/30"><span class="font-semibold text-ink">{{ job.id.slice(0, 8) }} · {{ job.requestedAt | date:'short' }}</span><span class="text-xs text-mute">{{ batchStatusLabel(job.status) }} · {{ job.publishedItems }}/{{ job.totalItems }}</span></button> }</div>
            </bbc-card>
          }
        }
      }

      <!-- ============ PV ============ -->
      @if (mode() === 'pv') {
        @if (pv(); as p) {
          <bbc-card [title]="(fr() ? 'Procès-verbal' : 'Master sheet') + ' — ' + p.className"
            [subtitle]="p.reportingPeriodCode ? p.rows.length + (fr() ? ' élèves · ' : ' students · ') + p.reportingPeriodCode + ' · ' + (p.completeStudents ?? 0) + (fr() ? ' complets' : ' complete') : p.rows.length + (fr() ? ' élèves · Séquence ' : ' students · Sequence ') + p.sequence">
            <div action class="text-right">
              <div class="text-[10px] uppercase tracking-wide text-mute font-semibold">{{ fr() ? 'Moy. classe' : 'Class avg' }}</div>
              <div class="text-lg font-bold text-brand-600">{{ p.classAverage }}/20</div>
            </div>
            @if (p.rows.length === 0) {
              <bbc-empty icon="users" [label]="fr() ? 'Aucun élève' : 'No students'" />
            } @else {
              <div class="overflow-x-auto -mx-5">
                <table class="min-w-full text-sm">
                  <thead class="border-y-2 border-brand-600 bg-brand-50">
                    <tr class="text-brand-700 font-bold uppercase text-[11px]">
                      <th class="text-left py-2 pl-5 w-12">#</th>
                      <th class="text-left py-2">{{ fr() ? 'Élève' : 'Student' }}</th>
                      <th class="text-right py-2 pr-5">{{ fr() ? 'Moyenne' : 'Average' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (r of p.rows; track r.studentId) {
                      <tr class="border-b border-slate-100 hover:bg-slate-50/50">
                        <td class="py-2 pl-5 text-mute font-mono">{{ r.rank ?? '-' }}</td>
                        <td class="py-2">
                          <div class="flex items-center gap-2.5">
                            <bbc-avatar [name]="r.studentName" [hue]="200" [size]="28" />
                            <span class="font-semibold text-ink">{{ r.studentName }}</span>
                          </div>
                        </td>
                        <td class="py-2 pr-5 text-right">
                          <span class="inline-block px-2 py-0.5 rounded font-bold"
                            [class]="r.average < 10 ? 'bg-rose-100 text-rose-700' : r.average < 14 ? 'bg-slate-100 text-ink' : 'bg-emerald-100 text-emerald-700'">
                            {{ r.complete === false ? (fr() ? 'Incomplet' : 'Incomplete') : r.average + '/20' }}
                          </span>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </bbc-card>
        } @else {
          <bbc-card>
            <bbc-empty icon="doc"
              [label]="fr() ? 'Choisissez une classe et une séquence, puis chargez le PV.' : 'Pick a class and sequence, then load the master sheet.'" />
          </bbc-card>
        }
      }
      @if (publicationDialog()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/55" (click)="cancelPublication()">
          <section class="bg-white rounded-xl2 shadow-pop w-full max-w-md p-6" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
            <h3 class="text-lg font-bold text-ink">{{ fr() ? 'Publier ce bulletin ?' : 'Publish this report card?' }}</h3>
            <p class="text-sm text-mute mt-2">{{ fr() ? 'Le bulletin validé deviendra visible dans le portail parent et ne sera plus modifiable comme brouillon.' : 'The validated report card will become visible in the parent portal and will no longer be editable as a draft.' }}</p>
            <label class="block mt-4"><span class="text-xs font-semibold">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><textarea [(ngModel)]="publicationReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm" [placeholder]="fr() ? 'Ex. Bulletin du 1er trimestre contrôlé par le conseil de classe.' : 'E.g. First-term report card checked by the class council.'"></textarea></label>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelPublication()" class="h-9 px-3 rounded-lg border border-slate-200 text-sm font-semibold">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmPublication()" [disabled]="!publicationReason.trim() || publicationBusy()" class="h-9 px-3 rounded-lg bg-emerald-600 text-white text-sm font-semibold disabled:opacity-50">{{ publicationBusy() ? '…' : (fr() ? 'Confirmer la publication' : 'Confirm publication') }}</button></div>
          </section>
        </div>
      }
      @if (gradeReviewDialog(); as action) {
        <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/55" (click)="cancelGradeReview()">
          <section class="bg-white rounded-xl2 shadow-pop w-full max-w-md p-6" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
            <h3 class="text-lg font-bold text-ink">{{ action === 'ACCEPT' ? (fr() ? 'Accepter cette feuille ?' : 'Accept this grade sheet?') : (fr() ? 'Retourner cette feuille ?' : 'Return this grade sheet?') }}</h3>
            <p class="text-sm text-mute mt-2">{{ action === 'ACCEPT' ? (fr() ? 'Les notes seront acceptées et entreront dans les calculs du bulletin.' : 'The grades will be accepted and included in report-card calculations.') : (fr() ? 'La feuille repassera en brouillon pour permettre à l’enseignant de la corriger.' : 'The sheet will return to draft so the teacher can correct it.') }}</p>
            @if (action === 'RETURN') {
              <label class="block mt-4"><span class="text-xs font-semibold">{{ fr() ? 'Motif du retour (obligatoire)' : 'Return reason (required)' }}</span><textarea [(ngModel)]="gradeReviewReason" rows="3" maxlength="500" class="w-full mt-1.5 px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:border-brand-500" [placeholder]="fr() ? 'Ex. Vérifier la note de l’évaluation de mathématiques.' : 'E.g. Check the mathematics assessment mark.'"></textarea></label>
            }
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelGradeReview()" class="h-9 px-3 rounded-lg border border-slate-200 text-sm font-semibold">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmGradeReview()" [disabled]="gradeBusy() || (action === 'RETURN' && !gradeReviewReason.trim())" [class]="action === 'ACCEPT' ? 'h-9 px-3 rounded-lg bg-emerald-600 text-white text-sm font-semibold disabled:opacity-50' : 'h-9 px-3 rounded-lg bg-rose-600 text-white text-sm font-semibold disabled:opacity-50'">{{ action === 'ACCEPT' ? (fr() ? 'Accepter' : 'Accept') : (fr() ? 'Retourner' : 'Return') }}</button></div>
          </section>
        </div>
      }

      @if (inputReviewTarget(); as review) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="presentation">
          <section class="w-full max-w-md rounded-xl2 bg-white shadow-pop p-6" role="dialog" aria-modal="true">
            <h3 class="text-lg font-bold text-ink">{{ review.action === 'APPROVE' ? (fr() ? 'Approuver les éléments ?' : 'Approve these inputs?') : (fr() ? 'Retourner les éléments ?' : 'Return these inputs?') }}</h3>
            <p class="text-sm text-mute mt-2">{{ review.row.studentName }} · {{ fr() ? 'Cette décision sera journalisée et recalculera le bulletin si nécessaire.' : 'This decision is audited and recalculates the bulletin when necessary.' }}</p>
            <label class="field-label mt-4"><span>{{ review.action === 'APPROVE' ? (fr() ? 'Motif (facultatif)' : 'Reason (optional)') : (fr() ? 'Motif obligatoire' : 'Required reason') }}</span><textarea [(ngModel)]="inputReviewReason" rows="3" maxlength="500" class="field resize-y" [class.border-rose-400]="review.action !== 'APPROVE' && !inputReviewReason.trim()"></textarea></label>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelInputReview()" class="h-9 px-3 rounded-lg border border-slate-200 text-sm font-semibold">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmInputReview()" [disabled]="inputBusy() === review.row.studentId || (review.action !== 'APPROVE' && !inputReviewReason.trim())" class="h-9 px-3 rounded-lg bg-emerald-600 text-white text-sm font-semibold disabled:opacity-50">{{ review.action === 'APPROVE' ? (fr() ? 'Approuver' : 'Approve') : (fr() ? 'Retourner' : 'Return') }}</button></div>
          </section>
        </div>
      }
    </div>
  `,
})
export class AcademicComponent {
  protected i18n = inject(I18nService);
  private studentApi = inject(StudentApi);
  private setupApi = inject(SetupApi);
  private api = inject(AcademicApi);
  private auth = inject(AuthService);
  private scope = inject(ScopeService);
  private foundationApi = inject(FoundationApi);

  protected canWrite = this.auth.can('academic', 'write');

  protected isApc = computed(() => {
    const lvl = this.scope.scope()?.level;
    return lvl === 'maternelle' || lvl === 'primary';
  });

  protected classes = signal<ClassView[]>([]);
  protected selectedClass = signal('');
  protected selectedClassId = signal('');
  protected classStudents = signal<Student[]>([]);
  protected studentQuery = signal('');
  protected selectedStudentId = signal('');
  protected sequence = signal(1);
  protected reportingPeriods = signal<AcademicReportingPeriodView[]>([]);
  protected selectedReportingPeriodId = signal('');
  protected selectedReportingPeriodCode = computed(() => this.reportingPeriods().find((p) => p.id === this.selectedReportingPeriodId())?.code ?? '');
  protected mode = signal<Mode>('bulletin');
  protected bulletin = signal<BulletinView | null>(null);
  protected pv = signal<PvView | null>(null);
  protected gradeEntry = signal<GradeEntryView | null>(null);
  protected gradeEntryError = signal<string | null>(null);
  protected reportInputs = signal<ReportCardInputsView | null>(null);
  protected inputDrafts = signal<Record<string, ReportCardInputUpsert>>({});
  protected inputBusy = signal<string | null>(null);
  protected selectedGradeSubjectCode = signal('');
  protected gradeBusy = signal(false);
  protected appreciationDraft = signal('');
  protected bulkBulletins = signal<BulletinView[]>([]);
  protected bulkBusy = signal(false);
  protected studentPhotoUrl = signal<string | null>(null);
  protected publicationDialog = signal(false);
  protected publicationReason = '';
  protected publicationTarget = signal<BulletinView | null>(null);
  protected publicationBusy = signal(false);
  protected bulletinBusy = signal(false);
  protected officialDocumentBusy = signal(false);
  protected officialDocument = signal<GeneratedDocumentView | null>(null);
  protected gradeReviewDialog = signal<'ACCEPT' | 'RETURN' | null>(null);
  protected gradeReviewReason = '';
  protected inputReviewTarget = signal<{ row: ReportCardInputRow; action: 'APPROVE' | 'RETURN' | 'REJECT' } | null>(null);
  protected inputReviewReason = '';
  protected batchJob = signal<BulletinBatchJobView | null>(null);
  protected batchJobs = signal<BulletinBatchJobView[]>([]);
  protected batchItems = signal<BulletinBatchItemView[]>([]);
  protected batchBusy = signal(false);
  private batchPollId: string | null = null;
  protected notice = signal<{ ok: boolean; text: string } | null>(null);
  private photoApi = inject(PhotoApi);

  protected fr = () => this.i18n.lang() === 'fr';
  protected String = String;
  protected display(value: string | null | undefined): string { return cleanDisplay(value); }

  protected tabs = computed(() => [
    { id: 'batch', label: this.fr() ? 'Génération en lot' : 'Batch generation' },
    { id: 'bulletin', label: this.fr() ? 'Bulletin' : 'Report card' },
    { id: 'grade-entry', label: this.fr() ? 'Saisie des notes' : 'Grade entry' },
    { id: 'inputs', label: this.fr() ? 'Assiduité & conseil' : 'Attendance & council' },
    { id: 'pv', label: this.fr() ? 'Procès-verbal' : 'Master sheet' },
  ]);

  protected canReview = computed(() => ['admin', 'principal', 'dean_of_studies', 'censor'].includes(this.auth.user()?.role ?? ''));

  protected filteredClassStudents = computed(() => {
    const q = this.studentQuery().trim().toLowerCase();
    const list = this.classStudents();
    if (!q) return list;
    return list.filter((s) =>
      s.name.toLowerCase().includes(q) || s.matricule.toLowerCase().includes(q));
  });

  constructor() {
    this.setupApi.listClasses().subscribe({
      next: (c) => this.classes.set(c),
      error: () => this.classes.set([]),
    });
    this.foundationApi.currentSession().subscribe({
      next: (s) => this.foundationApi.reportingPeriods(s.id).subscribe({
        next: (periods) => { const readablePeriods = periods.map((p) => ({ ...p, label: cleanDisplay(p.label) })); this.reportingPeriods.set(readablePeriods); const first = readablePeriods.find((p) => p.code === 'S1') ?? readablePeriods[0]; if (first) { this.selectedReportingPeriodId.set(first.id); this.sequence.set(this.periodSequence(first)); } },
        error: () => this.reportingPeriods.set([]),
      }),
      error: () => this.reportingPeriods.set([]),
    });
  }

  protected appr(mark: number): string {
    return appreciation(mark, this.fr());
  }

  protected setMode(m: Mode): void {
    this.mode.set(m);
    this.notice.set(null);
    this.gradeEntryError.set(null);
    this.bulkBulletins.set([]);
    if (m === 'grade-entry' && this.selectedClassId() && this.selectedReportingPeriodId()) this.loadGradeEntry();
    if (m === 'inputs' && this.selectedClassId() && this.selectedReportingPeriodId()) this.loadReportInputs();
    if (m === 'batch' && this.selectedClassId() && this.selectedReportingPeriodId()) this.loadBatchJobs();
  }

  protected onClassChange(name: string): void {
    this.notice.set(null);
    this.gradeEntryError.set(null);
    this.selectedClass.set(name);
    this.selectedClassId.set(this.classes().find((c) => c.name === name)?.id ?? '');
    this.selectedStudentId.set('');
    this.bulletin.set(null);
    this.pv.set(null);
    this.bulkBulletins.set([]);
    this.gradeEntry.set(null);
    this.reportInputs.set(null);
    this.inputDrafts.set({});
    this.selectedGradeSubjectCode.set('');
    this.studentQuery.set('');
    this.classStudents.set([]);
    this.batchJob.set(null); this.batchJobs.set([]); this.batchItems.set([]);
    if (!name) return;
    this.studentApi.list(name).subscribe({
      next: (r) => this.classStudents.set(r),
      error: () => this.classStudents.set([]),
    });
    if (this.mode() === 'pv') this.loadPv();
    if (this.mode() === 'grade-entry') this.loadGradeEntry();
    if (this.mode() === 'inputs') this.loadReportInputs();
    if (this.mode() === 'batch') this.loadBatchJobs();
  }

  protected onStudentChange(id: string): void {
    this.selectedStudentId.set(id);
    this.studentPhotoUrl.set(null);
    this.photoApi.load('students', id).subscribe((url) => this.studentPhotoUrl.set(url));
    this.bulkBulletins.set([]);
    this.loadBulletin();
  }

  protected onReportingPeriodChange(id: string): void {
    this.notice.set(null);
    this.gradeEntryError.set(null);
    this.selectedReportingPeriodId.set(id);
    const period = this.reportingPeriods().find((p) => p.id === id);
    if (period) this.sequence.set(this.periodSequence(period));
    if (this.mode() === 'bulletin' && this.selectedStudentId()) this.loadBulletin();
    if (this.mode() === 'grade-entry' && this.selectedClassId()) this.loadGradeEntry();
    if (this.mode() === 'inputs' && this.selectedClassId()) this.loadReportInputs();
    if (this.mode() === 'batch' && this.selectedClassId()) this.loadBatchJobs();
  }

  private loadBatchJobs(): void {
    const classId = this.selectedClassId(); const periodId = this.selectedReportingPeriodId();
    if (!classId || !periodId) { this.batchJobs.set([]); this.batchJob.set(null); this.batchItems.set([]); return; }
    this.api.bulletinBatchJobs(classId, periodId).subscribe({
      next: (jobs) => {
        this.batchJobs.set(jobs);
        const current = this.batchJob();
        const selected = current ? jobs.find((job) => job.id === current.id) : jobs[0];
        if (selected) this.selectBatchJob(selected);
        else { this.batchJob.set(null); this.batchItems.set([]); }
      },
      error: (e) => this.fail(e),
    });
  }

  protected selectBatchJob(job: BulletinBatchJobView): void {
    this.batchJob.set(job);
    this.api.bulletinBatchJobItems(job.id).subscribe({ next: (items) => this.batchItems.set(items), error: (e) => this.fail(e) });
    if (job.status === 'QUEUED' || job.status === 'RUNNING') this.pollBatch(job.id);
  }

  protected startBatchGeneration(): void {
    const classId = this.selectedClassId(); const periodId = this.selectedReportingPeriodId();
    if (!classId || !periodId || this.batchBusy()) return;
    this.batchBusy.set(true);
    this.api.createBulletinBatchJob({ classId, reportingPeriodId: periodId, locale: this.fr() ? 'fr' : 'en' }).subscribe({
      next: (job) => { this.batchBusy.set(false); this.batchJob.set(job); this.loadBatchJobs(); this.pollBatch(job.id); this.notice.set({ ok: true, text: this.fr() ? 'Lot lancé. Vous pouvez suivre chaque élève ci-dessous.' : 'Job started. Track each student below.' }); },
      error: (e) => { this.batchBusy.set(false); this.fail(e); },
    });
  }

  protected retryBatch(job: BulletinBatchJobView): void {
    if (this.batchBusy()) return;
    this.batchBusy.set(true);
    this.api.retryBulletinBatchJob(job.id).subscribe({
      next: (updated) => { this.batchBusy.set(false); this.batchJob.set(updated); this.loadBatchJobs(); this.pollBatch(updated.id); this.notice.set({ ok: true, text: this.fr() ? 'Les lignes bloquées ou en erreur sont relancées.' : 'Blocked and failed rows were queued again.' }); },
      error: (e) => { this.batchBusy.set(false); this.fail(e); },
    });
  }

  protected downloadBatchArchive(job: BulletinBatchJobView): void {
    this.api.downloadBulletinBatchJob(job.id).subscribe({
      next: (blob) => { const url = URL.createObjectURL(blob); const anchor = window.document.createElement('a'); anchor.href = url; anchor.download = `bulletin-batch-${job.id}.zip`; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); },
      error: (e) => this.fail(e),
    });
  }

  private pollBatch(id: string): void {
    if (this.batchPollId === id) return;
    this.batchPollId = id;
    window.setTimeout(() => this.api.bulletinBatchJob(id).subscribe({
      next: (job) => {
        if (this.batchPollId === id) this.batchPollId = null;
        if (this.batchJob()?.id !== id) return;
        this.batchJob.set(job);
        this.api.bulletinBatchJobItems(id).subscribe({ next: (items) => this.batchItems.set(items), error: (e) => this.fail(e) });
        if (job.status === 'QUEUED' || job.status === 'RUNNING') this.pollBatch(id);
        else this.loadBatchJobs();
      },
      error: (e) => { if (this.batchPollId === id) this.batchPollId = null; this.fail(e); },
    }), 1200);
  }

  protected batchStatusLabel(status: BulletinBatchJobView['status']): string {
    if (!this.fr()) return ({ QUEUED: 'Queued', RUNNING: 'Running', COMPLETED: 'Completed', COMPLETED_ERRORS: 'Completed with issues', FAILED: 'Failed' } as any)[status] ?? status;
    return ({ QUEUED: 'En attente', RUNNING: 'En cours', COMPLETED: 'Terminé', COMPLETED_ERRORS: 'Terminé avec alertes', FAILED: 'Échec' } as any)[status] ?? status;
  }
  protected batchStatusClass(status: BulletinBatchJobView['status']): string {
    return status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-700' : status === 'FAILED' ? 'bg-rose-100 text-rose-700' : status === 'COMPLETED_ERRORS' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-700';
  }
  protected batchItemStatusLabel(status: BulletinBatchItemView['status']): string {
    if (!this.fr()) return ({ QUEUED: 'Queued', RUNNING: 'Running', PUBLISHED: 'Published', BLOCKED: 'Blocked', ERROR: 'Error' } as any)[status] ?? status;
    return ({ QUEUED: 'En attente', RUNNING: 'En cours', PUBLISHED: 'Exporté', BLOCKED: 'Bloqué', ERROR: 'Erreur' } as any)[status] ?? status;
  }
  protected batchItemStatusClass(status: BulletinBatchItemView['status']): string {
    return status === 'PUBLISHED' ? 'bg-emerald-100 text-emerald-700' : status === 'ERROR' ? 'bg-rose-100 text-rose-700' : status === 'BLOCKED' ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-700';
  }

  protected onGradeSubjectChange(code: string): void {
    this.notice.set(null);
    this.gradeEntryError.set(null);
    this.selectedGradeSubjectCode.set(code);
    this.loadGradeEntry(code);
  }

  protected gradePacketStatusLabel(status: GradeEntryView['packetStatus']): string {
    if (!this.fr()) return ({ DRAFT: 'Draft — not sent', SUBMITTED: 'Sent for review', RETURNED: 'Returned for correction', ACCEPTED: 'Accepted and locked', LOCKED: 'Locked' } as any)[status] ?? status;
    return ({ DRAFT: 'Brouillon — non envoyé', SUBMITTED: 'Envoyée pour vérification', RETURNED: 'Retournée pour correction', ACCEPTED: 'Acceptée et verrouillée', LOCKED: 'Verrouillée' } as any)[status] ?? status;
  }

  protected gradeBlockerLabel(blocker: string): string {
    const readable = cleanDisplay(blocker);
    return readable.replace(' · ', ' — ');
  }

  protected gradeAssessmentLabel(entry: GradeEntryView, assessment: GradeEntryView['assessments'][number]): string {
    if (entry.assessments.length === 1) return this.display(entry.subjectLabel);
    return this.display(assessment.label) || assessment.code;
  }

  protected loadGradeEntry(subjectCode?: string): void {
    const classId = this.selectedClassId(); const periodId = this.selectedReportingPeriodId();
    if (!classId || !periodId) { this.gradeEntry.set(null); return; }
    this.gradeEntryError.set(null);
    this.api.gradeEntry(periodId, classId, subjectCode || this.selectedGradeSubjectCode() || undefined).subscribe({
      next: (entry) => { this.gradeEntry.set(this.refreshGradeProgress(entry)); this.gradeEntryError.set(null); this.selectedGradeSubjectCode.set(entry.subjectCode); },
      error: (e) => { this.gradeEntry.set(null); this.gradeEntryError.set(this.explainError(e, 'grade-entry')); },
    });
  }

  private loadReportInputs(): void {
    const classId = this.selectedClassId(); const periodId = this.selectedReportingPeriodId();
    if (!classId || !periodId) { this.reportInputs.set(null); this.inputDrafts.set({}); return; }
    this.api.reportCardInputs(periodId, classId).subscribe({
      next: (view) => {
        const readableView = this.cleanReportInputs(view);
        this.reportInputs.set(readableView);
        const drafts: Record<string, ReportCardInputUpsert> = {};
        for (const row of readableView.rows) {
          const adjustment = row.attendanceAdjustment;
          const conduct = row.conduct;
          drafts[row.studentId] = {
            reportingPeriodId: readableView.reportingPeriodId, classId: readableView.classId, studentId: row.studentId,
            justifiedAbsenceHours: adjustment?.justifiedAbsenceHours ?? 0,
            unjustifiedAbsenceHours: adjustment?.unjustifiedAbsenceHours ?? 0,
            lateMinutes: adjustment?.lateMinutes ?? 0,
            reason: adjustment?.reason ?? '', evidenceReference: adjustment?.evidenceReference ?? null,
            workWarning: conduct?.workWarning ?? false, workBlame: conduct?.workBlame ?? false,
            conductWarning: conduct?.conductWarning ?? false, conductBlame: conduct?.conductBlame ?? false,
            honorRoll: conduct?.honorRoll ?? false, encouragement: conduct?.encouragement ?? false,
            congratulations: conduct?.congratulations ?? false, exclusionDays: conduct?.exclusionDays ?? 0,
            decisionCode: conduct?.decisionCode ?? null, councilObservation: conduct?.councilObservation ?? null,
            attendanceVersion: adjustment?.version, conductVersion: conduct?.version,
          };
        }
        this.inputDrafts.set(drafts);
      },
      error: (e) => { this.reportInputs.set(null); this.fail(e); },
    });
  }

  protected inputDraft(studentId: string): ReportCardInputUpsert {
    return this.inputDrafts()[studentId] ?? {
      reportingPeriodId: this.selectedReportingPeriodId(), classId: this.selectedClassId(), studentId,
      justifiedAbsenceHours: 0, unjustifiedAbsenceHours: 0, lateMinutes: 0, reason: '', evidenceReference: null,
      workWarning: false, workBlame: false, conductWarning: false, conductBlame: false,
      honorRoll: false, encouragement: false, congratulations: false, exclusionDays: 0,
      decisionCode: null, councilObservation: null,
    };
  }

  protected updateInput(studentId: string, patch: Partial<ReportCardInputUpsert>): void {
    this.inputDrafts.update((all) => ({ ...all, [studentId]: { ...this.inputDraft(studentId), ...patch } }));
  }

  protected saveReportInput(row: ReportCardInputRow): void {
    if (!this.inputDraft(row.studentId).reason.trim()) {
      this.notice.set({ ok: false, text: this.fr() ? 'Renseignez le motif obligatoire avant d’enregistrer.' : 'Enter the required reason before saving.' });
      return;
    }
    this.inputBusy.set(row.studentId);
    this.api.saveReportCardInputs(this.inputDraft(row.studentId)).subscribe({
      next: (view) => { const readableView = this.cleanReportInputs(view); this.inputBusy.set(null); this.reportInputs.set(readableView); this.rebuildInputDrafts(readableView); this.notice.set({ ok: true, text: this.fr() ? 'Assiduité et fiche du conseil enregistrées.' : 'Attendance and council inputs saved.' }); },
      error: (e) => { this.inputBusy.set(null); this.fail(e); },
    });
  }

  protected submitReportInput(row: ReportCardInputRow): void {
    if (!this.inputDraft(row.studentId).reason.trim()) {
      this.notice.set({ ok: false, text: this.fr() ? 'Renseignez le motif obligatoire avant de soumettre.' : 'Enter the required reason before submitting.' });
      return;
    }
    this.inputBusy.set(row.studentId);
    this.api.submitReportCardInput(row.studentId, this.selectedReportingPeriodId(), this.selectedClassId()).subscribe({
      next: (view) => { const readableView = this.cleanReportInputs(view); this.inputBusy.set(null); this.reportInputs.set(readableView); this.rebuildInputDrafts(readableView); this.notice.set({ ok: true, text: this.fr() ? 'Fiche soumise à la revue.' : 'Input sheet submitted for review.' }); },
      error: (e) => { this.inputBusy.set(null); this.fail(e); },
    });
  }

  protected requestInputReview(row: ReportCardInputRow, action: 'APPROVE' | 'RETURN' | 'REJECT'): void {
    this.inputReviewTarget.set({ row, action });
    this.inputReviewReason = action === 'APPROVE' ? (this.fr() ? 'Contrôle effectué par la direction.' : 'Reviewed by management.') : '';
  }
  protected cancelInputReview(): void { this.inputReviewTarget.set(null); this.inputReviewReason = ''; }
  protected confirmInputReview(): void {
    const target = this.inputReviewTarget(); if (!target) return;
    if (target.action !== 'APPROVE' && !this.inputReviewReason.trim()) return;
    this.inputBusy.set(target.row.studentId);
    this.api.reviewReportCardInput(target.row.studentId, {
      reportingPeriodId: this.selectedReportingPeriodId(), classId: this.selectedClassId(), action: target.action,
      reason: this.inputReviewReason.trim(), attendanceVersion: target.row.attendanceAdjustment?.version,
      conductVersion: target.row.conduct?.version,
    }).subscribe({
      next: (view) => { const readableView = this.cleanReportInputs(view); this.inputBusy.set(null); this.cancelInputReview(); this.reportInputs.set(readableView); this.rebuildInputDrafts(readableView); this.notice.set({ ok: true, text: this.fr() ? 'Revue enregistrée et bulletin invalidé si nécessaire.' : 'Review recorded; affected snapshots were invalidated if necessary.' }); },
      error: (e) => { this.inputBusy.set(null); this.fail(e); },
    });
  }

  private cleanReportInputs(view: ReportCardInputsView): ReportCardInputsView {
    return { ...view, className: cleanDisplay(view.className), reportingPeriodLabel: cleanDisplay(view.reportingPeriodLabel) };
  }

  private rebuildInputDrafts(view: ReportCardInputsView): void {
    const drafts: Record<string, ReportCardInputUpsert> = {};
    for (const row of view.rows) {
      const a = row.attendanceAdjustment; const c = row.conduct;
      drafts[row.studentId] = { reportingPeriodId: view.reportingPeriodId, classId: view.classId, studentId: row.studentId,
        justifiedAbsenceHours: a?.justifiedAbsenceHours ?? 0, unjustifiedAbsenceHours: a?.unjustifiedAbsenceHours ?? 0, lateMinutes: a?.lateMinutes ?? 0,
        reason: a?.reason ?? '', evidenceReference: a?.evidenceReference ?? null,
        workWarning: c?.workWarning ?? false, workBlame: c?.workBlame ?? false, conductWarning: c?.conductWarning ?? false, conductBlame: c?.conductBlame ?? false,
        honorRoll: c?.honorRoll ?? false, encouragement: c?.encouragement ?? false, congratulations: c?.congratulations ?? false, exclusionDays: c?.exclusionDays ?? 0,
        decisionCode: c?.decisionCode ?? null, councilObservation: c?.councilObservation ?? null, attendanceVersion: a?.version, conductVersion: c?.version };
    }
    this.inputDrafts.set(drafts);
  }

  private updateGradeEntry(mutator: (entry: GradeEntryView) => GradeEntryView): void {
    const current = this.gradeEntry(); if (current) this.gradeEntry.set(this.refreshGradeProgress(mutator(current)));
  }

  private refreshGradeProgress(entry: GradeEntryView): GradeEntryView {
    if (!entry.assessments.length) {
      return { ...entry, completedStudents: 0, blockers: [this.fr() ? 'Aucune évaluation n’est configurée pour cette matière.' : 'No assessment is configured for this subject.'] };
    }
    const subject = entry.availableSubjects.find((item) => item.code.toUpperCase() === entry.subjectCode.toUpperCase());
    const blockers: string[] = [];
    const complete = entry.students.filter((row) => {
      let ok = true;
      entry.assessments.forEach((assessment, index) => {
        const cell = row.values[index];
        if (assessment.mandatory && (!cell || !['SCORED', 'ABSENT', 'EXEMPT'].includes(cell.valueStatus))) {
          ok = false;
          blockers.push(`${row.studentName} · ${this.gradeAssessmentLabel(entry, assessment)}`);
        }
      });
      if (subject?.remarkRequired && !row.comment?.trim()) {
        ok = false;
        blockers.push(`${row.studentName} · ${this.fr() ? 'Appréciation obligatoire' : 'Required comment'}`);
      }
      return ok;
    }).length;
    return { ...entry, completedStudents: complete, blockers: blockers.length > 12 ? [...blockers.slice(0, 12), this.fr() ? `… et ${blockers.length - 12} autre(s)` : `… and ${blockers.length - 12} more`] : blockers };
  }

  protected updateGradeMark(studentId: string, index: number, raw: unknown): void {
    const value = raw === '' || raw == null ? null : Number(raw);
    this.updateGradeEntry((entry) => ({ ...entry, students: entry.students.map((row) => row.studentId !== studentId ? row : ({ ...row, values: row.values.map((cell, i) => i === index ? { ...cell, mark: Number.isFinite(value) ? value : null, valueStatus: Number.isFinite(value) ? 'SCORED' : 'MISSING' } : cell) })) }));
  }

  protected updateGradeStatus(studentId: string, index: number, status: string): void {
    this.updateGradeEntry((entry) => ({ ...entry, students: entry.students.map((row) => row.studentId !== studentId ? row : ({ ...row, values: row.values.map((cell, i) => i === index ? { ...cell, mark: status === 'SCORED' ? cell.mark : null, valueStatus: status as any } : cell) })) }));
  }

  protected updateGradeComment(studentId: string, comment: string): void {
    this.updateGradeEntry((entry) => ({ ...entry, students: entry.students.map((row) => row.studentId === studentId ? { ...row, comment } : row) }));
  }

  protected saveGradeEntry(): void {
    const entry = this.gradeEntry(); if (!entry) return;
    this.gradeBusy.set(true);
    this.api.saveGradeEntry({ reportingPeriodId: entry.reportingPeriodId, classId: entry.classId, subjectCode: entry.subjectCode, packetVersion: entry.packetVersion, students: entry.students.map((row) => ({ studentId: row.studentId, comment: row.comment, values: row.values.map((cell) => ({ assessmentId: cell.assessmentId, mark: cell.mark, valueStatus: cell.valueStatus, version: cell.version })) })) }).subscribe({
      next: (updated) => { this.gradeEntry.set(updated); this.selectedGradeSubjectCode.set(updated.subjectCode); this.gradeBusy.set(false); this.notice.set({ ok: true, text: this.fr() ? 'Brouillon de notes enregistré.' : 'Grade draft saved.' }); },
      error: (e) => { this.gradeBusy.set(false); this.fail(e); },
    });
  }

  protected submitGradeEntry(): void {
    const entry = this.gradeEntry(); if (!entry) return;
    this.gradeBusy.set(true);
    this.api.gradeEntryWorkflow(entry.reportingPeriodId, entry.classId, entry.subjectCode, 'SUBMIT', undefined, entry.packetVersion).subscribe({
      next: (updated) => { this.gradeEntry.set(updated); this.gradeBusy.set(false); this.notice.set({ ok: true, text: this.fr() ? 'Saisie soumise à la direction.' : 'Grades submitted to management.' }); },
      error: (e) => { this.gradeBusy.set(false); this.fail(e); },
    });
  }

  protected reviewGradeEntry(action: 'ACCEPT' | 'RETURN'): void {
    if (!this.gradeEntry()) return;
    this.gradeReviewReason = action === 'ACCEPT' ? 'Contrôle de la saisie effectué' : '';
    this.gradeReviewDialog.set(action);
  }

  protected cancelGradeReview(): void { this.gradeReviewDialog.set(null); this.gradeReviewReason = ''; }

  protected confirmGradeReview(): void {
    const entry = this.gradeEntry(); const action = this.gradeReviewDialog(); if (!entry || !action) return;
    if (action === 'RETURN' && !this.gradeReviewReason.trim()) return;
    this.gradeBusy.set(true);
    this.api.gradeEntryWorkflow(entry.reportingPeriodId, entry.classId, entry.subjectCode, action, this.gradeReviewReason.trim(), entry.packetVersion).subscribe({
      next: (updated) => { this.gradeEntry.set(updated); this.gradeBusy.set(false); this.cancelGradeReview(); this.notice.set({ ok: true, text: action === 'ACCEPT' ? (this.fr() ? 'Saisie acceptée.' : 'Grades accepted.') : (this.fr() ? 'Saisie retournée.' : 'Grades returned for correction.') }); },
      error: (e) => { this.gradeBusy.set(false); this.fail(e); },
    });
  }

  private loadBulletin(): void {
    const id = this.selectedStudentId();
    if (!id) {
      this.bulletin.set(null);
      return;
    }
    const periodId = this.selectedReportingPeriodId();
    if (periodId) {
      this.api.previewBulletinSnapshot(id, periodId).subscribe((snapshot) => {
        this.bulletin.set({ id: snapshot.id, studentId: snapshot.studentId, studentName: snapshot.studentName, className: snapshot.className ?? '', sequence: this.periodSequence(this.reportingPeriods().find((p) => p.id === periodId)!), lines: snapshot.lines.map((l) => ({ subjectCode: l.subjectCode, subjectLabel: l.subjectLabel, coef: l.coefficient, mark: l.mark, weighted: l.weighted, teacherRemark: l.teacherRemark ?? undefined, periodMarks: l.periodMarks ?? undefined, teacherName: l.teacherName, subjectGroupCode: l.subjectGroupCode, subjectGroupLabel: l.subjectGroupLabel })), average: snapshot.average, rank: snapshot.rank ?? 0, classSize: snapshot.classSize, classAverage: snapshot.classStats?.average ?? snapshot.average, validated: snapshot.state === 'VALIDATED' || snapshot.state === 'PUBLISHED', generalAppreciation: snapshot.generalAppreciation, financiallyBlocked: false, reportingPeriodId: snapshot.reportingPeriodId, reportingPeriodCode: snapshot.reportingPeriodCode, state: snapshot.state, complete: snapshot.complete, blockers: snapshot.blockers, snapshotHash: snapshot.snapshotHash, version: snapshot.version, attendance: snapshot.attendance, conduct: snapshot.conduct, groupStats: snapshot.groupStats ?? undefined });
        this.appreciationDraft.set(snapshot.generalAppreciation ?? '');
      }, (e) => this.fail(e));
    } else {
      this.api.bulletin(id, this.sequence()).subscribe((b) => { this.bulletin.set(b); this.appreciationDraft.set(b.generalAppreciation ?? ''); });
    }
  }

  private periodSequence(period: AcademicReportingPeriodView): number { const match = period?.code?.match(/^S(\d+)$/); return match ? Number(match[1]) : this.sequence(); }

  protected loadPv(): void {
    const cls = this.selectedClass().trim();
    if (!cls) {
      this.pv.set(null);
      return;
    }
    if (this.selectedClassId() && this.selectedReportingPeriodId()) {
      this.api.sessionPv(this.selectedClassId(), this.selectedReportingPeriodId()).subscribe({
        next: (p) => this.pv.set({ ...p, sequence: this.sequence() }),
        error: (e) => this.fail(e),
      });
      return;
    }
    this.api.pv(cls, this.sequence()).subscribe((p) => this.pv.set(p));
  }

  protected validate(b: BulletinView): void {
    if (!b.id) return;
    this.api.validateSnapshot(b.id).subscribe({ next: (snapshot) => { this.applySnapshot(snapshot); this.notice.set({ ok: true, text: this.fr() ? 'Bulletin validé. Il peut maintenant être publié aux parents.' : 'Report card validated. It can now be published to parents.' }); }, error: (e) => this.fail(e) });
  }

  protected createBulletinDraft(b: BulletinView): void {
    if (b.id || !b.reportingPeriodId || b.state !== 'PREVIEW' || this.bulletinBusy()) return;
    this.bulletinBusy.set(true);
    this.api.bulletinSnapshot(b.studentId, b.reportingPeriodId).subscribe({
      next: (snapshot) => {
        this.bulletinBusy.set(false);
        this.applySnapshot(snapshot);
        this.notice.set({ ok: true, text: this.fr() ? 'Brouillon de bulletin créé. Il peut maintenant être validé.' : 'Report-card draft created. It can now be validated.' });
      },
      error: (e) => { this.bulletinBusy.set(false); this.fail(e); },
    });
  }

  protected requestPublication(b: BulletinView): void { this.publicationTarget.set(b); this.publicationReason = ''; this.publicationDialog.set(true); }
  protected cancelPublication(): void { this.publicationDialog.set(false); this.publicationTarget.set(null); this.publicationReason = ''; }
  protected confirmPublication(): void {
    const target = this.publicationTarget();
    if (!target?.id || !this.publicationReason.trim()) return;
    this.publicationBusy.set(true);
    this.api.publishSnapshot(target.id, this.publicationReason.trim(), target.version).subscribe({
      next: (snapshot) => { this.publicationBusy.set(false); this.cancelPublication(); this.applySnapshot(snapshot); this.notice.set({ ok: true, text: this.fr() ? 'Bulletin publié. Il est maintenant visible dans le portail parent.' : 'Report card published. It is now visible in the parent portal.' }); },
      error: (e) => { this.publicationBusy.set(false); this.fail(e); },
    });
  }

  private applySnapshot(snapshot: import('./academic.api').BulletinSnapshotView): void {
    this.bulletin.set({ id: snapshot.id, studentId: snapshot.studentId, studentName: snapshot.studentName, className: snapshot.className ?? '', sequence: this.periodSequence(this.reportingPeriods().find((p) => p.id === snapshot.reportingPeriodId)!), lines: snapshot.lines.map((l) => ({ subjectCode: l.subjectCode, subjectLabel: l.subjectLabel, coef: l.coefficient, mark: l.mark, weighted: l.weighted, teacherRemark: l.teacherRemark ?? undefined, periodMarks: l.periodMarks ?? undefined, teacherName: l.teacherName, subjectGroupCode: l.subjectGroupCode, subjectGroupLabel: l.subjectGroupLabel })), average: snapshot.average, rank: snapshot.rank ?? 0, classSize: snapshot.classSize, classAverage: snapshot.classStats?.average ?? snapshot.average, validated: snapshot.state === 'VALIDATED' || snapshot.state === 'PUBLISHED', generalAppreciation: snapshot.generalAppreciation, financiallyBlocked: false, reportingPeriodId: snapshot.reportingPeriodId, reportingPeriodCode: snapshot.reportingPeriodCode, state: snapshot.state, complete: snapshot.complete, blockers: snapshot.blockers, snapshotHash: snapshot.snapshotHash, version: snapshot.version, attendance: snapshot.attendance, conduct: snapshot.conduct, groupStats: snapshot.groupStats ?? undefined });
    this.appreciationDraft.set(snapshot.generalAppreciation ?? '');
  }

  private explainError(e: any, context?: 'grade-entry'): string {
    const raw = cleanDisplay(typeof e?.error?.message === 'string' ? e.error.message : '');
    const code = String(e?.error?.code ?? '').toUpperCase();
    const text = raw.toLowerCase();
    const className = this.selectedClass() || (this.fr() ? 'la classe sélectionnée' : 'the selected class');
    const period = this.selectedReportingPeriodCode() || (this.fr() ? 'la période sélectionnée' : 'the selected period');
    if (code === 'ENROLLMENT_MISSING' || text.includes('inscription active') || text.includes('active enrollment')) {
      return this.fr()
        ? `Cet élève n’est pas inscrit dans ${className} pour ${period}. Vérifiez son inscription dans Élèves → Inscription avant de consulter ou publier son bulletin.`
        : `This student is not enrolled in ${className} for ${period}. Check the student’s enrollment in Students → Enrollment before viewing or publishing the report card.`;
    }
    if (text.includes('matière') && (text.includes('affect') || text.includes('assigned'))) {
      return this.fr()
        ? `Cette matière n’est pas affectée à ${className}. Un administrateur doit la configurer dans Paramètres → Scolarité → Matières par classe.`
        : `This subject is not assigned to ${className}. An administrator must configure it in Settings → Academics → Class subjects.`;
    }
    if (text.includes('enseignant') || text.includes('teacher assignment') || code.includes('ASSIGNMENT_')) {
      return this.fr()
        ? `Aucun enseignant responsable n’est configuré pour cette matière et cette classe. Configurez l’affectation avant la saisie.`
        : `No responsible teacher is configured for this subject and class. Configure the assignment before entering marks.`;
    }
    if (context === 'grade-entry' && raw) return raw;
    return raw || (this.fr() ? 'Impossible de terminer cette opération.' : 'This operation could not be completed.');
  }

  private fail(e: any): void { this.notice.set({ ok: false, text: this.explainError(e) }); }

  protected print(): void {
    this.bulkBulletins.set([]);
    setTimeout(() => window.print(), 50);
  }

  protected generateOfficialDocument(b: BulletinView): void {
    if (!b.id || !b.version || this.officialDocumentBusy()) return;
    const locale = this.fr() ? 'fr' : 'en';
    const key = `report-card:${b.id}:${b.version}:${locale}`;
    this.officialDocumentBusy.set(true);
    this.api.generateOfficialReportCard(b.id, locale, key).subscribe({
      next: (generated) => {
        this.officialDocument.set(generated);
        this.officialDocumentBusy.set(false);
        this.notice.set({ ok: true, text: this.fr()
          ? `Document officiel ${generated.documentNumber} généré (${generated.sizeBytes} octets). Téléchargement en cours.`
          : `Official document ${generated.documentNumber} generated (${generated.sizeBytes} bytes). Download starting.` });
        this.foundationApi.documentContent(generated.id).subscribe({
          next: (blob) => {
            const url = URL.createObjectURL(blob);
            const anchor = window.document.createElement('a');
            anchor.href = url;
            anchor.download = `${generated.documentNumber}.pdf`;
            anchor.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
          },
          error: (e) => this.fail(e),
        });
      },
      error: (e) => { this.officialDocumentBusy.set(false); this.fail(e); },
    });
  }

  /** Download the server-generated class archive and its manifest. */
  protected printAllBulletins(): void {
    const classId = this.selectedClassId(); const periodId = this.selectedReportingPeriodId();
    if (!classId || !periodId || !this.classStudents().length) return;
    this.bulkBusy.set(true);
    this.api.bulletinBatch(classId, periodId, this.fr() ? 'fr' : 'en').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob); const anchor = window.document.createElement('a');
        anchor.href = url; anchor.download = `bulletins-${this.selectedClass() || classId}-${periodId}.zip`; anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.bulkBusy.set(false);
        this.notice.set({ ok: true, text: this.fr() ? 'Archive des bulletins téléchargée avec son manifeste.' : 'Report-card archive downloaded with its manifest.' });
      },
      error: (e) => { this.bulkBusy.set(false); this.fail(e); },
    });
  }
}
