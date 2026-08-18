import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessagesApi, NoticeView, NoticeUpsert } from './messages.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
  StudentClassPickerComponent, ListPaginationComponent, paginateRows,
} from '../../core/ui';

interface CategoryMeta { fr: string; en: string; badge: string; icon: string; }

@Component({
  selector: 'bbc-messages',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent, StudentClassPickerComponent,
    ListPaginationComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Carnet de correspondance' : 'Correspondence book'"
        [subtitle]="fr() ? 'Notes école ↔ parents avec accusé de lecture' : 'School ↔ parent notices with read-acknowledgement'" />

      <div class="grid grid-cols-12 gap-4">
        <!-- Student picker -->
        <bbc-card className="col-span-12 lg:col-span-4"
          [title]="fr() ? 'Élèves' : 'Students'"
          [subtitle]="fr() ? 'Filtrer par classe' : 'Filter by class'">
          <bbc-student-class-picker [selectedId]="selectedId()" (select)="select($event)" />
        </bbc-card>

        <!-- Notices detail -->
        <div class="col-span-12 lg:col-span-8 space-y-4">
          @if (!selectedId()) {
            <bbc-card>
              <bbc-empty icon="mail" [label]="fr() ? 'Sélectionnez un élève pour voir sa correspondance' : 'Select a student to see their correspondence'" />
            </bbc-card>
          } @else {
            <!-- Header + KPIs -->
            <bbc-card>
              <div class="flex items-center gap-3 mb-4">
                <bbc-avatar [name]="selectedStudent()?.name ?? '—'" [hue]="selectedHue()" [size]="44" />
                <div class="flex-1">
                  <div class="font-display text-lg font-bold text-ink">{{ selectedStudent()?.name }}</div>
                  <div class="text-xs text-mute">{{ selectedStudent()?.matricule }} · {{ selectedStudent()?.className }}</div>
                </div>
                @if (canWrite) {
                  <button (click)="toggleForm()"
                    class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Nouvelle note' : 'New notice' }}
                  </button>
                }
              </div>
              <div class="grid grid-cols-3 gap-3">
                <bbc-kpi [label]="fr() ? 'Notes' : 'Notices'" [value]="notices().length.toString()" icon="mail" />
                <bbc-kpi [label]="fr() ? 'Accusés en attente' : 'Pending acks'" [value]="pending().toString()" icon="clock" />
                <bbc-kpi [label]="fr() ? 'Lues / signées' : 'Read / signed'" [value]="signedCount().toString()" icon="check" />
              </div>
            </bbc-card>

            <!-- Compose form -->
            @if (canWrite && showForm()) {
              <bbc-card [title]="fr() ? 'Nouvelle note' : 'New notice'">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                  <select [(ngModel)]="draft.category"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    @for (c of categoryKeys; track c) {
                      <option [value]="c">{{ fr() ? META[c].fr : META[c].en }}</option>
                    }
                  </select>
                  <label class="flex items-center gap-2 h-10 px-3 text-sm text-ink">
                    <input type="checkbox" [(ngModel)]="draft.requiresAck"
                      class="w-4 h-4 rounded border-slate-300 text-brand-600 focus:ring-brand-400" />
                    {{ fr() ? 'Accusé de lecture requis' : 'Read-acknowledgement required' }}
                  </label>
                  <input [(ngModel)]="draft.subject" [placeholder]="fr() ? 'Objet' : 'Subject'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <textarea [(ngModel)]="draft.body" rows="4" [placeholder]="fr() ? 'Message…' : 'Message…'"
                    class="md:col-span-2 px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400"></textarea>
                  <div class="md:col-span-2 flex items-center justify-end gap-2">
                    <button (click)="cancel()"
                      class="h-9 px-3.5 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-mute hover:bg-slate-50">
                      {{ i18n.t('cancel') }}
                    </button>
                    <button (click)="save()"
                      class="inline-flex items-center gap-1.5 h-9 px-4 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                      <bbc-icon name="send" [s]="16" /> {{ fr() ? 'Envoyer' : 'Send' }}
                    </button>
                  </div>
                </div>
              </bbc-card>
            }

            <!-- Notices list -->
            <bbc-card [title]="fr() ? 'Correspondance' : 'Correspondence'"
              [subtitle]="filteredNotices().length + (fr() ? ' note(s) affichée(s)' : ' notice(s) shown')">
              @if (notices().length === 0) {
                <bbc-empty icon="mail" [label]="fr() ? 'Aucune note' : 'No notice'" />
              } @else {
                <div class="mb-4 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3 md:grid-cols-[1.5fr_1fr_1fr_auto] md:items-end">
                  <label class="block">
                    <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Recherche' : 'Search' }}</span>
                    <input [ngModel]="noticeQuery()" (ngModelChange)="setNoticeQuery($event)"
                      [placeholder]="fr() ? 'Objet, message ou expéditeur…' : 'Subject, message or sender…'"
                      class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-brand-400 focus:outline-none" />
                  </label>
                  <label class="block">
                    <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Catégorie' : 'Category' }}</span>
                    <select [ngModel]="noticeCategory()" (ngModelChange)="setNoticeCategory($event)" class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm">
                      <option value="">{{ fr() ? 'Toutes les catégories' : 'All categories' }}</option>
                      @for (key of categoryKeys; track key) { <option [value]="key">{{ fr() ? META[key].fr : META[key].en }}</option> }
                    </select>
                  </label>
                  <label class="block">
                    <span class="mb-1 block text-[11px] font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Lecture' : 'Reading' }}</span>
                    <select [ngModel]="noticeStatus()" (ngModelChange)="setNoticeStatus($event)" class="h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm">
                      <option value="all">{{ fr() ? 'Tous les statuts' : 'All statuses' }}</option>
                      <option value="pending">{{ fr() ? 'Accusé en attente' : 'Acknowledgement pending' }}</option>
                      <option value="acknowledged">{{ fr() ? 'Lu / signé' : 'Read / signed' }}</option>
                      <option value="none">{{ fr() ? 'Sans accusé requis' : 'No acknowledgement required' }}</option>
                    </select>
                  </label>
                  <button type="button" (click)="clearNoticeFilters()" [disabled]="!hasNoticeFilters()"
                    class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-mute hover:text-ink disabled:cursor-not-allowed disabled:opacity-40">
                    {{ fr() ? 'Effacer' : 'Clear' }}
                  </button>
                </div>
                @if (!filteredNotices().length) {
                  <bbc-empty icon="mail" [label]="fr() ? 'Aucune note ne correspond aux filtres' : 'No notice matches these filters'" />
                } @else {
                <div class="space-y-3">
                  @for (n of pagedNotices(); track n.id) {
                    <div class="p-3.5 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                      <div class="flex items-start gap-2">
                        <div class="flex-1 min-w-0">
                          <div class="flex items-center gap-2 flex-wrap">
                            <span class="inline-flex items-center gap-1 text-[10px] font-bold uppercase px-1.5 py-0.5 rounded"
                              [class]="META[n.category]?.badge ?? 'bg-slate-100 text-slate-700'">
                              <bbc-icon [name]="META[n.category]?.icon ?? 'mail'" [s]="11" />
                              {{ fr() ? (META[n.category]?.fr ?? n.category) : (META[n.category]?.en ?? n.category) }}
                            </span>
                            <span class="font-semibold text-ink">{{ n.subject }}</span>
                          </div>
                          <div class="text-sm text-ink mt-1.5 whitespace-pre-line">{{ n.body }}</div>
                          <div class="text-[11px] text-mute mt-1.5 flex items-center gap-2 flex-wrap">
                            @if (n.senderName) {
                              <span class="inline-flex items-center gap-1"><bbc-icon name="users" [s]="12" /> {{ n.senderName }}</span>
                            }
                            <span>· {{ fmtDate(n.createdAt) }}</span>
                          </div>
                          <!-- Acknowledgement status -->
                          @if (n.acknowledged) {
                            <div class="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-1 rounded">
                              <bbc-icon name="check" [s]="13" />
                              {{ fr() ? 'Lu / signé par' : 'Read / signed by' }} {{ n.acknowledgedBy }}
                              {{ fr() ? 'le' : 'on' }} {{ fmtDate(n.acknowledgedAt!) }}
                            </div>
                          } @else if (n.requiresAck) {
                            <div class="mt-2 flex items-center gap-2 flex-wrap">
                              <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-1 rounded">
                                <bbc-icon name="clock" [s]="13" />
                                {{ fr() ? "En attente d'accusé de lecture" : 'Awaiting read-acknowledgement' }}
                              </span>
                              <button (click)="markRead(n)"
                                class="inline-flex items-center gap-1.5 h-7 px-2.5 text-xs font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                                <bbc-icon name="check" [s]="13" /> {{ fr() ? 'Marquer comme lu' : 'Mark as read' }}
                              </button>
                            </div>
                          }
                        </div>
                        @if (canWrite) {
                          <button (click)="remove(n)"
                            class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center self-start opacity-0 group-hover:opacity-100 transition"
                            [title]="fr() ? 'Supprimer' : 'Delete'">
                            <bbc-icon name="trash" [s]="14" />
                          </button>
                        }
                      </div>
                    </div>
                  }
                </div>
                <bbc-list-pagination class="mt-4 block" [total]="filteredNotices().length" [page]="noticePage()" [pageSize]="noticePageSize()"
                  [language]="fr() ? 'fr' : 'en'" (pageChange)="noticePage.set($event)" (pageSizeChange)="setNoticePageSize($event)" />
                }
              }
            </bbc-card>
          }
        </div>
      </div>
    </div>
  `,
})
export class MessagesComponent {
  protected i18n = inject(I18nService);
  private api = inject(MessagesApi);
  private auth = inject(AuthService);

  protected readonly categoryKeys = ['info', 'convocation', 'absence', 'reminder', 'congrats'];
  protected readonly META: Record<string, CategoryMeta> = {
    info:        { fr: 'Information', en: 'Information',   badge: 'bg-sky-100 text-sky-700',         icon: 'bell' },
    convocation: { fr: 'Convocation', en: 'Summons',       badge: 'bg-violet-100 text-violet-700',   icon: 'calendar' },
    absence:     { fr: 'Absence',     en: 'Absence',       badge: 'bg-amber-100 text-amber-700',     icon: 'clock' },
    reminder:    { fr: 'Rappel',      en: 'Reminder',      badge: 'bg-slate-100 text-slate-700',     icon: 'history' },
    congrats:    { fr: 'Félicitations', en: 'Congrats',    badge: 'bg-emerald-100 text-emerald-700', icon: 'star' },
  };

  protected selectedId = signal<string | null>(null);
  protected selectedStudent = signal<Student | null>(null);
  protected selectedHue = signal(210);
  protected notices = signal<NoticeView[]>([]);
  protected noticeQuery = signal('');
  protected noticeCategory = signal('');
  protected noticeStatus = signal<'all' | 'pending' | 'acknowledged' | 'none'>('all');
  protected noticePage = signal(1);
  protected noticePageSize = signal(10);

  protected showForm = signal(false);
  protected draft: NoticeUpsert = this.blank();

  protected canWrite = this.auth.can('messages', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected pending = computed(() =>
    this.notices().filter((n) => n.requiresAck && !n.acknowledged).length);

  protected signedCount = computed(() =>
    this.notices().filter((n) => n.acknowledged).length);

  protected filteredNotices = computed(() => {
    const q = this.noticeQuery().trim().toLowerCase();
    const category = this.noticeCategory();
    const status = this.noticeStatus();
    return this.notices().filter((notice) => {
      if (category && notice.category !== category) return false;
      if (status === 'pending' && !(notice.requiresAck && !notice.acknowledged)) return false;
      if (status === 'acknowledged' && !notice.acknowledged) return false;
      if (status === 'none' && notice.requiresAck) return false;
      return !q || `${notice.subject} ${notice.body} ${notice.senderName ?? ''}`.toLowerCase().includes(q);
    });
  });
  protected pagedNotices = computed(() => paginateRows(this.filteredNotices(), this.noticePage(), this.noticePageSize()));
  protected hasNoticeFilters = computed(() => !!(this.noticeQuery().trim() || this.noticeCategory() || this.noticeStatus() !== 'all'));

  protected setNoticeQuery(value: string): void { this.noticeQuery.set(value); this.noticePage.set(1); }
  protected setNoticeCategory(value: string): void { this.noticeCategory.set(value || ''); this.noticePage.set(1); }
  protected setNoticeStatus(value: 'all' | 'pending' | 'acknowledged' | 'none'): void { this.noticeStatus.set(value); this.noticePage.set(1); }
  protected setNoticePageSize(value: number): void { this.noticePageSize.set(value); this.noticePage.set(1); }
  protected clearNoticeFilters(): void { this.noticeQuery.set(''); this.noticeCategory.set(''); this.noticeStatus.set('all'); this.noticePage.set(1); }

  protected select(s: Student): void {
    this.selectedId.set(s.id);
    this.selectedStudent.set(s);
    this.selectedHue.set(s.photoHue);
    this.showForm.set(false);
    this.draft = this.blank();
    this.clearNoticeFilters();
    this.reload(s.id);
  }

  protected toggleForm(): void {
    this.draft = this.blank();
    this.showForm.update((v) => !v);
  }

  protected cancel(): void {
    this.showForm.set(false);
    this.draft = this.blank();
  }

  protected save(): void {
    const id = this.selectedId();
    if (!id || !this.draft.subject.trim() || !this.draft.body.trim()) return;
    this.api.create({ ...this.draft, studentId: id }).subscribe(() => {
      this.cancel();
      this.reload(id);
    });
  }

  protected markRead(n: NoticeView): void {
    const id = this.selectedId();
    if (!id) return;
    const who = window.prompt(this.fr() ? 'Nom du parent signataire :' : 'Signing parent name:');
    if (!who || !who.trim()) return;
    this.api.acknowledge(n.id, who.trim()).subscribe(() => this.reload(id));
  }

  protected remove(n: NoticeView): void {
    const id = this.selectedId();
    if (!id) return;
    this.api.remove(n.id).subscribe(() => this.reload(id));
  }

  protected fmtDate(iso: string): string {
    return new Date(iso).toLocaleDateString(this.fr() ? 'fr-FR' : 'en-GB',
      { day: '2-digit', month: 'short', year: 'numeric' });
  }

  private reload(studentId: string): void {
    this.api.forStudent(studentId).subscribe((r) => this.notices.set(r));
  }

  private blank(): NoticeUpsert {
    return { studentId: '', category: 'info', subject: '', body: '', requiresAck: true };
  }
}
