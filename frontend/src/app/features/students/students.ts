import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StudentApi, StudentUpsert } from './students.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { Student } from '../../core/models';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
  AvatarComponent, ChipFilterComponent, StatusPillComponent,
} from '../../core/ui';

@Component({
  selector: 'bbc-students',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, ChipFilterComponent, StatusPillComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('students')" [subtitle]="headerSub()">
        <div right class="flex items-center gap-2">
          <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
            <bbc-icon name="download" [s]="16" /> {{ fr() ? 'Exporter liste' : 'Export list' }}
          </button>
          @if (canWrite) {
            <button (click)="openCreate()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white">
              <bbc-icon name="plus" [s]="16" /> {{ i18n.t('newStudent') }}
            </button>
          }
        </div>
      </bbc-page-header>

      <!-- Toolbar / filters -->
      <bbc-card className="mb-5">
        <div class="flex items-center gap-3 flex-wrap">
          <div class="relative">
            <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
            <input [ngModel]="search()" (ngModelChange)="search.set($event)"
              [placeholder]="fr() ? 'Rechercher un élève, parent, matricule…' : 'Search student, parent, ID…'"
              class="h-9 pl-9 pr-3 text-sm rounded-lg border border-slate-200 bg-white w-72 focus:outline-none focus:border-brand-400" />
          </div>
          <div class="flex items-center gap-1.5 ml-auto flex-wrap">
            <bbc-chip-filter [allLabel]="fr() ? 'Tout' : 'All'" [value]="subFilter()" (change)="subFilter.set($event)"
              [options]="[{value:'FR',label:'FR'},{value:'EN',label:'EN'}]" />
            <span class="text-slate-300">|</span>
            <bbc-chip-filter [allLabel]="fr() ? 'Tout' : 'All'" [value]="levelFilter()" (change)="levelFilter.set($event)"
              [options]="levelOptions()" />
          </div>
        </div>
      </bbc-card>

      <div class="grid grid-cols-12 gap-4">
        <!-- List -->
        <bbc-card className="col-span-12 lg:col-span-5">
         <div class="-m-5">
          <div class="px-5 py-3 border-b border-slate-100 flex items-center justify-between">
            <div class="text-sm font-semibold">{{ filtered().length }} {{ fr() ? 'résultats' : 'results' }}</div>
            <div class="text-xs text-mute">{{ fr() ? 'Cliquez pour ouvrir la fiche' : 'Click to open profile' }}</div>
          </div>
          <div class="max-h-[640px] overflow-y-auto">
            @for (s of filtered().slice(0, 60); track s.id) {
              <button (click)="selectedId.set(s.id)"
                class="w-full flex items-center gap-3 px-5 py-3 border-b border-slate-50 last:border-0 text-left transition"
                [class]="s.id === selectedId() ? 'bg-brand-50' : 'hover:bg-slate-50'">
                <bbc-avatar [name]="s.name" [hue]="s.photoHue" [size]="40" />
                <div class="flex-1 min-w-0">
                  <div class="font-semibold text-ink truncate">{{ s.name }}</div>
                  <div class="text-xs text-mute truncate">{{ s.className }} · {{ s.subsystem }} · {{ s.matricule }}</div>
                </div>
                @if (s.sex) { <bbc-status-pill status="ok" [label]="sexLabel(s.sex)" /> }
              </button>
            } @empty {
              <bbc-empty icon="search" [label]="fr() ? 'Aucun résultat' : 'No results'" />
            }
          </div>
         </div>
        </bbc-card>

        <!-- Detail panel -->
        @if (selected(); as sel) {
          <bbc-card className="col-span-12 lg:col-span-7 overflow-hidden">
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
      </div>
    </div>

    <!-- Create / edit modal -->
    @if (editing()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 fade-in" (click)="closeEditor()">
        <div class="bg-white rounded-xl2 shadow-card w-full max-w-lg" (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <div class="text-[15px] font-semibold text-ink">{{ editId() ? (fr() ? 'Modifier l’élève' : 'Edit student') : i18n.t('newStudent') }}</div>
            <button (click)="closeEditor()" class="text-mute hover:text-ink"><bbc-icon name="x" [s]="18" /></button>
          </div>
          <div class="p-5 grid grid-cols-2 gap-3">
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Nom' : 'Last name' }}</span>
              <input [(ngModel)]="draft.lastName" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Prénom' : 'First name' }}</span>
              <input [(ngModel)]="draft.firstName" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Sexe' : 'Sex' }}</span>
              <select [(ngModel)]="draft.sex" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                <option value="M">{{ fr() ? 'Masculin' : 'Male' }}</option>
                <option value="F">{{ fr() ? 'Féminin' : 'Female' }}</option>
              </select>
            </label>
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Classe' : 'Class' }}</span>
              <input [(ngModel)]="draft.className" placeholder="6ème" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Sous-système' : 'Subsystem' }}</span>
              <select [(ngModel)]="draft.subsystem" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                <option value="FR">FR</option>
                <option value="EN">EN</option>
              </select>
            </label>
            <label class="block">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Niveau' : 'Level' }}</span>
              <select [(ngModel)]="draft.level" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
                <option value="secondary">{{ fr() ? 'Secondaire' : 'Secondary' }}</option>
              </select>
            </label>
            <label class="block col-span-2">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Parent' : 'Parent' }}</span>
              <input [(ngModel)]="draft.parentName" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
            <label class="block col-span-2">
              <span class="text-[11px] text-mute font-semibold">{{ fr() ? 'Téléphone parent' : 'Parent phone' }}</span>
              <input [(ngModel)]="draft.parentPhone" class="mt-1 w-full h-10 px-3 rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
            </label>
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-100">
            <button (click)="closeEditor()" class="h-9 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
            <button (click)="save()" class="h-9 px-4 rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold">{{ i18n.t('save') }}</button>
          </div>
        </div>
      </div>
    }

    <!-- Confirm delete modal -->
    @if (confirmDel(); as cd) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 fade-in" (click)="confirmDel.set(null)">
        <div class="bg-white rounded-xl2 shadow-card w-full max-w-md p-6" (click)="$event.stopPropagation()">
          <div class="flex items-start gap-3">
            <div class="w-10 h-10 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
              <bbc-icon name="alertTri" [s]="20" />
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
  `,
})
export class StudentsComponent {
  protected i18n = inject(I18nService);
  private api = inject(StudentApi);
  private auth = inject(AuthService);

  protected fr = () => this.i18n.lang() === 'fr';

  protected rows = signal<Student[]>([]);
  protected search = signal('');
  protected subFilter = signal<string | null>(null);
  protected levelFilter = signal<string | null>(null);
  protected selectedId = signal<string | null>(null);

  protected editing = signal(false);
  protected editId = signal<string | null>(null);
  protected confirmDel = signal<Student | null>(null);

  protected canWrite = this.auth.can('students', 'write');
  protected draft: StudentUpsert = this.blank();

  protected levelOptions = computed(() => [
    { value: 'primary', label: this.fr() ? 'Primaire' : 'Primary' },
    { value: 'secondary', label: this.fr() ? 'Secondaire' : 'Secondary' },
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
    const list = this.filtered();
    const id = this.selectedId();
    return list.find((s) => s.id === id) ?? list[0] ?? null;
  });

  protected headerSub = computed(() => {
    const n = this.rows().length;
    return this.fr() ? `${n} élèves inscrits · 2 sous-systèmes` : `${n} enrolled students · 2 subsystems`;
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.api.list().subscribe((r) => {
      this.rows.set(r);
      if (!this.selectedId() && r.length) this.selectedId.set(r[0].id);
    });
  }

  protected sexLabel(sex: string): string {
    if (!sex) return '—';
    return sex.toUpperCase().startsWith('M') ? (this.fr() ? 'Masculin' : 'Male') : (this.fr() ? 'Féminin' : 'Female');
  }

  protected subsystemLabel(sub: string): string {
    return (sub || '').toUpperCase().startsWith('F') ? (this.fr() ? 'Francophone' : 'Francophone') : (this.fr() ? 'Anglophone' : 'English');
  }

  protected sectionLabel(s: Student): string {
    const prim = (s.level || '').toLowerCase() === 'primary';
    const f = (s.subsystem || '').toUpperCase().startsWith('F');
    if (f) return prim ? 'Primaire FR' : 'Secondaire FR';
    return prim ? 'Primary EN' : 'Secondary EN';
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
    this.editing.set(true);
  }

  openEdit(s: Student): void {
    this.editId.set(s.id);
    this.draft = {
      firstName: s.firstName,
      lastName: s.lastName,
      sex: s.sex || 'M',
      dob: s.dob,
      className: s.className,
      subsystem: s.subsystem || 'FR',
      level: s.level || 'primary',
      parentName: s.parentName,
      parentPhone: s.parentPhone,
    };
    this.editing.set(true);
  }

  closeEditor(): void {
    this.editing.set(false);
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
    return { firstName: '', lastName: '', sex: 'M', className: '', subsystem: 'FR', level: 'primary', parentName: '', parentPhone: '' };
  }
}
