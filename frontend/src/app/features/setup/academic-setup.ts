import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { ScopeService } from '../../core/scope.service';
import {
  SetupApi, SectionView, SectionUpsert, ClassView, ClassUpsert, SubjectView, SubjectUpsert, TeacherOption,
  ClassCoefView, CoefImportRow, CoefImportResult, CurriculumView, CurriculumSubjectView, SubjectGroupView, AssignmentImpactView,
} from '../../core/setup.api';
import { FoundationApi, AcademicSessionView, AcademicReportingPeriodView, DocumentDesignView } from '../../core/foundation.api';
import { AcademicApi, SecondaryCompetencyModelView } from '../academic/academic.api';
import { AssessmentDefaultsComponent } from './assessment-defaults/assessment-defaults';
import { defaultSubjects } from './subject-defaults';
import { forkJoin } from 'rxjs';
import { IconComponent, CardComponent, TabsComponent, EmptyComponent } from '../../core/ui';
import { downloadCsv } from '../../core/csv';

/**
 * Academic Setup — admins build the relational backbone here (sections → classes,
 * and subjects) BEFORE enrolling students. This is what makes the student form's
 * class a locked dropdown instead of free text (review issues #1 and #3).
 */
@Component({
  selector: 'bbc-academic-setup',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, CardComponent, TabsComponent, EmptyComponent, AssessmentDefaultsComponent],
  template: `
    <bbc-tabs [tabs]="displayedSubTabs()" [value]="sub()" (change)="switchTo($any($event))" />

    @switch (sub()) {
      <!-- ===================== SECTIONS ===================== -->
      @case ('sections') {
        <bbc-card [title]="fr() ? 'Sections' : 'Sections'"
          [subtitle]="fr() ? 'Regroupent les classes par sous-système et niveau' : 'Group classes by subsystem and level'">
          <div action>
            @if (canWrite) {
              <button (click)="newSection()" class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle section' : 'New section' }}
              </button>
            }
          </div>

          @if (scopeBanner(); as banner) {
            <div class="mb-3 text-xs rounded-lg px-3 py-2 bg-sky-50 text-sky-800 border border-sky-100">{{ banner }}</div>
          }
          @if (err(); as e) {
            <div class="mb-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-700 border border-rose-100">{{ e }}</div>
          }

          @if (canWrite && secForm()) {
            <form (ngSubmit)="saveSection()" class="grid grid-cols-1 md:grid-cols-4 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
              <label class="block md:col-span-2">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Libellé' : 'Label' }} *</span>
                <input [(ngModel)]="secDraft.label" name="label" required maxlength="120"
                  [placeholder]="fr() ? 'Primaire francophone' : 'Francophone primary'"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sous-système' : 'Subsystem' }}</span>
                <select [(ngModel)]="secDraft.subsystem" name="subsystem" [disabled]="!!activeScope()"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white disabled:bg-slate-100">
                  <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
                  <option value="EN">{{ fr() ? 'Anglophone' : 'English' }}</option>
                </select>
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Niveau' : 'Level' }}</span>
                <select [(ngModel)]="secDraft.level" name="level" [disabled]="!!activeScope()"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white disabled:bg-slate-100">
                  <option value="maternelle">{{ fr() ? 'Maternelle' : 'Kindergarten' }}</option>
                  <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                  <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
                </select>
              </label>
              <div class="md:col-span-4 flex items-center justify-end gap-2">
                <button type="button" (click)="secForm.set(false)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                <button type="submit" [disabled]="!secDraft.label" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
              </div>
            </form>
          }

          @if (sections().length) {
            <table class="min-w-full text-sm">
              <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
                <th class="py-2 pr-3 font-semibold">{{ fr() ? 'Libellé' : 'Label' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Système' : 'System' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Niveau' : 'Level' }}</th>
                <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Classes' : 'Classes' }}</th>
                <th></th>
              </tr></thead>
              <tbody>
                @for (s of sections(); track s.id) {
                  <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                    <td class="py-2 pr-3 font-semibold text-ink">{{ s.label }}</td>
                    <td class="py-2 px-3">{{ s.subsystem === 'FR' ? 'FR' : 'EN' }}</td>
                    <td class="py-2 px-3">{{ levelLabel(s.level) }}</td>
                    <td class="py-2 px-3 text-center">{{ s.classCount }}</td>
                    <td class="py-2 pl-3 text-right whitespace-nowrap">
                      @if (canWrite) {
                        <button (click)="editSection(s)" class="text-mute hover:text-brand-600 px-1.5" title="{{ fr() ? 'Modifier' : 'Edit' }}"><bbc-icon name="edit" [s]="15" /></button>
                        <button (click)="deleteSection(s)" class="text-mute hover:text-rose-600 px-1.5" title="{{ fr() ? 'Supprimer' : 'Delete' }}"><bbc-icon name="trash" [s]="15" /></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <bbc-empty icon="building" [label]="fr() ? 'Aucune section — créez-en une pour commencer.' : 'No sections — create one to begin.'" />
          }
        </bbc-card>
      }

      <!-- ===================== CLASSES ===================== -->
      @case ('classes') {
        <bbc-card [title]="fr() ? 'Classes' : 'Classes'"
          [subtitle]="fr() ? 'Les élèves sont rattachés à une classe réelle (plus de texte libre)' : 'Students attach to a real class (no more free text)'">
          <div action class="flex items-center gap-2">
            @if (canWrite) {
              <button type="button" (click)="downloadClassTemplate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Modèle CSV' : 'CSV template' }}
              </button>
              <button (click)="newClass()" [disabled]="!sections().length"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle classe' : 'New class' }}
              </button>
            }
          </div>

          @if (scopeBanner(); as banner) {
            <div class="mb-3 text-xs rounded-lg px-3 py-2 bg-sky-50 text-sky-800 border border-sky-100">{{ banner }}</div>
          }
          @if (!sections().length) {
            <div class="mb-3 text-sm rounded-lg px-3 py-2.5 bg-amber-50 text-amber-900 border border-amber-100">
              {{ fr()
                ? 'Veuillez créer une section dans ce parcours avant d’ajouter une classe (Paramètres → Scolarité → Sections), ou changez de parcours.'
                : 'Please create a section in this parcours before adding a class (Settings → Academics → Sections), or switch parcours.' }}
            </div>
          }
          @if (err(); as e) {
            <div class="mb-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-700 border border-rose-100">{{ e }}</div>
          }

          @if (canWrite && clsForm()) {
            <form (ngSubmit)="saveClass()" class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom de la classe' : 'Class name' }} *</span>
                <input [(ngModel)]="clsDraft.name" name="cname" required maxlength="80" placeholder="6ème A"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block md:col-span-2">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Section' : 'Section' }} *</span>
                <select [(ngModel)]="clsDraft.sectionId" name="csection" required class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                  @for (s of sections(); track s.id) { <option [value]="s.id">{{ s.label }}</option> }
                </select>
              </label>
              <div class="md:col-span-3 flex items-center justify-end gap-2">
                <button type="button" (click)="clsForm.set(false)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                <button type="submit" [disabled]="!clsDraft.name || !clsDraft.sectionId" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
              </div>
            </form>
          }

          @if (classes().length) {
            <table class="min-w-full text-sm">
              <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
                <th class="py-2 pr-3 font-semibold">{{ fr() ? 'Classe' : 'Class' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Section' : 'Section' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Niveau' : 'Level' }}</th>
                <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Élèves' : 'Students' }}</th>
                <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Enseignants' : 'Teachers' }}</th>
                <th></th>
              </tr></thead>
              <tbody>
                @for (c of classes(); track c.id) {
                  <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                    <td class="py-2 pr-3 font-semibold text-ink">{{ c.name }}</td>
                    <td class="py-2 px-3 text-mute">{{ c.sectionLabel }}</td>
                    <td class="py-2 px-3">{{ levelLabel(c.level) }} · {{ c.subsystem }}</td>
                    <td class="py-2 px-3 text-center">{{ c.studentCount }}</td>
                    <td class="py-2 px-3 text-center">
                      <button (click)="openTeachers(c)" class="inline-flex items-center gap-1 text-mute hover:text-brand-600" title="{{ fr() ? 'Gérer les enseignants' : 'Manage teachers' }}">
                        <bbc-icon name="users" [s]="15" /> {{ c.teacherCount }}
                      </button>
                    </td>
                    <td class="py-2 pl-3 text-right whitespace-nowrap">
                      @if (canWrite) {
                        <button (click)="editClass(c)" class="text-mute hover:text-brand-600 px-1.5"><bbc-icon name="edit" [s]="15" /></button>
                        <button (click)="deleteClass(c)" class="text-mute hover:text-rose-600 px-1.5"><bbc-icon name="trash" [s]="15" /></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }

          <!-- Teacher assignment panel (0..N teachers per class) -->
          @if (teacherClass(); as tc) {
            <div class="mt-4 p-4 rounded-lg border border-brand-100 bg-brand-50/40">
              <div class="flex items-center justify-between mb-3">
                <div class="font-semibold text-ink">
                  {{ fr() ? 'Enseignants de' : 'Teachers of' }} « {{ tc.name }} »
                </div>
                <button (click)="teacherClass.set(null)" class="text-mute hover:text-ink"><bbc-icon name="x" [s]="16" /></button>
              </div>
              @if (allTeachers().length) {
                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 max-h-64 overflow-auto">
                  @for (t of allTeachers(); track t.id) {
                    <label class="flex items-center gap-2 px-3 h-10 rounded-lg bg-white border border-slate-200 cursor-pointer hover:border-brand-300">
                      <input type="checkbox" [checked]="picked().has(t.id)" (change)="toggleTeacher(t.id)" [disabled]="!canWrite"
                        class="rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                      <span class="text-sm text-ink truncate">{{ t.name }}</span>
                      <span class="ml-auto text-[11px] font-mono text-mute">{{ t.code }}</span>
                    </label>
                  }
                </div>
                @if (canWrite) {
                  <div class="flex items-center justify-end gap-2 mt-3">
                    <span class="text-xs text-mute mr-auto">{{ picked().size }} {{ fr() ? 'sélectionné(s)' : 'selected' }}</span>
                    <button (click)="teacherClass.set(null)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                    <button (click)="saveTeachers()" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
                  </div>
                }
              } @else {
                <bbc-empty icon="users" [label]="fr() ? 'Aucun enseignant — ajoutez du personnel d’abord.' : 'No staff — add employees first.'" />
              }
            </div>
          } @else {
            <bbc-empty icon="book" [label]="fr() ? 'Aucune classe.' : 'No classes.'" />
          }
        </bbc-card>
      }

      <!-- ===================== CLASS SUBJECTS ===================== -->
      @case ('class-subjects') {
        <bbc-card [title]="fr() ? 'Matières par classe' : 'Class subjects'"
          [subtitle]="fr() ? 'Affectez les matières enseignées et définissez le coefficient utilisé sur les bulletins de chaque classe.' : 'Assign the subjects taught and define the coefficient used on each class bulletin.'">
          <div action class="flex items-center gap-2">
            <label class="inline-flex items-center gap-2 text-xs font-semibold text-ink">
              {{ fr() ? 'Session' : 'Session' }}
              <select [ngModel]="curriculumSessionId()" (ngModelChange)="selectCurriculumSession($event)" class="h-9 px-2 rounded-lg border border-slate-300 bg-white text-sm">
                <option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>
                @for (session of academicSessions(); track session.id) { <option [value]="session.id">{{ session.label }}{{ session.current ? ' · current' : '' }}</option> }
              </select>
            </label>
            @if (canWrite) {
              <label class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 cursor-pointer"
                [title]="fr() ? 'Optionnel : import en masse depuis un fichier officiel' : 'Optional: bulk import from an official file'">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Import en masse (optionnel)' : 'Bulk import (optional)' }}
                <input type="file" accept=".csv,.xls,.xlsx,.xlsm,text/csv,text/plain" (change)="onCoefFile($event)" class="hidden" />
              </label>
            }
          </div>

          <div class="mb-5 rounded-xl border border-brand-100 bg-brand-50/40 p-4">
            <div class="mb-3">
              <div class="font-semibold text-ink">{{ fr() ? 'Ajouter une matière à une classe' : 'Add a subject to a class' }}</div>
              <div class="text-xs text-mute mt-1">{{ fr() ? 'Le coefficient de la matière est proposé automatiquement. Vous pouvez le modifier pour cette classe uniquement.' : 'The subject default is proposed automatically. You can override it for this class only.' }}</div>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Classe' : 'Class' }} *</span>
                <select [ngModel]="assignmentClassId()" (ngModelChange)="selectAssignmentClass($event)"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-300 bg-white focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100">
                  <option value="">{{ fr() ? 'Choisir une classe' : 'Choose a class' }}</option>
                  @for (c of classes(); track c.id) { <option [value]="c.id">{{ c.name }} · {{ c.subsystem }}</option> }
                </select>
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Matière à ajouter' : 'Subject to add' }} *</span>
                <select [ngModel]="assignmentSubjectId()" (ngModelChange)="selectAssignmentSubject($event)"
                  [disabled]="!assignmentClassId() || !availableAssignmentSubjects().length"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-300 bg-white focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100">
                  <option value="">{{ availableAssignmentSubjects().length ? (fr() ? 'Choisir une matière' : 'Choose a subject') : (fr() ? 'Toutes les matières sont affectées' : 'All subjects are assigned') }}</option>
                  @for (s of availableAssignmentSubjects(); track s.id) { <option [value]="s.id">{{ subjectLabel(s) }} · {{ s.code }}</option> }
                </select>
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Coefficient pour cette classe' : 'Coefficient for this class' }} *</span>
                <input type="number" min="1" step="1" [ngModel]="assignmentCoef()" (ngModelChange)="assignmentCoef.set(+$event)"
                  [disabled]="!assignmentSubjectId()"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-300 bg-white focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100" />
                @if (selectedAssignmentSubject(); as subject) {
                  <span class="text-[11px] text-mute mt-1 block">{{ fr() ? 'Défaut matière :' : 'Subject default:' }} {{ subject.coef }}</span>
                }
              </label>
              <button type="button" (click)="addAssignment()" [disabled]="!canWrite || !assignmentClassId() || !assignmentSubjectId() || assignmentCoef() < 1"
                class="h-10 px-4 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold inline-flex items-center justify-center gap-2">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Ajouter à la classe' : 'Add to class' }}
              </button>
            </div>
          </div>

          @if (assignmentClassId() && curriculum()) {
            <div class="mb-5 rounded-xl border border-slate-200 bg-white p-4">
              <div class="flex items-start justify-between gap-3 mb-3">
                <div>
                  <div class="font-semibold text-ink">{{ fr() ? 'Groupes de matières' : 'Subject groups' }}</div>
                  <div class="text-xs text-mute mt-1">{{ fr() ? 'Les groupes structurent les sous-totaux et la présentation du bulletin de cette session.' : 'Groups control subtotals and report-card presentation for this session.' }}</div>
                </div>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-5 gap-2 items-end">
                <label><span class="meta">Code *</span><input [(ngModel)]="groupCode" class="field" placeholder="SCIENCES" /></label>
                <label><span class="meta">{{ fr() ? 'Libellé FR' : 'French label' }} *</span><input [(ngModel)]="groupFr" class="field" placeholder="Sciences" /></label>
                <label><span class="meta">{{ fr() ? 'Libellé EN' : 'English label' }}</span><input [(ngModel)]="groupEn" class="field" placeholder="Science" /></label>
                <label><span class="meta">{{ fr() ? 'Ordre' : 'Order' }} *</span><input type="number" min="1" [(ngModel)]="groupOrder" class="field" /></label>
                <button type="button" (click)="createGroup()" [disabled]="!canWrite" class="h-10 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">{{ fr() ? 'Créer le groupe' : 'Create group' }}</button>
              </div>
              @if (curriculum()?.groups?.length) {
                <div class="flex flex-wrap gap-2 mt-3">
                  @for (group of (curriculum()?.groups ?? []); track group.id) {
                    <span class="inline-flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                      {{ group.displayOrder }} · {{ groupLabel(group) }}
                      @if (canWrite) {
                        @if (pendingGroupRemoval() === group.id) { <button type="button" (click)="removeGroup(group)" class="text-rose-700 font-bold">{{ fr() ? 'Confirmer' : 'Confirm' }}</button><button type="button" (click)="pendingGroupRemoval.set(null)" class="text-slate-500">×</button> }
                        @else { <button type="button" (click)="askRemoveGroup(group)" class="text-slate-400 hover:text-rose-600">×</button> }
                      }
                    </span>
                  }
                </div>
              }
              @if (groupNotice(); as notice) { <div class="mt-3 text-sm rounded-lg px-3 py-2" [class]="notice.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ notice.text }}</div> }
            </div>
          }

          @if (assignmentClassId()) {
            <div class="flex items-center justify-between mb-3">
              <div>
                <div class="font-semibold text-ink">{{ fr() ? 'Matières affectées' : 'Assigned subjects' }}</div>
                <div class="text-xs text-mute">{{ selectedAssignmentClass()?.name }} · {{ assignmentRows().length }} {{ fr() ? 'matière(s)' : 'subject(s)' }}</div>
              </div>
            </div>
            @if (assignmentRows().length) {
              <div class="rounded-lg border border-slate-200 overflow-auto">
                <table class="w-full text-sm">
                  <thead class="bg-slate-50 text-[11px] uppercase text-mute text-left">
                    <tr>
                      <th class="px-3 py-2 font-semibold">{{ fr() ? 'Matière' : 'Subject' }}</th>
                      <th class="px-3 py-2 font-semibold">{{ fr() ? 'Code' : 'Code' }}</th>
                      <th class="px-3 py-2 font-semibold text-center">{{ fr() ? 'Défaut' : 'Default' }}</th>
                      <th class="px-3 py-2 font-semibold text-center">{{ fr() ? 'Coef de la classe' : 'Class coefficient' }}</th>
                      <th class="px-3 py-2 font-semibold text-right">{{ fr() ? 'Action' : 'Action' }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of assignmentRows(); track row.subjectId) {
                      <tr class="border-t border-slate-100">
                        <td class="px-3 py-2 font-semibold text-ink">{{ assignmentSubjectLabel(row) }}</td>
                        <td class="px-3 py-2 font-mono text-xs">{{ row.subjectCode }}</td>
                        <td class="px-3 py-2 text-center text-mute">{{ row.defaultCoef }}</td>
                        <td class="px-3 py-2 text-center">
                          <input type="number" min="1" step="1" [ngModel]="draftCoefficient(row)" (ngModelChange)="setDraftCoefficient(row, $event)"
                            [name]="'class-coef-' + row.subjectId" [disabled]="!canWrite"
                            class="w-24 h-9 px-2 text-center rounded-lg border border-slate-300 bg-white focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100" />
                        </td>
                        <td class="px-3 py-2 text-right whitespace-nowrap">
                          @if (canWrite) {
                            <button type="button" (click)="saveAssignment(row)" class="h-8 px-2.5 rounded-lg text-xs font-semibold text-brand-700 bg-brand-50 hover:bg-brand-100">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
                            @if (pendingCoefficientRemoval() === assignmentKey(row)) {
                              <button type="button" (click)="removeAssignment(row)" class="h-8 px-2.5 rounded-lg text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 ml-1">{{ fr() ? 'Confirmer' : 'Confirm' }}</button>
                              <button type="button" (click)="pendingCoefficientRemoval.set(null)" class="h-8 px-2 rounded-lg text-xs text-mute hover:text-ink ml-1">{{ fr() ? 'Annuler' : 'Cancel' }}</button>
                            } @else {
                              <button type="button" (click)="askRemoveAssignment(row)" class="h-8 px-2.5 rounded-lg text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 ml-1">{{ fr() ? 'Retirer' : 'Remove' }}</button>
                            }
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
              <div class="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-mute">
                {{ fr() ? 'Aucune matière affectée. Utilisez le formulaire ci-dessus pour commencer.' : 'No subject assigned yet. Use the form above to get started.' }}
              </div>
            }
          } @else {
            <div class="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-mute">
              {{ fr() ? 'Choisissez une classe pour voir et gérer ses matières.' : 'Choose a class to view and manage its subjects.' }}
            </div>
          }

          @if (assignmentRows().length) {
            @if (selectedAssignmentClass()?.level !== 'secondary') {
              <div class="mb-3 rounded-xl border border-brand-200 bg-brand-50/60 p-4">
                <div class="font-semibold text-ink">{{ fr() ? 'Titulaire de classe' : 'Homeroom teacher' }} <b class="text-rose-600">*</b></div>
                <div class="text-xs text-mute mt-1 mb-3">{{ fr() ? 'Une classe primaire utilise un seul titulaire daté. Toutes les matières héritent automatiquement de cette affectation.' : 'A primary class uses one dated homeroom assignment. Every subject inherits this authority automatically.' }}</div>
                <div class="flex flex-col md:flex-row md:items-end gap-3">
                    <label class="block flex-1"><span class="meta">{{ fr() ? 'Enseignant titulaire' : 'Homeroom teacher' }} <b class="text-rose-600">*</b></span><select class="field" [class.invalid]="!curriculum()?.homeroomTeacher" [ngModel]="curriculum()?.homeroomTeacher?.employeeId ?? ''" (ngModelChange)="prepareHomeroom($event)" [disabled]="!canWrite"><option value="">{{ fr() ? 'Choisir un titulaire' : 'Choose a homeroom teacher' }}</option>@for (teacher of allTeachers(); track teacher.id) { <option [value]="teacher.id">{{ teacher.name }}</option> }</select></label>
                  <div class="text-xs text-slate-600 md:max-w-sm">{{ fr() ? 'Le planning et la saisie des notes sont verrouillés tant que cette affectation est absente.' : 'Timetable and grade entry remain blocked while this assignment is missing.' }}</div>
                </div>
                @if (!curriculum()?.homeroomTeacher) { <div class="mt-2 text-xs text-rose-700">{{ fr() ? 'Affectation titulaire manquante — action requise.' : 'Homeroom assignment missing — repair required.' }}</div> }
              </div>
            } @else {
              <div class="mb-3 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">{{ fr() ? 'Classe secondaire : choisissez un enseignant RESPONSIBLE dans chaque ligne de matière. Le planning ne propose aucune autre autorité.' : 'Secondary class: choose one RESPONSIBLE teacher in each subject row. The timetable exposes no other authority.' }}</div>
            }
            <div class="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div class="font-semibold text-ink">{{ fr() ? 'Règles du bulletin et enseignant responsable' : 'Report-card rules and responsible teacher' }}</div>
              <div class="text-xs text-mute mt-1 mb-3">{{ fr() ? 'Ces valeurs sont enregistrées pour la combinaison session + classe + matière. Le coefficient ci-dessus est celui qui sera calculé sur le bulletin.' : 'These values are saved for the session + class + subject combination. The coefficient above is the one used on the report card.' }}</div>
              <div class="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900"><span class="font-bold">{{ fr() ? 'Règle de passage :' : 'Promotion rule:' }}</span> {{ fr() ? 'Obligatoire contrôle la complétude des notes : il faut saisir une note, une absence ou une exemption pour valider le résultat. Cela ne rend pas la matière décisive pour le passage. Le passage utilise la moyenne annuelle globale, calculée sur toutes les matières incluses, et la décision du conseil.' : 'Required controls result completeness: a mark, absence, or exemption must be entered before the result can be validated. It does not make this subject independently decisive for promotion. Promotion uses the overall annual average across included subjects and the council decision.' }}</div>
              <div class="space-y-2">
                @for (row of assignmentRows(); track row.subjectId) {
                  <div class="grid grid-cols-1 md:grid-cols-8 gap-2 items-center rounded-lg bg-white border border-slate-200 p-2">
                    <div class="font-semibold text-sm md:col-span-2">{{ row.subjectCode }} · {{ assignmentSubjectLabel(row) }}</div>
                    <label><span class="meta">{{ fr() ? 'Groupe' : 'Group' }}</span><select [ngModel]="row.groupId ?? ''" (ngModelChange)="setSubjectGroup(row, $event)" class="field"><option value="">{{ fr() ? 'Aucun' : 'None' }}</option>@for (group of (curriculum()?.groups ?? []); track group.id) { <option [value]="group.id">{{ groupLabel(group) }}</option> }</select></label>
                    <label><span class="meta">{{ fr() ? 'Barème' : 'Max score' }}</span><input type="number" min="1" [ngModel]="row.maxScore" (ngModelChange)="row.maxScore = +$event" class="field" /></label>
                    <label><span class="meta">{{ fr() ? 'Seuil indicatif' : 'Subject reference threshold' }}</span><input type="number" min="0" [ngModel]="row.passThreshold" (ngModelChange)="row.passThreshold = +$event" class="field" /><span class="text-[11px] text-mute">{{ fr() ? 'Référence matière, pas le seuil de passage.' : 'Subject reference; not the promotion threshold.' }}</span></label>
                    <label class="flex items-start gap-2 text-xs font-semibold pt-2"><input type="checkbox" [ngModel]="row.mandatory" (ngModelChange)="row.mandatory = $event" /> <span>{{ fr() ? 'Obligatoire pour compléter le résultat' : 'Required for result completeness' }}</span></label>
                    <label class="flex items-center gap-2 text-xs font-semibold pt-4"><input type="checkbox" [ngModel]="row.remarkRequired" (ngModelChange)="row.remarkRequired = $event" /> {{ fr() ? 'Remarque' : 'Remark' }}</label>
                    @if (selectedAssignmentClass()?.level === 'secondary') {
                      <div class="flex items-end gap-1"><select [ngModel]="row.responsibleTeacher?.employeeId ?? ''" (ngModelChange)="prepareSubjectTeacher(row, $event)" class="field" [class.invalid]="!row.responsibleTeacher" [disabled]="!canWrite"><option value="">{{ fr() ? 'Enseignant RESPONSIBLE' : 'RESPONSIBLE teacher' }}</option>@for (teacher of allTeachers(); track teacher.id) { <option [value]="teacher.id">{{ teacher.name }}</option> }</select><button type="button" (click)="saveAssignment(row)" [disabled]="!canWrite" class="h-10 px-2 rounded-lg bg-brand-50 text-brand-700 text-xs font-bold">{{ fr() ? 'Sauver' : 'Save' }}</button></div>
                    } @else {
                      <div><span class="meta">{{ fr() ? 'Enseignant hérité' : 'Inherited teacher' }}</span><div class="field bg-slate-100 text-slate-600 cursor-not-allowed">{{ curriculum()?.homeroomTeacher?.employeeName ?? (fr() ? 'Affectation manquante' : 'Assignment missing') }}</div><div class="text-[11px] text-slate-500 mt-1">{{ fr() ? 'Hérité du titulaire de classe — non modifiable ici.' : 'Inherited from homeroom — not editable here.' }}</div></div>
                    }
                  </div>
                }
              </div>
            </div>
          }

          @if (assignmentNotice(); as notice) {
            <div class="mt-3 text-sm rounded-lg px-3 py-2" [class]="notice.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ notice.text }}</div>
          }
          @if (coefResult(); as result) {
            <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-slate-50 text-mute">{{ result.applied }} {{ fr() ? 'affectation(s) importée(s).' : 'assignment(s) imported.' }}</div>
          }
          @if (coefError(); as importError) {
            <div class="mt-3 text-sm rounded-lg px-3 py-2 bg-rose-50 text-rose-700">{{ importError }}</div>
          }
        </bbc-card>
      }

      <!-- ===================== SUBJECTS ===================== -->
      @case ('subjects') {
        <bbc-card [title]="fr() ? 'Matières & coefficients' : 'Subjects & coefficients'"
          [subtitle]="fr() ? 'Listes distinctes par sous-système, avec coefficient pour les moyennes pondérées' : 'Distinct lists per subsystem, with coefficient for weighted averages'">
          <div action class="flex items-center gap-2">
            @if (canWrite && (subjFilter() === 'FR' || subjFilter() === 'EN')) {
              <button (click)="importDefaults()" [disabled]="!missingDefaultsCount()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50"
                [title]="fr() ? 'Depuis la liste officielle MATIÈRE EXCEL' : 'From the official MATIERE EXCEL master list'">
                <bbc-icon name="download" [s]="16" />
                {{ missingDefaultsCount()
                    ? (fr() ? 'Importer ' + missingDefaultsCount() + ' matières standard' : 'Import ' + missingDefaultsCount() + ' standard subjects')
                    : (fr() ? 'Matières standard importées' : 'Standard subjects imported') }}
              </button>
            }
            @if (canWrite) {
              <button (click)="newSubject()" class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle matière' : 'New subject' }}
              </button>
            }
          </div>

          <!-- Subsystem filter -->
          <div class="flex items-center gap-1.5 mb-4 flex-wrap">
            @for (f of subjFilters; track f.value) {
              <button (click)="subjFilter.set(f.value)"
                class="px-3 py-1.5 rounded-full text-xs font-semibold transition"
                [class]="subjFilter() === f.value ? 'bg-brand-600 text-white' : 'bg-white text-mute border border-slate-200 hover:border-brand-300'">
                {{ fr() ? f.fr : f.en }}
                <span class="ml-1 opacity-70">{{ countFor(f.value) }}</span>
              </button>
            }
          </div>

          @if (canWrite && subjForm()) {
            <form (ngSubmit)="saveSubject()" class="grid grid-cols-1 md:grid-cols-5 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Code' : 'Code' }} *</span>
                <input [(ngModel)]="subjCode" name="scode" required placeholder="MATH" [disabled]="!!subjEditId()"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400 disabled:bg-slate-100 uppercase" />
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sous-système' : 'Subsystem' }}</span>
                <select [(ngModel)]="subjSub" name="ssub" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                  <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
                  <option value="EN">{{ fr() ? 'Anglophone' : 'Anglophone' }}</option>
                  <option value="">{{ fr() ? 'Commune (FR + EN)' : 'Common (FR + EN)' }}</option>
                </select>
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom (FR)' : 'Name (FR)' }}</span>
                <input [(ngModel)]="subjFr" name="sfr" placeholder="Mathématiques"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom (EN)' : 'Name (EN)' }}</span>
                <input [(ngModel)]="subjEn" name="sen" placeholder="Mathematics"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Coefficient' : 'Coefficient' }}</span>
                <input type="number" min="1" [(ngModel)]="subjCoef" name="scoef"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <div class="md:col-span-5 flex items-center justify-end gap-2">
                <button type="button" (click)="subjForm.set(false)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                <button type="submit" [disabled]="!subjCode" class="h-9 px-5 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
              </div>
            </form>
          }

          @if (filteredSubjects().length) {
            <table class="min-w-full text-sm">
              <thead><tr class="border-b border-slate-100 text-[11px] uppercase text-mute text-left">
                <th class="py-2 pr-3 font-semibold">{{ fr() ? 'Code' : 'Code' }}</th>
                <th class="py-2 px-3 font-semibold">{{ fr() ? 'Nom' : 'Name' }}</th>
                <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Section' : 'Section' }}</th>
                <th class="py-2 px-3 font-semibold text-center">{{ fr() ? 'Coef.' : 'Coef.' }}</th>
                <th></th>
              </tr></thead>
              <tbody>
                @for (s of filteredSubjects(); track s.id) {
                  <tr class="border-b border-slate-50 hover:bg-slate-50/40">
                    <td class="py-2 pr-3 font-mono font-semibold text-ink">{{ s.code }}</td>
                    <td class="py-2 px-3">{{ subjectLabel(s) }}</td>
                    <td class="py-2 px-3 text-center">
                      <span class="inline-block px-2 py-0.5 rounded-full text-[11px] font-semibold"
                        [class]="s.subsystem === 'FR' ? 'bg-brand-50 text-brand-700' : s.subsystem === 'EN' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'">
                        {{ s.subsystem === 'FR' ? 'FR' : s.subsystem === 'EN' ? 'EN' : (fr() ? 'FR+EN' : 'FR+EN') }}
                      </span>
                    </td>
                    <td class="py-2 px-3 text-center font-semibold">{{ s.coef }}</td>
                    <td class="py-2 pl-3 text-right whitespace-nowrap">
                      @if (canWrite) {
                        <button (click)="editSubject(s)" class="text-mute hover:text-brand-600 px-1.5"><bbc-icon name="edit" [s]="15" /></button>
                        <button (click)="deleteSubject(s)" class="text-mute hover:text-rose-600 px-1.5"><bbc-icon name="trash" [s]="15" /></button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <div class="py-8 text-center">
              <bbc-empty icon="book" [label]="fr() ? 'Aucune matière dans cette liste.' : 'No subject in this list.'" />
              @if (canWrite && subjFilter() !== 'ALL') {
                <button (click)="importDefaults()" class="mt-3 inline-flex items-center gap-2 h-9 px-4 text-sm font-semibold rounded-lg bg-gold-400 hover:bg-gold-500 text-brand-800">
                  <bbc-icon name="plus" [s]="16" />
                  {{ fr() ? 'Importer les matières standard ' + subjFilter() : 'Import standard ' + subjFilter() + ' subjects' }}
                </button>
              }
            </div>
          }
        </bbc-card>

      }

      <!-- ===================== SECONDARY COMPETENCIES ===================== -->
      @case ('assessments') {
        <bbc-assessment-defaults />
      }

      @case ('competencies') {
        <bbc-card [title]="fr() ? 'Compétences du secondaire' : 'Secondary competencies'"
          [subtitle]="fr() ? 'Définissez manuellement les compétences évaluées ou importez les notes par fichier CSV. Chaque modèle est versionné par session, période, classe et matière.' : 'Define assessed competencies manually or import marks from CSV. Each model is versioned by session, period, class and subject.'">
          <div class="mb-4 rounded-xl border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-950 leading-relaxed">
            <strong>{{ fr() ? 'Flux conseillé :' : 'Recommended flow:' }}</strong>
            {{ fr() ? 'choisissez une classe secondaire, une matière et une période, créez les descriptions puis publiez le modèle. Les notes peuvent ensuite être saisies ou importées avec les colonnes studentId, competencyCode, mark, valueStatus.' : 'choose a secondary class, subject and period, create descriptions, then publish the model. Marks can then be entered or imported with studentId, competencyCode, mark, valueStatus columns.' }}
          </div>
          <div class="grid grid-cols-1 md:grid-cols-5 gap-3">
            <label class="block"><span class="field-label">{{ fr() ? 'Session' : 'Session' }} *</span>
              <select [ngModel]="competencySessionId()" (ngModelChange)="selectCompetencySession($event)" class="field" required>
                <option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>
                @for (session of academicSessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }
              </select>
            </label>
            <label class="block"><span class="field-label">{{ fr() ? 'Période' : 'Period' }} *</span>
              <select [ngModel]="competencyPeriodId()" (ngModelChange)="selectCompetencyPeriod($event)" class="field" required>
                <option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>
                @for (period of competencyPeriods(); track period.id) { <option [value]="period.id">{{ period.code }} · {{ period.label }}</option> }
              </select>
            </label>
            <label class="block"><span class="field-label">{{ fr() ? 'Classe secondaire' : 'Secondary class' }} *</span>
              <select [ngModel]="competencyClassId()" (ngModelChange)="selectCompetencyClass($event)" class="field" required>
                <option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>
                @for (klass of secondaryClasses(); track klass.id) { <option [value]="klass.id">{{ klass.name }} · {{ klass.subsystem }}</option> }
              </select>
            </label>
            <label class="block"><span class="field-label">{{ fr() ? 'Matière' : 'Subject' }} *</span>
              <select [ngModel]="competencySubjectId()" (ngModelChange)="selectCompetencySubject($event)" class="field" required>
                <option value="">{{ fr() ? 'Choisir' : 'Choose' }}</option>
                @for (subject of secondarySubjects(); track subject.id) { <option [value]="subject.id">{{ subject.code }} · {{ subjectLabel(subject) }}</option> }
              </select>
            </label>
            <label class="block"><span class="field-label">{{ fr() ? 'Langue du modèle' : 'Model language' }} *</span>
              <select [ngModel]="competencyLocale()" (ngModelChange)="competencyLocale.set($event); loadCompetencyModels()" class="field" required>
                <option value="fr">Français</option><option value="en">English</option>
              </select>
            </label>
          </div>
          @if (competencyNotice(); as notice) { <div class="mt-3 rounded-lg border px-3 py-2 text-sm" [class]="notice.ok ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'">{{ notice.text }}</div> }
          @if (!secondaryClasses().length) {
            <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{{ fr() ? 'Aucune classe secondaire n’est configurée. Créez d’abord une section et une classe secondaire.' : 'No secondary class is configured. Create a secondary section and class first.' }}</div>
          } @else if (competencyClassId() && competencySubjectId() && competencyPeriodId()) {
            <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 mt-5">
              <section class="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div class="flex items-start justify-between gap-3"><div><h3 class="font-semibold text-ink">{{ fr() ? 'Saisie manuelle du modèle' : 'Manual model entry' }}</h3><p class="text-xs text-mute mt-1">{{ fr() ? 'Les champs obligatoires sont signalés par *.' : 'Required fields are marked with *.' }}</p></div><button type="button" (click)="addCompetencyRow()" class="btn-secondary">+ {{ fr() ? 'Compétence' : 'Competency' }}</button></div>
                <label class="block mt-3"><span class="field-label">{{ fr() ? 'Nom du modèle' : 'Model name' }} *</span><input [(ngModel)]="competencyName" class="field" required [placeholder]="fr() ? 'Évaluations du trimestre' : 'Term competencies'" /></label>
                <div class="space-y-2 mt-3">
                  @for (row of competencyRows; track $index; let i = $index) {
                    <div class="grid grid-cols-[90px_1fr_80px_32px] gap-2 items-start">
                      <input [(ngModel)]="row.code" [name]="'competency-code-' + i" class="field" required placeholder="CODE" aria-label="{{ fr() ? 'Code' : 'Code' }}" />
                      <textarea [(ngModel)]="row.description" [name]="'competency-description-' + i" class="field min-h-[42px]" required rows="2" placeholder="{{ fr() ? 'Description de la compétence évaluée' : 'Assessed competency description' }}"></textarea>
                      <input [(ngModel)]="row.maxScore" [name]="'competency-max-' + i" class="field" type="number" min="1" max="100" required aria-label="{{ fr() ? 'Barème' : 'Max score' }}" />
                      <button type="button" (click)="removeCompetencyRow(i)" class="h-10 rounded-lg border border-slate-200 bg-white text-slate-500 hover:text-rose-600" [disabled]="competencyRows.length === 1" aria-label="{{ fr() ? 'Supprimer' : 'Remove' }}">×</button>
                    </div>
                  }
                </div>
                <div class="flex justify-end mt-4"><button type="button" (click)="saveCompetencyModel()" [disabled]="competencyBusy() || !canWrite" class="btn-primary">{{ competencyBusy() ? '…' : (fr() ? 'Enregistrer le brouillon' : 'Save draft') }}</button></div>
              </section>
              <section class="rounded-xl border border-slate-200 bg-white p-4">
                <div class="flex items-start justify-between gap-3"><div><h3 class="font-semibold text-ink">{{ fr() ? 'Versions et import des notes' : 'Versions and mark import' }}</h3><p class="text-xs text-mute mt-1">{{ fr() ? 'Publiez une version, puis importez un CSV sans supprimer la saisie manuelle.' : 'Publish a version, then import CSV marks without removing manual entry.' }}</p></div><label class="btn-secondary cursor-pointer">{{ fr() ? 'Importer CSV' : 'Import CSV' }}<input type="file" accept=".csv,text/csv,text/plain" class="hidden" (change)="onCompetencyMarksFile($event)" /></label></div>
                @if (competencyModels().length) {
                  <div class="space-y-2 mt-3">
                    @for (model of competencyModels(); track model.id) {
                      <div class="rounded-lg border border-slate-200 p-3" [class.border-brand-300]="competencyModelId() === model.id">
                        <button type="button" (click)="competencyModelId.set(model.id)" class="w-full text-left"><div class="flex items-center justify-between gap-2"><strong>{{ model.name }}</strong><span class="chip" [class]="model.status === 'PUBLISHED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">v{{ model.version }} · {{ model.status }}</span></div><div class="text-xs text-mute mt-1">{{ model.competencies.length }} {{ fr() ? 'compétences' : 'competencies' }} · {{ model.source }}</div></button>
                        @if (canWrite && model.status === 'DRAFT') { <button type="button" (click)="publishCompetencyModel(model)" class="mt-2 text-xs font-semibold text-brand-700">{{ fr() ? 'Publier cette version' : 'Publish this version' }}</button> }
                      </div>
                    }
                  </div>
                } @else { <div class="mt-4 rounded-lg border border-dashed border-slate-300 p-4 text-sm text-mute">{{ fr() ? 'Aucun modèle pour cette sélection. Créez le premier à gauche.' : 'No model for this selection. Create the first one on the left.' }}</div> }
                <div class="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-mute"><strong class="text-ink">CSV :</strong> studentId, competencyCode, mark, valueStatus. {{ fr() ? 'Les statuts acceptés sont SCORED, ABSENT, EXEMPT et MISSING.' : 'Accepted statuses are SCORED, ABSENT, EXEMPT and MISSING.' }}</div>
              </section>
            </div>
          } @else {
            <div class="mt-5 rounded-lg border border-dashed border-slate-300 p-5 text-center text-sm text-mute">{{ fr() ? 'Choisissez les quatre champs de contexte pour gérer un modèle secondaire.' : 'Choose the four context fields to manage a secondary model.' }}</div>
          }
        </bbc-card>
      }

      <!-- ===================== DOCUMENT DESIGN ===================== -->
      @case ('design') {
        <bbc-card [title]="fr() ? 'Modèles de bulletins et identité de l’établissement' : 'Report templates and school identity'"
          [subtitle]="fr() ? 'Cette page contrôle l’apparence des documents officiels. Elle ne modifie ni les notes, ni les coefficients, ni les décisions.' : 'This page controls the appearance of official documents. It does not change marks, coefficients, or decisions.'">
          <div class="mb-4 rounded-xl border border-brand-200 bg-brand-50 px-4 py-4 text-sm text-brand-950 leading-relaxed">
            <div class="flex items-start gap-3">
              <div class="mt-0.5 text-brand-700"><bbc-icon name="doc" [s]="20" /></div>
              <div>
                <strong>{{ fr() ? 'À quoi sert cette page ?' : 'What is this page for?' }}</strong>
                <p class="mt-1">{{ fr() ? 'Elle détermine ce qui apparaît sur un PDF officiel : identité de l’établissement, mise en page, langue, niveau et zones de signature.' : 'It controls what appears on an official PDF: school identity, layout, language, level, and signature areas.' }}</p>
              </div>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mt-4">
              <div class="rounded-lg border border-brand-100 bg-white/70 p-3"><div class="flex items-center gap-2 font-bold"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-700 text-white text-xs">1</span>{{ fr() ? 'Préparer' : 'Prepare' }}</div><p class="mt-2 text-xs">{{ fr() ? 'Modifiez d’abord le profil de l’établissement si son identité change.' : 'First update the school profile if its identity changes.' }}</p></div>
              <div class="rounded-lg border border-brand-100 bg-white/70 p-3"><div class="flex items-center gap-2 font-bold"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-700 text-white text-xs">2</span>{{ fr() ? 'Publier' : 'Publish' }}</div><p class="mt-2 text-xs">{{ fr() ? 'Créez une version avec un motif obligatoire et contrôlable.' : 'Create a version with a required, auditable reason.' }}</p></div>
              <div class="rounded-lg border border-brand-100 bg-white/70 p-3"><div class="flex items-center gap-2 font-bold"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-700 text-white text-xs">3</span>{{ fr() ? 'Générer' : 'Generate' }}</div><p class="mt-2 text-xs">{{ fr() ? 'Les nouveaux documents utilisent la version publiée ; les anciens restent inchangés.' : 'New documents use the published version; older documents stay unchanged.' }}</p></div>
            </div>
          </div>
          <div class="mb-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950 leading-relaxed">
            <strong>{{ fr() ? 'Important :' : 'Important:' }}</strong>
            {{ fr() ? 'Cette page est un registre de versions, pas encore un éditeur visuel. Le bouton de modèle copie la définition existante dans une nouvelle version ; le bouton de marque photographie le profil actuel de l’établissement. Si rien n’a changé, ne publiez pas.' : 'This page is a version ledger, not yet a visual editor. The template button copies the existing definition into a new version; the branding button captures the current school profile. If nothing changed, do not publish.' }}
          </div>
          @if (documentDesign(); as design) {
            <div class="space-y-6">
              <section>
                <div class="flex flex-wrap items-start justify-between gap-3 mb-3">
                  <div class="flex items-start gap-2"><span class="flex h-7 w-7 items-center justify-center rounded-full bg-slate-800 text-white text-xs font-bold">1</span><div><h3 class="font-semibold text-ink">{{ fr() ? 'Mise en page des documents' : 'Document layout' }}</h3><p class="text-xs text-mute mt-1">{{ fr() ? 'Ces lignes indiquent quel format le système associe à chaque bulletin ou certificat.' : 'These rows show which format the system associates with each report card or certificate.' }}</p></div></div>
                  <div class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-mute">{{ fr() ? 'Les résultats scolaires ne changent pas ici.' : 'Academic results are not changed here.' }}</div>
                </div>
                <div class="overflow-x-auto rounded-lg border border-slate-200">
                  <table class="min-w-full text-xs"><thead class="bg-slate-50"><tr class="text-left"><th class="p-2">{{ fr() ? 'Document' : 'Document' }}</th><th class="p-2">{{ fr() ? 'Utilisé pour' : 'Used for' }}</th><th class="p-2">{{ fr() ? 'Version active' : 'Active version' }}</th><th class="p-2">{{ fr() ? 'Référence technique' : 'Technical reference' }}</th><th class="p-2"></th></tr></thead><tbody>
                    @for (template of design.templates; track template.id) {
                      <tr class="border-t border-slate-100 align-top"><td class="p-2"><strong>{{ template.name }}</strong><div class="text-slate-500 mt-0.5">{{ designTemplateTypeLabel(template) }}</div></td><td class="p-2"><div class="font-semibold text-ink">{{ designProductLabel(template) }}</div><div class="text-slate-500">{{ designLocaleLabel(template.locale) }} · {{ designSubsystemLabel(template.subsystem) }}</div><div class="text-slate-500 mt-0.5">{{ designFamilyLabel(template.referenceFamily) }}</div></td><td class="p-2"><span class="chip bg-emerald-50 text-emerald-700">v{{ template.version }} · {{ designStatusLabel(template.status) }}</span><div class="text-slate-500 mt-1">{{ fr() ? 'Utilisée pour les nouveaux documents' : 'Used for new documents' }}</div></td><td class="p-2"><div class="font-mono" [title]="template.checksum || ''">{{ template.checksum ? template.checksum.slice(0, 12) + '…' : '—' }}</div><div class="text-slate-500 mt-1">{{ fr() ? 'Contrôle d’intégrité' : 'Integrity check' }}</div></td><td class="p-2 text-right whitespace-nowrap">@if (canWrite && template.status === 'PUBLISHED') { <button type="button" (click)="openDesignPublish('template', template.id, template.name)" class="text-brand-700 font-semibold" [title]="fr() ? 'Copier la définition actuelle dans une nouvelle version' : 'Copy the current definition into a new version'">{{ fr() ? 'Créer une version' : 'Create version' }}</button> }</td></tr>
                    } @empty { <tr><td colspan="5" class="p-4 text-center text-mute">{{ fr() ? 'Aucun modèle versionné.' : 'No versioned templates.' }}</td></tr> }
                  </tbody></table>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
                  @for (template of design.templates; track template.id) {
                    <article class="rounded-xl border border-slate-200 bg-slate-50 p-3" aria-label="Report card live sample preview">
                      <div class="flex items-center justify-between gap-2 mb-2"><strong class="text-sm">{{ template.name }}</strong><span class="chip bg-white text-slate-600">{{ template.locale.toUpperCase() }} · v{{ template.version }}</span></div>
                      <div class="rounded-lg border border-slate-300 bg-white p-3 shadow-inner min-h-[150px]">
                        <div class="flex items-start justify-between border-b border-slate-200 pb-2"><div><div class="text-[9px] uppercase tracking-wide text-slate-500">{{ fr() ? 'République du Cameroun · établissement' : 'Republic of Cameroon · school' }}</div><div class="font-bold text-xs mt-1">{{ template.referenceFamily === 'SECONDARY' ? (fr() ? 'Bulletin secondaire' : 'Secondary report card') : (fr() ? 'Bulletin scolaire' : 'School report card') }}</div></div><div class="h-8 w-8 rounded bg-brand-100"></div></div>
                        <div class="grid grid-cols-3 gap-1 mt-3"><div class="h-2 rounded bg-slate-200"></div><div class="h-2 rounded bg-slate-200"></div><div class="h-2 rounded bg-slate-200"></div></div>
                        <div class="mt-2 space-y-1">@for (row of [1,2,3,4]; track row) { <div class="grid grid-cols-6 gap-1"><div class="col-span-2 h-2 rounded bg-slate-100"></div><div class="h-2 rounded bg-slate-100"></div><div class="h-2 rounded bg-slate-100"></div><div class="h-2 rounded bg-slate-100"></div><div class="h-2 rounded bg-slate-100"></div></div> }</div>
                        <div class="mt-3 flex justify-between"><span class="h-2 w-20 rounded bg-brand-100"></span><span class="h-2 w-12 rounded bg-slate-200"></span></div>
                      </div>
                      <p class="text-[11px] text-mute mt-2">{{ template.referenceFamily === 'SECONDARY' ? (fr() ? 'Aperçu : compétences, notes /20, coefficient, produit, cote et appréciation.' : 'Preview: competencies, marks /20, coefficient, product, grade and remarks.') : (fr() ? 'Aperçu primaire conservé.' : 'Primary preview preserved.') }}</p>
                    </article>
                  }
                </div>
              </section>
              <section>
                <div class="flex flex-wrap items-start justify-between gap-3 mb-3"><div class="flex items-start gap-2"><span class="flex h-7 w-7 items-center justify-center rounded-full bg-slate-800 text-white text-xs font-bold">2</span><div><h3 class="font-semibold text-ink">{{ fr() ? 'Identité imprimée de l’établissement' : 'School identity printed on documents' }}</h3><p class="text-xs text-mute mt-1">{{ fr() ? 'Nom, ville, ministère, logo, cachet et titres des signataires utilisés sur les nouveaux PDF.' : 'Name, city, ministry, logo, stamp, and signatory titles used on new PDFs.' }}</p></div></div>@if (canWrite) { <button type="button" (click)="openDesignPublish('branding', undefined, fr() ? 'Identité de l’établissement' : 'School identity')" class="btn-primary">{{ fr() ? 'Publier après modification' : 'Publish after a change' }}</button> }</div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                  @for (branding of design.branding; track branding.id) {
                    <div class="rounded-lg border border-slate-200 bg-white p-4"><div class="flex items-start justify-between gap-2"><div><strong>{{ branding.schoolName }}</strong><div class="text-xs text-mute mt-1">{{ branding.city || '—' }} · {{ branding.country || '—' }}</div></div><span class="chip" [class]="branding.status === 'PUBLISHED' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'">{{ designLocaleLabel(branding.locale) }} · v{{ branding.version }} · {{ designStatusLabel(branding.status) }}</span></div><div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-4 text-xs"><div class="rounded-lg bg-slate-50 p-2"><div class="text-slate-500">{{ fr() ? 'Principal / signataire' : 'Principal / signatory' }}</div><div class="font-semibold mt-1">{{ branding.principalName || (fr() ? 'Non renseigné' : 'Not configured') }}</div><div class="text-slate-500">{{ branding.principalTitle || '—' }}</div></div><div class="rounded-lg bg-slate-50 p-2"><div class="text-slate-500">{{ fr() ? 'Version publiée le' : 'Published on' }}</div><div class="font-semibold mt-1">{{ branding.publishedAt || branding.createdAt }}</div></div></div><details class="mt-3 text-[11px] text-slate-500"><summary class="cursor-pointer font-semibold">{{ fr() ? 'Afficher la référence technique' : 'Show technical reference' }}</summary><div class="font-mono break-all mt-2">{{ branding.contentHash }}</div></details></div>
                  } @empty { <div class="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-mute">{{ fr() ? 'Aucune version de marque.' : 'No branding version.' }}</div> }
                </div>
              </section>
              <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-950 leading-relaxed"><strong>{{ fr() ? 'Après publication :' : 'After publishing:' }}</strong> {{ fr() ? 'un bulletin ou certificat généré ensuite reprend cette version et son identité. Un document déjà publié conserve son ancienne version, même si le profil ou le modèle change plus tard.' : 'a report card or certificate generated afterward uses this version and identity. An already published document keeps its old version, even if the profile or template changes later.' }}</div>
            </div>
          } @else {
            <div class="py-8 text-center text-mute">{{ fr() ? 'Chargement des versions…' : 'Loading versions…' }}</div>
          }
        </bbc-card>
      }
    }

    @if (assignmentImpact(); as impact) {
      <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" role="presentation">
        <section class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-xl p-5" role="dialog" aria-modal="true" aria-labelledby="assignment-impact-title">
          <h2 id="assignment-impact-title" class="text-lg font-bold text-ink">{{ fr() ? 'Vérifier les conséquences de l’affectation' : 'Review assignment consequences' }}</h2>
          <p class="text-sm text-mute mt-2">{{ fr() ? 'La modification est préparée mais aucune donnée n’a encore été changée.' : 'The change is prepared, but no data has been changed yet.' }}</p>
          <div class="grid grid-cols-2 gap-3 mt-4 text-center text-sm">
            <div class="rounded-lg border border-slate-200 bg-slate-50 p-3"><strong class="block text-xl">{{ impact.draftSlotCount }}</strong>{{ fr() ? 'créneaux brouillon à actualiser' : 'draft slots to refresh' }}</div>
            <div class="rounded-lg border p-3" [class]="impact.publishedScheduleDrift ? 'border-amber-300 bg-amber-50 text-amber-900' : 'border-emerald-200 bg-emerald-50 text-emerald-900'"><strong class="block text-xl">{{ impact.publishedSlotCount }}</strong>{{ fr() ? 'créneaux publiés concernés' : 'published slots affected' }}</div>
          </div>
          @if (impact.warnings.length) { <div class="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900"><strong>{{ fr() ? 'Conséquences :' : 'Consequences:' }}</strong><ul class="list-disc pl-5 mt-1">@for (warning of impact.warnings; track warning) { <li>{{ warning === 'PUBLISHED_SCHEDULE_DRIFT' ? (fr() ? 'La version publiée reste inchangée et sera signalée en dérive. Créez un nouveau brouillon pour appliquer le nouvel enseignant.' : 'The published version stays unchanged and will report drift. Create a new draft to apply the new teacher.') : (fr() ? 'Les brouillons concernés devront être actualisés.' : 'Affected drafts will need to be refreshed.') }}</li> }</ul></div> }
          @if (impact.blockers.length) { <div class="mt-3 rounded-lg border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800"><strong>{{ fr() ? 'Correction requise :' : 'Repair required:' }}</strong> {{ impact.blockers.join(' · ') }}</div> }
          <div class="flex justify-end gap-2 mt-5"><button (click)="cancelAssignmentImpact()" class="h-9 px-4 rounded-lg border border-slate-300 bg-white text-sm font-semibold">{{ fr() ? 'Annuler — ne rien changer' : 'Cancel — make no change' }}</button><button (click)="confirmAssignmentImpact()" [disabled]="impact.blockers.length > 0 || assignmentBusy()" class="h-9 px-4 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">{{ assignmentBusy() ? '…' : (fr() ? 'Confirmer l’affectation' : 'Confirm assignment') }}</button></div>
        </section>
      </div>
    }

    @if (designPublish(); as request) {
      <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" role="presentation">
        <section class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-xl p-5" role="dialog" aria-modal="true" aria-labelledby="design-publish-title">
          <h2 id="design-publish-title" class="text-lg font-bold text-ink">{{ fr() ? 'Vérifier la publication de version' : 'Review version publication' }}</h2>
          <p class="text-sm text-mute mt-2">{{ request.label }}</p>
          <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-950 leading-relaxed"><strong>{{ fr() ? 'Conséquence :' : 'Consequence:' }}</strong> {{ request.kind === 'branding' ? (fr() ? 'La version publiée actuelle sera conservée dans l’historique et une nouvelle version de marque sera créée à partir du profil de l’établissement.' : 'The current published version will remain in history and a new branding version will be created from the school profile.') : (fr() ? 'Le modèle est copié dans un nouveau numéro de version. Les snapshots déjà publiés continuent de référencer leur modèle d’origine.' : 'The template is copied into a new version number. Existing published snapshots continue to reference their original template.') }}</div>
          <label class="block mt-4"><span class="text-xs font-semibold text-slate-700">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} <span class="text-rose-600">*</span></span><textarea [(ngModel)]="designReason" rows="3" class="field mt-1.5" [class.invalid]="!designReason.trim()" [placeholder]="fr() ? 'Expliquez pourquoi cette version est publiée…' : 'Explain why this version is being published…'"></textarea></label>
          @if (!designReason.trim()) { <div class="mt-1 text-xs text-rose-600">{{ fr() ? 'Le motif est obligatoire.' : 'A reason is required.' }}</div> }
          <div class="flex justify-end gap-2 mt-5"><button type="button" (click)="cancelDesignPublish()" class="btn-secondary">{{ fr() ? 'Annuler — ne rien changer' : 'Cancel — make no change' }}</button><button type="button" (click)="confirmDesignPublish()" [disabled]="!designReason.trim() || designBusy()" class="btn-primary">{{ designBusy() ? '…' : (fr() ? 'Confirmer la publication' : 'Confirm publication') }}</button></div>
        </section>
      </div>
    }

    @if (err(); as e) {
      <div class="mt-3 text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div>
    }
  `,
})
export class AcademicSetupComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private api = inject(SetupApi);
  private academicApi = inject(AcademicApi);
  private foundation = inject(FoundationApi);
  private scopeSvc = inject(ScopeService);
  private route = inject(ActivatedRoute);

  protected fr = () => this.i18n.lang() === 'fr';
  protected canWrite = this.auth.can('settings', 'write');
  protected activeScope = computed(() => this.scopeSvc.scope());

  protected scopeBanner = computed(() => {
    const s = this.activeScope();
    if (!s) {
      return this.fr()
        ? 'Aucun parcours sélectionné — toutes les sections/classes sont listées. Choisissez un parcours pour filtrer.'
        : 'No parcours selected — all sections/classes are listed. Pick a parcours to filter.';
    }
    const lvl = this.levelLabel(s.level);
    const sub = s.subsystem === 'FR'
      ? (this.fr() ? 'Francophone' : 'Francophone')
      : (this.fr() ? 'Anglophone' : 'English');
    return this.fr()
      ? `Parcours actif : ${lvl} · ${sub}. Les données des autres parcours sont masquées — changez via le bandeau.`
      : `Active parcours: ${lvl} · ${sub}. Other parcours data is hidden — switch from the top bar.`;
  });

  protected sub = signal<'sections' | 'classes' | 'subjects' | 'class-subjects' | 'assessments' | 'competencies' | 'design'>('sections');
  protected sections = signal<SectionView[]>([]);
  protected classes = signal<ClassView[]>([]);
  protected subjects = signal<SubjectView[]>([]);
  protected err = signal<string | null>(null);

  protected subTabs = computed(() => [
    { id: 'sections', label: this.fr() ? 'Sections' : 'Sections' },
    { id: 'classes', label: this.fr() ? 'Classes' : 'Classes' },
    { id: 'subjects', label: this.fr() ? 'Matières' : 'Subjects' },
    { id: 'class-subjects', label: this.fr() ? 'Matières par classe' : 'Class subjects' },
    { id: 'competencies', label: this.fr() ? 'Compétences secondaire' : 'Secondary competencies' },
    { id: 'design', label: this.fr() ? 'Modèles / marque' : 'Templates / branding' },
  ]);
  protected displayedSubTabs = computed(() => this.subTabs().map((tab) =>
    tab.id === 'competencies' ? { id: 'assessments', label: this.fr() ? 'Évaluations' : 'Evaluations' } : tab));

  // Section form
  protected secForm = signal(false);
  protected secEditId = signal<string | null>(null);
  protected secDraft: SectionUpsert = { label: '', subsystem: 'FR', level: 'primary' };

  // Class form
  protected clsForm = signal(false);
  protected clsEditId = signal<string | null>(null);
  protected clsDraft: ClassUpsert = { name: '', sectionId: '' };

  // Teacher assignment panel (0..N teachers per class)
  protected teacherClass = signal<ClassView | null>(null);
  protected allTeachers = signal<TeacherOption[]>([]);
  protected picked = signal<Set<string>>(new Set());

  // Subject form (split label fields for editing)
  protected subjForm = signal(false);
  protected subjEditId = signal<string | null>(null);
  protected subjCode = '';
  protected subjSub = 'FR';
  protected subjFr = '';
  protected subjEn = '';
  protected subjCoef = 1;

  // Subject subsystem filter
  protected subjFilter = signal<'FR' | 'EN' | 'ALL'>('FR');
  protected readonly subjFilters: { value: 'FR' | 'EN' | 'ALL'; fr: string; en: string }[] = [
    { value: 'FR', fr: 'Francophone', en: 'Francophone' },
    { value: 'EN', fr: 'Anglophone', en: 'Anglophone' },
    { value: 'ALL', fr: 'Toutes', en: 'All' },
  ];

  protected filteredSubjects = computed(() => {
    const f = this.subjFilter();
    const all = this.subjects();
    if (f === 'ALL') return all;
    // FR/EN lists include subjects common to both (subsystem null).
    return all.filter((s) => s.subsystem === f || !s.subsystem);
  });

  protected countFor(f: 'FR' | 'EN' | 'ALL'): number {
    const all = this.subjects();
    if (f === 'ALL') return all.length;
    return all.filter((s) => s.subsystem === f || !s.subsystem).length;
  }

  /** How many standard subjects of the active list are not yet created (0 = all present). */
  protected missingDefaultsCount = computed(() => {
    const sub = this.subjFilter();
    if (sub !== 'FR' && sub !== 'EN') return 0;
    const existing = new Set(
      this.subjects().filter((s) => s.subsystem === sub).map((s) => s.code.toUpperCase()),
    );
    return defaultSubjects(sub).filter((d) => !existing.has(d.code.toUpperCase())).length;
  });

  // Per-class coefficients
  protected coefRows = signal<ClassCoefView[]>([]);
  protected coefResult = signal<CoefImportResult | null>(null);
  protected coefError = signal<string | null>(null);
  protected assignmentClassId = signal('');
  protected assignmentSubjectId = signal('');
  protected assignmentCoef = signal(1);
  protected assignmentNotice = signal<{ ok: boolean; text: string } | null>(null);
  protected assignmentImpact = signal<AssignmentImpactView | null>(null);
  protected assignmentBusy = signal(false);
  private pendingAssignment: { role: 'HOMEROOM' | 'RESPONSIBLE'; employeeId: string; row?: CurriculumSubjectView } | null = null;
  protected pendingCoefficientRemoval = signal<string | null>(null);
  protected coefficientDrafts: Record<string, number> = {};
  protected academicSessions = signal<AcademicSessionView[]>([]);
  protected curriculumSessionId = signal('');
  protected curriculum = signal<CurriculumView | null>(null);
  protected groupCode = '';
  protected groupFr = '';
  protected groupEn = '';
  protected groupOrder = 1;
  protected groupNotice = signal<{ ok: boolean; text: string } | null>(null);
  protected pendingGroupRemoval = signal<string | null>(null);
  protected documentDesign = signal<DocumentDesignView | null>(null);
  protected designPublish = signal<{ kind: 'template' | 'branding'; id?: string; label: string } | null>(null);
  protected designReason = '';
  protected designBusy = signal(false);

  protected competencyPeriods = signal<AcademicReportingPeriodView[]>([]);
  protected competencySessionId = signal('');
  protected competencyPeriodId = signal('');
  protected competencyClassId = signal('');
  protected competencySubjectId = signal('');
  protected competencyLocale = signal<'fr' | 'en'>('fr');
  protected competencyName = '';
  protected competencyRows: Array<{ code: string; description: string; maxScore: number }> = [
    { code: 'UNDERSTAND', description: '', maxScore: 20 },
    { code: 'APPLY', description: '', maxScore: 20 },
  ];
  protected competencyModels = signal<SecondaryCompetencyModelView[]>([]);
  protected competencyModelId = signal('');
  protected competencyBusy = signal(false);
  protected competencyNotice = signal<{ ok: boolean; text: string } | null>(null);

  protected secondaryClasses = computed(() => this.classes().filter((klass) => klass.level.toLowerCase() === 'secondary'));
  protected secondarySubjects = computed(() => {
    const klass = this.secondaryClasses().find((item) => item.id === this.competencyClassId());
    return this.subjects().filter((subject) => !klass || !subject.subsystem || subject.subsystem === klass.subsystem);
  });

  protected selectedAssignmentClass = computed(() =>
    this.classes().find((c) => c.id === this.assignmentClassId()) ?? null,
  );
  protected selectedAssignmentSubject = computed(() =>
    this.subjects().find((s) => s.id === this.assignmentSubjectId()) ?? null,
  );
  protected assignmentRows = computed<CurriculumSubjectView[]>(() => this.curriculum()?.subjects ?? []);
  protected availableAssignmentSubjects = computed(() => {
    const selectedClass = this.selectedAssignmentClass();
    if (!selectedClass) return [];
    const assigned = new Set(this.assignmentRows().map((row) => row.subjectId));
    return this.subjects()
      .filter((s) => (s.subsystem == null || s.subsystem === selectedClass.subsystem) && !assigned.has(s.id))
      .sort((a, b) => this.subjectLabel(a).localeCompare(this.subjectLabel(b)));
  });

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const requestedSubtab = params.get('subtab');
    if (requestedSubtab === 'sections' || requestedSubtab === 'classes' || requestedSubtab === 'subjects'
      || requestedSubtab === 'class-subjects' || requestedSubtab === 'assessments' || requestedSubtab === 'competencies' || requestedSubtab === 'design') {
      this.sub.set(requestedSubtab === 'competencies' ? 'assessments' : requestedSubtab);
    }
    const requestedSessionId = params.get('sessionId');
    const requestedClassId = params.get('classId');
    if (requestedSessionId) this.curriculumSessionId.set(requestedSessionId);
    if (requestedClassId) this.assignmentClassId.set(requestedClassId);
    this.loadSections();
    this.loadClasses();
    this.loadSubjects();
    this.loadCoefficients();
    this.loadDocumentDesign();
    this.foundation.listSessions().subscribe((rows) => {
      this.academicSessions.set(rows);
      const current = rows.find((s) => s.id === this.curriculumSessionId())
        ?? rows.find((s) => s.current) ?? rows.find((s) => s.status === 'OPEN') ?? rows[0];
      if (current) {
        this.curriculumSessionId.set(current.id);
        this.loadCurriculum();
        this.selectCompetencySession(current.id);
      }
    });
  }

  private loadCoefficients(): void {
    this.api.listCoefficients().subscribe((r) => {
      this.coefRows.set(r.sort((a, b) => a.className.localeCompare(b.className) || a.subjectCode.localeCompare(b.subjectCode)));
      this.syncCoefficientDrafts();
    });
  }

  protected selectAssignmentClass(classId: string): void {
    this.assignmentClassId.set(classId);
    this.assignmentSubjectId.set('');
    this.assignmentCoef.set(1);
    this.pendingCoefficientRemoval.set(null);
    this.assignmentNotice.set(null);
    this.syncCoefficientDrafts();
    const selected = this.classes().find((c) => c.id === classId);
    if (selected) this.api.assignableTeachers(selected.level).subscribe((teachers) => this.allTeachers.set(teachers));
    this.loadCurriculum();
  }

  protected selectCurriculumSession(sessionId: string): void {
    this.curriculumSessionId.set(sessionId);
    this.curriculum.set(null);
    this.assignmentNotice.set(null);
    this.groupNotice.set(null);
    this.loadCurriculum();
  }

  private loadCurriculum(): void {
    const sessionId = this.curriculumSessionId();
    const classId = this.assignmentClassId();
    if (!sessionId || !classId) { this.curriculum.set(null); return; }
    this.api.curriculum(sessionId, classId).subscribe({
      next: (value) => { this.curriculum.set({ ...value, subjects: value.subjects.map((row) => ({ ...row, classId: value.classId, className: value.className, defaultCoef: this.subjects().find((s) => s.id === row.subjectId)?.coef ?? row.coefficient })) }); this.syncCoefficientDrafts(); },
      error: (e) => this.assignmentError(e),
    });
  }

  protected selectAssignmentSubject(subjectId: string): void {
    this.assignmentSubjectId.set(subjectId);
    const subject = this.subjects().find((s) => s.id === subjectId);
    this.assignmentCoef.set(subject?.coef ?? 1);
  }

  protected assignmentSubjectLabel(row: CurriculumSubjectView): string {
    return row.subjectLabel || row.subjectCode;
  }

  protected assignmentKey(row: CurriculumSubjectView): string { return `${this.assignmentClassId()}:${row.subjectId}`; }

  protected draftCoefficient(row: CurriculumSubjectView): number {
    return this.coefficientDrafts[this.assignmentKey(row)] ?? row.coefficient;
  }

  protected setDraftCoefficient(row: CurriculumSubjectView, raw: number | string): void {
    this.coefficientDrafts = { ...this.coefficientDrafts, [this.assignmentKey(row)]: Number(raw) };
  }

  private syncCoefficientDrafts(): void {
    const next: Record<string, number> = {};
    for (const row of this.assignmentRows()) next[this.assignmentKey(row)] = row.coefficient;
    this.coefficientDrafts = next;
  }

  private setAssignmentNotice(ok: boolean, text: string): void {
    this.assignmentNotice.set({ ok, text });
  }

  private assignmentError(e: any): void {
    this.setAssignmentNotice(false, e?.error?.message ?? (this.fr() ? 'Affectation impossible.' : 'Assignment failed.'));
  }

  protected addAssignment(): void {
    const classId = this.assignmentClassId();
    const subjectId = this.assignmentSubjectId();
    const coef = Number(this.assignmentCoef());
    if (!classId || !subjectId || !Number.isInteger(coef) || coef < 1) {
      this.setAssignmentNotice(false, this.fr() ? 'Choisissez une classe, une matière et un coefficient valide.' : 'Choose a class, subject, and valid coefficient.');
      return;
    }
    const sessionId = this.curriculumSessionId();
    if (!sessionId) { this.setAssignmentNotice(false, this.fr() ? 'Choisissez d’abord une session académique.' : 'Choose an academic session first.'); return; }
    this.assignmentNotice.set(null);
    this.api.upsertCurriculumSubject({ academicSessionId: sessionId, classId, subjectId, coefficient: coef }).subscribe({
      next: (row) => {
        this.curriculum.update((current) => current ? { ...current, subjects: [...current.subjects.filter((x) => x.subjectId !== row.subjectId), row].sort((a, b) => a.displayOrder - b.displayOrder) } : current);
        this.assignmentSubjectId.set('');
        this.assignmentCoef.set(1);
        this.setAssignmentNotice(true, this.fr() ? `Matière ${row.subjectCode} ajoutée à ${row.className}.` : `${row.subjectCode} added to ${row.className}.`);
      },
      error: (e) => this.assignmentError(e),
    });
  }

  protected saveAssignment(row: CurriculumSubjectView): void {
    const coef = Number(this.draftCoefficient(row));
    if (!Number.isInteger(coef) || coef < 1) {
      this.setAssignmentNotice(false, this.fr() ? 'Le coefficient doit être un entier supérieur ou égal à 1.' : 'The coefficient must be a whole number of at least 1.');
      return;
    }
    this.api.upsertCurriculumSubject({ academicSessionId: this.curriculumSessionId(), classId: this.assignmentClassId(), subjectId: row.subjectId, groupId: row.groupId, displayOrder: row.displayOrder, coefficient: coef, maxScore: row.maxScore, mandatory: row.mandatory, passThreshold: row.passThreshold, showSubjectRank: row.showSubjectRank, remarkRequired: row.remarkRequired, version: row.version }).subscribe({
      next: (updated) => {
        this.curriculum.update((current) => current ? { ...current, subjects: current.subjects.map((x) => x.subjectId === updated.subjectId ? updated : x) } : current);
        this.setAssignmentNotice(true, this.fr() ? `Coefficient ${updated.subjectCode} enregistré pour ${updated.className}.` : `${updated.subjectCode} coefficient saved for ${updated.className}.`);
      },
      error: (e) => this.assignmentError(e),
    });
  }

  protected askRemoveAssignment(row: CurriculumSubjectView): void {
    this.pendingCoefficientRemoval.set(this.assignmentKey(row));
    this.assignmentNotice.set(null);
  }

  protected removeAssignment(row: CurriculumSubjectView): void {
    this.api.deleteCurriculumSubject(this.curriculumSessionId(), this.assignmentClassId(), row.subjectId).subscribe({
      next: () => {
        this.curriculum.update((current) => current ? { ...current, subjects: current.subjects.filter((currentRow) => currentRow.subjectId !== row.subjectId) } : current);
        this.pendingCoefficientRemoval.set(null);
        this.syncCoefficientDrafts();
        this.setAssignmentNotice(true, this.fr() ? `Matière ${row.subjectCode} retirée de ${row.className}.` : `${row.subjectCode} removed from ${row.className}.`);
      },
      error: (e) => this.assignmentError(e),
    });
  }

  /** Read an Excel/CSV coefficient file (long format) and import it. */
  protected groupLabel(group: SubjectGroupView): string {
    return (this.fr() ? group.label?.['fr'] : group.label?.['en']) || group.label?.['fr'] || group.label?.['en'] || group.code;
  }

  protected setSubjectGroup(row: CurriculumSubjectView, groupId: string): void {
    const group = this.curriculum()?.groups.find((g) => g.id === groupId);
    this.curriculum.update((current) => current ? { ...current, subjects: current.subjects.map((x) => x.subjectId === row.subjectId ? { ...x, groupId: groupId || null, groupCode: group?.code ?? null } : x) } : current);
  }

  protected prepareSubjectTeacher(row: CurriculumSubjectView, employeeId: string): void {
    if (!employeeId || !this.canWrite) return;
    this.assignmentBusy.set(true);
    this.api.assignmentImpactPreview({
      academicSessionId: this.curriculumSessionId(), classId: this.assignmentClassId(), subjectId: row.subjectId,
      employeeId, role: 'RESPONSIBLE',
    }).subscribe({
      next: (impact) => { this.assignmentBusy.set(false); this.pendingAssignment = { role: 'RESPONSIBLE', employeeId, row }; this.assignmentImpact.set(impact); },
      error: (e) => { this.assignmentBusy.set(false); this.assignmentError(e); },
    });
  }

  protected prepareHomeroom(employeeId: string): void {
    if (!employeeId || !this.canWrite) return;
    this.assignmentBusy.set(true);
    this.api.assignmentImpactPreview({
      academicSessionId: this.curriculumSessionId(), classId: this.assignmentClassId(), employeeId, role: 'HOMEROOM',
    }).subscribe({
      next: (impact) => { this.assignmentBusy.set(false); this.pendingAssignment = { role: 'HOMEROOM', employeeId }; this.assignmentImpact.set(impact); },
      error: (e) => { this.assignmentBusy.set(false); this.assignmentError(e); },
    });
  }

  protected cancelAssignmentImpact(): void {
    this.assignmentImpact.set(null);
    this.pendingAssignment = null;
    this.assignmentBusy.set(false);
  }

  protected confirmAssignmentImpact(): void {
    const pending = this.pendingAssignment;
    if (!pending || this.assignmentBusy()) return;
    this.assignmentBusy.set(true);
    if (pending.role === 'RESPONSIBLE' && pending.row) this.saveSubjectTeacher(pending.row, pending.employeeId);
    else this.saveHomeroom(pending.employeeId);
  }

  protected saveSubjectTeacher(row: CurriculumSubjectView, employeeId: string): void {
    if (!employeeId) return;
    this.api.upsertCurriculumTeacher({ academicSessionId: this.curriculumSessionId(), classId: this.assignmentClassId(), subjectId: row.subjectId, employeeId, role: 'RESPONSIBLE', source: 'MANUAL' }).subscribe({
      next: (teacher) => { this.curriculum.update((current) => current ? { ...current, subjects: current.subjects.map((x) => x.subjectId === row.subjectId ? { ...x, responsibleTeacher: teacher } : x) } : current); this.assignmentBusy.set(false); this.cancelAssignmentImpact(); },
      error: (e) => { this.assignmentBusy.set(false); this.assignmentError(e); },
    });
  }

  protected saveHomeroom(employeeId: string): void {
    if (!employeeId || !this.canWrite) return;
    this.api.upsertHomeroom({
      academicSessionId: this.curriculumSessionId(),
      classId: this.assignmentClassId(),
      employeeId,
      version: this.curriculum()?.homeroomTeacher?.version,
    }).subscribe({
      next: (teacher) => { this.curriculum.update((current) => current ? { ...current, homeroomTeacher: teacher } : current); this.assignmentBusy.set(false); this.cancelAssignmentImpact(); },
      error: (e) => { this.assignmentBusy.set(false); this.assignmentError(e); },
    });
  }

  protected createGroup(): void {
    const sessionId = this.curriculumSessionId();
    if (!sessionId || !this.groupCode.trim() || !this.groupFr.trim() || this.groupOrder < 1) {
      this.groupNotice.set({ ok: false, text: this.fr() ? 'Code, libellé et ordre du groupe sont obligatoires.' : 'Group code, label, and order are required.' }); return;
    }
    this.api.createCurriculumGroup({ academicSessionId: sessionId, code: this.groupCode, label: { fr: this.groupFr, en: this.groupEn || this.groupFr }, displayOrder: this.groupOrder, showSubtotal: true, showRank: false, averagePolicy: 'WEIGHTED_COEFFICIENT' }).subscribe({
      next: (group) => { this.curriculum.update((current) => current ? { ...current, groups: [...current.groups, group].sort((a, b) => a.displayOrder - b.displayOrder) } : current); this.groupCode = ''; this.groupFr = ''; this.groupEn = ''; this.groupOrder = (this.curriculum()?.groups.length ?? 0) + 1; this.groupNotice.set({ ok: true, text: this.fr() ? `Groupe ${group.code} créé.` : `Group ${group.code} created.` }); },
      error: (e) => this.groupNotice.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Création du groupe impossible.' : 'Could not create group.') }),
    });
  }

  protected askRemoveGroup(group: SubjectGroupView): void { this.pendingGroupRemoval.set(group.id); }
  protected removeGroup(group: SubjectGroupView): void {
    this.api.deleteCurriculumGroup(group.id).subscribe({ next: () => { this.curriculum.update((current) => current ? { ...current, groups: current.groups.filter((x) => x.id !== group.id), subjects: current.subjects.map((x) => x.groupId === group.id ? { ...x, groupId: null, groupCode: null } : x) } : current); this.pendingGroupRemoval.set(null); }, error: (e) => this.groupNotice.set({ ok: false, text: e?.error?.message ?? (this.fr() ? 'Suppression impossible.' : 'Could not delete group.') }) });
  }

  /** Read an Excel/CSV coefficient file (long format) and import it. */
  protected onCoefFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.coefError.set(null);
    this.coefResult.set(null);
    const isExcel = /\.(xlsx?|xlsm)$/i.test(file.name);
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        let csv: string;
        if (isExcel) {
          const XLSX = await import('xlsx');
          const wb = XLSX.read(reader.result, { type: 'array' });
          // Prefer the importable long sheet, else the first one.
          const name = wb.SheetNames.find((n) => /coef/i.test(n)) ?? wb.SheetNames[0];
          csv = XLSX.utils.sheet_to_csv(wb.Sheets[name]);
        } else {
          csv = String(reader.result ?? '');
        }
        const rows = this.parseCoefRows(csv);
        if (!rows.length) { this.coefError.set(this.fr() ? 'Aucune ligne exploitable.' : 'No usable row.'); input.value = ''; return; }
        this.api.importCoefficients(rows).subscribe({
          next: (res) => { this.coefResult.set(res); this.loadCoefficients(); this.loadSubjects(); },
          error: (e) => this.coefError.set(e?.error?.message ?? (this.fr() ? 'Import impossible.' : 'Import failed.')),
        });
      } catch {
        this.coefError.set(this.fr() ? 'Fichier illisible — vérifiez le format.' : 'Unreadable file — check the format.');
      }
      input.value = '';
    };
    if (isExcel) reader.readAsArrayBuffer(file); else reader.readAsText(file);
  }

  /** Parse the long coefficient layout: Sous-système, Code, Matière, Classe, Coefficient. */
  private parseCoefRows(text: string): CoefImportRow[] {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length);
    if (!lines.length) return [];
    const delim = (lines[0].match(/;/g)?.length ?? 0) > (lines[0].match(/,/g)?.length ?? 0) ? ';' : ',';
    const split = (l: string) => l.split(delim).map((c) => c.replace(/^"|"$/g, '').trim());
    const header = split(lines[0]).map((c) => c.toLowerCase());
    const find = (re: RegExp, dflt: number) => { const i = header.findIndex((h) => re.test(h)); return i < 0 ? dflt : i; };
    const hasHeader = header.some((h) => /sous|coef|classe|mati|code|subsystem|class/.test(h));
    const iSub = find(/sous|subsystem|syst/, 0), iCode = find(/code/, 1), iLabel = find(/mati|subject|nom|label/, 2),
      iClass = find(/classe|class/, 3), iCoef = find(/coef/, 4);
    const body = hasHeader ? lines.slice(1) : lines;
    const out: CoefImportRow[] = [];
    for (const l of body) {
      const c = split(l);
      const coefRaw = (c[iCoef] ?? '').trim();
      const coef = /^\d+$/.test(coefRaw) ? parseInt(coefRaw, 10) : null;
      const sub = (c[iSub] ?? '').trim().toUpperCase();
      const code = (c[iCode] ?? '').trim();
      const klass = (c[iClass] ?? '').trim();
      if (!code && !klass) continue;
      out.push({ subsystem: sub, code, label: (c[iLabel] ?? '').trim(), klass, coef });
    }
    return out;
  }

  protected switchTo(t: 'sections' | 'classes' | 'subjects' | 'class-subjects' | 'assessments' | 'competencies' | 'design'): void {
    this.sub.set(t === 'competencies' ? 'assessments' : t);
    this.secForm.set(false); this.clsForm.set(false); this.subjForm.set(false);
    this.assignmentNotice.set(null);
  }

  private loadDocumentDesign(): void {
    this.foundation.documentDesign().subscribe({ next: (design) => this.documentDesign.set(design), error: (error) => this.fail(error) });
  }

  protected selectCompetencySession(sessionId: string): void {
    this.competencySessionId.set(sessionId);
    this.competencyPeriodId.set('');
    this.competencyModels.set([]);
    this.competencyModelId.set('');
    if (!sessionId) { this.competencyPeriods.set([]); return; }
    this.foundation.reportingPeriods(sessionId).subscribe({
      next: (periods) => {
        this.competencyPeriods.set(periods);
        const first = periods.find((period) => period.code === 'S1') ?? periods[0];
        if (first) { this.competencyPeriodId.set(first.id); this.loadCompetencyModels(); }
      },
      error: (error) => this.fail(error),
    });
  }

  protected selectCompetencyPeriod(periodId: string): void { this.competencyPeriodId.set(periodId); this.loadCompetencyModels(); }
  protected selectCompetencyClass(classId: string): void {
    this.competencyClassId.set(classId);
    const klass = this.secondaryClasses().find((item) => item.id === classId);
    if (klass && this.competencyLocale() !== (klass.subsystem === 'EN' ? 'en' : 'fr')) this.competencyLocale.set(klass.subsystem === 'EN' ? 'en' : 'fr');
    this.competencySubjectId.set('');
    this.loadCompetencyModels();
  }
  protected selectCompetencySubject(subjectId: string): void { this.competencySubjectId.set(subjectId); this.loadCompetencyModels(); }

  protected loadCompetencyModels(): void {
    const reportingPeriodId = this.competencyPeriodId();
    const classId = this.competencyClassId();
    const subjectId = this.competencySubjectId();
    if (!reportingPeriodId || !classId || !subjectId) { this.competencyModels.set([]); this.competencyModelId.set(''); return; }
    this.academicApi.secondaryCompetencyModels({ reportingPeriodId, classId, subjectId, locale: this.competencyLocale() }).subscribe({
      next: (models) => {
        this.competencyModels.set(models);
        const selected = models.find((model) => model.status === 'DRAFT') ?? models.find((model) => model.status === 'PUBLISHED') ?? models[0];
        this.competencyModelId.set(selected?.id ?? '');
      },
      error: (error) => this.fail(error),
    });
  }

  protected addCompetencyRow(): void { this.competencyRows = [...this.competencyRows, { code: '', description: '', maxScore: 20 }]; }
  protected removeCompetencyRow(index: number): void { if (this.competencyRows.length > 1) this.competencyRows = this.competencyRows.filter((_, i) => i !== index); }

  protected saveCompetencyModel(): void {
    const sessionId = this.competencySessionId();
    const reportingPeriodId = this.competencyPeriodId();
    const classId = this.competencyClassId();
    const subjectId = this.competencySubjectId();
    const rows = this.competencyRows.map((row, index) => ({ code: row.code.trim(), description: row.description.trim(), maxScore: Number(row.maxScore), displayOrder: index + 1 }));
    if (!this.canWrite || !sessionId || !reportingPeriodId || !classId || !subjectId || !this.competencyName.trim() || rows.some((row) => !row.code || !row.description || !Number.isFinite(row.maxScore) || row.maxScore <= 0)) {
      this.competencyNotice.set({ ok: false, text: this.fr() ? 'Renseignez tous les champs obligatoires du modèle.' : 'Complete all required model fields.' });
      return;
    }
    this.competencyBusy.set(true);
    this.academicApi.createSecondaryCompetencyModel({ academicSessionId: sessionId, reportingPeriodId, classId, subjectId, locale: this.competencyLocale(), name: this.competencyName.trim(), competencies: rows }).subscribe({
      next: () => { this.competencyBusy.set(false); this.competencyNotice.set({ ok: true, text: this.fr() ? 'Brouillon de modèle enregistré. Publiez-le pour l’utiliser dans un bulletin.' : 'Model draft saved. Publish it before using it in a report card.' }); this.loadCompetencyModels(); },
      error: (error) => { this.competencyBusy.set(false); this.fail(error); },
    });
  }

  protected publishCompetencyModel(model: SecondaryCompetencyModelView): void {
    if (!this.canWrite || this.competencyBusy()) return;
    this.competencyBusy.set(true);
    this.academicApi.publishSecondaryCompetencyModel(model.id, this.fr() ? 'Publication du modèle de compétences secondaire' : 'Publish secondary competency model').subscribe({
      next: () => { this.competencyBusy.set(false); this.competencyNotice.set({ ok: true, text: this.fr() ? 'Modèle publié. Les nouveaux snapshots utiliseront cette version.' : 'Model published. New snapshots will use this version.' }); this.loadCompetencyModels(); },
      error: (error) => { this.competencyBusy.set(false); this.fail(error); },
    });
  }

  protected onCompetencyMarksFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const modelId = this.competencyModelId();
    const reportingPeriodId = this.competencyPeriodId();
    if (!file || !modelId || !reportingPeriodId) { this.competencyNotice.set({ ok: false, text: this.fr() ? 'Choisissez une version de modèle avant l’import.' : 'Choose a model version before importing.' }); return; }
    const reader = new FileReader();
    reader.onload = () => {
      const lines = String(reader.result ?? '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
      if (!lines.length) { this.competencyNotice.set({ ok: false, text: this.fr() ? 'Le fichier CSV est vide.' : 'The CSV file is empty.' }); return; }
      const cells = (line: string) => line.split(/[;,]/).map((value) => value.trim());
      const header = cells(lines[0]).map((value) => value.toLowerCase());
      const index = (name: string, fallback: number) => { const found = header.indexOf(name); return found >= 0 ? found : fallback; };
      const studentIndex = index('studentid', 0), competencyIndex = index('competencycode', 1), markIndex = index('mark', 2), statusIndex = index('valuestatus', 3);
      const data = (header.includes('studentid') || header.includes('competencycode')) ? lines.slice(1) : lines;
      const rows = data.map((line) => { const row = cells(line); const rawMark = row[markIndex] ?? ''; return { studentId: row[studentIndex] ?? '', competencyCode: row[competencyIndex] ?? '', mark: rawMark === '' ? null : Number(rawMark), valueStatus: row[statusIndex] || undefined }; }).filter((row) => row.studentId && row.competencyCode);
      if (!rows.length || rows.some((row) => row.mark !== null && !Number.isFinite(row.mark))) { this.competencyNotice.set({ ok: false, text: this.fr() ? 'Aucune ligne valide trouvée. Utilisez studentId, competencyCode, mark, valueStatus.' : 'No valid rows found. Use studentId, competencyCode, mark, valueStatus.' }); return; }
      this.competencyBusy.set(true);
      this.academicApi.importSecondaryCompetencyMarks({ modelId, reportingPeriodId, rows }).subscribe({
        next: (saved) => { this.competencyBusy.set(false); this.competencyNotice.set({ ok: true, text: `${saved.length} ${this.fr() ? 'note(s) importée(s).' : 'mark(s) imported.'}` }); input.value = ''; },
        error: (error) => { this.competencyBusy.set(false); this.fail(error); },
      });
    };
    reader.readAsText(file);
  }

  protected openDesignPublish(kind: 'template' | 'branding', id: string | undefined, label: string): void {
    this.designPublish.set({ kind, id, label });
    this.designReason = '';
  }

  protected cancelDesignPublish(): void {
    this.designPublish.set(null);
    this.designReason = '';
  }

  protected confirmDesignPublish(): void {
    const request = this.designPublish();
    const reason = this.designReason.trim();
    if (!request || !reason || !this.canWrite) return;
    this.designBusy.set(true);
    const complete = () => { this.designBusy.set(false); this.cancelDesignPublish(); this.loadDocumentDesign(); };
    const failed = (error: any) => { this.designBusy.set(false); this.fail(error); };
    if (request.kind === 'template' && request.id) {
      this.foundation.publishDocumentTemplate(request.id, reason).subscribe({ next: complete, error: failed });
    } else {
      this.foundation.publishDocumentBranding('fr', reason).subscribe({ next: complete, error: failed });
    }
  }

  protected levelLabel(l: string): string {
    switch ((l || '').toLowerCase()) {
      case 'maternelle': return this.fr() ? 'Maternelle' : 'Kindergarten';
      case 'secondary': return this.fr() ? 'Secondaire' : 'Secondary';
      default: return this.fr() ? 'Primaire' : 'Primary';
    }
  }
  protected designTemplateTypeLabel(template: DocumentDesignView['templates'][number]): string {
    switch ((template.type || '').toUpperCase()) {
      case 'REPORT_CARD': return this.fr() ? 'Bulletin scolaire' : 'Report card';
      case 'ENROLLMENT_CERTIFICATE': return this.fr() ? 'Certificat de scolarité' : 'Enrollment certificate';
      default: return template.type || (this.fr() ? 'Document' : 'Document');
    }
  }
  protected designProductLabel(template: DocumentDesignView['templates'][number]): string {
    switch ((template.product || '').toUpperCase()) {
      case 'SEQUENCE': return this.fr() ? 'Bulletin de séquence' : 'Sequence report card';
      case 'TERM': return this.fr() ? 'Résultat trimestriel' : 'Term result';
      case 'ANNUAL': return this.fr() ? 'Résultat annuel' : 'Annual result';
      default: return this.fr() ? 'Document générique' : 'Generic document';
    }
  }
  protected designLocaleLabel(locale: string | null | undefined): string {
    return (locale || '').toLowerCase() === 'en'
      ? (this.fr() ? 'Anglais' : 'English')
      : (this.fr() ? 'Français' : 'French');
  }
  protected designSubsystemLabel(subsystem: string | null | undefined): string {
    const value = (subsystem || '').toUpperCase();
    if (value === 'PRI' || value === 'PRIMARY') return this.fr() ? 'Primaire' : 'Primary';
    if (value === 'SEC' || value === 'SECONDARY') return this.fr() ? 'Secondaire' : 'Secondary';
    return this.fr() ? 'Tous niveaux' : 'All levels';
  }
  protected designFamilyLabel(family: string | null | undefined): string {
    const value = (family || '').toUpperCase();
    if (value === 'REFERENCE') return this.fr() ? 'Modèle de référence' : 'Reference template';
    if (value === 'GENERIC') return this.fr() ? 'Modèle générique' : 'Generic template';
    if (value === 'SECONDARY') return this.fr() ? 'Modèle secondaire' : 'Secondary template';
    return family || (this.fr() ? 'Modèle' : 'Template');
  }
  protected designStatusLabel(status: string | null | undefined): string {
    switch ((status || '').toUpperCase()) {
      case 'PUBLISHED': return this.fr() ? 'Publiée' : 'Published';
      case 'RETIRED': return this.fr() ? 'Retirée' : 'Retired';
      case 'DRAFT': return this.fr() ? 'Brouillon' : 'Draft';
      default: return status || (this.fr() ? 'Inconnue' : 'Unknown');
    }
  }
  protected subjectLabel(s: SubjectView): string {
    const l = s.label || {};
    return (this.fr() ? l['fr'] : l['en']) || l['fr'] || l['en'] || s.code;
  }

  private fail = (e: any) => {
    const msg = e?.error?.message;
    if (msg && typeof msg === 'object') {
      this.err.set(Object.values(msg).join(' · '));
    } else {
      this.err.set(msg ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'));
    }
  };
  private loadSections(): void { this.api.listSections().subscribe((r) => this.sections.set(r)); }
  private loadClasses(): void { this.api.listClasses().subscribe((r) => this.classes.set(r)); }
  private loadSubjects(): void { this.api.listSubjects().subscribe((r) => this.subjects.set(r)); }

  // ---- Sections ----
  protected newSection(): void {
    this.secEditId.set(null);
    const s = this.activeScope();
    this.secDraft = {
      label: '',
      subsystem: s?.subsystem ?? 'FR',
      level: s?.level ?? 'primary',
    };
    this.secForm.set(true);
  }
  protected editSection(s: SectionView): void { this.secEditId.set(s.id); this.secDraft = { label: s.label, subsystem: s.subsystem, level: s.level }; this.secForm.set(true); }
  protected saveSection(): void {
    this.err.set(null);
    const id = this.secEditId();
    const req = id ? this.api.updateSection(id, this.secDraft) : this.api.createSection(this.secDraft);
    req.subscribe({ next: () => { this.secForm.set(false); this.loadSections(); }, error: this.fail });
  }
  protected deleteSection(s: SectionView): void {
    if (!confirm(this.fr() ? `Supprimer la section « ${s.label} » ?` : `Delete section "${s.label}"?`)) return;
    this.err.set(null);
    this.api.deleteSection(s.id).subscribe({ next: () => this.loadSections(), error: this.fail });
  }

  // ---- Classes ----
  protected newClass(): void { this.clsEditId.set(null); this.clsDraft = { name: '', sectionId: this.sections()[0]?.id ?? '' }; this.clsForm.set(true); }
  protected editClass(c: ClassView): void { this.clsEditId.set(c.id); this.clsDraft = { name: c.name, sectionId: c.sectionId }; this.clsForm.set(true); }
  protected saveClass(): void {
    this.err.set(null);
    const id = this.clsEditId();
    const req = id ? this.api.updateClass(id, this.clsDraft) : this.api.createClass(this.clsDraft);
    req.subscribe({ next: () => { this.clsForm.set(false); this.loadClasses(); this.loadSections(); }, error: this.fail });
  }
  protected deleteClass(c: ClassView): void {
    if (!confirm(this.fr() ? `Supprimer la classe « ${c.name} » ?` : `Delete class "${c.name}"?`)) return;
    this.err.set(null);
    this.api.deleteClass(c.id).subscribe({ next: () => { this.loadClasses(); this.loadSections(); }, error: this.fail });
  }

  /** Sample CSV so admins know the expected columns when preparing a class list offline. */
  protected downloadClassTemplate(): void {
    downloadCsv('modele-classes.csv',
      ['nom', 'section'],
      [['6ème A', 'Primaire francophone'], ['Form 1', 'Primary English']]);
  }

  // ---- Teacher assignment ----
  protected openTeachers(c: ClassView): void {
    this.err.set(null);
    this.teacherClass.set(c);
    // Seuls les enseignants de la section de la classe sont proposés : un prof du
    // primaire n'a rien à faire dans le sélecteur d'une classe du secondaire.
    this.api.assignableTeachers(c.level).subscribe((t) => this.allTeachers.set(t));
    this.api.classTeachers(c.id).subscribe((t) => this.picked.set(new Set(t.map((x) => x.id))));
  }
  protected toggleTeacher(id: string): void {
    const next = new Set(this.picked());
    next.has(id) ? next.delete(id) : next.add(id);
    this.picked.set(next);
  }
  protected saveTeachers(): void {
    const c = this.teacherClass();
    if (!c) return;
    this.err.set(null);
    this.api.setClassTeachers(c.id, [...this.picked()]).subscribe({
      next: () => { this.teacherClass.set(null); this.loadClasses(); },
      error: this.fail,
    });
  }

  // ---- Subjects ----
  protected newSubject(): void {
    this.subjEditId.set(null);
    this.subjCode = ''; this.subjFr = ''; this.subjEn = ''; this.subjCoef = 1;
    // Default the new subject's subsystem to whichever list is being viewed.
    this.subjSub = this.subjFilter() === 'EN' ? 'EN' : 'FR';
    this.subjForm.set(true);
  }
  protected editSubject(s: SubjectView): void {
    this.subjEditId.set(s.id); this.subjCode = s.code;
    this.subjSub = s.subsystem ?? '';
    this.subjFr = s.label?.['fr'] ?? ''; this.subjEn = s.label?.['en'] ?? ''; this.subjCoef = s.coef; this.subjForm.set(true);
  }
  protected saveSubject(): void {
    this.err.set(null);
    const body: SubjectUpsert = {
      code: this.subjCode,
      subsystem: this.subjSub || null,
      label: { fr: this.subjFr, en: this.subjEn },
      coef: this.subjCoef || 1,
    };
    const id = this.subjEditId();
    const req = id ? this.api.updateSubject(id, body) : this.api.createSubject(body);
    req.subscribe({ next: () => { this.subjForm.set(false); this.loadSubjects(); }, error: this.fail });
  }
  protected deleteSubject(s: SubjectView): void {
    if (!confirm(this.fr() ? `Supprimer la matière « ${s.code} » ?` : `Delete subject "${s.code}"?`)) return;
    this.err.set(null);
    this.api.deleteSubject(s.id).subscribe({ next: () => this.loadSubjects(), error: this.fail });
  }

  /** Bulk-create the standard subject list for the active subsystem (skips existing codes). */
  protected importDefaults(): void {
    const sub = this.subjFilter();
    if (sub !== 'FR' && sub !== 'EN') return;
    this.err.set(null);
    const existing = new Set(
      this.subjects().filter((s) => s.subsystem === sub).map((s) => s.code.toUpperCase()),
    );
    const toCreate = defaultSubjects(sub).filter((d) => !existing.has(d.code.toUpperCase()));
    if (!toCreate.length) return;
    const reqs = toCreate.map((d) =>
      this.api.createSubject({ code: d.code, subsystem: sub, label: { fr: d.fr, en: d.en }, coef: 1 }),
    );
    forkJoin(reqs).subscribe({ next: () => this.loadSubjects(), error: this.fail });
  }
}
