import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CoursebookApi, ClassRef, EntryView, EntryUpsert } from './coursebook.api';
import { SubjectView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, KpiComponent,
  ListPaginationComponent, paginateRows,
} from '../../core/ui';

interface DayGroup { date: string; entries: EntryView[]; }

@Component({
  selector: 'bbc-coursebook',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, KpiComponent,
    ListPaginationComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Cahier de textes' : 'Coursebook'"
        [subtitle]="fr() ? 'Journal de classe & devoirs — jour par jour' : 'Daily class log & homework — day by day'">
        <div right class="flex items-center gap-2">
          @if (canWrite && selectedClass()) {
            <button (click)="toggleForm()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle entrée' : 'New entry' }}
            </button>
          }
        </div>
      </bbc-page-header>

      <!-- Class selector + KPIs -->
      <bbc-card className="mb-4">
        <div class="flex flex-col md:flex-row md:items-end gap-3">
          <div class="flex-1">
            <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">
              {{ fr() ? 'Classe' : 'Class' }}
            </label>
            <select [ngModel]="selectedClass()" (ngModelChange)="selectClass($event)"
              class="w-full md:w-72 h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
              <option value="">{{ fr() ? '— Choisir une classe —' : '— Choose a class —' }}</option>
              @for (c of classes(); track c.id) {
                <option [value]="c.name">{{ c.name }}</option>
              }
            </select>
          </div>
          @if (selectedClass()) {
            <div class="grid grid-cols-2 gap-3 md:w-80">
              <bbc-kpi [label]="fr() ? 'Entrées' : 'Entries'" [value]="entries().length.toString()" icon="book" />
              <bbc-kpi [label]="fr() ? 'Devoirs' : 'Homework'" [value]="homeworkCount().toString()" icon="doc" />
            </div>
          }
        </div>
      </bbc-card>

      <!-- Add / edit form -->
      @if (canWrite && showForm() && selectedClass()) {
        <bbc-card className="mb-4"
          [title]="editingId() ? (fr() ? 'Modifier l’entrée' : 'Edit entry') : (fr() ? 'Nouvelle entrée' : 'New entry')"
          [subtitle]="selectedClass()!">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
            <div>
              <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">{{ fr() ? 'Matière' : 'Subject' }}</label>
              <select [(ngModel)]="draft.subjectCode"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                @for (s of subjectsForClass(); track s.id) {
                  <option [value]="s.code">{{ subjectLabel(s) }}</option>
                }
              </select>
            </div>
            <div>
              <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">{{ fr() ? 'Date du cours' : 'Lesson date' }}</label>
              <input [(ngModel)]="draft.entryDate" type="date"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
            </div>
            <div class="md:col-span-2">
              <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">{{ fr() ? 'Contenu du cours' : 'Lesson content' }}</label>
              <textarea [(ngModel)]="draft.content" rows="3"
                [placeholder]="fr() ? 'Ce qui a été traité en classe…' : 'What was covered in class…'"
                class="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400"></textarea>
            </div>
            <div class="md:col-span-2">
              <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">{{ fr() ? 'Devoir à faire' : 'Homework' }}</label>
              <textarea [(ngModel)]="draft.homework" rows="2"
                [placeholder]="fr() ? 'Travail à faire (facultatif)…' : 'Assignment (optional)…'"
                class="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400"></textarea>
            </div>
            <div>
              <label class="block text-[11px] font-semibold uppercase tracking-wide text-mute mb-1">{{ fr() ? 'À rendre le' : 'Due date' }}</label>
              <input [(ngModel)]="draft.dueDate" type="date"
                class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
            </div>
            <div class="md:col-span-2 flex items-center justify-end gap-2">
              <button (click)="cancel()"
                class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                {{ i18n.t('cancel') }}
              </button>
              <button (click)="save()"
                class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                <bbc-icon name="check" [s]="16" /> {{ i18n.t('save') }}
              </button>
            </div>
          </div>
        </bbc-card>
      }

      <!-- Log -->
      @if (!selectedClass()) {
        <bbc-card>
          <bbc-empty icon="book" [label]="fr() ? 'Sélectionnez une classe pour voir son cahier de textes' : 'Select a class to see its coursebook'" />
        </bbc-card>
      } @else if (entries().length === 0) {
        <bbc-card>
          <bbc-empty icon="calendar" [label]="fr() ? 'Aucune entrée pour cette classe' : 'No entry for this class'" />
        </bbc-card>
      } @else {
        <bbc-card className="mb-4" [title]="fr() ? 'Retrouver une entrée' : 'Find an entry'"
          [subtitle]="filteredEntries().length + (fr() ? ' entrée(s) trouvée(s)' : ' matching entries')">
          <div class="grid grid-cols-1 gap-3 md:grid-cols-[1.5fr_1fr_1fr_1fr_auto] md:items-end">
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Recherche' : 'Search' }}</span>
              <div class="relative">
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
                <input [ngModel]="query()" (ngModelChange)="setQuery($event)"
                  [placeholder]="fr() ? 'Matière, cours ou devoir…' : 'Subject, lesson or homework…'"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 text-sm focus:border-brand-400 focus:outline-none" />
              </div>
            </label>
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Matière' : 'Subject' }}</span>
              <select [ngModel]="subjectFilter()" (ngModelChange)="setSubjectFilter($event)"
                class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
                <option value="">{{ fr() ? 'Toutes les matières' : 'All subjects' }}</option>
                @for (subject of entrySubjects(); track subject.code) { <option [value]="subject.code">{{ subject.label }}</option> }
              </select>
            </label>
            <label class="block"><span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Du' : 'From' }}</span>
              <input type="date" [ngModel]="dateFrom()" (ngModelChange)="setDateFrom($event)" class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm" />
            </label>
            <label class="block"><span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Au' : 'To' }}</span>
              <input type="date" [ngModel]="dateTo()" (ngModelChange)="setDateTo($event)" class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm" />
            </label>
            <button type="button" (click)="clearFilters()" [disabled]="!hasFilters()"
              class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
              {{ fr() ? 'Effacer' : 'Clear' }}
            </button>
          </div>
        </bbc-card>
        @if (!filteredEntries().length) {
          <bbc-card><bbc-empty icon="book" [label]="fr() ? 'Aucune entrée ne correspond aux filtres' : 'No entry matches these filters'" /></bbc-card>
        } @else {
        <div class="space-y-4">
          @for (g of pagedGroups(); track g.date) {
            <bbc-card [title]="formatDate(g.date)"
              [subtitle]="g.entries.length + (fr() ? ' matière(s)' : ' subject(s)')">
              <div class="space-y-3">
                @for (e of g.entries; track e.id) {
                  <div class="rounded-lg border border-slate-100 p-3 hover:bg-slate-50/50 group">
                    <div class="flex items-start gap-3">
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 flex-wrap mb-1">
                          <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-brand-50 text-brand-700">
                            {{ e.subjectLabel }}
                          </span>
                        </div>
                        <div class="text-sm text-ink whitespace-pre-line">{{ e.content }}</div>
                        @if (e.homework) {
                          <div class="mt-2 rounded-lg border border-amber-200 bg-amber-50 p-2.5">
                            <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-amber-700 mb-0.5">
                              <bbc-icon name="doc" [s]="13" /> {{ fr() ? 'Devoir' : 'Homework' }}
                              @if (e.dueDate) {
                                <span class="ml-auto inline-flex items-center gap-1 normal-case font-semibold text-amber-800">
                                  <bbc-icon name="clock" [s]="12" /> {{ fr() ? 'à rendre le' : 'due' }} {{ formatDate(e.dueDate) }}
                                </span>
                              }
                            </div>
                            <div class="text-sm text-amber-900 whitespace-pre-line">{{ e.homework }}</div>
                          </div>
                        }
                      </div>
                      @if (canWrite) {
                        <div class="flex items-center gap-1 self-start opacity-0 group-hover:opacity-100 transition">
                          <button (click)="edit(e)"
                            class="w-7 h-7 rounded text-mute hover:text-brand-600 hover:bg-brand-50 flex items-center justify-center"
                            [title]="fr() ? 'Modifier' : 'Edit'">
                            <bbc-icon name="edit" [s]="14" />
                          </button>
                          <button (click)="remove(e)"
                            class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center"
                            [title]="fr() ? 'Supprimer' : 'Delete'">
                            <bbc-icon name="trash" [s]="14" />
                          </button>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            </bbc-card>
          }
        </div>
        <bbc-list-pagination class="mt-4 block" [total]="groups().length" [page]="page()" [pageSize]="pageSize()"
          [language]="fr() ? 'fr' : 'en'" (pageChange)="page.set($event)" (pageSizeChange)="setPageSize($event)" />
        }
      }
    </div>
  `,
})
export class CoursebookComponent {
  protected i18n = inject(I18nService);
  private api = inject(CoursebookApi);
  private auth = inject(AuthService);

  protected subjects = signal<SubjectView[]>([]);
  protected classes = signal<ClassRef[]>([]);
  protected selectedClass = signal<string>('');
  protected entries = signal<EntryView[]>([]);
  protected query = signal('');
  protected subjectFilter = signal('');
  protected dateFrom = signal('');
  protected dateTo = signal('');
  protected page = signal(1);
  protected pageSize = signal(10);

  protected showForm = signal(false);
  protected editingId = signal<string | null>(null);
  protected draft: EntryUpsert = this.blank();

  protected canWrite = this.auth.can('coursebook', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected homeworkCount = computed(() =>
    this.entries().filter((e) => !!e.homework).length);

  protected subjectsForClass = computed(() => {
    const name = this.selectedClass();
    const cls = this.classes().find((c) => c.name === name);
    const all = this.subjects();
    if (!cls?.subsystem) return all;
    return all.filter((s) => !s.subsystem || s.subsystem === cls.subsystem);
  });

  protected entrySubjects = computed(() => {
    const subjects = new Map<string, string>();
    for (const entry of this.entries()) subjects.set(entry.subjectCode, entry.subjectLabel || entry.subjectCode);
    return [...subjects.entries()].map(([code, label]) => ({ code, label })).sort((a, b) => a.label.localeCompare(b.label));
  });

  protected hasFilters = computed(() => !!(this.query().trim() || this.subjectFilter() || this.dateFrom() || this.dateTo()));
  protected filteredEntries = computed(() => {
    const q = this.query().trim().toLowerCase();
    const subject = this.subjectFilter();
    const from = this.dateFrom();
    const to = this.dateTo();
    return this.entries().filter((entry) => {
      if (subject && entry.subjectCode !== subject) return false;
      if (from && entry.entryDate < from) return false;
      if (to && entry.entryDate > to) return false;
      return !q || `${entry.subjectCode} ${entry.subjectLabel} ${entry.content} ${entry.homework ?? ''}`.toLowerCase().includes(q);
    });
  });

  protected groups = computed<DayGroup[]>(() => {
    const map = new Map<string, EntryView[]>();
    for (const e of this.filteredEntries()) {
      const list = map.get(e.entryDate) ?? [];
      list.push(e);
      map.set(e.entryDate, list);
    }
    return [...map.entries()]
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([date, list]) => ({ date, entries: list }));
  });
  protected pagedGroups = computed(() => paginateRows(this.groups(), this.page(), this.pageSize()));

  constructor() {
    this.api.classes().subscribe({ next: (c) => this.classes.set(c), error: () => {} });
  }

  protected subjectLabel(s: SubjectView): string {
    const l = s.label || {};
    return (this.fr() ? l['fr'] : l['en']) || l['fr'] || l['en'] || s.code;
  }

  protected selectClass(name: string): void {
    this.selectedClass.set(name);
    this.clearFilters();
    this.cancel();
    if (!name) {
      this.entries.set([]);
      this.subjects.set([]);
      return;
    }
    this.api.subjects(name).subscribe({
      next: (s) => {
        this.subjects.set(s);
        this.draft = { ...this.draft, subjectCode: s[0]?.code ?? '' };
      },
      error: () => this.subjects.set([]),
    });
    this.reload();
  }

  protected setQuery(value: string): void { this.query.set(value); this.page.set(1); }
  protected setSubjectFilter(value: string): void { this.subjectFilter.set(value || ''); this.page.set(1); }
  protected setDateFrom(value: string): void { this.dateFrom.set(value || ''); this.page.set(1); }
  protected setDateTo(value: string): void { this.dateTo.set(value || ''); this.page.set(1); }
  protected setPageSize(value: number): void { this.pageSize.set(value); this.page.set(1); }
  protected clearFilters(): void { this.query.set(''); this.subjectFilter.set(''); this.dateFrom.set(''); this.dateTo.set(''); this.page.set(1); }

  protected toggleForm(): void {
    this.editingId.set(null);
    this.draft = this.blank();
    this.showForm.update((v) => !v);
  }

  protected edit(e: EntryView): void {
    this.editingId.set(e.id);
    this.draft = {
      className: e.className, subjectCode: e.subjectCode, entryDate: e.entryDate,
      content: e.content, homework: e.homework ?? '', dueDate: e.dueDate ?? '',
    };
    this.showForm.set(true);
  }

  protected cancel(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.draft = this.blank();
  }

  protected save(): void {
    const cls = this.selectedClass();
    if (!cls || !this.draft.subjectCode || !this.draft.entryDate || !this.draft.content.trim()) return;
    const body: EntryUpsert = {
      ...this.draft,
      className: cls,
      homework: this.draft.homework?.trim() ? this.draft.homework : null,
      dueDate: this.draft.dueDate || null,
    };
    const id = this.editingId();
    const req = id ? this.api.update(id, body) : this.api.create(body);
    req.subscribe(() => {
      this.cancel();
      this.reload();
    });
  }

  protected remove(e: EntryView): void {
    this.api.remove(e.id).subscribe(() => this.reload());
  }

  protected formatDate(iso: string): string {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB',
      { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' });
  }

  private reload(): void {
    const cls = this.selectedClass();
    if (!cls) return;
    this.api.forClass(cls).subscribe((r) => this.entries.set(r));
  }

  private blank(): EntryUpsert {
    const name = this.selectedClass();
    const cls = this.classes().find((c) => c.name === name);
    const all = this.subjects();
    const filtered = cls?.subsystem
      ? all.filter((s) => !s.subsystem || s.subsystem === cls.subsystem)
      : all;
    return { className: '', subjectCode: filtered[0]?.code ?? '', entryDate: '', content: '', homework: '', dueDate: '' };
  }
}
