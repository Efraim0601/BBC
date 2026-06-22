import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessagesApi, NoticeView, NoticeUpsert } from './messages.api';
import { StudentApi } from '../students/students.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
} from '../../core/ui';

interface CategoryMeta { fr: string; en: string; badge: string; icon: string; }

@Component({
  selector: 'bbc-messages',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="fr() ? 'Carnet de correspondance' : 'Correspondence book'"
        [subtitle]="fr() ? 'Notes école ↔ parents avec accusé de lecture' : 'School ↔ parent notices with read-acknowledgement'" />

      <div class="grid grid-cols-12 gap-4">
        <!-- Student picker -->
        <bbc-card className="col-span-12 lg:col-span-4"
          [title]="fr() ? 'Élèves' : 'Students'"
          [subtitle]="students().length + (fr() ? ' inscrits' : ' enrolled')">
          <input [ngModel]="query()" (ngModelChange)="query.set($event)"
            [placeholder]="fr() ? 'Rechercher un élève…' : 'Search a student…'"
            class="w-full h-9 px-3 mb-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:border-brand-400" />
          <div class="space-y-1 max-h-[28rem] overflow-y-auto pr-1">
            @for (s of filtered(); track s.id) {
              <button (click)="select(s)"
                class="w-full flex items-center gap-2.5 p-2 rounded-lg text-left transition"
                [class]="selectedId() === s.id ? 'bg-brand-50 border border-brand-200' : 'hover:bg-slate-50 border border-transparent'">
                <bbc-avatar [name]="s.name" [hue]="s.photoHue" />
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-semibold text-ink truncate">{{ s.name }}</div>
                  <div class="text-[11px] text-mute">{{ s.matricule }} · {{ s.className }}</div>
                </div>
              </button>
            } @empty {
              <bbc-empty icon="users" [label]="fr() ? 'Aucun élève' : 'No student'" />
            }
          </div>
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
              [subtitle]="notices().length + (fr() ? ' note(s)' : ' notice(s)')">
              @if (notices().length === 0) {
                <bbc-empty icon="mail" [label]="fr() ? 'Aucune note' : 'No notice'" />
              } @else {
                <div class="space-y-3">
                  @for (n of notices(); track n.id) {
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
  private studentApi = inject(StudentApi);
  private auth = inject(AuthService);

  protected readonly categoryKeys = ['info', 'convocation', 'absence', 'reminder', 'congrats'];
  protected readonly META: Record<string, CategoryMeta> = {
    info:        { fr: 'Information', en: 'Information',   badge: 'bg-sky-100 text-sky-700',         icon: 'bell' },
    convocation: { fr: 'Convocation', en: 'Summons',       badge: 'bg-violet-100 text-violet-700',   icon: 'calendar' },
    absence:     { fr: 'Absence',     en: 'Absence',       badge: 'bg-amber-100 text-amber-700',     icon: 'clock' },
    reminder:    { fr: 'Rappel',      en: 'Reminder',      badge: 'bg-slate-100 text-slate-700',     icon: 'history' },
    congrats:    { fr: 'Félicitations', en: 'Congrats',    badge: 'bg-emerald-100 text-emerald-700', icon: 'star' },
  };

  protected students = signal<Student[]>([]);
  protected query = signal('');
  protected selectedId = signal<string | null>(null);
  protected notices = signal<NoticeView[]>([]);

  protected showForm = signal(false);
  protected draft: NoticeUpsert = this.blank();

  protected canWrite = this.auth.can('messages', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.students();
    if (!q) return list;
    return list.filter((s) =>
      s.name.toLowerCase().includes(q) || s.matricule.toLowerCase().includes(q) || s.className.toLowerCase().includes(q));
  });

  protected selectedStudent = computed(() =>
    this.students().find((s) => s.id === this.selectedId()) ?? null);

  protected selectedHue = computed(() => this.selectedStudent()?.photoHue ?? 210);

  protected pending = computed(() =>
    this.notices().filter((n) => n.requiresAck && !n.acknowledged).length);

  protected signedCount = computed(() =>
    this.notices().filter((n) => n.acknowledged).length);

  constructor() {
    this.studentApi.list().subscribe((r) => this.students.set(r));
  }

  protected select(s: Student): void {
    this.selectedId.set(s.id);
    this.showForm.set(false);
    this.draft = this.blank();
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
