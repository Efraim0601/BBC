import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventApi, EventView, EventUpsert } from './events.api';
import { SetupApi, ClassView } from '../../core/setup.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent,
  ListPaginationComponent, paginateRows,
} from '../../core/ui';

interface EventDraft {
  title: string;
  type: string;
  eventDate: string;
  description: string;
  audience: string;
}

interface TypeMeta {
  fr: string;
  en: string;
  icon: string;
  color: string;
}

const EVENT_TYPES: Record<string, TypeMeta> = {
  meeting: { fr: 'Réunion', en: 'Meeting', icon: 'users', color: 'bg-brand-100 text-brand-700' },
  exam: { fr: 'Examen', en: 'Exam', icon: 'doc', color: 'bg-violet-100 text-violet-700' },
  culture: { fr: 'Culturel', en: 'Cultural', icon: 'star', color: 'bg-gold-100 text-gold-600' },
  annonce: { fr: 'Annonce', en: 'Announcement', icon: 'bell', color: 'bg-amber-100 text-amber-700' },
  holiday: { fr: 'Congé', en: 'Holiday', icon: 'calendar', color: 'bg-emerald-100 text-emerald-700' },
  sport: { fr: 'Sport', en: 'Sport', icon: 'spark', color: 'bg-orange-100 text-orange-700' },
  other: { fr: 'Autre', en: 'Other', icon: 'bell', color: 'bg-slate-100 text-slate-700' },
};

@Component({
  selector: 'bbc-events',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet, FormsModule, IconComponent, CardComponent, KpiComponent, PageHeaderComponent, EmptyComponent, ListPaginationComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('events')" [subtitle]="fr() ? 'Annonces de l’école & notifications aux parents' : 'School announcements & parent notifications'">
        <div right class="flex items-center gap-2">
          @if (canWrite) {
            <button (click)="openCreate()"
              class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
              <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvel événement' : 'New event' }}
            </button>
          }
        </div>
      </bbc-page-header>

      <!-- KPIs -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
        <bbc-kpi tone="neutral" icon="calendar" [label]="fr() ? 'À venir' : 'Upcoming'"
          [value]="upcoming().length" [sub]="fr() ? 'événements' : 'events'" />
        <bbc-kpi tone="ok" icon="bell" [label]="fr() ? 'Notifiés' : 'Notified'"
          [value]="notifiedCount()" [sub]="'/ ' + rows().length" />
        <bbc-kpi tone="warn" icon="alertTri" [label]="fr() ? 'Non notifiés' : 'Not notified'"
          [value]="rows().length - notifiedCount()" [sub]="fr() ? 'en attente' : 'pending'" />
        <bbc-kpi tone="gold" icon="users" [label]="fr() ? 'Notifications envoyées' : 'Notifications sent'"
          [value]="totalNotifiedParents()" [sub]="fr() ? 'parents' : 'parents'" />
      </div>

      <bbc-card className="mb-5">
        <div class="grid grid-cols-1 gap-3 md:grid-cols-[1.5fr_1fr_1fr_auto] md:items-end">
          <label class="block">
            <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Recherche' : 'Search' }}</span>
            <input [ngModel]="eventQuery()" (ngModelChange)="setEventQuery($event)"
              [placeholder]="fr() ? 'Titre, description ou classe…' : 'Title, description or class…'"
              class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none" />
          </label>
          <label class="block">
            <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">Type</span>
            <select [ngModel]="eventTypeFilter()" (ngModelChange)="setEventType($event)"
              class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
              <option value="">{{ fr() ? 'Tous les types' : 'All types' }}</option>
              @for (type of eventTypeOptions(); track type.value) { <option [value]="type.value">{{ type.label }}</option> }
            </select>
          </label>
          <label class="block">
            <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Notification' : 'Notification' }}</span>
            <select [ngModel]="notificationFilter()" (ngModelChange)="setNotificationFilter($event)"
              class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none">
              <option value="">{{ fr() ? 'Tous les statuts' : 'All statuses' }}</option>
              <option value="notified">{{ fr() ? 'Notifiés' : 'Notified' }}</option>
              <option value="pending">{{ fr() ? 'Non notifiés' : 'Not notified' }}</option>
            </select>
          </label>
          <button type="button" (click)="clearEventFilters()" [disabled]="!eventQuery() && !eventTypeFilter() && !notificationFilter()"
            class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:opacity-40">{{ fr() ? 'Effacer' : 'Clear' }}</button>
        </div>
      </bbc-card>

      <div class="grid grid-cols-12 gap-4">
        <div class="col-span-12 lg:col-span-7 space-y-4">
          <!-- Upcoming -->
          <div class="flex items-center gap-3">
            <h2 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? 'À venir' : 'Upcoming' }}</h2>
            <div class="flex-1 h-px bg-slate-200"></div>
            <span class="text-[11px] text-slate-400 font-semibold">{{ upcoming().length }}</span>
          </div>

          @if (upcoming().length === 0) {
            <bbc-card><bbc-empty icon="calendar" [label]="fr() ? 'Aucun événement à venir' : 'No upcoming events'" /></bbc-card>
          } @else {
            @for (e of pagedUpcoming(); track e.id) {
              <ng-container *ngTemplateOutlet="eventCard; context: { $implicit: e, past: false }" />
            }
            <bbc-list-pagination [total]="upcoming().length" [page]="upcomingPage()" [pageSize]="eventPageSize()"
              [language]="fr() ? 'fr' : 'en'" (pageChange)="upcomingPage.set($event)" (pageSizeChange)="setEventPageSize($event)" />
          }

          <!-- Past -->
          @if (past().length) {
            <div class="flex items-center gap-3 pt-2">
              <h2 class="text-[11px] uppercase tracking-[0.18em] text-mute font-bold">{{ fr() ? 'Passés' : 'Past' }}</h2>
              <div class="flex-1 h-px bg-slate-200"></div>
              <span class="text-[11px] text-slate-400 font-semibold">{{ past().length }}</span>
            </div>
            @for (e of pagedPast(); track e.id) {
              <ng-container *ngTemplateOutlet="eventCard; context: { $implicit: e, past: true }" />
            }
            <bbc-list-pagination [total]="past().length" [page]="pastPage()" [pageSize]="eventPageSize()"
              [language]="fr() ? 'fr' : 'en'" (pageChange)="pastPage.set($event)" (pageSizeChange)="setEventPageSize($event)" />
          }
        </div>

        <!-- How it works -->
        <div class="col-span-12 lg:col-span-5">
          <bbc-card [title]="fr() ? 'Comment ça marche' : 'How it works'">
            <div class="space-y-3 text-sm">
              @for (s of steps(); track s.n) {
                <div class="flex items-start gap-2.5">
                  <div class="w-5 h-5 rounded-full bg-brand-100 text-brand-700 flex items-center justify-center text-[11px] font-bold shrink-0 mt-0.5">{{ s.n }}</div>
                  <div class="text-mute">{{ s.text }}</div>
                </div>
              }
            </div>
            <div class="mt-4 p-3 rounded-lg bg-emerald-50 flex items-center gap-2.5">
              <div class="w-8 h-8 rounded-md bg-white flex items-center justify-center text-emerald-600 shrink-0"><bbc-icon name="send" [s]="16" /></div>
              <div class="text-xs text-ink">{{ fr() ? 'Les notifications partent par WhatsApp (canal principal) et SMS de secours.' : 'Notifications go via WhatsApp (main channel) and SMS fallback.' }}</div>
            </div>
          </bbc-card>
        </div>
      </div>
    </div>

    <!-- Event card template -->
    <ng-template #eventCard let-e let-past="past">
      <bbc-card [className]="past ? 'opacity-75' : ''">
        <div class="flex gap-4">
          <!-- Date chip -->
          <div class="w-14 shrink-0 text-center">
            <div class="bg-brand-700 text-white rounded-lg overflow-hidden">
              <div class="text-[10px] uppercase tracking-wide bg-brand-800 py-0.5">{{ monthShort(e.eventDate) }}</div>
              <div class="text-xl font-bold py-1">{{ dayNum(e.eventDate) }}</div>
            </div>
          </div>

          <!-- Body -->
          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-bold text-ink">{{ e.title }}</span>
                  <span class="text-[10px] font-bold uppercase tracking-wide px-1.5 py-0.5 rounded {{ typeMeta(e.type).color }}">{{ typeLabel(e.type) }}</span>
                </div>
                @if (e.description) {
                  <div class="text-xs text-mute mt-1">{{ e.description }}</div>
                }
              </div>
              @if (canWrite) {
                <div class="flex items-center gap-1 shrink-0">
                  <button (click)="remove(e)" title="{{ fr() ? 'Supprimer' : 'Delete' }}"
                    class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center"><bbc-icon name="trash" [s]="14" /></button>
                </div>
              }
            </div>

            <div class="flex items-center justify-between mt-3 flex-wrap gap-2">
              <div class="flex items-center gap-2 flex-wrap">
                @if (isWholeSchool(e)) {
                  <span class="text-[11px] font-semibold bg-slate-100 text-slate-700 px-2 py-1 rounded-full">{{ fr() ? 'Toute l’école' : 'Whole school' }}</span>
                } @else {
                  @for (c of e.targetClasses.slice(0, 6); track c) {
                    <span class="text-[11px] font-bold bg-brand-50 text-brand-700 px-2 py-0.5 rounded-full">{{ c }}</span>
                  }
                  @if (e.targetClasses.length > 6) {
                    <span class="text-[11px] text-mute">+{{ e.targetClasses.length - 6 }}</span>
                  }
                }
              </div>

              <div class="flex items-center gap-2">
                @if (e.notified) {
                  <span class="inline-flex items-center gap-1.5 text-[11px] font-semibold text-emerald-700">
                    <bbc-icon name="check" [s]="13" [sw]="2.5" /> {{ fr() ? 'Notifié' : 'Notified' }}
                    @if (counts()[e.id] !== undefined) {
                      <span class="text-emerald-600">· {{ counts()[e.id] }} {{ fr() ? 'parents' : 'parents' }}</span>
                    }
                  </span>
                } @else if (canWrite) {
                  <button (click)="notify(e)"
                    class="inline-flex items-center gap-1.5 h-8 px-3 text-xs font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                    <bbc-icon name="send" [s]="14" /> {{ fr() ? 'Notifier les parents' : 'Notify parents' }}
                  </button>
                } @else {
                  <span class="text-[11px] text-amber-600 font-semibold">{{ fr() ? 'Non notifié' : 'Not notified' }}</span>
                }
              </div>
            </div>
          </div>
        </div>
      </bbc-card>
    </ng-template>

    <!-- Create modal -->
    @if (creating()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40" (click)="closeCreate()">
        <div class="bg-white rounded-xl2 shadow-card w-full max-w-2xl max-h-[90vh] overflow-y-auto" (click)="$event.stopPropagation()">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <div class="text-[15px] font-semibold text-ink font-display">{{ fr() ? 'Nouvel événement' : 'New event' }}</div>
            <button (click)="closeCreate()" class="w-8 h-8 rounded-lg text-mute hover:bg-slate-100 flex items-center justify-center"><bbc-icon name="x" [s]="18" /></button>
          </div>

          <div class="p-5 space-y-4">
            <div>
              <label class="text-xs font-semibold text-mute mb-1 block">{{ fr() ? 'Titre' : 'Title' }}</label>
              <input [(ngModel)]="draft.title"
                [placeholder]="fr() ? 'ex. Réunion parents-enseignants' : 'e.g. Parent-teacher meeting'"
                class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="text-xs font-semibold text-mute mb-1 block">{{ fr() ? 'Type' : 'Type' }}</label>
                <select [(ngModel)]="draft.type"
                  class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 font-semibold">
                  <option value="meeting">{{ fr() ? 'Réunion' : 'Meeting' }}</option>
                  <option value="exam">{{ fr() ? 'Examen' : 'Exam' }}</option>
                  <option value="culture">{{ fr() ? 'Culturel' : 'Cultural' }}</option>
                  <option value="annonce">{{ fr() ? 'Annonce' : 'Announcement' }}</option>
                </select>
              </div>
              <div>
                <label class="text-xs font-semibold text-mute mb-1 block">{{ fr() ? 'Date' : 'Date' }}</label>
                <input [(ngModel)]="draft.eventDate" type="date"
                  class="w-full h-10 px-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400" />
              </div>
            </div>

            <div>
              <label class="text-xs font-semibold text-mute mb-1 block">{{ fr() ? 'Description' : 'Description' }}</label>
              <textarea [(ngModel)]="draft.description" rows="3"
                class="w-full p-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 resize-none"></textarea>
            </div>

            <div>
              <label class="text-xs font-semibold text-mute mb-2 block">{{ fr() ? 'Public ciblé' : 'Target audience' }}</label>
              <div class="flex items-center gap-2 mb-2">
                <button (click)="draft.audience = 'all'"
                  class="px-3 py-1.5 text-xs font-bold rounded-lg border transition"
                  [class]="draft.audience === 'all' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                  {{ fr() ? 'Toute l’école' : 'Whole school' }}
                </button>
                <button (click)="draft.audience = 'classes'"
                  class="px-3 py-1.5 text-xs font-bold rounded-lg border transition"
                  [class]="draft.audience === 'classes' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-mute hover:border-brand-300'">
                  {{ fr() ? 'Classes ciblées' : 'Selected classes' }}
                </button>
              </div>
              @if (draft.audience === 'classes') {
                <div class="max-h-40 overflow-y-auto rounded-lg border border-slate-200 p-2 space-y-1">
                  @for (c of setupClasses(); track c.id) {
                    <label class="flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-slate-50 cursor-pointer text-sm">
                      <input type="checkbox" [checked]="selectedTargets().includes(c.name)"
                        (change)="toggleTarget(c.name)"
                        class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                      <span class="text-ink">{{ c.name }}</span>
                      <span class="text-[11px] text-mute ml-auto">{{ c.subsystem }}</span>
                    </label>
                  } @empty {
                    <div class="text-xs text-mute px-2 py-1">{{ fr() ? 'Aucune classe configurée' : 'No classes configured' }}</div>
                  }
                </div>
              }
            </div>
          </div>

          <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-slate-100">
            <button (click)="closeCreate()" class="h-10 px-4 text-sm font-semibold rounded-lg bg-slate-100 text-ink hover:bg-slate-200">{{ i18n.t('cancel') }}</button>
            <button (click)="save()" [disabled]="!draft.title.trim() || !draft.eventDate"
              class="inline-flex items-center gap-2 h-10 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed">
              <bbc-icon name="check" [s]="16" [sw]="2.5" /> {{ i18n.t('save') }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class EventsComponent {
  protected i18n = inject(I18nService);
  private api = inject(EventApi);
  private setupApi = inject(SetupApi);
  private auth = inject(AuthService);

  protected rows = signal<EventView[]>([]);
  protected counts = signal<Record<string, number>>({});
  protected setupClasses = signal<ClassView[]>([]);
  protected selectedTargets = signal<string[]>([]);
  protected canWrite = this.auth.can('events', 'write');
  protected creating = signal(false);
  protected eventQuery = signal('');
  protected eventTypeFilter = signal<string | null>(null);
  protected notificationFilter = signal<string | null>(null);
  protected upcomingPage = signal(1);
  protected pastPage = signal(1);
  protected eventPageSize = signal(10);
  protected draft: EventDraft = this.blank();

  protected fr = () => this.i18n.lang() === 'fr';

  private todayKey = new Date().toISOString().slice(0, 10);

  protected filteredRows = computed(() => {
    const q = this.eventQuery().trim().toLowerCase();
    const type = this.eventTypeFilter();
    const notification = this.notificationFilter();
    return this.rows().filter((event) => {
      if (type && event.type !== type) return false;
      if (notification === 'notified' && !event.notified) return false;
      if (notification === 'pending' && event.notified) return false;
      if (q && !`${event.title} ${event.description ?? ''} ${(event.targetClasses ?? []).join(' ')}`.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  protected upcoming = computed(() =>
    this.filteredRows()
      .filter((e) => (e.eventDate ?? '') >= this.todayKey)
      .sort((a, b) => (a.eventDate ?? '').localeCompare(b.eventDate ?? '')),
  );

  protected past = computed(() =>
    this.filteredRows()
      .filter((e) => (e.eventDate ?? '') < this.todayKey)
      .sort((a, b) => (b.eventDate ?? '').localeCompare(a.eventDate ?? '')),
  );
  protected pagedUpcoming = computed(() => paginateRows(this.upcoming(), this.upcomingPage(), this.eventPageSize()));
  protected pagedPast = computed(() => paginateRows(this.past(), this.pastPage(), this.eventPageSize()));
  protected eventTypeOptions = computed(() => Object.keys(EVENT_TYPES).map((value) => ({ value, label: this.typeLabel(value) })));

  protected setEventQuery(value: string): void { this.eventQuery.set(value); this.resetEventPages(); }
  protected setEventType(value: string | null): void { this.eventTypeFilter.set(value || null); this.resetEventPages(); }
  protected setNotificationFilter(value: string | null): void { this.notificationFilter.set(value || null); this.resetEventPages(); }
  protected setEventPageSize(value: number): void { this.eventPageSize.set(value); this.resetEventPages(); }
  protected clearEventFilters(): void { this.eventQuery.set(''); this.eventTypeFilter.set(null); this.notificationFilter.set(null); this.resetEventPages(); }
  private resetEventPages(): void { this.upcomingPage.set(1); this.pastPage.set(1); }

  protected notifiedCount = computed(() => this.rows().filter((e) => e.notified).length);

  protected totalNotifiedParents = computed(() =>
    Object.values(this.counts()).reduce((a, b) => a + b, 0),
  );

  protected steps = computed(() => {
    const f = this.fr();
    return [
      { n: 1, text: f ? 'Créez un événement et sa date.' : 'Create an event and its date.' },
      { n: 2, text: f ? 'Choisissez les classes concernées (ou toute l’école).' : 'Choose concerned classes (or the whole school).' },
      { n: 3, text: f ? 'Cliquez « Notifier les parents » — envoi WhatsApp + SMS aux parents concernés.' : 'Click "Notify parents" — WhatsApp + SMS to concerned parents.' },
      { n: 4, text: f ? 'Les parents voient l’événement dans leur espace.' : 'Parents see the event in their portal.' },
    ];
  });

  constructor() {
    this.reload();
    this.setupApi.listClasses().subscribe({ next: (c) => this.setupClasses.set(c), error: () => {} });
  }

  private reload(): void {
    this.api.list().subscribe((r) => this.rows.set(r));
  }

  protected openCreate(): void {
    this.draft = this.blank();
    this.selectedTargets.set([]);
    this.creating.set(true);
  }

  protected closeCreate(): void {
    this.creating.set(false);
    this.draft = this.blank();
    this.selectedTargets.set([]);
  }

  protected toggleTarget(name: string): void {
    this.selectedTargets.update((list) =>
      list.includes(name) ? list.filter((n) => n !== name) : [...list, name]);
  }

  protected save(): void {
    if (!this.draft.title || !this.draft.eventDate) return;
    const body: EventUpsert = {
      title: this.draft.title,
      type: this.draft.type,
      eventDate: this.draft.eventDate,
      description: this.draft.description,
      audience: this.draft.audience,
      targetClasses: this.draft.audience === 'classes' ? [...this.selectedTargets()] : [],
    };
    this.api.create(body).subscribe(() => {
      this.closeCreate();
      this.reload();
    });
  }

  protected notify(e: EventView): void {
    this.api.notify(e.id).subscribe((res) => {
      this.rows.update((list) =>
        list.map((x) => (x.id === e.id ? { ...x, notified: true } : x)),
      );
      this.counts.update((c) => ({ ...c, [e.id]: res.notifiedCount }));
    });
  }

  protected remove(e: EventView): void {
    this.api.remove(e.id).subscribe(() => this.reload());
  }

  protected isWholeSchool(e: EventView): boolean {
    return e.audience === 'all' || !e.targetClasses?.length;
  }

  protected typeMeta(type: string): TypeMeta {
    return EVENT_TYPES[type] ?? EVENT_TYPES['other'];
  }

  protected typeLabel(type: string): string {
    const m = this.typeMeta(type);
    return this.fr() ? m.fr : m.en;
  }

  private parsed(d: string | null): Date | null {
    if (!d) return null;
    const p = new Date(d);
    return Number.isNaN(p.getTime()) ? null : p;
  }

  protected monthShort(d: string | null): string {
    const p = this.parsed(d);
    if (!p) return '—';
    return p.toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB', { month: 'short' });
  }

  protected dayNum(d: string | null): string {
    const p = this.parsed(d);
    return p ? '' + p.getDate() : '–';
  }

  private blank(): EventDraft {
    return {
      title: '',
      type: 'meeting',
      eventDate: '',
      description: '',
      audience: 'all',
    };
  }
}
