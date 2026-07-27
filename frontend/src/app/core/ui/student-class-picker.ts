import {
  Component, ChangeDetectionStrategy, inject, input, output, signal, computed,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SetupApi, ClassView } from '../setup.api';
import { StudentApi } from '../../features/students/students.api';
import { Student } from '../models';
import { I18nService } from '../i18n.service';
import { AvatarComponent, EmptyComponent } from './ui';

/**
 * Class-first student picker: choose a class from Setup, then pick a student
 * from that class only (avoids scrolling 500+ school-wide names).
 */
@Component({
  selector: 'bbc-student-class-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, AvatarComponent, EmptyComponent],
  template: `
    <div class="space-y-2">
      <select [ngModel]="className()" (ngModelChange)="onClassChange($event)"
        class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-semibold">
        <option value="">{{ fr() ? '— Choisir une classe —' : '— Pick a class —' }}</option>
        @for (c of classes(); track c.id) {
          <option [value]="c.name">{{ c.name }}</option>
        }
      </select>

      @if (className()) {
        <input [ngModel]="query()" (ngModelChange)="query.set($event)"
          [placeholder]="fr() ? 'Rechercher dans la classe…' : 'Search in class…'"
          class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
        <div class="space-y-1 max-h-[28rem] overflow-y-auto pr-1">
          @for (s of filtered(); track s.id) {
            <button type="button" (click)="pick(s)"
              class="w-full flex items-center gap-2.5 p-2 rounded-lg text-left transition"
              [class]="selectedId() === s.id ? 'bg-brand-50 border border-brand-200' : 'hover:bg-slate-50 border border-transparent'">
              <bbc-avatar [name]="s.name" [hue]="s.photoHue" />
              <div class="flex-1 min-w-0">
                <div class="text-sm font-semibold text-ink truncate">{{ s.name }}</div>
                <div class="text-[11px] text-mute font-mono">{{ s.matricule }}</div>
              </div>
            </button>
          } @empty {
            <bbc-empty icon="users"
              [label]="loading()
                ? (fr() ? 'Chargement…' : 'Loading…')
                : (fr() ? 'Aucun élève dans cette classe' : 'No students in this class')" />
          }
        </div>
      } @else {
        <bbc-empty icon="users"
          [label]="fr() ? 'Choisissez d’abord une classe.' : 'Pick a class first.'" />
      }
    </div>
  `,
})
export class StudentClassPickerComponent {
  private setupApi = inject(SetupApi);
  private studentApi = inject(StudentApi);
  private i18n = inject(I18nService);

  /** Currently selected student id (highlight). */
  readonly selectedId = input<string | null>(null);
  /** Emits when the user picks a student. */
  readonly select = output<Student>();
  /** Emits when the class filter changes (null/empty = cleared). */
  readonly classChange = output<string>();

  protected classes = signal<ClassView[]>([]);
  protected className = signal('');
  protected students = signal<Student[]>([]);
  protected query = signal('');
  protected loading = signal(false);

  protected fr = () => this.i18n.lang() === 'fr';

  protected filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.students();
    if (!q) return list;
    return list.filter((s) =>
      s.name.toLowerCase().includes(q) || s.matricule.toLowerCase().includes(q));
  });

  constructor() {
    this.setupApi.listClasses().subscribe({
      next: (c) => this.classes.set(c),
      error: () => this.classes.set([]),
    });
  }

  protected onClassChange(name: string): void {
    this.className.set(name);
    this.query.set('');
    this.students.set([]);
    this.classChange.emit(name);
    if (!name) return;
    this.loading.set(true);
    this.studentApi.list(name).subscribe({
      next: (r) => { this.students.set(r); this.loading.set(false); },
      error: () => { this.students.set([]); this.loading.set(false); },
    });
  }

  protected pick(s: Student): void {
    this.select.emit(s);
  }

  /** Programmatic class filter (e.g. sync from parent). */
  setClass(name: string): void {
    if (name === this.className()) return;
    this.onClassChange(name);
  }
}
