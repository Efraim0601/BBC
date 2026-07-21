import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  SetupApi, SectionView, SectionUpsert, ClassView, ClassUpsert, SubjectView, SubjectUpsert, TeacherOption,
} from '../../core/setup.api';
import { defaultSubjects } from './subject-defaults';
import { forkJoin } from 'rxjs';
import { IconComponent, CardComponent, TabsComponent, EmptyComponent } from '../../core/ui';

/**
 * Academic Setup — admins build the relational backbone here (sections → classes,
 * and subjects) BEFORE enrolling students. This is what makes the student form's
 * class a locked dropdown instead of free text (review issues #1 and #3).
 */
@Component({
  selector: 'bbc-academic-setup',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, CardComponent, TabsComponent, EmptyComponent],
  template: `
    <bbc-tabs [tabs]="subTabs()" [value]="sub()" (change)="switchTo($any($event))" />

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

          @if (canWrite && secForm()) {
            <form (ngSubmit)="saveSection()" class="grid grid-cols-1 md:grid-cols-4 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
              <label class="block md:col-span-2">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Libellé' : 'Label' }} *</span>
                <input [(ngModel)]="secDraft.label" name="label" required [placeholder]="fr() ? 'Primaire francophone' : 'Francophone primary'"
                  class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Sous-système' : 'Subsystem' }}</span>
                <select [(ngModel)]="secDraft.subsystem" name="subsystem" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
                  <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
                  <option value="EN">{{ fr() ? 'Anglophone' : 'English' }}</option>
                </select>
              </label>
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Niveau' : 'Level' }}</span>
                <select [(ngModel)]="secDraft.level" name="level" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white">
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
          <div action>
            @if (canWrite) {
              <button (click)="newClass()" [disabled]="!sections().length" title="{{ !sections().length ? (fr() ? 'Créez d’abord une section' : 'Create a section first') : '' }}"
                class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white">
                <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle classe' : 'New class' }}
              </button>
            }
          </div>

          @if (canWrite && clsForm()) {
            <form (ngSubmit)="saveClass()" class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4 p-3 rounded-lg bg-slate-50">
              <label class="block">
                <span class="text-xs font-semibold text-ink">{{ fr() ? 'Nom de la classe' : 'Class name' }} *</span>
                <input [(ngModel)]="clsDraft.name" name="cname" required placeholder="6ème A"
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

      <!-- ===================== SUBJECTS ===================== -->
      @case ('subjects') {
        <bbc-card [title]="fr() ? 'Matières & coefficients' : 'Subjects & coefficients'"
          [subtitle]="fr() ? 'Listes distinctes par sous-système, avec coefficient pour les moyennes pondérées' : 'Distinct lists per subsystem, with coefficient for weighted averages'">
          <div action>
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

  protected fr = () => this.i18n.lang() === 'fr';
  protected canWrite = this.auth.can('settings', 'write');

  protected sub = signal<'sections' | 'classes' | 'subjects'>('sections');
  protected sections = signal<SectionView[]>([]);
  protected classes = signal<ClassView[]>([]);
  protected subjects = signal<SubjectView[]>([]);
  protected err = signal<string | null>(null);

  protected subTabs = computed(() => [
    { id: 'sections', label: this.fr() ? 'Sections' : 'Sections' },
    { id: 'classes', label: this.fr() ? 'Classes' : 'Classes' },
    { id: 'subjects', label: this.fr() ? 'Matières' : 'Subjects' },
  ]);

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

  constructor() {
    this.loadSections();
    this.loadClasses();
    this.loadSubjects();
  }

  protected switchTo(t: 'sections' | 'classes' | 'subjects'): void {
    this.sub.set(t);
    this.secForm.set(false); this.clsForm.set(false); this.subjForm.set(false);
  }

  protected levelLabel(l: string): string {
    switch ((l || '').toLowerCase()) {
      case 'maternelle': return this.fr() ? 'Maternelle' : 'Kindergarten';
      case 'secondary': return this.fr() ? 'Secondaire' : 'Secondary';
      default: return this.fr() ? 'Primaire' : 'Primary';
    }
  }
  protected subjectLabel(s: SubjectView): string {
    const l = s.label || {};
    return (this.fr() ? l['fr'] : l['en']) || l['fr'] || l['en'] || s.code;
  }

  private fail = (e: any) => this.err.set(e?.error?.message ?? (this.fr() ? 'Opération impossible.' : 'Operation failed.'));
  private loadSections(): void { this.api.listSections().subscribe((r) => this.sections.set(r)); }
  private loadClasses(): void { this.api.listClasses().subscribe((r) => this.classes.set(r)); }
  private loadSubjects(): void { this.api.listSubjects().subscribe((r) => this.subjects.set(r)); }

  // ---- Sections ----
  protected newSection(): void { this.secEditId.set(null); this.secDraft = { label: '', subsystem: 'FR', level: 'primary' }; this.secForm.set(true); }
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

  // ---- Teacher assignment ----
  protected openTeachers(c: ClassView): void {
    this.err.set(null);
    this.teacherClass.set(c);
    this.api.assignableTeachers().subscribe((t) => this.allTeachers.set(t));
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
