import { Component, ChangeDetectionStrategy, inject, signal, computed, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StudentApi, StudentUpsert, ParentAccountView, ParentLinkRequest, StudentImportRow, StudentImportResult } from './students.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { Student } from '../../core/models';
import {
  IconComponent, CardComponent, PageHeaderComponent,
  AvatarComponent, ChipFilterComponent, StatusPillComponent,
  DataTableComponent, CellTemplateDirective, Column,
} from '../../core/ui';

@Component({
  selector: 'bbc-students',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent,
    AvatarComponent, ChipFilterComponent, StatusPillComponent,
    DataTableComponent, CellTemplateDirective,
  ],
  template: `
    <div class="fade-in max-w-7xl mx-auto">
      <bbc-page-header [title]="i18n.t('students')" [subtitle]="headerSub()">
        <div right class="flex items-center gap-2">
          @if (mode() === 'list') {
            <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter liste' : 'Export list' }}
            </button>
            @if (canWrite) {
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
            <bbc-chip-filter [options]="subOptions()" [value]="subFilter()" (change)="subFilter.set($event)"
              [allLabel]="fr() ? 'Tous systèmes' : 'All systems'" />
            <bbc-chip-filter [options]="levelOptions()" [value]="levelFilter()" (change)="levelFilter.set($event)"
              [allLabel]="fr() ? 'Tous niveaux' : 'All levels'" />
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
              (rowClick)="selectedId.set($event.id)">

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
                <bbc-avatar [name]="sel.name" [hue]="sel.photoHue" [size]="64" />
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
                    <div class="text-[11px] text-mute">{{ fr() ? 'Date de naissance' : 'Date of birth' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ dobLabel(sel.dob) }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Classe' : 'Class' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sel.className || '—' }}</div>
                  </div>
                  <div>
                    <div class="text-[11px] text-mute">{{ fr() ? 'Section' : 'Section' }}</div>
                    <div class="font-semibold text-ink text-sm">{{ sectionLabel(sel) }}</div>
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

              <!-- Parent / guardian -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Parent / tuteur' : 'Parent / guardian' }}</div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom du parent' : 'Parent name' }}</span>
                    <input [(ngModel)]="draft.parentName" name="parentName"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                  <label class="block">
                    <span class="text-xs font-semibold text-ink">{{ fr() ? 'Téléphone parent' : 'Parent phone' }}</span>
                    <input [(ngModel)]="draft.parentPhone" name="parentPhone"
                      class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
                  </label>
                </div>
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
              <!-- Target class -->
              <section>
                <div class="text-[11px] uppercase tracking-wider text-mute font-bold mb-3">{{ fr() ? 'Classe cible' : 'Target class' }} *</div>
                @if (classes().length) {
                  <select [ngModel]="importClassId()" (ngModelChange)="importClassId.set($event)" name="importClass"
                    class="w-full md:w-96 h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option [ngValue]="null">{{ fr() ? '— Choisir une classe —' : '— Choose a class —' }}</option>
                    @for (c of classes(); track c.id) {
                      <option [ngValue]="c.id">{{ c.name }} · {{ c.sectionLabel }}</option>
                    }
                  </select>
                } @else {
                  <div class="text-sm text-mute p-3 rounded-lg bg-amber-50 border border-amber-100">
                    {{ fr() ? 'Aucune classe définie. Créez vos classes dans Paramètres → Scolarité.' : 'No classes defined. Create classes in Settings → Academics.' }}
                  </div>
                }
              </section>

              <!-- Data input -->
              <section>
                <div class="flex items-center justify-between mb-2">
                  <div class="text-[11px] uppercase tracking-wider text-mute font-bold">{{ fr() ? 'Données' : 'Data' }}</div>
                  <div class="flex items-center gap-2">
                    <label class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 cursor-pointer">
                      <bbc-icon name="download" [s]="14" /> {{ fr() ? 'Fichier CSV' : 'CSV file' }}
                      <input type="file" accept=".csv,text/csv,text/plain" (change)="onFile($event)" class="hidden" />
                    </label>
                    <button type="button" (click)="loadSample()" class="h-8 px-3 text-xs font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">{{ fr() ? 'Exemple' : 'Sample' }}</button>
                  </div>
                </div>
                <textarea [ngModel]="importText()" (ngModelChange)="onText($event)" name="importText" rows="7"
                  [placeholder]="fr() ? 'Collez vos lignes ici, une par élève — Nom, Prénom, Sexe, Naissance, Parent, Téléphone' : 'Paste rows here, one per student — Last name, First name, Sex, DOB, Parent, Phone'"
                  class="w-full px-3 py-2 rounded-lg border border-slate-200 font-mono text-xs focus:outline-none focus:border-brand-400"></textarea>
                <div class="text-[11px] text-mute mt-1">
                  {{ fr() ? 'Colonnes attendues : Nom, Prénom, Sexe (M/F), Date de naissance (AAAA-MM-JJ ou JJ/MM/AAAA), Parent, Téléphone. Une ligne d’en-tête est détectée automatiquement.'
                          : 'Expected columns: Last name, First name, Sex (M/F), DOB (YYYY-MM-DD or DD/MM/YYYY), Parent, Phone. A header row is auto-detected.' }}
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
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Nom' : 'Last name' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Prénom' : 'First name' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Sexe' : 'Sex' }}</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Naissance' : 'DOB' }}</th>
                          <th class="px-3 py-2 font-semibold">Parent</th>
                          <th class="px-3 py-2 font-semibold">{{ fr() ? 'Téléphone' : 'Phone' }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (r of importRows(); track $index) {
                          <tr class="border-t border-slate-100" [class.bg-rose-50]="!rowValid(r)">
                            <td class="px-3 py-1.5">
                              @if (rowValid(r)) { <span class="text-emerald-600"><bbc-icon name="check" [s]="14" /></span> }
                              @else { <span class="text-rose-500" title="{{ fr() ? 'Nom et prénom requis' : 'Name required' }}"><bbc-icon name="x" [s]="14" /></span> }
                            </td>
                            <td class="px-3 py-1.5 font-medium text-ink">{{ r.lastName || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.firstName || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.sex || '—' }}</td>
                            <td class="px-3 py-1.5 font-mono text-xs">{{ r.dob || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.parentName || '—' }}</td>
                            <td class="px-3 py-1.5">{{ r.parentPhone || '—' }}</td>
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
                <button (click)="doImport()" [disabled]="!importClassId() || !validCount() || importing()"
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
  private setupApi = inject(SetupApi);
  private auth = inject(AuthService);

  protected fr = () => this.i18n.lang() === 'fr';

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
  protected selectedId = signal<string | null>(null);

  protected mode = signal<'list' | 'edit' | 'import'>('list');
  protected editId = signal<string | null>(null);
  protected confirmDel = signal<Student | null>(null);

  // Bulk import
  protected importClassId = signal<string | null>(null);
  protected importText = signal('');
  protected importRows = signal<StudentImportRow[]>([]);
  protected importResult = signal<StudentImportResult | null>(null);
  protected importing = signal(false);
  protected importError = signal<string | null>(null);

  protected canWrite = this.auth.can('students', 'write');
  protected draft: StudentUpsert = this.blank();
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
    return this.rows().filter((s) => {
      if (sub && (s.subsystem || '').toUpperCase() !== sub) return false;
      if (lvl && (s.level || '').toLowerCase() !== lvl) return false;
      if (q) {
        const hay = `${s.name} ${s.matricule} ${s.parentName}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  });

  protected selected = computed(() => {
    const id = this.selectedId();
    if (!id) return null;
    return this.rows().find((s) => s.id === id) ?? null;
  });

  protected headerSub = computed(() => {
    const n = this.rows().length;
    return this.fr() ? `${n} élèves inscrits · 2 sous-systèmes` : `${n} enrolled students · 2 subsystems`;
  });

  constructor() {
    this.reload();
    this.setupApi.listClasses().subscribe((c) => this.classes.set(c));

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
      if (!this.selectedId() && r.length) this.selectedId.set(r[0].id);
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
    this.editId.set(null);
    this.draft = this.blank();
    this.mode.set('edit');
  }

  openEdit(s: Student): void {
    this.editId.set(s.id);
    this.draft = {
      firstName: s.firstName,
      lastName: s.lastName,
      sex: s.sex || 'M',
      dob: s.dob,
      classId: s.classId ?? null,
      parentName: s.parentName,
      parentPhone: s.parentPhone,
    };
    this.mode.set('edit');
  }

  closeEditor(): void {
    this.mode.set('list');
    this.editId.set(null);
    this.draft = this.blank();
  }

  save(): void {
    if (!this.draft.firstName || !this.draft.lastName) return;
    const id = this.editId();
    const req = id ? this.api.update(id, this.draft) : this.api.create(this.draft);
    req.subscribe((s) => {
      this.closeEditor();
      this.selectedId.set(s.id);
      this.reload();
    });
  }

  remove(s: Student): void {
    this.api.remove(s.id).subscribe(() => {
      this.confirmDel.set(null);
      if (this.selectedId() === s.id) this.selectedId.set(null);
      this.reload();
    });
  }

  private blank(): StudentUpsert {
    return { firstName: '', lastName: '', sex: 'M', classId: null, parentName: '', parentPhone: '' };
  }

  // ---- Bulk import ---------------------------------------------------------
  protected validCount = computed(() => this.importRows().filter((r) => this.rowValid(r)).length);

  protected rowValid(r: StudentImportRow): boolean {
    return !!r.firstName?.trim() && !!r.lastName?.trim();
  }

  protected openImport(): void {
    this.resetImport();
    // Pre-select the class currently filtered, if any, for convenience.
    this.mode.set('import');
  }

  protected closeImport(): void {
    this.mode.set('list');
    this.resetImport();
  }

  protected resetImport(): void {
    this.importClassId.set(null);
    this.importText.set('');
    this.importRows.set([]);
    this.importResult.set(null);
    this.importError.set(null);
    this.importing.set(false);
  }

  protected onText(text: string): void {
    this.importText.set(text);
    this.importRows.set(this.parseRows(text));
  }

  protected onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { this.onText(String(reader.result ?? '')); input.value = ''; };
    reader.readAsText(file);
  }

  protected loadSample(): void {
    const sample = this.fr()
      ? 'Nom,Prénom,Sexe,Naissance,Parent,Téléphone\nMBALLA,Alice,F,2014-03-12,Paul MBALLA,+237690000001\nNGONO,Jean,M,13/07/2013,Marie NGONO,+237690000002'
      : 'Last name,First name,Sex,DOB,Parent,Phone\nMBALLA,Alice,F,2014-03-12,Paul MBALLA,+237690000001\nNGONO,Jean,M,13/07/2013,Marie NGONO,+237690000002';
    this.onText(sample);
  }

  protected doImport(): void {
    const classId = this.importClassId();
    const rows = this.importRows().filter((r) => this.rowValid(r));
    if (!classId || !rows.length) return;
    this.importing.set(true);
    this.importError.set(null);
    this.api.importStudents({ classId, rows }).subscribe({
      next: (res) => { this.importing.set(false); this.importResult.set(res); this.reload(); },
      error: (e) => { this.importing.set(false); this.importError.set(e?.error?.message ?? (this.fr() ? 'Import impossible.' : 'Import failed.')); },
    });
  }

  private parseRows(text: string): StudentImportRow[] {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length);
    if (!lines.length) return [];
    const delim = this.detectDelim(lines[0]);
    const cells = lines.map((l) => this.splitLine(l, delim));
    const map = this.mapHeader(cells[0]);
    const idx = map ?? { lastName: 0, firstName: 1, sex: 2, dob: 3, parentName: 4, parentPhone: 5 };
    const dataRows = map ? cells.slice(1) : cells;
    return dataRows.map((r) => ({
      lastName: (r[idx.lastName] ?? '').trim(),
      firstName: (r[idx.firstName] ?? '').trim(),
      sex: this.normSex(r[idx.sex]),
      dob: this.normDob(r[idx.dob]),
      parentName: (r[idx.parentName] ?? '').trim(),
      parentPhone: (r[idx.parentPhone] ?? '').trim(),
    }));
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

  /** Map a header row to column indices; null when the first row is data, not a header. */
  private mapHeader(cells: string[]): Record<'lastName' | 'firstName' | 'sex' | 'dob' | 'parentName' | 'parentPhone', number> | null {
    const idx: any = {};
    cells.forEach((raw, i) => {
      const c = raw.toLowerCase();
      if (idx.firstName === undefined && /(pr[ée]nom|first)/.test(c)) idx.firstName = i;
      else if (idx.lastName === undefined && /(^nom|last|surname|famille)/.test(c)) idx.lastName = i;
      else if (idx.sex === undefined && /(sexe|sex|genre|gender)/.test(c)) idx.sex = i;
      else if (idx.dob === undefined && /(naiss|dob|birth|date)/.test(c)) idx.dob = i;
      else if (idx.parentPhone === undefined && /(t[ée]l|phone|contact|num)/.test(c)) idx.parentPhone = i;
      else if (idx.parentName === undefined && /(parent|tuteur|guardian|responsable)/.test(c)) idx.parentName = i;
    });
    if (idx.lastName === undefined || idx.firstName === undefined) return null;
    return {
      lastName: idx.lastName,
      firstName: idx.firstName,
      sex: idx.sex ?? 2,
      dob: idx.dob ?? 3,
      parentName: idx.parentName ?? 4,
      parentPhone: idx.parentPhone ?? 5,
    };
  }

  private normSex(v: string | undefined): string {
    const c = (v ?? '').trim().toLowerCase();
    if (!c) return '';
    if (/^(m|masculin|male|g|gar)/.test(c)) return 'M';
    if (/^(f|f[ée]minin|female|fille)/.test(c)) return 'F';
    return '';
  }

  private normDob(v: string | undefined): string | null {
    const c = (v ?? '').trim();
    if (!c) return null;
    let m = c.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);      // YYYY-MM-DD
    if (m) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
    m = c.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);          // DD/MM/YYYY
    if (m) return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
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
