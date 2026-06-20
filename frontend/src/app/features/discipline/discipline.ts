import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DisciplineApi, IncidentView, IncidentUpsert } from './discipline.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent,
} from '../../core/ui';

@Component({
  selector: 'bbc-discipline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent,
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
        <!-- Recent incidents -->
        <bbc-card className="col-span-12 lg:col-span-7"
          [title]="fr() ? 'Incidents récents' : 'Recent incidents'"
          [subtitle]="rows().length + (fr() ? ' incidents enregistrés' : ' incidents recorded')">
          <div action class="inline-flex items-center gap-1.5 text-xs font-semibold text-rose-600">
            <bbc-icon name="shield" [s]="14" /> {{ fr() ? 'Conduite' : 'Conduct' }}
          </div>

          @if (canWrite && showForm()) {
            <div class="rounded-xl border border-slate-100 bg-slate-50/50 p-4 mb-3 grid grid-cols-1 md:grid-cols-2 gap-2.5">
              <input [(ngModel)]="draft.studentId" [placeholder]="fr() ? 'Matricule / ID élève' : 'Matricule / Student ID'"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              <input [(ngModel)]="draft.incidentDate" type="date"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
              <select [(ngModel)]="draft.type"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                <option value="Retard">Retard</option>
                <option value="Absence">Absence</option>
                <option value="Conduite">Conduite</option>
                <option value="Tenue">Tenue</option>
              </select>
              <select [(ngModel)]="draft.sanction"
                class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                <option value="">{{ fr() ? '— Sanction —' : '— Sanction —' }}</option>
                <option value="Avertissement verbal">Avertissement verbal</option>
                <option value="Avertissement écrit">Avertissement écrit</option>
                <option value="Convocation parent">Convocation parent</option>
                <option value="Exclusion temporaire">Exclusion temporaire</option>
                <option value="Conseil de discipline">Conseil de discipline</option>
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

          @if (rows().length === 0) {
            <bbc-empty icon="shield" [label]="fr() ? 'Aucun incident — bravo' : 'No incidents — well done'" />
          } @else {
            <div class="space-y-2">
              @for (i of rows(); track i.id) {
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
          }
        </bbc-card>

        <!-- Parent notification -->
        <bbc-card className="col-span-12 lg:col-span-5"
          [title]="fr() ? 'Notifier le parent' : 'Notify parent'"
          [subtitle]="fr() ? 'SMS / Email automatique au parent' : 'Automatic SMS / Email to parent'">

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
          </div>

          <div class="mb-3">
            <div class="text-xs font-semibold text-mute mb-1.5">{{ fr() ? 'Message' : 'Message' }}</div>
            <textarea [value]="message()" readonly rows="5"
              class="w-full p-3 text-sm rounded-lg border border-slate-200 bg-white focus:outline-none focus:border-brand-400 resize-none"></textarea>
            <div class="text-[11px] text-mute mt-1">{{ message().length }} {{ fr() ? 'caractères' : 'chars' }}</div>
          </div>

          <div class="grid grid-cols-2 gap-2 mt-3">
            <button class="inline-flex items-center justify-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-white border border-slate-200 text-ink hover:bg-slate-50">
              <bbc-icon name="phone" [s]="14" /> SMS
            </button>
            <button class="inline-flex items-center justify-center gap-1.5 h-9 px-3 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
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
  private auth = inject(AuthService);

  protected rows = signal<IncidentView[]>([]);
  protected canWrite = this.auth.can('discipline', 'write');
  protected draft: IncidentUpsert = this.blank();

  protected showForm = signal(false);
  protected tpl = signal<'absence' | 'late' | 'summon' | 'closure'>('absence');
  protected notifyName = signal('');

  protected fr = () => this.i18n.lang() === 'fr';

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
        fr: `Bonjour, votre enfant ${name} est arrivé en retard ce matin (scan: lecteur principal). Merci de veiller à la ponctualité. — BBC`,
        en: `Hello, your child ${name} arrived late this morning (main reader scan). Please ensure punctuality. — BBC`,
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
  }

  private reload(): void {
    this.api.list().subscribe((r) => this.rows.set(r));
  }

  protected toggleForm(): void {
    this.showForm.update((v) => !v);
  }

  protected save(): void {
    if (!this.draft.studentId || !this.draft.incidentDate || !this.draft.type) return;
    this.api.create(this.draft).subscribe(() => {
      this.draft = this.blank();
      this.showForm.set(false);
      this.reload();
    });
  }

  protected remove(i: IncidentView): void {
    this.api.remove(i.id).subscribe(() => this.reload());
  }

  protected prefillNotify(i: IncidentView): void {
    this.notifyName.set(i.studentName);
    this.tpl.set(i.type === 'Absence' ? 'absence' : i.type === 'Retard' ? 'late' : 'summon');
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
    return { studentId: '', incidentDate: '', type: 'Retard', description: '', sanction: '' };
  }
}
