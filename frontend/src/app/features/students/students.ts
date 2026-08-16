import { Component, ChangeDetectionStrategy, inject, signal, computed, effect } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { StudentApi, StudentUpsert, ParentAccountView, ParentLinkRequest, StudentImportRow, StudentImportRequest, StudentImportResult } from './students.api';
import { ClassView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { ScopeService } from '../../core/scope.service';
import { Student } from '../../core/models';
import { downloadCsv, stampedName } from '../../core/csv';
import {
  IconComponent, CardComponent, PageHeaderComponent,
  AvatarComponent, ChipFilterComponent, StatusPillComponent,
  DataTableComponent, CellTemplateDirective, Column, PhotoCaptureComponent,
} from '../../core/ui';
import { PhotoApi } from '../../core/photo.api';
import { StudentEnrollmentPanelComponent } from './student-enrollment-panel';

/** Column index per field of an import file; -1 when the column is absent. */
interface HeaderMap {
  niu: number; name: number; lastName: number; firstName: number; sex: number;
  dob: number; birthplace: number; repeats: number;
  parentName: number; parentPhone: number;
  fatherName: number; fatherPhone: number; fatherEmail: number;
  motherName: number; motherPhone: number; motherEmail: number;
  guardianName: number; guardianRelation: number; guardianPhone: number; guardianEmail: number;
}

@Component({
  selector: 'bbc-students',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent,
    AvatarComponent, ChipFilterComponent, StatusPillComponent,
    DataTableComponent, CellTemplateDirective, PhotoCaptureComponent, StudentEnrollmentPanelComponent,
  ],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="i18n.t('students')" [subtitle]="headerSub()">
        <div right class="flex items-center gap-2">
          @if (mode() === 'list') {
            <button (click)="exportList()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter liste' : 'Export list' }}
            </button>
            @if (canWrite) {
              <button (click)="downloadStudentTemplate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Modèle import' : 'Import template' }}
              </button>
              <button (click)="openImport()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Importer' : 'Import' }}
              </button>
              <button (click)="openCreate()"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ i18n.t('newStudent') }}
              </button>
            }
          }
        </div>
      </bbc-page-header>

      @if (mode() === 'list') {
        <!-- Toolbar / filters -->
        <bbc-card className="mb-5">
          <div class="flex items-center gap-3 flex-wrap">
            <div class="relative">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
              <input [ngModel]="search()" (ngModelChange)="search.set($event)"
                [placeholder]="fr() ? 'Rechercher un élève, matricule, parent…' : 'Search student, ID, parent…'"
                class="h-9 w-72 pl-9 pr-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
            </div>
            @if (!activeScope()) {
              <bbc-chip-filter [options]="subOptions()" [value]="subFilter()" (change)="onSubFilter($event)"
                [allLabel]="fr() ? 'Tous systèmes' : 'All systems'" />
              <bbc-chip-filter [options]="levelOptions()" [value]="levelFilter()" (change)="onLevelFilter($event)"
                [allLabel]="fr() ? 'Tous niveaux' : 'All levels'" />
            } @else {
              <span class="text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-brand-50 text-brand-700 border border-brand-100">
                {{ scopeChip() }}
              </span>
            }
            @if (listClassOptions().length) {
              <select [ngModel]="classFilter()" (ngModelChange)="classFilter.set($event)"
                class="h-9 min-w-[11rem] px-3 rounded-lg border border-slate-200 bg-white text-sm focus:outline-none focus:border-brand-400">
                <option [ngValue]="null">{{ fr() ? 'Toutes classes' : 'All classes' }}</option>
                @for (g of listClassGroups(); track g.key) {
                  <optgroup [label]="g.label">
                    @for (c of g.classes; track c.id) {
                      <option [ngValue]="c.id">{{ c.name }}{{ c.studentCount ? ' · ' + c.studentCount : '' }}</option>
                    }
                  </optgroup>
                }
              </select>
            }
          </div>
        </bbc-card>

        <!-- High-density data table -->
        <bbc-card className="mb-5 overflow-hidden">
          <div class="-m-5">
            <div class="flex items-center justify-between px-5 py-3 border-b border-slate-100">
              <div class="text-sm font-semibold">{{ filtered().length }} {{ fr() ? 'résultats' : 'results' }}</div>
              <div class="text-xs text-mute">{{ fr() ? 'Cliquez une ligne pour ouvrir la fiche' : 'Click a row to open the profile' }}</div>
            </div>
            <bbc-data-table [columns]="columns()" [rows]="filtered()"
              [trackBy]="trackId" [activeId]="selectedId()"
              [emptyLabel]="fr() ? 'Aucun résultat' : 'No results'"
              (rowClick)="openDetails($event)">

              <ng-template bbcCell="name" let-s>
                <div class="flex items-center gap-3">
                  <bbc-avatar [name]="s.name" [hue]="s.photoHue" [size]="34" />
                  <div class="min-w-0">
                    <div class="font-semibold text-ink truncate">{{ s.name }}</div>
                    <div class="text-[11px] text-mute font-mono">{{ s.matricule }}</div>
                  </div>
                </div>
              </ng-template>

              <ng-template bbcCell="className" let-s>
                <span class="font-medium">{{ s.className || '—' }}</span>
              </ng-template>

              <ng-template bbcCell="subsystem" let-s>
                <span class="text-xs font-semibold px-2 py-0.5 rounded-full"
                  [class]="(s.subsystem || '').toUpperCase().startsWith('F') ? 'bg-sky-100 text-sky-700' : 'bg-violet-100 text-violet-700'">
                  {{ subsystemLabel(s.subsystem) }}
                </span>
              </ng-template>

              <ng-template bbcCell="level" let-s>{{ levelLabel(s.level) }}</ng-template>

              <ng-template bbcCell="sex" let-s>
                @if (s.sex) { <bbc-status-pill status="ok" [label]="sexLabel(s.sex)" /> } @else { — }
              </ng-template>

              <ng-template bbcCell="parent" let-s>
                @if (s.parentName) {
                  <div class="min-w-0">
                    <div class="text-ink truncate">{{ s.parentName }}</div>
                    @if (s.parentPhone) { <div class="text-[11px] text-mute">{{ s.parentPhone }}</div> }
                  </div>
                } @else {
                  <span class="text-mute">{{ fr() ? 'Non renseigné' : 'None' }}</span>
                }
              </ng-template>
            </bbc-data-table>
          </div>
        </bbc-card>

        <!-- Detail panel -->
        @if (selected(); as sel) {
          <bbc-card className="overflow-hidden">
           <div class="-m-5">
            <div class="p-6 bg-gradient-to-br from-brand-700 to-brand-800 text-white rounded-t-xl2">
              <div class="flex items-start gap-4">
                <bbc-avatar [name]="sel.name" [hue]="sel.photoHue" [size]="64" [photoUrl]="selectedPhoto()" />
                <div class="flex-1 min-w-0">
                  <div class="text-[10px] uppercase tracking-wider text-gold-200 font-semibold">{{ sel.matricule }}</div>
                  <div class="text-xl font-bold leading-tight">{{ sel.name }}</div>
                  <div class="text-sm text-brand-100">{{ sel.className }} · {{ subsystemLabel(sel.subsystem) }} · {{ sexLabel(sel.sex) }}</div>
                </div>
                @if (canWrite) {
                  <div class="flex flex-col gap-1.5">
                    <button (click)="openEdit(sel)"
                      class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-gold-400 hover:bg-gold-500 text-brand-800">
                      <bbc-icon name="edit" [s]="14" /> {{ fr() ? 'Modifier' : 'Edit' }}
                    </button>
                    <button (click)="confirmDel.set(sel)" class="text-xs text-rose-200 hover:text-white px-2 py-1">
                      {{ fr() ? 'Supprimer' : 'Delete' }}
                    </button>
                  </div>
                }
              </div>
            </div>

            <div class="p-6 space-y-5">
              <bbc-student-enrollment-panel [student]="sel" [classes]="classes()" />

              <!-- Parent -->
              <div>
                <div class="text-[11px] uppercase tracking-wider text-mute font-semibold mb-2">{{ fr() ? 'Informations parent' : 'Parent info' }}</div>
                @if (sel.parentName) {
                  <div class="flex items-center gap-3 p-3 rounded-lg bg-slate-50">
                    <bbc-avatar [name]="sel.parentName" [hue]="(sel.photoHue + 120) % 360" [size]="40" />
                    <div class="flex-1 min-w-0">
                      <div class="font-semibold text-ink">{{ sel.parentName }}</div>
                      @if (sel.parentPhone) {
                        <div class="text-xs text-mute flex items-center gap-1 mt-0.5">
                          <bbc-icon name="phone" [s]="12" /> {{ sel.parentPhone }}
                        </div>
                      }
                    </div>
                    @if (sel.parentPhone) {
                      <button class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                        <bbc-icon name="send" [s]="12" /> SMS
                      </button>
                    }
                  </div>
                } @else {
                  <div class="text-sm text-mute p-3 rounded-lg bg-slate-50">{{ fr() ? 'Aucun parent renseigné' : 'No parent on file' }}</div>
                }
              </div>

              <!-- Parent login accounts (review #2) -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <div class="text-[11px] uppercase tracking-wider text-mute font-semibold">{{ fr() ? 'Comptes parents' : 'Parent accounts' }}</div>
                  @if (canWrite && !parentForm()) {
                    <button (click)="openParentForm()" class="inline-flex items-center gap-1.5 h-7 px-2.5 text-[11px] font-semibold rounded-lg bg-brand-50 text-brand-700 hover:bg-brand-100">
                      <bbc-icon name="plus" [s]="13" /> {{ fr() ? 'Ajouter' : 'Add' }}
                    </button>
                  }
                </div>

                @for (p of parents(); track p.userId) {
                  <div class="flex items-center gap-3 p-2.5 rounded-lg bg-slate-50 mb-1.5">
                    <div class="w-8 h-8 rounded-full bg-brand-100 text-brand-700 flex items-center justify-center text-xs font-bold shrink-0">
                      <bbc-icon name="users" [s]="14" />
                    </div>
                    <div class="flex-1 min-w-0">
                      <div class="font-semibold text-ink text-sm truncate">{{ p.displayName }}</div>
                      <div class="text-[11px] text-mute">
                        {{ fr() ? 'Identifiant' : 'Login' }}: <span class="font-mono">{{ p.username }}</span>
                        @if (p.childCount > 1) { · {{ p.childCount }} {{ fr() ? 'enfants' : 'children' }} }
                        @if (!p.active) { · <span class="text-rose-600">{{ fr() ? 'inactif' : 'inactive' }}</span> }
                      </div>
                    </div>
                    @if (canWrite) {
                      <button (click)="unlinkParent(p)" class="text-mute hover:text-rose-600 px-1.5" title="{{ fr() ? 'Détacher' : 'Unlink' }}"><bbc-icon name="trash" [s]="14" /></button>
                    }
                  </div>
                } @empty {
                  @if (!parentForm()) {
                    <div class="text-xs text-mute p-2.5 rounded-lg bg-slate-50">{{ fr() ? 'Aucun compte parent — ajoutez-en un pour activer le portail parent.' : 'No parent account — add one to enable the parent portal.' }}</div>
                  }
                }

                @if (parentForm()) {
                  <form (ngSubmit)="linkParent()" class="p-3 rounded-lg bg-slate-50 space-y-2.5 mt-1">
                    <input [(ngModel)]="parentDraft.displayName" name="pdisplay" required [placeholder]="fr() ? 'Nom complet du parent' : 'Parent full name'"
                      class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <input [(ngModel)]="parentDraft.username" name="puser" required autocomplete="off" [placeholder]="fr() ? 'Identifiant de connexion' : 'Login username'"
                      class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <input [(ngModel)]="parentDraft.password" name="ppass" type="password" autocomplete="new-password" [placeholder]="fr() ? 'Mot de passe (si nouveau compte)' : 'Password (if new account)'"
                      class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    <div class="text-[11px] text-mute">{{ fr() ? 'Un identifiant existant relie un parent à plusieurs enfants.' : 'An existing username links one parent to several children.' }}</div>
                    @if (parentErr(); as e) { <div class="text-[11px] text-rose-600">{{ e }}</div> }
                    <div class="flex items-center justify-end gap-2">
                      <button type="button" (click)="parentForm.set(false)" class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">{{ i18n.t('cancel') }}</button>
                      <button type="submit" [disabled]="!parentDraft.displayName || !parentDraft.username" class="h-8 px-4 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white">{{ fr() ? 'Enregistrer' : 'Save' }}</button>
                    </div>
                  </form>
                }
              </div>

              <!-- Info -->
              <div>
                <div class="text-[11px] uppercase tracking-wider text-mute font-semibold mb-2">{{ fr() ? 'Informations' : 'Information' }}</div>
                <div class="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Matricule' : 'Student ID' }}</div>
                    <div class="font-semibold text-ink text-sm font-mono">{{ sel.matricule }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">NIU</div>
                    <div class="font-semibold text-ink text-sm font-mono">{{ sel.niu || '—' }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Date de naissance' : 'Date of birth' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ dobLabel(sel.dob) }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Lieu de naissance' : 'Birthplace' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sel.birthplace || '—' }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Classe' : 'Class' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sel.className || '—' }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Section' : 'Section' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sectionLabel(sel) }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Redouble' : 'Repeats year' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sel.repeats ? (fr() ? 'Oui' : 'Yes') : (fr() ? 'Non' : 'No') }}</div>
                  </div>
                </div>
              </div>
            </div>
           </div>
          </bbc-card>
        }
      } @else if (mode() === 'edit') {
        <!-- Full-page entity form (replaces the create/edit modal) -->
        <form (ngSubmit)="save()">
          <bbc-card>
            <div class="flex items-center gap-3 pb-4 mb-4 border-b border-slate-100">
              <button type="button" (click)="closeEditor()"
                class="w-9 h-9 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-ink">
                <bbc-icon name="chevronLeft" [s]="18" />
              </button>
              <div class="flex-1">
                <div class="text-[17px] font-bold text-ink font-display">
                  {{ editId() ? (fr() ? 'Modifier l’élève' : 'Edit student') : (fr() ? 'Nouvel élève' : 'New student') }}
                </div>
                <div class="text-xs text-mute">{{ fr() ? 'Renseignez la fiche complète de l’élève.' : 'Fill in the full student record.' }}</div>
              </div>
            </div>

            <div class="space-y-8 max-w-3xl">
              <!-- Photo : selfie ou fichier, envoyée après l'enregistrement de la fiche -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Photo' : 'Photo' }}</div>
                <bbc-photo-capture [(photo)]="photoDraft"
                  [label]="fr() ? 'Photo de l’élève' : 'Student photo'" />
              </section>

              <!-- Identity -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Identité' : 'Identity' }}</div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Last name' }} *</span>
                    <input [(ngModel)]="draft.lastName" name="lastName" required
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Prénom' : 'First name' }} *</span>
                    <input [(ngModel)]="draft.firstName" name="firstName" required
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sexe' : 'Sex' }}</span>
                    <select [(ngModel)]="draft.sex" name="sex"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                      <option value="M">{{ fr() ? 'Masculin' : 'Male' }}</option>
                      <option value="F">{{ fr() ? 'Féminin' : 'Female' }}</option>
                    </select>
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Date de naissance' : 'Date of birth' }}</span>
                    <input type="date" [(ngModel)]="draft.dob" name="dob"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Lieu de naissance' : 'Birthplace' }}</span>
                    <input [(ngModel)]="draft.birthplace" name="birthplace"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">NIU</span>
                    <input [(ngModel)]="draft.niu" name="niu" placeholder="{{ fr() ? 'Identifiant unique (facultatif)' : 'Unique ID (optional)' }}"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 font-mono focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="flex items-center gap-2 mt-6">
                    <input type="checkbox" [(ngModel)]="draft.repeats" name="repeats"
                      class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Redouble cette année' : 'Repeating this year' }}</span>
                  </label>
                </div>
              </section>

              <!-- Schooling — class is a locked dropdown bound to a real class (review #1) -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Scolarité' : 'Schooling' }}</div>
                @if (classes().length) {
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Classe' : 'Class' }}</span>
                      <select [(ngModel)]="draft.classId" name="classId"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                        <option [ngValue]="null">{{ fr() ? '— Non affecté —' : '— Unassigned —' }}</option>
                        @for (c of classes(); track c.id) {
                          <option [ngValue]="c.id">{{ c.name }} · {{ c.sectionLabel }}</option>
                        }
                      </select>
                      <span class="text-[11px] text-mute mt-1 block">
                        {{ fr() ? 'Sous-système et niveau sont déduits de la classe.' : 'Subsystem and level are derived from the class.' }}
                      </span>
                    </label>
                  </div>
                } @else {
                  <div class="text-sm text-mute p-3 rounded-lg bg-amber-50 border border-amber-100">
                    {{ fr()
                      ? 'Aucune classe n’est encore définie. Créez d’abord vos classes dans Paramètres → Scolarité.'
                      : 'No classes are defined yet. Create your classes first in Settings → Academics.' }}
                  </div>
                }
              </section>

              <!-- Parents / guardian -->
              <section class="space-y-5">
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold">{{ fr() ? 'Famille / tuteur' : 'Family / guardian' }}</div>

                <div class="rounded-lg border border-slate-100 p-4">
                  <div class="text-xs font-bold text-ink mb-3">{{ fr() ? 'Père' : 'Father' }}</div>
                  <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Name' }}</span>
                      <input [(ngModel)]="draft.fatherName" name="fatherName" (ngModelChange)="syncParentDisplay()"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                      <input [(ngModel)]="draft.fatherPhone" name="fatherPhone" (ngModelChange)="syncParentDisplay()" placeholder="+237 6XX XX XX XX"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">E-mail</span>
                      <input [(ngModel)]="draft.fatherEmail" name="fatherEmail" type="email"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                  </div>
                </div>

                <div class="rounded-lg border border-slate-100 p-4">
                  <div class="text-xs font-bold text-ink mb-3">{{ fr() ? 'Mère' : 'Mother' }}</div>
                  <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Name' }}</span>
                      <input [(ngModel)]="draft.motherName" name="motherName" (ngModelChange)="syncParentDisplay()"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                      <input [(ngModel)]="draft.motherPhone" name="motherPhone" (ngModelChange)="syncParentDisplay()" placeholder="+237 6XX XX XX XX"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">E-mail</span>
                      <input [(ngModel)]="draft.motherEmail" name="motherEmail" type="email"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                  </div>
                </div>

                <div class="rounded-lg border border-slate-100 p-4">
                  <div class="text-xs font-bold text-ink mb-3">{{ fr() ? 'Tuteur' : 'Guardian' }}</div>
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom' : 'Name' }}</span>
                      <input [(ngModel)]="draft.guardianName" name="guardianName" (ngModelChange)="syncParentDisplay()"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Lien / relation' : 'Relation' }}</span>
                      <input [(ngModel)]="draft.guardianRelation" name="guardianRelation" [placeholder]="fr() ? 'Oncle, grand-mère…' : 'Uncle, grandmother…'"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone' : 'Phone' }}</span>
                      <input [(ngModel)]="draft.guardianPhone" name="guardianPhone" (ngModelChange)="syncParentDisplay()" placeholder="+237 6XX XX XX XX"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-xs font-semibold text-ink">E-mail</span>
                      <input [(ngModel)]="draft.guardianEmail" name="guardianEmail" type="email"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                  </div>
                </div>

                <p class="text-[11px] text-mute">
                  {{ fr()
                    ? 'Pour créer un compte de connexion parent (identifiant / mot de passe), enregistrez d’abord l’élève puis utilisez « Comptes parents » sur sa fiche.'
                    : 'To create a parent login (username / password), save the student first, then use “Parent accounts” on their profile.' }}
                </p>
              </section>
            </div>

            <div class="flex items-center justify-end gap-2 mt-8 pt-5 border-t border-slate-100">
              <button type="button" (click)="closeEditor()"
                class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
              <button type="submit" [disabled]="!draft.firstName || !draft.lastName"
                class="h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                {{ fr() ? 'Enregistrer' : 'Save' }}
              </button>
            </div>
          </bbc-card>
        </form>
      } @else {
        <!-- Bulk import students into a class -->
        <bbc-card>
          <div class="flex items-center gap-3 pb-4 mb-4 border-b border-slate-100">
            <button type="button" (click)="closeImport()"
              class="w-9 h-9 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-ink">
              <bbc-icon name="chevronLeft" [s]="18" />
            </button>
            <div class="flex-1">
              <div class="text-[17px] font-bold text-ink font-display">{{ fr() ? 'Importer des élèves' : 'Import students' }}</div>
              <div class="text-xs text-mute">{{ fr() ? 'Ajoutez plusieurs élèves d’un coup dans une classe.' : 'Add many students at once to a class.' }}</div>
            </div>
          </div>

          @if (importResult(); as res) {
            <!-- Result screen -->
            <div class="max-w-2xl space-y-4">
              <div class="flex items-center gap-3 p-4 rounded-lg" [class]="res.failed ? 'bg-amber-50' : 'bg-emerald-50'">
                <div class="w-10 h-10 rounded-full flex items-center justify-center shrink-0"
                  [class]="res.failed ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'">
                  <bbc-icon name="check" [s]="20" />
                </div>
                <div>
                  <div class="font-semibold text-ink">{{ res.created }} {{ fr() ? 'élève(s) importé(s)' : 'student(s) imported' }}</div>
                  @if (res.failed) { <div class="text-sm text-amber-700">{{ res.failed }} {{ fr() ? 'ligne(s) ignorée(s)' : 'row(s) skipped' }}</div> }
                </div>
              </div>
              @if (res.errors.length) {
                <div class="rounded-lg border border-slate-200 overflow-hidden">
                  <div class="px-3 py-2 bg-slate-50 text-xs font-semibold text-mute">{{ fr() ? 'Lignes ignorées' : 'Skipped rows' }}</div>
                  <table class="w-full text-sm">
                    <tbody>
                      @for (e of res.errors; track e.row) {
                        <tr class="border-t border-slate-100">
                          <td class="px-3 py-1.5 text-mute font-mono w-14">#{{ e.row }}</td>
                          <td class="px-3 py-1.5 font-medium text-ink">{{ e.name }}</td>
                          <td class="px-3 py-1.5 text-rose-600">{{ e.message }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
              <div class="flex items-center justify-end gap-2 pt-2">
                <button (click)="resetImport()" class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ fr() ? 'Importer d’autres' : 'Import more' }}</button>
                <button (click)="closeImport()" class="h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold">{{ fr() ? 'Terminer' : 'Done' }}</button>
              </div>
            </div>
          } @else {
            <div class="space-y-6 max-w-3xl">
              <!-- Target class — pick an existing one, or create it on the fly ("5e A") -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Classe cible' : 'Target class' }} *</div>
                <div class="inline-flex rounded-lg border border-slate-200 p-0.5 mb-3">
                  <button type="button" (click)="importTarget.set('existing')"
                    class="h-8 px-3 text-xs font-semibold rounded-md" [class]="importTarget() === 'existing' ? 'bg-brand-600 text-white' : 'text-ink hover:bg-slate-50'">
                    {{ fr() ? 'Classe existante' : 'Existing class' }}
                  </button>
                  <button type="button" (click)="importTarget.set('new')"
                    class="h-8 px-3 text-xs font-semibold rounded-md" [class]="importTarget() === 'new' ? 'bg-brand-600 text-white' : 'text-ink hover:bg-slate-50'">
                    {{ fr() ? 'Nouvelle classe' : 'New class' }}
                  </button>
                </div>

                @if (importTarget() === 'existing') {
                  @if (classes().length) {
                    <div class="space-y-3">
                      <div class="flex items-center gap-2 flex-wrap">
                        <bbc-chip-filter [options]="subOptions()" [value]="importSubFilter()" (change)="onImportSubFilter($event)"
                          [allLabel]="fr() ? 'Tous systèmes' : 'All systems'" />
                        <bbc-chip-filter [options]="levelOptions()" [value]="importLevelFilter()" (change)="onImportLevelFilter($event)"
                          [allLabel]="fr() ? 'Tous niveaux' : 'All levels'" />
                      </div>
                      @if (importGradeOptions().length > 1) {
                        <div class="flex items-center gap-2 flex-wrap">
                          <span class="text-[11px] font-semibold text-mute uppercase tracking-wide">{{ fr() ? 'Série / classe' : 'Grade / class' }}</span>
                          <div class="inline-flex flex-wrap gap-1">
                            <button type="button" (click)="setImportGrade(null)"
                              class="h-8 px-2.5 text-xs font-semibold rounded-md border"
                              [class]="!importGradeFilter() ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink border-slate-200 hover:bg-slate-50'">
                              {{ fr() ? 'Toutes' : 'All' }}
                            </button>
                            @for (g of importGradeOptions(); track g) {
                              <button type="button" (click)="setImportGrade(g)"
                                class="h-8 px-2.5 text-xs font-semibold rounded-md border"
                                [class]="importGradeFilter() === g ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink border-slate-200 hover:bg-slate-50'">
                                {{ g }}
                                <span class="opacity-70 font-normal">({{ importSubclassCount(g) }})</span>
                              </button>
                            }
                          </div>
                        </div>
                      }
                      <select [ngModel]="importClassId()" (ngModelChange)="importClassId.set($event)" name="importClass"
                        class="w-full md:w-[28rem] h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                        <option [ngValue]="null">{{ fr() ? '— Choisir une classe / sous-classe —' : '— Choose a class / section —' }}</option>
                        @for (g of importClassGroups(); track g.key) {
                          <optgroup [label]="g.label">
                            @for (c of g.classes; track c.id) {
                              <option [ngValue]="c.id">
                                {{ c.name }} · {{ c.sectionLabel }}{{ c.studentCount ? ' · ' + c.studentCount + (fr() ? ' él.' : ' st.') : '' }}
                              </option>
                            }
                          </optgroup>
                        }
                      </select>
                      @if (!importClassGroups().length) {
                        <div class="text-sm text-mute p-3 rounded-lg bg-slate-50 border border-slate-100">
                          {{ fr() ? 'Aucune classe ne correspond à ces filtres.' : 'No class matches these filters.' }}
                        </div>
                      } @else if (importGradeFilter(); as grade) {
                        <div class="text-[11px] text-mute">
                          {{ importSubclassHint(grade) }}
                        </div>
                      }
                    </div>
                  } @else {
                    <div class="text-sm text-mute p-3 rounded-lg bg-amber-50 border border-amber-100">
                      {{ fr() ? 'Aucune classe définie — utilisez « Nouvelle classe » pour la créer à l’import.' : 'No classes defined — use “New class” to create one on import.' }}
                    </div>
                  }
                } @else {
                  <div class="grid grid-cols-1 md:grid-cols-3 gap-3 max-w-2xl">
                    <label class="block">
                      <span class="text-[11px] font-semibold text-ink">{{ fr() ? 'Nom de la classe' : 'Class name' }}</span>
                      <input [ngModel]="newClassName()" (ngModelChange)="newClassName.set($event)" name="ncName" placeholder="5e A"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                    </label>
                    <label class="block">
                      <span class="text-[11px] font-semibold text-ink">{{ fr() ? 'Sous-système' : 'Subsystem' }}</span>
                      <select [ngModel]="newClassSubsystem()" (ngModelChange)="newClassSubsystem.set($event)" name="ncSub"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                        <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
                        <option value="EN">{{ fr() ? 'Anglophone' : 'English' }}</option>
                      </select>
                    </label>
                    <label class="block">
                      <span class="text-[11px] font-semibold text-ink">{{ fr() ? 'Niveau' : 'Level' }}</span>
                      <select [ngModel]="newClassLevel()" (ngModelChange)="newClassLevel.set($event)" name="ncLvl"
                        class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                        <option value="maternelle">{{ fr() ? 'Maternelle' : 'Kindergarten' }}</option>
                        <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                        <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
                      </select>
                    </label>
                  </div>
                  <div class="text-[11px] text-mute mt-1.5">
                    {{ fr() ? 'Si aucune classe portant ce nom n’existe, elle est créée automatiquement (avec sa section).' : 'If no class with this name exists, it is created automatically (with its section).' }}
                  </div>
                }
              </section>

              <!-- Data input -->
              <section>
                <div class="flex items-center justify-between mb-2">
                  <div class="text-[11px] uppercase tracking-wider text-mute font-bold">{{ fr() ? 'Données' : 'Data' }}</div>
                  <div class="flex items-center gap-2">
                    <button type="button" (click)="downloadStudentTemplate()"
                      class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                      {{ fr() ? 'Modèle CSV' : 'CSV template' }}
                    </button>
                    <label class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 cursor-pointer">
                      <bbc-icon name="download" [s]="14" /> {{ fr() ? 'Fichier Excel / CSV' : 'Excel / CSV file' }}
                      <input type="file" accept=".csv,.xls,.xlsx,.xlsm,text/csv,text/plain,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" (change)="onFile($event)" class="hidden" />
                    </label>
                    <button type="button" (click)="loadSample()" class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">{{ fr() ? 'Exemple' : 'Sample' }}</button>
                  </div>
                </div>
                <textarea [ngModel]="importText()" (ngModelChange)="onText($event)" name="importText" rows="7"
                  [placeholder]="fr() ? 'Collez le registre ici — Nom, Prénom, Sexe, Date de naissance, Lieu de naissance, NIU, Redouble, Père / Mère / Tuteur' : 'Paste the register here — Last name, First name, Sex, DOB, Birthplace, NIU, Repeats, Father / Mother / Guardian'"
                  class="w-full px-3 py-2 rounded-lg border border-slate-200 font-mono text-xs focus:outline-none focus:border-brand-400"></textarea>
                <div class="text-[11px] text-mute mt-1">
                  {{ fr() ? 'Colonnes reconnues — les mêmes champs que la fiche élève : Nom, Prénom (ou une seule colonne « Nom et Prénom »), Sexe (M/F), Date de naissance (JJ mois AAAA, AAAA-MM-JJ ou JJ/MM/AAAA), Lieu de naissance, NIU, Redouble (OUI/NON), pere_nom / pere_telephone / pere_email, mere_nom / mere_telephone / mere_email, tuteur_nom / tuteur_lien / tuteur_telephone / tuteur_email. L’en-tête est détecté automatiquement ; la classe est celle choisie ci-dessus.'
                          : 'Recognised columns — the same fields as the student record: Last name, First name (or a single “Full name” column), Sex (M/F), DOB (DD month YYYY, YYYY-MM-DD or DD/MM/YYYY), Birthplace, NIU, Repeats (YES/NO), pere_nom / pere_telephone / pere_email, mere_nom / mere_telephone / mere_email, tuteur_nom / tuteur_lien / tuteur_telephone / tuteur_email. The header row is auto-detected; the class is the one picked above.' }}
                </div>
              </section>

              <!-- Preview -->
              @if (importRows().length) {
                <section>
                  <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-2">
                    {{ fr() ? 'Aperçu' : 'Preview' }} — {{ validCount() }} / {{ importRows().length }} {{ fr() ? 'valides' : 'valid' }}
                  </div>
                  <div class="rounded-lg border border-slate-200 overflow-auto max-h-80">
                    <table class="w-full text-sm">
                      <thead class="bg-slate-50 sticky top-0">
                        <tr class="text-[11px] uppercase text-mute text-left">
                          <th class="px-3 py-2 font-semibold w-8"></th>
                          <th class="px-3 py-2 font-semibold">NIU</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Nom et prénom' : 'Full name' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Sexe' : 'Sex' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Naissance' : 'DOB' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Lieu' : 'Birthplace' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Redouble' : 'Repeats' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Famille / tuteur' : 'Family / guardian' }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (r of importRows(); track $index) {
                          <tr class="border-t border-slate-100" [class.bg-rose-50]="!rowValid(r)">
                            <td class="px-3 py-1.5">
                              @if (rowValid(r)) { <span class="text-emerald-600"><bbc-icon name="check" [s]="14" /></span> }
                              @else { <span class="text-rose-500" title="{{ fr() ? 'Nom requis' : 'Name required' }}"><bbc-icon name="x" [s]="14" /></span> }
                            </td>
                            <td class="px-3 py-1.5 font-mono text-xs text-mute">{{ r.niu || '—' }}</td>
                            <td class="px-3 py-1.5 font-medium text-ink">{{ rowName(r) || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.sex || '—' }}</td>
                            <td class="px-3 py-1.5 font-mono text-xs">{{ r.dob || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.birthplace || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.repeats ? (fr() ? 'Oui' : 'Yes') : '—' }}</td>
                            <td class="px-3 py-1.5 text-xs text-mute">{{ rowContacts(r) || '—' }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </section>
              }

              @if (importError(); as e) { <div class="text-xs rounded-lg px-3 py-2 bg-rose-50 text-rose-600">{{ e }}</div> }

              <div class="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button (click)="closeImport()" class="h-10 px-5 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
                <button (click)="doImport()" [disabled]="!importReady() || !validCount() || importing()"
                  class="inline-flex items-center gap-1.5 h-10 px-6 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
                  <bbc-icon name="plus" [s]="16" />
                  {{ importing() ? (fr() ? 'Import…' : 'Importing…') : (fr() ? 'Importer ' + validCount() + ' élève(s)' : 'Import ' + validCount() + ' student(s)') }}
                </button>
              </div>
            </div>
          }
        </bbc-card>
      }

      <!-- Confirm delete -->
      @if (confirmDel(); as cd) {
        <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 fade-in" (click)="confirmDel.set(null)">
          <div class="bg-white rounded-xl2 shadow-pop w-full max-w-md p-6" (click)="$event.stopPropagation()">
            <div class="flex items-start gap-3">
              <div class="w-10 h-10 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center shrink-0">
                <bbc-icon name="trash" [s]="18" />
              </div>
              <div class="flex-1">
                <div class="text-[15px] font-semibold text-ink">
                  {{ fr() ? 'Supprimer ' + cd.name + ' ?' : 'Delete ' + cd.name + '?' }}
                </div>
                <div class="text-sm text-mute mt-1">
                  {{ fr() ? 'L’élève et toutes ses données associées seront retirés du registre.' : 'The student and all related data will be removed from the registry.' }}
                </div>
              </div>
            </div>
            <div class="flex items-center justify-end gap-2 mt-5">
              <button (click)="confirmDel.set(null)" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
              <button (click)="remove(cd)" class="h-9 px-4 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-sm font-semibold">{{ fr() ? 'Supprimer' : 'Delete' }}</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class StudentsComponent {
  protected i18n = inject(I18nService);
  private api = inject(StudentApi);
  private photoApi = inject(PhotoApi);
  private auth = inject(AuthService);
  private scopeSvc = inject(ScopeService);
  private router = inject(Router);

  protected fr = () => this.i18n.lang() === 'fr';
  protected activeScope = computed(() => this.scopeSvc.scope());
  protected scopeChip = computed(() => {
    const s = this.activeScope();
    if (!s) return '';
    return `${this.levelLabel(s.level)} · ${s.subsystem === 'FR' ? (this.fr() ? 'Francophone' : 'Francophone') : (this.fr() ? 'Anglophone' : 'English')}`;
  });

  protected rows = signal<Student[]>([]);
  protected classes = signal<ClassView[]>([]);

  // Parent accounts for the selected student (review #2)
  protected parents = signal<ParentAccountView[]>([]);
  protected parentForm = signal(false);
  protected parentDraft: ParentLinkRequest = { displayName: '', username: '', password: '' };
  protected parentErr = signal<string | null>(null);
  protected search = signal('');
  protected subFilter = signal<string | null>(null);
  protected levelFilter = signal<string | null>(null);
  protected classFilter = signal<string | null>(null);
  protected selectedId = signal<string | null>(null);

  protected mode = signal<'list' | 'edit' | 'import'>('list');
  protected editId = signal<string | null>(null);
  protected confirmDel = signal<Student | null>(null);

  // Bulk import
  protected importTarget = signal<'existing' | 'new'>('existing');
  protected importClassId = signal<string | null>(null);
  protected importSubFilter = signal<string | null>(null);
  protected importLevelFilter = signal<string | null>(null);
  protected importGradeFilter = signal<string | null>(null);
  protected newClassName = signal('');
  protected newClassSubsystem = signal<'FR' | 'EN'>('FR');
  protected newClassLevel = signal<'maternelle' | 'primary' | 'secondary'>('secondary');
  protected importText = signal('');
  protected importRows = signal<StudentImportRow[]>([]);
  protected importResult = signal<StudentImportResult | null>(null);
  protected importing = signal(false);
  protected importError = signal<string | null>(null);

  /** Action-authorized registrar users may use scoped student controls even
   * when the legacy module matrix intentionally has no students entry. */
  protected get canWrite(): boolean {
    return this.auth.can('students', 'write')
      || this.auth.canModuleOrAction('students', 'STUDENT_PROFILE_CREATE')
      || this.auth.canModuleOrAction('students', 'STUDENT_PROFILE_EDIT')
      || this.auth.canModuleOrAction('students', 'GUARDIAN_LINK_MANAGE');
  }
  protected draft: StudentUpsert = this.blank();
  /**
   * Photo saisie dans le formulaire (data URL) ; envoyée APRÈS l'enregistrement,
   * une création n'ayant pas encore d'identifiant. `null` = aucune photo,
   * `''` = photo retirée par l'utilisateur (à supprimer côté serveur).
   */
  protected photoDraft = signal<string | null>(null);
  private photoWasSet = false;
  /** Photo de l'élève sélectionné, chargée en blob (l'API exige le jeton). */
  protected selectedPhoto = signal<string | null>(null);
  protected trackId = (s: Student) => s.id;

  protected subOptions = computed(() => [
    { value: 'FR', label: this.fr() ? 'Francophone' : 'Francophone' },
    { value: 'EN', label: this.fr() ? 'Anglophone' : 'English' },
  ]);

  protected levelOptions = computed(() => [
    { value: 'maternelle', label: this.fr() ? 'Maternelle' : 'Kindergarten' },
    { value: 'primary', label: this.fr() ? 'Primaire' : 'Primary' },
    { value: 'secondary', label: this.fr() ? 'Secondaire' : 'Secondary' },
  ]);

  protected columns = computed<Column<Student>[]>(() => [
    { key: 'name', label: this.fr() ? 'Élève' : 'Student', value: (s) => `${s.lastName} ${s.firstName}` },
    { key: 'className', label: this.fr() ? 'Classe' : 'Class', value: (s) => s.className },
    { key: 'subsystem', label: this.fr() ? 'Système' : 'System', value: (s) => s.subsystem },
    { key: 'level', label: this.fr() ? 'Niveau' : 'Level', value: (s) => s.level },
    { key: 'sex', label: this.fr() ? 'Sexe' : 'Sex', align: 'center', value: (s) => s.sex },
    { key: 'parent', label: 'Parent', value: (s) => s.parentName },
  ]);

  protected filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const sub = this.subFilter();
    const lvl = this.levelFilter();
    const classId = this.classFilter();
    return this.rows().filter((s) => {
      if (sub && (s.subsystem || '').toUpperCase() !== sub) return false;
      if (lvl && (s.level || '').toLowerCase() !== lvl) return false;
      if (classId && s.classId !== classId) return false;
      if (q) {
        const hay = `${s.name} ${s.matricule} ${s.parentName}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    }).sort((a, b) => {
      const ca = (a.className || '').localeCompare(b.className || '', 'fr', { sensitivity: 'base' });
      if (ca !== 0) return ca;
      return a.name.localeCompare(b.name, 'fr', { sensitivity: 'base' });
    });
  });

  /** Classes visible in the list filter, respecting système / niveau chips. */
  protected listClassOptions = computed(() => this.filterClasses(this.classes(), this.subFilter(), this.levelFilter(), null));

  protected listClassGroups = computed(() => this.groupClasses(this.listClassOptions()));

  /** Import picker: classes after système / niveau / série filters. */
  protected importFilteredClasses = computed(() =>
    this.filterClasses(this.classes(), this.importSubFilter(), this.importLevelFilter(), this.importGradeFilter()));

  protected importClassGroups = computed(() => this.groupClasses(this.importFilteredClasses()));

  /** Distinct grade bases (4e, 5e, CM1…) among classes matching système/niveau. */
  protected importGradeOptions = computed(() => {
    const scoped = this.filterClasses(this.classes(), this.importSubFilter(), this.importLevelFilter(), null);
    const keys = new Set<string>();
    for (const c of scoped) keys.add(this.gradeBase(c.name));
    return [...keys].sort((a, b) => a.localeCompare(b, 'fr', { numeric: true, sensitivity: 'base' }));
  });

  protected importSubclassCount(grade: string): number {
    return this.filterClasses(this.classes(), this.importSubFilter(), this.importLevelFilter(), grade).length;
  }

  protected importSubclassHint(grade: string): string {
    const names = this.importFilteredClasses().map((c) => c.name).join(', ');
    return this.fr()
      ? `Sous-classes de « ${grade} » : ${names}`
      : `Sections of “${grade}”: ${names}`;
  }
  protected selected = computed(() => {
    const id = this.selectedId();
    if (!id) return null;
    return this.rows().find((s) => s.id === id) ?? null;
  });

  /** Charge (et libère) la photo de la fiche ouverte. */
  private readonly photoLoader = effect(() => {
    const id = this.selectedId();
    const previous = this.selectedPhoto();
    if (previous?.startsWith('blob:')) URL.revokeObjectURL(previous);
    this.selectedPhoto.set(null);
    if (!id) return;
    this.photoApi.load('students', id).subscribe((url) => this.selectedPhoto.set(url));
  }, { allowSignalWrites: true });

  protected headerSub = computed(() => {
    const n = this.rows().length;
    return this.fr() ? `${n} élèves inscrits · 2 sous-systèmes` : `${n} enrolled students · 2 subsystems`;
  });

  constructor() {
    this.reload();
    this.api.listClassOptions().subscribe({
      next: (c) => this.classes.set(c),
      error: () => this.classes.set([]),
    });

    // Load the linked parent accounts whenever the selected student changes.
    effect(() => {
      const id = this.selectedId();
      this.parentForm.set(false);
      this.parentErr.set(null);
      if (!id) { this.parents.set([]); return; }
      this.api.listParents(id).subscribe((p) => this.parents.set(p));
    });
  }

  private reload(): void {
    this.api.list().subscribe((r) => {
      this.rows.set(r);
    });
  }

  protected className(id: string | null): string {
    if (!id) return this.fr() ? 'Non affecté' : 'Unassigned';
    return this.classes().find((c) => c.id === id)?.name ?? (this.fr() ? 'Non affecté' : 'Unassigned');
  }

  protected sexLabel(sex: string): string {
    if (!sex) return '—';
    return sex.toUpperCase().startsWith('M') ? (this.fr() ? 'Masculin' : 'Male') : (this.fr() ? 'Féminin' : 'Female');
  }

  protected subsystemLabel(sub: string): string {
    return (sub || '').toUpperCase().startsWith('F') ? (this.fr() ? 'Francophone' : 'Francophone') : (this.fr() ? 'Anglophone' : 'English');
  }

  protected levelLabel(level: string): string {
    switch ((level || '').toLowerCase()) {
      case 'maternelle': return this.fr() ? 'Maternelle' : 'Kindergarten';
      case 'secondary': return this.fr() ? 'Secondaire' : 'Secondary';
      default: return this.fr() ? 'Primaire' : 'Primary';
    }
  }

  protected sectionLabel(s: Student): string {
    const f = (s.subsystem || '').toUpperCase().startsWith('F');
    return `${this.levelLabel(s.level)} ${f ? 'FR' : 'EN'}`;
  }

  protected dobLabel(dob: string | null): string {
    if (!dob) return '—';
    const d = new Date(dob);
    if (isNaN(d.getTime())) return dob;
    return d.toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB', { day: '2-digit', month: 'long', year: 'numeric' });
  }

  openCreate(): void {
    this.router.navigate(['/students/new']);
  }

  openEdit(s: Student): void {
    this.editId.set(s.id);
    this.photoDraft.set(null);
    this.photoWasSet = false;
    // Photo existante : affichée dans le composant de capture pour pouvoir la remplacer.
    this.photoApi.load('students', s.id).subscribe((url) => {
      if (url) { this.photoDraft.set(url); this.photoWasSet = true; }
    });
    this.draft = {
      firstName: s.firstName,
      lastName: s.lastName,
      niu: s.niu,
      sex: s.sex || 'M',
      dob: s.dob,
      birthplace: s.birthplace,
      repeats: s.repeats,
      classId: s.classId ?? null,
      parentName: s.parentName,
      parentPhone: s.parentPhone,
      fatherName: s.fatherName ?? '',
      fatherPhone: s.fatherPhone ?? '',
      fatherEmail: s.fatherEmail ?? '',
      motherName: s.motherName ?? '',
      motherPhone: s.motherPhone ?? '',
      motherEmail: s.motherEmail ?? '',
      guardianName: s.guardianName ?? '',
      guardianPhone: s.guardianPhone ?? '',
      guardianEmail: s.guardianEmail ?? '',
      guardianRelation: s.guardianRelation ?? '',
    };
    this.syncParentDisplay();
    this.mode.set('edit');
  }

  closeEditor(): void {
    this.mode.set('list');
    this.editId.set(null);
    this.draft = this.blank();
    this.photoDraft.set(null);
    this.photoWasSet = false;
  }

  save(): void {
    if (!this.draft.firstName || !this.draft.lastName) return;
    this.syncParentDisplay();
    const id = this.editId();
    const req = id ? this.api.update(id, this.draft) : this.api.create(this.draft);
    req.subscribe((s) => {
      this.savePhoto(s.id, () => {
        this.closeEditor();
        this.selectedId.set(s.id);
        this.reload();
      });
    });
  }

  /**
   * Envoie la photo une fois la fiche enregistrée. Une URL d'objet (blob:) est
   * la photo déjà en base rechargée à l'ouverture : rien à renvoyer.
   */
  private savePhoto(studentId: string, done: () => void): void {
    const photo = this.photoDraft();
    if (photo && photo.startsWith('data:')) {
      this.photoApi.save('students', studentId, photo).subscribe({ next: done, error: done });
      return;
    }
    if (!photo && this.photoWasSet) {
      this.photoApi.remove('students', studentId).subscribe({ next: done, error: done });
      return;
    }
    done();
  }

  /** Prefer father → mother → guardian for legacy parentName / parentPhone display fields. */
  protected syncParentDisplay(): void {
    const d = this.draft;
    if (d.fatherName?.trim()) {
      d.parentName = d.fatherName.trim();
      d.parentPhone = d.fatherPhone?.trim() || d.parentPhone || '';
    } else if (d.motherName?.trim()) {
      d.parentName = d.motherName.trim();
      d.parentPhone = d.motherPhone?.trim() || d.parentPhone || '';
    } else if (d.guardianName?.trim()) {
      d.parentName = d.guardianName.trim();
      d.parentPhone = d.guardianPhone?.trim() || d.parentPhone || '';
    } else {
      d.parentName = '';
      d.parentPhone = '';
    }
  }

  remove(s: Student): void {
    this.api.remove(s.id).subscribe(() => {
      this.confirmDel.set(null);
      if (this.selectedId() === s.id) this.selectedId.set(null);
      this.reload();
    });
  }

  private blank(): StudentUpsert {
    const s = this.activeScope();
    return {
      firstName: '', lastName: '', niu: '', sex: 'M', birthplace: '', repeats: false,
      classId: null, parentName: '', parentPhone: '',
      fatherName: '', fatherPhone: '', fatherEmail: '',
      motherName: '', motherPhone: '', motherEmail: '',
      guardianName: '', guardianPhone: '', guardianEmail: '', guardianRelation: '',
      subsystem: s?.subsystem,
      level: s?.level,
    };
  }

  /**
   * Columns of the import template, in the order of the creation form: identity
   * first, then the family block. The class is not a column — the import screen
   * asks for the target class once, for the whole batch.
   */
  private static readonly TEMPLATE_HEADERS = [
    'nom', 'prenom', 'sexe', 'date_naissance', 'lieu_naissance', 'niu', 'redouble',
    'pere_nom', 'pere_telephone', 'pere_email',
    'mere_nom', 'mere_telephone', 'mere_email',
    'tuteur_nom', 'tuteur_lien', 'tuteur_telephone', 'tuteur_email',
  ];

  protected exportList(): void {
    // Same column names as the import template (plus matricule / class) so an
    // exported list can be corrected in a spreadsheet and imported straight back.
    const rows = this.filtered().map((s) => [
      s.matricule, s.lastName, s.firstName, s.sex, s.dob ?? '', s.birthplace ?? '', s.niu ?? '', s.repeats ? 'oui' : 'non',
      s.className, s.subsystem, s.level,
      s.fatherName ?? '', s.fatherPhone ?? '', s.fatherEmail ?? '',
      s.motherName ?? '', s.motherPhone ?? '', s.motherEmail ?? '',
      s.guardianName ?? '', s.guardianRelation ?? '', s.guardianPhone ?? '', s.guardianEmail ?? '',
    ]);
    downloadCsv(stampedName('eleves'),
      ['matricule', 'nom', 'prenom', 'sexe', 'date_naissance', 'lieu_naissance', 'niu', 'redouble',
       'classe', 'sous_systeme', 'niveau',
       'pere_nom', 'pere_telephone', 'pere_email',
       'mere_nom', 'mere_telephone', 'mere_email',
       'tuteur_nom', 'tuteur_lien', 'tuteur_telephone', 'tuteur_email'],
      rows);
  }

  protected downloadStudentTemplate(): void {
    downloadCsv('modele-eleves.csv', StudentsComponent.TEMPLATE_HEADERS, [[
      'DUPONT', 'Jean', 'M', '2015-03-12', 'Douala', '', 'non',
      'DUPONT Paul', '+237 6XX XX XX XX', 'paul.dupont@example.cm',
      'NGO Marie', '+237 6XX XX XX XX', 'marie.ngo@example.cm',
      '', '', '', '',
    ]]);
  }

  // ---- Bulk import ---------------------------------------------------------
  protected validCount = computed(() => this.importRows().filter((r) => this.rowValid(r)).length);

  /** A row is importable when it carries a name — either combined or split. */
  protected rowValid(r: StudentImportRow): boolean {
    return !!(r.name?.trim() || (r.firstName?.trim() && r.lastName?.trim()) || r.lastName?.trim());
  }

  /** Best-effort display name for the preview, from combined or split fields. */
  protected rowName(r: StudentImportRow): string {
    if (r.name?.trim()) return r.name.trim().replace(/(\s+-)+\s*$/, '').trim();
    return `${r.lastName ?? ''} ${r.firstName ?? ''}`.trim();
  }

  /** Compact "Père · Mère · Tuteur" summary for the preview — names only, phones would overflow. */
  protected rowContacts(r: StudentImportRow): string {
    const fr = this.fr();
    const parts = [
      r.fatherName?.trim() ? `${fr ? 'Père' : 'Father'} : ${r.fatherName.trim()}` : '',
      r.motherName?.trim() ? `${fr ? 'Mère' : 'Mother'} : ${r.motherName.trim()}` : '',
      r.guardianName?.trim() ? `${fr ? 'Tuteur' : 'Guardian'} : ${r.guardianName.trim()}` : '',
    ].filter(Boolean);
    if (parts.length) return parts.join(' · ');
    return r.parentName?.trim() ?? '';
  }

  /** True once a target class is chosen (existing id or a new class name). */
  protected importReady = computed(() =>
    this.importTarget() === 'existing' ? !!this.importClassId() : !!this.newClassName().trim());

  protected openImport(): void {
    this.router.navigate(['/students/import-family']);
  }

  protected openDetails(student: Student): void { this.router.navigate(['/students', student.id]); }

  protected closeImport(): void {
    this.mode.set('list');
    this.resetImport();
  }

  protected resetImport(): void {
    this.importTarget.set('existing');
    this.importClassId.set(null);
    this.importSubFilter.set(null);
    this.importLevelFilter.set(null);
    this.importGradeFilter.set(null);
    this.newClassName.set('');
    this.newClassSubsystem.set('FR');
    this.newClassLevel.set('secondary');
    this.importText.set('');
    this.importRows.set([]);
    this.importResult.set(null);
    this.importError.set(null);
    this.importing.set(false);
  }

  protected onSubFilter(v: string | null): void {
    this.subFilter.set(v);
    this.clearClassFilterIfOutOfScope();
  }

  protected onLevelFilter(v: string | null): void {
    this.levelFilter.set(v);
    this.clearClassFilterIfOutOfScope();
  }

  protected onImportSubFilter(v: string | null): void {
    this.importSubFilter.set(v);
    this.importGradeFilter.set(null);
    this.clearImportClassIfOutOfScope();
  }

  protected onImportLevelFilter(v: string | null): void {
    this.importLevelFilter.set(v);
    this.importGradeFilter.set(null);
    this.clearImportClassIfOutOfScope();
  }

  protected setImportGrade(g: string | null): void {
    this.importGradeFilter.set(g);
    this.clearImportClassIfOutOfScope();
  }

  private clearClassFilterIfOutOfScope(): void {
    const id = this.classFilter();
    if (!id) return;
    if (!this.listClassOptions().some((c) => c.id === id)) this.classFilter.set(null);
  }

  private clearImportClassIfOutOfScope(): void {
    const id = this.importClassId();
    if (!id) return;
    if (!this.importFilteredClasses().some((c) => c.id === id)) this.importClassId.set(null);
  }

  /** Strip trailing section letter: "4e A" / "4e-B" → "4e"; leave "Form 2" as-is. */
  private gradeBase(name: string): string {
    const n = (name || '').trim();
    const m = n.match(/^(.*?)(?:[\s\-]+([A-Za-z]))$/);
    if (m && m[1]!.trim()) return m[1]!.trim();
    return n;
  }

  private filterClasses(
    all: ClassView[],
    sub: string | null,
    lvl: string | null,
    grade: string | null,
  ): ClassView[] {
    return all
      .filter((c) => {
        if (sub && (c.subsystem || '').toUpperCase() !== sub) return false;
        if (lvl && (c.level || '').toLowerCase() !== lvl) return false;
        if (grade && this.gradeBase(c.name) !== grade) return false;
        return true;
      })
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name, 'fr', { numeric: true, sensitivity: 'base' }));
  }

  private groupClasses(list: ClassView[]): { key: string; label: string; classes: ClassView[] }[] {
    const map = new Map<string, ClassView[]>();
    for (const c of list) {
      const key = this.gradeBase(c.name);
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(c);
    }
    return [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b, 'fr', { numeric: true, sensitivity: 'base' }))
      .map(([key, classes]) => {
        const sample = classes[0]!;
        const level = this.levelLabel(sample.level);
        const sub = this.subsystemLabel(sample.subsystem);
        const label = classes.length > 1
          ? `${key} — ${level} · ${sub} (${classes.length})`
          : `${key} — ${level} · ${sub}`;
        return { key, label, classes };
      });
  }

  protected onText(text: string): void {
    this.importText.set(text);
    this.importRows.set(this.parseRows(text));
  }

  protected onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.importError.set(null);
    const isExcel = /\.(xlsx?|xlsm)$/i.test(file.name);
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        if (isExcel) {
          // Parse the raw .xls/.xlsx register: take the first sheet, emit CSV.
          // xlsx is heavy (~700 kB) so it is loaded on demand, only when needed.
          const XLSX = await import('xlsx');
          const wb = XLSX.read(reader.result, { type: 'array' });
          const sheet = wb.Sheets[wb.SheetNames[0]];
          this.onText(XLSX.utils.sheet_to_csv(sheet));
        } else {
          this.onText(String(reader.result ?? ''));
        }
      } catch {
        this.importError.set(this.fr() ? 'Fichier illisible — vérifiez le format.' : 'Unreadable file — check the format.');
      }
      input.value = '';
    };
    if (isExcel) reader.readAsArrayBuffer(file); else reader.readAsText(file);
  }

  protected loadSample(): void {
    // Matches the official BBC register layout: NIU, combined "Nom et Prénom",
    // Sexe, French-worded date, Lieu de naissance, Redouble.
    const sample =
      'NIU,Nom et Prénom,Sexe,Date de naissance,Lieu de naissance,Redouble\n' +
      '250193630,ABBASSI HAMADOU - -,M,06 janvier 2011,MAROUA,NON\n' +
      '250193640,ABDEL RACHID ABDOURAMAN - -,M,19 novembre 2014,MAROUA,NON\n' +
      '250418390,KOUDADAÏ RACHEL MIRYAM,F,31 décembre 2015,MAROUA,OUI';
    this.onText(sample);
  }

  protected doImport(): void {
    const rows = this.importRows().filter((r) => this.rowValid(r));
    if (!this.importReady() || !rows.length) return;
    const req: StudentImportRequest =
      this.importTarget() === 'existing'
        ? { classId: this.importClassId(), rows }
        : { newClass: { name: this.newClassName().trim(), subsystem: this.newClassSubsystem(), level: this.newClassLevel() }, rows };
    this.importing.set(true);
    this.importError.set(null);
    this.api.importStudents(req).subscribe({
      next: (res) => {
        this.importing.set(false);
        this.importResult.set(res);
        this.reload();
        this.api.listClassOptions().subscribe({
          next: (c) => this.classes.set(c),
          error: () => this.classes.set([]),
        });
      },
      error: (e) => { this.importing.set(false); this.importError.set(this.importErrorMessage(e)); },
    });
  }

  /** Turn any import failure into a message the user can act on, not a bare "Import impossible.". */
  private importErrorMessage(e: unknown): string {
    const fr = this.fr();
    if (e instanceof HttpErrorResponse) {
      // Network down, request blocked, or CORS — no HTTP response reached us.
      if (e.status === 0) return fr ? 'Connexion interrompue (réseau ou délai dépassé) — réessayez.' : 'Connection lost (network or timeout) — please retry.';
      if (e.status === 401) return fr ? 'Session expirée — reconnectez-vous puis relancez l\'import.' : 'Session expired — sign in again then retry the import.';
      if (e.status === 403) return fr ? 'Vous n\'avez pas la permission d\'importer des élèves.' : 'You do not have permission to import students.';
      if (e.status === 413) return fr ? 'Fichier trop volumineux — importez par lots plus petits.' : 'File too large — import in smaller batches.';
      const msg = e.error?.message;
      // Bean-validation errors arrive as a { field: reason } object — flatten them.
      if (msg && typeof msg === 'object') {
        const parts = Object.entries(msg as Record<string, string>).map(([k, v]) => `${k}: ${v}`);
        if (parts.length) return parts.join(' · ');
      }
      if (typeof msg === 'string' && msg) return msg;
      return (fr ? 'Import impossible' : 'Import failed') + ` (HTTP ${e.status}).`;
    }
    return fr ? 'Import impossible.' : 'Import failed.';
  }

  private parseRows(text: string): StudentImportRow[] {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length);
    if (!lines.length) return [];
    const delim = this.detectDelim(lines[0]);
    const cells = lines.map((l) => this.splitLine(l, delim));
    const map = this.mapHeader(cells[0]);
    const dataRows = map ? cells.slice(1) : cells;
    // Default layout when no header is recognised: assume the official register
    // order (NIU, Nom et Prénom, Sexe, Naissance, Lieu, Redouble).
    const idx: HeaderMap = map ?? { ...StudentsComponent.EMPTY_MAP, niu: 0, name: 1, sex: 2, dob: 3, birthplace: 4, repeats: 5 };
    const at = (r: string[], i: number) => (i >= 0 ? (r[i] ?? '').trim() : '');
    return dataRows.map((r) => ({
      name: at(r, idx.name),
      lastName: at(r, idx.lastName),
      firstName: at(r, idx.firstName),
      niu: at(r, idx.niu) || null,
      sex: this.normSex(r[idx.sex]),
      dob: this.normDob(r[idx.dob]),
      birthplace: at(r, idx.birthplace) || null,
      repeats: this.normYesNo(r[idx.repeats]),
      parentName: at(r, idx.parentName),
      parentPhone: at(r, idx.parentPhone),
      fatherName: at(r, idx.fatherName),
      fatherPhone: at(r, idx.fatherPhone),
      fatherEmail: at(r, idx.fatherEmail),
      motherName: at(r, idx.motherName),
      motherPhone: at(r, idx.motherPhone),
      motherEmail: at(r, idx.motherEmail),
      guardianName: at(r, idx.guardianName),
      guardianPhone: at(r, idx.guardianPhone),
      guardianEmail: at(r, idx.guardianEmail),
      guardianRelation: at(r, idx.guardianRelation),
    }));
  }

  private normYesNo(v: string | undefined): boolean {
    return /^(o|oui|y|yes|1|true|vrai)/i.test((v ?? '').trim());
  }

  private detectDelim(line: string): string {
    const counts: Record<string, number> = { ';': 0, '\t': 0, ',': 0 };
    for (const ch of line) if (ch in counts) counts[ch]++;
    if (counts['\t'] >= counts[';'] && counts['\t'] >= counts[',']) return '\t';
    if (counts[';'] >= counts[',']) return ';';
    return ',';
  }

  private splitLine(line: string, delim: string): string[] {
    return line.split(delim).map((c) => c.replace(/^"|"$/g, '').trim());
  }

  /** Every column index unknown — the starting point of a header map. */
  private static readonly EMPTY_MAP: HeaderMap = {
    niu: -1, name: -1, lastName: -1, firstName: -1, sex: -1, dob: -1, birthplace: -1, repeats: -1,
    parentName: -1, parentPhone: -1,
    fatherName: -1, fatherPhone: -1, fatherEmail: -1,
    motherName: -1, motherPhone: -1, motherEmail: -1,
    guardianName: -1, guardianRelation: -1, guardianPhone: -1, guardianEmail: -1,
  };

  /**
   * Map a header row to column indices; null when the first row is data, not a header.
   * Understands the official register ("NIU, Nom et Prénom, Sexe, Date de naissance,
   * Lieu de naissance, Redouble"), a split Nom/Prénom layout, and the downloadable
   * template, whose columns mirror the creation form (père / mère / tuteur included).
   */
  private mapHeader(cells: string[]): HeaderMap | null {
    const idx: HeaderMap = { ...StudentsComponent.EMPTY_MAP };
    cells.forEach((raw, i) => {
      const key = this.headerKey(raw);
      if (key && idx[key] < 0) idx[key] = i;
    });
    // A header must carry at least one recognisable name column.
    if (idx.name < 0 && idx.lastName < 0) return null;
    // A lone "Nom" column (no "Prénom" beside it) holds the full name — treat it as
    // combined so "DUPONT Jean" is split, instead of importing a pupil with no first name.
    if (idx.name < 0 && idx.firstName < 0) { idx.name = idx.lastName; idx.lastName = -1; }
    return idx;
  }

  /**
   * Classify one header cell. Family columns are tested first: "pere_nom" must not
   * be read as the pupil's own name, nor "mere_telephone" as a bare phone column.
   */
  private headerKey(raw: string): keyof HeaderMap | null {
    const c = raw.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
    if (!c) return null;
    const mail = /(mail|courriel)/.test(c);
    const phone = /(tel|phone|contact|numero|mobile|whatsapp|portable)/.test(c);

    if (/(pere|father|papa)/.test(c)) return mail ? 'fatherEmail' : phone ? 'fatherPhone' : 'fatherName';
    if (/(mere|mother|maman)/.test(c)) return mail ? 'motherEmail' : phone ? 'motherPhone' : 'motherName';
    if (/(tuteur|tutrice|guardian)/.test(c)) {
      if (mail) return 'guardianEmail';
      if (phone) return 'guardianPhone';
      return /(lien|relation|parente)/.test(c) ? 'guardianRelation' : 'guardianName';
    }

    if (/\bniu\b|identifiant/.test(c)) return 'niu';
    if (/(nom et prenom|nom.*prenom|full name|nom complet)/.test(c)) return 'name';
    if (/(prenom|first)/.test(c)) return 'firstName';
    if (/(^nom|last|surname|famille)/.test(c)) return 'lastName';
    if (/(sexe|sex|genre|gender)/.test(c)) return 'sex';
    if (/(naiss|dob|birth|date)/.test(c) && !/(lieu|place)/.test(c)) return 'dob';
    if (/(lieu|birthplace|place)/.test(c)) return 'birthplace';
    if (/(redouble|repeat|redoublant)/.test(c)) return 'repeats';
    if (/(lien|relation)/.test(c)) return 'guardianRelation';
    if (phone) return 'parentPhone';
    if (/(parent|responsable)/.test(c)) return 'parentName';
    return null;
  }

  private normSex(v: string | undefined): string {
    const c = (v ?? '').trim().toLowerCase();
    if (!c) return '';
    if (/^(m|masculin|male|g|gar)/.test(c)) return 'M';
    if (/^(f|f[ée]minin|female|fille)/.test(c)) return 'F';
    return '';
  }

  private static readonly FR_MONTHS: Record<string, string> = {
    janvier: '01', fevrier: '02', mars: '03', avril: '04', mai: '05', juin: '06',
    juillet: '07', aout: '08', septembre: '09', octobre: '10', novembre: '11', decembre: '12',
  };

  private normDob(v: string | undefined): string | null {
    const c = (v ?? '').trim();
    if (!c) return null;
    let m = c.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);      // YYYY-MM-DD
    if (m) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
    m = c.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);          // DD/MM/YYYY
    if (m) return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
    // French-worded date, as printed on the registers: "06 janvier 2011".
    m = c.match(/^(\d{1,2})\s+([a-zà-ÿ]+)\.?\s+(\d{4})$/i);
    if (m) {
      const key = m[2].toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
      const mo = StudentsComponent.FR_MONTHS[key];
      if (mo) return `${m[3]}-${mo}-${m[1].padStart(2, '0')}`;
    }
    return null;
  }

  // ---- Parent accounts (review #2) -----------------------------------------
  protected openParentForm(): void {
    const sel = this.selected();
    this.parentErr.set(null);
    this.parentDraft = { displayName: sel?.parentName || '', username: '', password: '' };
    this.parentForm.set(true);
  }

  protected linkParent(): void {
    const sel = this.selected();
    if (!sel || !this.parentDraft.displayName || !this.parentDraft.username) return;
    this.parentErr.set(null);
    this.api.linkParent(sel.id, this.parentDraft).subscribe({
      next: () => { this.parentForm.set(false); this.api.listParents(sel.id).subscribe((p) => this.parents.set(p)); },
      error: (e) => this.parentErr.set(e?.error?.message ?? (this.fr() ? 'Échec de la création du compte.' : 'Account creation failed.')),
    });
  }

  protected unlinkParent(p: ParentAccountView): void {
    const sel = this.selected();
    if (!sel) return;
    if (!confirm(this.fr() ? `Détacher ${p.displayName} de cet élève ?` : `Unlink ${p.displayName} from this student?`)) return;
    this.api.unlinkParent(sel.id, p.userId).subscribe(() => this.api.listParents(sel.id).subscribe((r) => this.parents.set(r)));
  }
}
