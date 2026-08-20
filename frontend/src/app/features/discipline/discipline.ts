import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DisciplineApi, IncidentView, IncidentUpsert, StudentLookup, NotifyResult } from './discipline.api';
import { SettingsApi, CatalogItemView } from '../settings/settings.api';
import { StudentApi } from '../students/students.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent,
  ListPaginationComponent, paginateRows,
} from '../../core/ui';

const FALLBACK_TYPES = ['Retard', 'Absence', 'Conduite', 'Tenue'];
const FALLBACK_SANCTIONS = [
  'Avertissement verbal',
  'Avertissement écrit',
  'Convocation parent',
  'Exclusion temporaire',
  'Conseil de discipline',
];

@Component({
  selector: 'bbc-discipline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent,
    ListPaginationComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('discipline')"
        [subtitle]="fr() ? 'Incidents, sanctions, notifications parents' : 'Incidents, sanctions, parent notifications'">
        <div right class="flex items-center gap-2">
          @if (canWrite) {
            <button (click)="toggleForm()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvel incident' : 'New incident' }}
            </button>
          }
        </div>
      </bbc-page-header>

      <div class="grid grid-cols-12 gap-4">
        <bbc-card className="col-span-12 lg:col-span-7"
          [title]="fr() ? 'Incidents récents' : 'Recent incidents'"
          [subtitle]="rows().length + (fr() ? ' incidents enregistrés' : ' incidents recorded')">
          <div action class="inline-flex items-center gap-1.5 text-xs font-semibold text-rose-600">
            <bbc-icon name="shield" [s]="14" /> {{ fr() ? 'Conduite' : 'Conduct' }}
          </div>

          <div class="mb-4 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3 md:grid-cols-[1.5fr_1fr_1fr_auto] md:items-end">
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Recherche' : 'Search' }}</span>
              <div class="relative">
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-mute"><bbc-icon name="search" [s]="14" /></span>
                <input [ngModel]="query()" (ngModelChange)="setQuery($event)"
                  [placeholder]="fr() ? 'Élève, classe, incident ou sanction…' : 'Student, class, incident or sanction…'"
                  class="h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 text-sm focus:border-brand-400 focus:outline-none" />
              </div>
            </label>
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Classe' : 'Class' }}</span>
              <select [ngModel]="classFilter()" (ngModelChange)="setClassFilter($event)"
                class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
                <option value="">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>
                @for (name of classOptions(); track name) { <option [value]="name">{{ name }}</option> }
              </select>
            </label>
            <label class="block">
              <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Type' : 'Type' }}</span>
              <select [ngModel]="typeFilter()" (ngModelChange)="setTypeFilter($event)"
                class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
                <option value="">{{ fr() ? 'Tous les types' : 'All types' }}</option>
                @for (type of incidentTypeOptions(); track type) { <option [value]="type">{{ type }}</option> }
              </select>
            </label>
            <button type="button" (click)="clearFilters()" [disabled]="!hasFilters()"
              class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
              {{ fr() ? 'Effacer' : 'Clear' }}
            </button>
          </div>

          @if (canWrite && showForm()) {
            <div class="rounded-xl border border-slate-100 bg-slate-50/50 p-4 mb-3 grid grid-cols-1 md:grid-cols-2 gap-2.5">
              <div>
                <label class="block text-[11px] font-semibold text-mute mb-1">{{ fr() ? 'Classe' : 'Class' }}</label>
                <select [ngModel]="incidentClass()" (ngModelChange)="onIncidentClass($event)"
                  class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                  <option value="">{{ fr() ? '— Choisir —' : '— Choose —' }}</option>
                  @for (c of setupClasses(); track c.id) {
                    <option [value]="c.name">{{ c.name }}</option>
                  }
                </select>
              </div>
              <div>
                <label class="block text-[11px] font-semibold text-mute mb-1">{{ fr() ? 'Élève' : 'Student' }}</label>
                <select [ngModel]="incidentStudentId()" (ngModelChange)="onIncidentStudent($event)" [disabled]="!incidentClass()"
                  class="w-full h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400 disabled:bg-slate-50">
                  <option value="">{{ fr() ? '— Choisir —' : '— Choose —' }}</option>
                  @for (s of incidentStudents(); track s.id) {
                    <option [value]="s.id">{{ s.name }} · {{ s.matricule }}</option>
                  }
                </select>
              </div>
              @if (lookup(); as lu) {
                <div class="md:col-span-2 flex items-center gap-2.5 p-2 rounded-lg bg-white border border-emerald-100">
                  <bbc-avatar [name]="lu.name" [hue]="200" [size]="36" />
                  <div class="min-w-0">
                    <div class="text-sm font-semibold text-ink truncate">{{ lu.name }}</div>
                    <div class="text-[11px] text-mute">{{ lu.matricule }} · {{ lu.className || (fr() ? 'Sans classe' : 'No class') }}</div>
                  </div>
                </div>
              }
              <input [(ngModel)]="draft.incidentDate" type="date"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              <select [(ngModel)]="draft.type"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                @for (t of typeOptions(); track t) {
                  <option [value]="t">{{ t }}</option>
                }
              </select>
              <select [(ngModel)]="draft.sanction"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                <option value="">{{ fr() ? '— Sanction —' : '— Sanction —' }}</option>
                @for (s of sanctionOptions(); track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
              <input [(ngModel)]="draft.description" [placeholder]="fr() ? 'Description' : 'Description'"
                class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              <div class="md:col-span-2 flex items-center justify-end gap-2">
                <button (click)="toggleForm()"
                  class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                  {{ fr() ? 'Annuler' : 'Cancel' }}
                </button>
                <button (click)="save()"
                  class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                  <bbc-icon name="check" [s]="16" /> {{ i18n.t('save') }}
                </button>
              </div>
            </div>
          }

          @if (filteredRows().length === 0) {
            <bbc-empty icon="shield" [label]="hasFilters()
              ? (fr() ? 'Aucun incident ne correspond aux filtres' : 'No incident matches these filters')
              : (fr() ? 'Aucun incident — bravo' : 'No incidents — well done')" />
          } @else {
            <div class="space-y-2">
              @for (i of pagedRows(); track i.id) {
                <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                  <bbc-avatar [name]="i.studentName || '?'" [hue]="200" />
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-ink">{{ i.studentName }}</span>
                      @if (i.className) { <span class="text-[11px] text-mute">· {{ i.className }}</span> }
                      <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="typeBadge(i.type)">{{ i.type }}</span>
                    </div>
                    @if (i.description) { <div class="text-sm text-ink mt-0.5">{{ i.description }}</div> }
                    <div class="text-[11px] text-mute mt-1 flex items-center gap-1.5">
                      <bbc-icon name="clock" [s]="12" /> {{ i.incidentDate }}
                      @if (i.sanction) {
                        <span>· {{ fr() ? 'Sanction' : 'Sanction' }}: <span class="font-semibold text-rose-700">{{ i.sanction }}</span></span>
                      }
                    </div>
                  </div>
                  <div class="flex items-center gap-1 self-center">
                    <button (click)="prefillNotify(i)"
                      class="w-7 h-7 rounded text-mute hover:text-brand-600 hover:bg-brand-50 flex items-center justify-center"
                      [title]="fr() ? 'Notifier le parent' : 'Notify parent'">
                      <bbc-icon name="bell" [s]="14" />
                    </button>
                    @if (canWrite) {
                      <button (click)="remove(i)"
                        class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center"
                        [title]="fr() ? 'Supprimer' : 'Delete'">
                        <bbc-icon name="x" [s]="14" />
                      </button>
                    }
                  </div>
                </div>
              }
            </div>
            <bbc-list-pagination class="mt-4 block" [total]="filteredRows().length" [page]="page()" [pageSize]="pageSize()"
              [language]="fr() ? 'fr' : 'en'" (pageChange)="page.set($event)" (pageSizeChange)="setPageSize($event)" />
          }
        </bbc-card>

        <bbc-card className="col-span-12 lg:col-span-5"
          [title]="fr() ? 'Notifier le parent' : 'Notify parent'"
          [subtitle]="fr() ? 'SMS / Email au parent' : 'SMS / Email to parent'">

          <div class="mb-3">
            <div class="text-xs font-semibold text-mute mb-1.5">{{ fr() ? 'Modèles' : 'Templates' }}</div>
            <div class="grid grid-cols-2 gap-1.5">
              @for (o of tplOptions(); track o.id) {
                <button (click)="tpl.set(o.id)"
                  class="text-left text-xs font-semibold px-2.5 py-2 rounded-lg border transition"
                  [class]="tpl() === o.id ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                  {{ o.label }}
                </button>
              }
            </div>
          </div>

          <div class="mb-3">
            <div class="text-xs font-semibold text-mute mb-1.5">{{ fr() ? 'Élève concerné' : 'Student' }}</div>
            <input [ngModel]="notifyName()" (ngModelChange)="notifyName.set($event)"
              [placeholder]="fr() ? 'Nom de l’élève' : 'Student name'"
              class="w-full h-9 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
            <input [ngModel]="notifyRef()" (ngModelChange)="notifyRef.set($event)"
              [placeholder]="fr() ? 'Matricule (pour l’envoi)' : 'Matricule (for sending)'"
              class="mt-1.5 w-full h-9 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-mono" />
          </div>

          <div class="mb-3">
            <div class="text-xs font-semibold text-mute mb-1.5">{{ fr() ? 'Message' : 'Message' }}</div>
            <textarea [value]="message()" readonly rows="5"
              class="w-full p-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 resize-none"></textarea>
            <div class="text-[11px] text-mute mt-1">{{ message().length }} {{ fr() ? 'caractères' : 'chars' }}</div>
          </div>

          @if (notifyMsg(); as nm) {
            <div class="mb-2 text-xs rounded-lg px-3 py-2 border"
              [class]="nm.ok ? 'bg-emerald-50 text-emerald-700 border-emerald-100' : 'bg-amber-50 text-amber-800 border-amber-100'">
              {{ nm.text }}
            </div>
          }

          <div class="grid grid-cols-2 gap-2 mt-3">
            <button (click)="sendNotify('sms')" [disabled]="!canWrite || notifying() || !notifyRef().trim()"
              class="inline-flex items-center justify-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50 disabled:opacity-50">
              <bbc-icon name="phone" [s]="14" /> SMS
            </button>
            <button (click)="sendNotify('email')" [disabled]="!canWrite || notifying() || !notifyRef().trim()"
              class="inline-flex items-center justify-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
              <bbc-icon name="send" [s]="14" /> {{ fr() ? 'Envoyer' : 'Send' }}
            </button>
          </div>
          <div class="text-[11px] text-mute mt-2 flex items-center gap-1.5">
            <bbc-icon name="bell" [s]="12" />
            {{ fr() ? 'Le SMS est lu plus vite par les parents.' : 'SMS is read faster by parents.' }}
          </div>
        </bbc-card>
      </div>
    </div>
  `,
})
export class DisciplineComponent {
  protected i18n = inject(I18nService);
  private api = inject(DisciplineApi);
  private settingsApi = inject(SettingsApi);
  private setupApi = inject(SetupApi);
  private studentApi = inject(StudentApi);
  private auth = inject(AuthService);

  protected rows = signal<IncidentView[]>([]);
  protected query = signal('');
  protected classFilter = signal('');
  protected typeFilter = signal('');
  protected page = signal(1);
  protected pageSize = signal(25);
  protected canWrite = this.auth.can('discipline', 'write');
  protected draft: IncidentUpsert = this.blank();
  protected lookup = signal<StudentLookup | null>(null);

  protected setupClasses = signal<ClassView[]>([]);
  protected incidentClass = signal('');
  protected incidentStudents = signal<Student[]>([]);
  protected incidentStudentId = signal('');

  protected showForm = signal(false);
  protected tpl = signal<'absence' | 'late' | 'summon' | 'closure'>('absence');
  protected notifyName = signal('');
  protected notifyRef = signal('');
  protected notifyMsg = signal<{ text: string; ok: boolean } | null>(null);
  protected notifying = signal(false);

  private catalogTypes = signal<CatalogItemView[]>([]);
  private catalogSanctions = signal<CatalogItemView[]>([]);

  protected fr = () => this.i18n.lang() === 'fr';

  protected classOptions = computed(() => [...new Set(this.rows().map((row) => row.className).filter((name): name is string => !!name))].sort());
  protected incidentTypeOptions = computed(() => [...new Set(this.rows().map((row) => row.type).filter(Boolean))].sort());
  protected filteredRows = computed(() => {
    const q = this.query().trim().toLowerCase();
    const cls = this.classFilter();
    const type = this.typeFilter();
    return this.rows().filter((row) => {
      if (cls && row.className !== cls) return false;
      if (type && row.type !== type) return false;
      if (!q) return true;
      return `${row.studentName ?? ''} ${row.className ?? ''} ${row.type ?? ''} ${row.description ?? ''} ${row.sanction ?? ''}`.toLowerCase().includes(q);
    });
  });
  protected pagedRows = computed(() => paginateRows(this.filteredRows(), this.page(), this.pageSize()));
  protected hasFilters = computed(() => !!(this.query().trim() || this.classFilter() || this.typeFilter()));

  protected typeOptions = computed(() => {
    const items = this.catalogTypes().filter((c) => c.active);
    if (!items.length) return FALLBACK_TYPES;
    return items.map((c) => (this.fr() ? c.labelFr : c.labelEn) || c.labelFr);
  });

  protected sanctionOptions = computed(() => {
    const items = this.catalogSanctions().filter((c) => c.active);
    if (!items.length) return FALLBACK_SANCTIONS;
    return items.map((c) => (this.fr() ? c.labelFr : c.labelEn) || c.labelFr);
  });

  protected tplOptions = computed(() => [
    { id: 'absence' as const, label: this.fr() ? 'Absence' : 'Absence' },
    { id: 'late' as const, label: this.fr() ? 'Retard' : 'Late' },
    { id: 'summon' as const, label: this.fr() ? 'Convocation' : 'Summon' },
    { id: 'closure' as const, label: this.fr() ? 'Fermeture' : 'Closure' },
  ]);

  protected message = computed(() => {
    const name = this.notifyName() || (this.fr() ? 'votre enfant' : 'your child');
    const fr = this.fr();
    const t: Record<string, { fr: string; en: string }> = {
      absence: {
        fr: `Bonjour, votre enfant ${name} a été déclaré ABSENT à l'école ce jour. Merci de justifier cette absence sous 48h. — Bayo Bilingual Complex`,
        en: `Hello, your child ${name} was marked ABSENT from school today. Please justify within 48h. — Bayo Bilingual Complex`,
      },
      late: {
        fr: `Bonjour, votre enfant ${name} est arrivé en retard ce matin. Merci de veiller à la ponctualité. — BBC`,
        en: `Hello, your child ${name} arrived late this morning. Please ensure punctuality. — BBC`,
      },
      summon: {
        fr: `Bonjour, vous êtes prié(e) de bien vouloir vous présenter à l'établissement pour échanger au sujet de votre enfant ${name}. — Le Principal`,
        en: `Hello, you are kindly requested to come to school to discuss your child ${name}. — Principal`,
      },
      closure: {
        fr: `Information aux parents: l'école sera exceptionnellement fermée. Reprise normale ultérieurement. — BBC`,
        en: `Notice to parents: school will be exceptionally closed. Normal resumption later. — BBC`,
      },
    };
    const m = t[this.tpl()];
    return fr ? m.fr : m.en;
  });

  constructor() {
    this.reload();
    this.loadCatalog();
    this.setupApi.listClasses().subscribe({ next: (c) => this.setupClasses.set(c), error: () => {} });
  }

  protected setQuery(value: string): void { this.query.set(value); this.page.set(1); }
  protected setClassFilter(value: string): void { this.classFilter.set(value || ''); this.page.set(1); }
  protected setTypeFilter(value: string): void { this.typeFilter.set(value || ''); this.page.set(1); }
  protected setPageSize(value: number): void { this.pageSize.set(value); this.page.set(1); }
  protected clearFilters(): void { this.query.set(''); this.classFilter.set(''); this.typeFilter.set(''); this.page.set(1); }

  private reload(): void {
    this.api.list().subscribe((r) => this.rows.set(r));
  }

  private loadCatalog(): void {
    this.settingsApi.listCatalog().subscribe({
      next: (items) => {
        this.catalogTypes.set(items.filter((c) => c.kind === 'type'));
        this.catalogSanctions.set(items.filter((c) => c.kind === 'sanction'));
        const types = this.typeOptions();
        if (types.length && !types.includes(this.draft.type)) {
          this.draft.type = types[0];
        }
      },
      error: () => { /* keep hardcoded fallbacks */ },
    });
  }

  protected toggleForm(): void {
    this.showForm.update((v) => !v);
    this.lookup.set(null);
    this.incidentClass.set('');
    this.incidentStudents.set([]);
    this.incidentStudentId.set('');
  }

  protected onIncidentClass(name: string): void {
    this.incidentClass.set(name);
    this.incidentStudentId.set('');
    this.incidentStudents.set([]);
    this.draft.studentRef = '';
    this.lookup.set(null);
    if (!name) return;
    this.studentApi.list(name).subscribe({
      next: (r) => this.incidentStudents.set(r),
      error: () => this.incidentStudents.set([]),
    });
  }

  protected onIncidentStudent(id: string): void {
    this.incidentStudentId.set(id);
    const s = this.incidentStudents().find((x) => x.id === id);
    if (!s) {
      this.draft.studentRef = '';
      this.lookup.set(null);
      return;
    }
    this.draft.studentRef = s.matricule || s.id;
    this.lookup.set({
      id: s.id,
      name: s.name,
      matricule: s.matricule,
      className: s.className,
      parentName: '',
      parentPhone: '',
    });
  }

  protected save(): void {
    if (!this.draft.studentRef?.trim() || !this.draft.incidentDate || !this.draft.type) return;
    this.api.create(this.draft).subscribe({
      next: () => {
        this.draft = this.blank();
        this.lookup.set(null);
        this.incidentClass.set('');
        this.incidentStudents.set([]);
        this.incidentStudentId.set('');
        this.showForm.set(false);
        this.reload();
      },
    });
  }

  protected remove(i: IncidentView): void {
    this.api.remove(i.id).subscribe(() => this.reload());
  }

  protected prefillNotify(i: IncidentView): void {
    this.notifyName.set(i.studentName);
    this.notifyRef.set(i.studentId);
    this.notifyMsg.set(null);
    this.tpl.set(i.type === 'Absence' ? 'absence' : i.type === 'Retard' ? 'late' : 'summon');
  }

  protected sendNotify(channel: 'sms' | 'email'): void {
    const ref = this.notifyRef().trim();
    if (!ref) return;
    this.notifying.set(true);
    this.notifyMsg.set(null);
    this.api.notify({ studentRef: ref, channel, message: this.message() }).subscribe({
      next: (r: NotifyResult) => {
        this.notifying.set(false);
        this.notifyMsg.set({ text: r.message, ok: r.delivered });
      },
      error: (e) => {
        this.notifying.set(false);
        this.notifyMsg.set({
          text: e?.error?.message ?? (this.fr() ? 'Envoi impossible.' : 'Send failed.'),
          ok: false,
        });
      },
    });
  }

  protected typeBadge(type: string): string {
    const colors: Record<string, string> = {
      Retard: 'bg-amber-100 text-amber-700',
      Absence: 'bg-rose-100 text-rose-700',
      Conduite: 'bg-rose-200 text-rose-800',
      Tenue: 'bg-sky-100 text-sky-700',
    };
    return colors[type] ?? 'bg-slate-100 text-slate-700';
  }

  private blank(): IncidentUpsert {
    return {
      studentRef: '',
      incidentDate: new Date().toISOString().slice(0, 10),
      type: FALLBACK_TYPES[0],
      description: '',
      sanction: '',
    };
  }
}
