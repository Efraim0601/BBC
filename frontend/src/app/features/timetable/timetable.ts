import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TimetableApi, ClassRef, SlotView, Conflict } from './timetable.api';
import { SetupApi, SubjectView, TeacherOption } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
} from '../../core/ui';

interface SlotDraft {
  dayIdx: number;
  slotIdx: number;
  subjectCode: string;
  room: string;
  teacherId: string;
  existing: boolean;
}

const DAYS_FR = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi'];
const DAYS_EN = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const SLOT_TIMES = ['07:30', '08:30', '09:30', '10:30', '11:30', '12:30', '13:30', '14:30', '15:30'];
const SLOTS = [0, 1, 2, 3, 4, 5, 6, 7, 8];

// Palette ported from the prototype's TT_SUBJECT_COLOR — keyed by subject code,
// with a deterministic fallback for any code the backend returns.
const SUBJECT_COLORS = [
  'bg-brand-100 text-brand-700 border-brand-200',
  'bg-rose-100 text-rose-700 border-rose-200',
  'bg-emerald-100 text-emerald-700 border-emerald-200',
  'bg-amber-100 text-amber-800 border-amber-200',
  'bg-teal-100 text-teal-700 border-teal-200',
  'bg-violet-100 text-violet-700 border-violet-200',
  'bg-orange-100 text-orange-700 border-orange-200',
  'bg-pink-100 text-pink-700 border-pink-200',
];
const KNOWN_COLORS: Record<string, string> = {
  MATH: SUBJECT_COLORS[0],
  FR: SUBJECT_COLORS[1],
  EN: SUBJECT_COLORS[2],
  HG: SUBJECT_COLORS[3],
  SVT: SUBJECT_COLORS[4],
  PC: SUBJECT_COLORS[5],
  EPS: SUBJECT_COLORS[6],
  ART: SUBJECT_COLORS[7],
  BREAK: 'bg-slate-100 text-mute border-slate-200',
};

@Component({
  selector: 'bbc-timetable',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('timetable')" [subtitle]="headerSub()">
        <div right class="flex items-center gap-2">
          @if (canWrite) {
            <span class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-gold-50 text-gold-600 border border-gold-200">
              <bbc-icon name="edit" [s]="16" /> {{ fr() ? 'Mode édition' : 'Edit mode' }}
            </span>
          }
          <button class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
            <bbc-icon name="printer" [s]="16" /> {{ fr() ? 'Imprimer' : 'Print' }}
          </button>
        </div>
      </bbc-page-header>

      @if (canWrite) {
        <div class="bg-cyan-50 border border-cyan-200 rounded-xl2 p-3 flex items-center gap-3 mb-5">
          <div class="w-9 h-9 rounded-full bg-white flex items-center justify-center shrink-0 text-cyan-600">
            <bbc-icon name="calendar" [s]="18" />
          </div>
          <div class="flex-1 text-sm text-cyan-800">
            {{ fr()
              ? 'Vous pouvez créer et modifier l’emploi du temps de toutes les classes. Le système vous alerte si un enseignant est programmé dans deux salles à la même heure.'
              : 'You can create and edit every class timetable. The system alerts you if a teacher is booked in two rooms at the same time.' }}
          </div>
        </div>
      }

      <!-- Class picker -->
      <bbc-card className="mb-5">
        <div class="flex items-start gap-3 flex-wrap">
          <div class="text-xs font-semibold text-mute uppercase pt-2.5">{{ fr() ? 'Classe' : 'Class' }} :</div>
          <div class="flex-1 min-w-[280px]">
            <select
              [(ngModel)]="selectedClassModel"
              (ngModelChange)="onClassChange($event)"
              class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-semibold">
              <option value="">{{ fr() ? '— Choisir une classe —' : '— Pick a class —' }}</option>
              @for (c of classes(); track c.id) {
                <option [value]="c.name">{{ c.name }}</option>
              }
            </select>
          </div>
          @if (canWrite && selectedClass()) {
            <div class="flex items-center gap-2 text-xs text-gold-600 font-semibold pt-2.5">
              <bbc-icon name="edit" [s]="14" />
              {{ fr() ? 'Cliquez sur une case pour modifier' : 'Click a cell to edit' }}
            </div>
          }
        </div>
      </bbc-card>

      <!-- Conflict banner -->
      @if (conflicts().length > 0) {
        <div class="bg-rose-50 border border-rose-200 rounded-xl2 p-4 mb-5 fade-in">
          <div class="flex items-center gap-2 text-rose-700 font-semibold text-sm mb-2">
            <bbc-icon name="alertTri" [s]="18" />
            {{ fr() ? 'Conflits d’enseignant détectés' : 'Teacher clashes detected' }}
          </div>
          <div class="space-y-1.5">
            @for (cf of conflicts(); track $index) {
              <div class="flex items-start gap-2 text-sm text-rose-700">
                <bbc-icon name="alertTri" [s]="14" />
                <span>
                  {{ cf.className }} — {{ dayName(cf.dayIdx) }} · {{ slotTime(cf.slotIdx) }}
                  ({{ fr() ? 'enseignant' : 'teacher' }} {{ teacherLabel(cf.teacherId) }})
                </span>
              </div>
            }
          </div>
        </div>
      }

      @if (selectedClass()) {
        <bbc-card>
          <div class="overflow-x-auto">
            <div class="grid" [style.gridTemplateColumns]="gridCols">
              <!-- header row -->
              <div class="bg-slate-50 border-b border-slate-100 p-2 text-[11px] uppercase tracking-wide text-mute font-bold sticky left-0 z-10">
                {{ fr() ? 'Heure' : 'Time' }}
              </div>
              @for (s of slotIdxs; track s) {
                <div class="bg-slate-50 border-b border-l border-slate-100 p-2 text-center text-[11px] font-bold text-mute">
                  {{ slotTime(s) }}
                </div>
              }

              <!-- day rows -->
              @for (d of dayIdxs; track d) {
                <div class="bg-brand-50 border-b border-slate-100 p-2 text-sm font-bold text-brand-700 sticky left-0 z-10 flex items-center">
                  {{ dayName(d) }}
                </div>
                @for (s of slotIdxs; track s) {
                  <div class="border-b border-l border-slate-100 p-1.5 relative">
                    @if (slotAt(d, s); as slot) {
                      <button
                        [disabled]="!canWrite"
                        (click)="onCellClick(d, s)"
                        class="w-full h-full text-left rounded-md border px-2 py-1.5 transition min-h-[58px]"
                        [class]="subjectColor(slot.subjectCode)"
                        [class.hover:shadow-md]="canWrite"
                        [class.hover:scale-[1.02]]="canWrite"
                        [class.ring-2]="isConflict(d, s)"
                        [class.ring-rose-500]="isConflict(d, s)">
                        <div class="text-xs font-bold leading-tight flex items-center justify-between gap-1">
                          <span>{{ subjectDisplay(slot.subjectCode) }}</span>
                          @if (isConflict(d, s)) {
                            <span class="text-rose-600 shrink-0"><bbc-icon name="alertTri" [s]="11" /></span>
                          }
                        </div>
                        @if (slot.teacherId) {
                          <div class="text-[10px] opacity-80 mt-0.5 truncate">{{ teacherLabel(slot.teacherId) }}</div>
                        }
                        @if (slot.room) {
                          <div class="text-[9px] opacity-60 mt-0.5">{{ slot.room }}</div>
                        }
                      </button>
                    } @else if (canWrite) {
                      <button
                        (click)="onCellClick(d, s)"
                        class="w-full bg-white hover:bg-gold-50 group transition">
                        <div class="w-full h-full border-2 border-dashed border-slate-200 rounded-md flex items-center justify-center text-mute group-hover:text-gold-600 group-hover:border-gold-300 min-h-[58px]">
                          <bbc-icon name="plus" [s]="18" />
                        </div>
                      </button>
                    } @else {
                      <div class="w-full bg-white min-h-[58px]"></div>
                    }
                  </div>
                }
              }
            </div>
          </div>
        </bbc-card>

        <!-- Slot editor -->
        @if (canWrite && draft(); as d) {
          <bbc-card className="mt-5 fade-in"
            [title]="d.existing ? (fr() ? 'Modifier le créneau' : 'Edit slot') : (fr() ? 'Nouveau créneau' : 'New slot')">
            <div class="space-y-4">
              <div class="bg-brand-50 border border-brand-100 rounded-lg p-3 flex items-center gap-3">
                <div class="w-10 h-10 rounded-lg bg-brand-600 text-white flex items-center justify-center shrink-0">
                  <bbc-icon name="calendar" [s]="18" />
                </div>
                <div>
                  <div class="text-sm font-bold text-brand-700">
                    {{ selectedClass() }} — {{ dayName(d.dayIdx) }} · {{ slotTime(d.slotIdx) }}
                  </div>
                  <div class="text-xs text-mute">
                    {{ fr() ? 'Sélectionnez la matière, l’enseignant et la salle' : 'Select subject, teacher and room' }}
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">
                    {{ fr() ? 'Matière' : 'Subject' }}
                  </label>
                  <select [(ngModel)]="d.subjectCode"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="">{{ fr() ? '— Choisir —' : '— Pick —' }}</option>
                    @if (d.subjectCode && !subjectsForClass().some(s => s.code === d.subjectCode)) {
                      <option [value]="d.subjectCode">{{ d.subjectCode }}</option>
                    }
                    @for (s of subjectsForClass(); track s.id) {
                      <option [value]="s.code">{{ subjectLabel(s) }} ({{ s.code }})</option>
                    }
                  </select>
                </div>
                <div>
                  <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">
                    {{ fr() ? 'Enseignant' : 'Teacher' }}
                  </label>
                  <select [(ngModel)]="d.teacherId"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400">
                    <option value="">{{ fr() ? '— Aucun —' : '— None —' }}</option>
                    @if (d.teacherId && !teachers().some(t => t.id === d.teacherId)) {
                      <option [value]="d.teacherId">{{ d.teacherId }}</option>
                    }
                    @for (t of teachers(); track t.id) {
                      <option [value]="t.id">{{ t.name }}{{ t.code ? ' (' + t.code + ')' : '' }}</option>
                    }
                  </select>
                </div>
                <div>
                  <label class="block text-xs font-semibold text-mute uppercase tracking-wide mb-1.5">
                    {{ fr() ? 'Salle' : 'Room' }}
                  </label>
                  <input [(ngModel)]="d.room" list="tt-rooms" placeholder="S1, Lab…"
                    class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
                  <datalist id="tt-rooms">
                    @for (r of knownRooms(); track r) {
                      <option [value]="r"></option>
                    }
                  </datalist>
                </div>
              </div>

              <div class="flex items-center gap-2">
                @if (d.existing) {
                  <button (click)="remove()"
                    class="inline-flex items-center gap-2 h-10 px-3.5 text-sm font-semibold rounded-lg bg-rose-50 text-rose-600 border border-rose-200 hover:bg-rose-100">
                    <bbc-icon name="trash" [s]="16" /> {{ fr() ? 'Supprimer' : 'Delete' }}
                  </button>
                }
                <div class="flex-1"></div>
                <button (click)="cancel()"
                  class="inline-flex items-center gap-2 h-10 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
                  {{ i18n.t('cancel') }}
                </button>
                <button (click)="save()"
                  class="inline-flex items-center gap-2 h-10 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                  <bbc-icon name="check" [s]="16" [sw]="2.5" /> {{ i18n.t('save') }}
                </button>
              </div>
            </div>
          </bbc-card>
        }
      } @else {
        <bbc-card>
          <bbc-empty icon="calendar"
            [label]="fr() ? 'Sélectionnez une classe pour afficher l’emploi du temps.' : 'Pick a class to display its timetable.'" />
        </bbc-card>
      }
    </div>
  `,
})
export class TimetableComponent {
  protected i18n = inject(I18nService);
  private api = inject(TimetableApi);
  private setupApi = inject(SetupApi);
  private auth = inject(AuthService);

  protected readonly dayIdxs = DAYS_FR.map((_, i) => i);
  protected readonly slotIdxs = SLOTS;
  protected readonly gridCols = `80px repeat(${SLOTS.length}, minmax(120px, 1fr))`;

  protected classes = signal<ClassRef[]>([]);
  protected subjects = signal<SubjectView[]>([]);
  protected teachers = signal<TeacherOption[]>([]);
  protected knownRooms = signal<string[]>([]);
  protected slots = signal<SlotView[]>([]);
  protected selectedClass = signal<string>('');
  protected draft = signal<SlotDraft | null>(null);
  protected conflicts = signal<Conflict[]>([]);

  protected canWrite = this.auth.can('timetable', 'write');

  // Plain mirror for [(ngModel)] on the <select>.
  protected selectedClassModel = '';

  protected fr = () => this.i18n.lang() === 'fr';

  protected subjectsForClass = computed(() => {
    const name = this.selectedClass();
    const cls = this.classes().find((c) => c.name === name);
    const all = this.subjects();
    if (!cls?.subsystem) return all;
    return all.filter((s) => !s.subsystem || s.subsystem === cls.subsystem);
  });

  protected headerSub = computed(() => {
    const cls = this.selectedClass();
    if (!cls) {
      return this.fr() ? 'Grille hebdomadaire' : 'Weekly grid';
    }
    if (this.canWrite) {
      return this.fr() ? `Création & édition — Classe ${cls}` : `Create & edit — Class ${cls}`;
    }
    return this.fr() ? `Grille hebdomadaire — Classe ${cls}` : `Weekly grid — Class ${cls}`;
  });

  constructor() {
    this.api.classes().subscribe((c) => this.classes.set(c));
    this.api.rooms().subscribe((r) => this.knownRooms.set(r));
    this.setupApi.listSubjects().subscribe((s) => this.subjects.set(s));
    this.setupApi.assignableTeachers().subscribe((t) => this.teachers.set(t));
  }

  protected dayName(idx: number): string {
    return (this.fr() ? DAYS_FR : DAYS_EN)[idx] ?? '';
  }

  protected slotTime(idx: number): string {
    return SLOT_TIMES[idx] ?? `${idx + 1}`;
  }

  protected subjectLabel(s: SubjectView): string {
    const l = s.label || {};
    return (this.fr() ? l['fr'] : l['en']) || l['fr'] || l['en'] || s.code;
  }

  protected subjectDisplay(code: string | null): string {
    if (!code) return '—';
    const s = this.subjects().find((x) => x.code === code);
    return s ? this.subjectLabel(s) : code;
  }

  protected teacherLabel(id: string | null): string {
    if (!id) return '—';
    const t = this.teachers().find((x) => x.id === id);
    return t ? t.name : id;
  }

  protected subjectColor(code: string | null): string {
    if (!code) return 'bg-slate-100 text-mute border-slate-200';
    const upper = code.toUpperCase();
    if (KNOWN_COLORS[upper]) return KNOWN_COLORS[upper];
    let h = 0;
    for (let i = 0; i < upper.length; i++) h = (h * 31 + upper.charCodeAt(i)) >>> 0;
    return SUBJECT_COLORS[h % SUBJECT_COLORS.length];
  }

  protected isConflict(dayIdx: number, slotIdx: number): boolean {
    const name = this.selectedClass();
    return this.conflicts().some(
      (c) => c.className === name && c.dayIdx === dayIdx && c.slotIdx === slotIdx,
    );
  }

  protected onClassChange(name: string): void {
    this.selectedClass.set(name);
    this.draft.set(null);
    this.conflicts.set([]);
    this.slots.set([]);
    if (name) this.reload();
  }

  private reload(): void {
    const name = this.selectedClass();
    if (!name) return;
    this.api.grid(name).subscribe((s) => {
      this.slots.set(s);
      const next = new Set(this.knownRooms());
      for (const slot of s) {
        if (slot.room) next.add(slot.room);
      }
      this.knownRooms.set([...next].sort((a, b) => a.localeCompare(b)));
    });
  }

  protected slotAt(dayIdx: number, slotIdx: number): SlotView | undefined {
    return this.slots().find((s) => s.dayIdx === dayIdx && s.slotIdx === slotIdx);
  }

  protected onCellClick(dayIdx: number, slotIdx: number): void {
    if (!this.canWrite) return;
    const existing = this.slotAt(dayIdx, slotIdx);
    this.draft.set({
      dayIdx,
      slotIdx,
      subjectCode: existing?.subjectCode ?? '',
      room: existing?.room ?? '',
      teacherId: existing?.teacherId ?? '',
      existing: !!existing,
    });
  }

  protected save(): void {
    const d = this.draft();
    const name = this.selectedClass();
    if (!d || !name) return;
    this.api
      .saveSlot({
        className: name,
        dayIdx: d.dayIdx,
        slotIdx: d.slotIdx,
        subjectCode: d.subjectCode || undefined,
        room: d.room || undefined,
        teacherId: d.teacherId || undefined,
      })
      .subscribe((res) => {
        this.conflicts.set(res.conflicts);
        this.draft.set(null);
        this.reload();
      });
  }

  protected remove(): void {
    const d = this.draft();
    const name = this.selectedClass();
    if (!d || !name) return;
    this.api.deleteSlot(name, d.dayIdx, d.slotIdx).subscribe(() => {
      this.draft.set(null);
      this.reload();
    });
  }

  protected cancel(): void {
    this.draft.set(null);
  }
}
