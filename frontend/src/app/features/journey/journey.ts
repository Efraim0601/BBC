import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { JourneyApi, StudentJourney, JourneyView, JourneyUpsert } from './journey.api';
import { Student } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  IconComponent, CardComponent, PageHeaderComponent, EmptyComponent, AvatarComponent, KpiComponent,
  StudentClassPickerComponent,
} from '../../core/ui';
import { AreaChartComponent, Pt } from '../../core/ui/charts';
import { SetupApi, ClassView } from '../../core/setup.api';
interface ResultMeta { fr: string; en: string; badge: string; }

@Component({
  selector: 'bbc-journey',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, RouterLink, IconComponent, CardComponent, PageHeaderComponent, EmptyComponent,
    AvatarComponent, KpiComponent, AreaChartComponent, StudentClassPickerComponent,
  ],
  template: `
    <div class="fade-in max-w-6xl mx-auto">
      <bbc-page-header [title]="i18n.t('journey')"
        [subtitle]="fr() ? 'Parcours scolaire complet — année par année' : 'Full school journey — year by year'">
        @if (canWrite) {
          <a action routerLink="/journey/promotions"
            class="inline-flex items-center gap-2 h-9 px-4 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
            <bbc-icon name="route" [s]="16" /> {{ fr() ? 'Promotions de fin d’année' : 'End-of-year promotions' }}
          </a>
        }
      </bbc-page-header>

      <div class="grid grid-cols-12 gap-4">
        <!-- Student picker -->
        <bbc-card className="col-span-12 lg:col-span-4"
          [title]="fr() ? 'Élèves' : 'Students'"
          [subtitle]="fr() ? 'Filtrer par classe' : 'Filter by class'">
          <bbc-student-class-picker [selectedId]="selectedId()" (select)="select($event)" />
        </bbc-card>

        <!-- Journey detail -->
        <div class="col-span-12 lg:col-span-8 space-y-4">
          @if (!journey()) {
            <bbc-card>
              <bbc-empty icon="book" [label]="fr() ? 'Sélectionnez un élève pour voir son parcours' : 'Select a student to see their journey'" />
            </bbc-card>
          } @else {
            <!-- Header + KPIs -->
            <bbc-card>
              <div class="flex items-center gap-3 mb-4">
                <bbc-avatar [name]="journey()!.studentName" [hue]="selectedHue()" [size]="44" />
                <div class="flex-1">
                  <div class="font-display text-lg font-bold text-ink">{{ journey()!.studentName }}</div>
                  <div class="text-xs text-mute">{{ journey()!.matricule }} · {{ fr() ? 'Classe actuelle' : 'Current class' }}: {{ journey()!.currentClass }}</div>
                </div>
                @if (canWrite) {
                  <button (click)="toggleForm()"
                    class="inline-flex items-center gap-2 h-9 px-3.5 text-sm font-semibold rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    <bbc-icon name="plus" [s]="16" /> {{ fr() ? 'Ajouter une année' : 'Add a year' }}
                  </button>
                }
              </div>
              <div class="grid grid-cols-3 gap-3">
                <bbc-kpi [label]="fr() ? 'Années suivies' : 'Years tracked'" [value]="journey()!.yearsCount.toString()" icon="calendar" />
                <bbc-kpi [label]="fr() ? 'Meilleure moyenne' : 'Best average'" [value]="journey()!.bestAverage != null ? journey()!.bestAverage + '/20' : '—'" icon="chart" />
                <bbc-kpi [label]="fr() ? 'Redoublements' : 'Repeats'" [value]="repeats().toString()" icon="shield" />
              </div>
            </bbc-card>

            <!-- Add/edit form -->
            @if (canWrite && showForm()) {
              <bbc-card [title]="editingId() ? (fr() ? 'Modifier l’année' : 'Edit year') : (fr() ? 'Nouvelle année' : 'New year')">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                  <input [(ngModel)]="draft.academicYear" [placeholder]="fr() ? 'Année (ex: 2024-2025)' : 'Year (e.g. 2024-2025)'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <select [(ngModel)]="draft.className"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    <option value="">{{ fr() ? '— Classe —' : '— Class —' }}</option>
                    @for (c of setupClasses(); track c.id) {
                      <option [value]="c.name">{{ c.name }}</option>
                    }
                  </select>
                  <select [(ngModel)]="draft.result"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm bg-white focus:outline-none focus:border-brand-400">
                    @for (r of resultKeys; track r) {
                      <option [value]="r">{{ fr() ? META[r].fr : META[r].en }}</option>
                    }
                  </select>
                  <input [(ngModel)]="draft.generalAverage" type="number" min="0" max="20" step="0.01"
                    [placeholder]="fr() ? 'Moyenne /20' : 'Average /20'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="draft.rank" type="number" min="1" [placeholder]="fr() ? 'Rang' : 'Rank'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="draft.classSize" type="number" min="1" [placeholder]="fr() ? 'Effectif classe' : 'Class size'"
                    class="h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
                  <input [(ngModel)]="draft.decision" [placeholder]="fr() ? 'Décision du conseil de classe' : 'Class council decision'"
                    class="md:col-span-2 h-10 px-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:border-brand-400" />
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

            <!-- Progression chart -->
            @if (trend().length >= 2) {
              <bbc-card [title]="fr() ? 'Progression de la moyenne' : 'Average progression'"
                [subtitle]="fr() ? 'Moyenne générale par année' : 'General average per year'">
                <bbc-area-chart [data]="trend()" [h]="180" color="#1E3A5F" stroke="#1E3A5F" />
              </bbc-card>
            }

            <!-- Timeline -->
            <bbc-card [title]="fr() ? 'Chronologie' : 'Timeline'"
              [subtitle]="journey()!.entries.length + (fr() ? ' années enregistrées' : ' years recorded')">
              @if (journey()!.entries.length === 0) {
                <bbc-empty icon="calendar" [label]="fr() ? 'Aucune année enregistrée' : 'No year recorded'" />
              } @else {
                <div class="relative pl-5">
                  <div class="absolute left-[7px] top-1 bottom-1 w-px bg-slate-200"></div>
                  @for (e of journey()!.entries; track e.id) {
                    <div class="relative pb-4 last:pb-0">
                      <div class="absolute -left-5 top-1 w-3.5 h-3.5 rounded-full border-2 border-white shadow"
                        [style.background]="dotColor(e.result)"></div>
                      <div class="flex items-start gap-3 p-3 rounded-lg border border-slate-100 hover:bg-slate-50/50 group">
                        <div class="flex-1 min-w-0">
                          <div class="flex items-center gap-2 flex-wrap">
                            <span class="font-semibold text-ink">{{ e.academicYear }}</span>
                            <span class="text-[11px] text-mute">· {{ e.className }}</span>
                            <span class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded" [class]="META[e.result]?.badge ?? 'bg-slate-100 text-slate-700'">
                              {{ fr() ? (META[e.result]?.fr ?? e.result) : (META[e.result]?.en ?? e.result) }}
                            </span>
                          </div>
                          <div class="text-[11px] text-mute mt-1 flex items-center gap-2 flex-wrap">
                            @if (e.generalAverage != null) {
                              <span class="inline-flex items-center gap-1"><bbc-icon name="chart" [s]="12" /> {{ e.generalAverage }}/20</span>
                            }
                            @if (e.rank != null) {
                              <span>· {{ fr() ? 'Rang' : 'Rank' }} {{ e.rank }}@if (e.classSize) {/{{ e.classSize }}}</span>
                            }
                          </div>
                          @if (e.decision) { <div class="text-sm text-ink mt-1">{{ e.decision }}</div> }
                          @if (e.promotionBatchId) {
                            <div class="mt-2 rounded-lg border border-teal-200 bg-teal-50 p-2.5 text-xs">
                              <div class="font-semibold text-teal-900">
                                {{ fr() ? 'Recommandation' : 'Recommendation' }}: {{ promotionLabel(e.recommendation) }}
                                <span class="mx-1">→</span>
                                {{ fr() ? 'Décision finale' : 'Final decision' }}: {{ promotionLabel(e.finalDecision) }}
                              </div>
                              @if (e.targetClassName) { <div class="text-teal-800 mt-1">{{ fr() ? 'Classe cible' : 'Target class' }}: {{ e.targetClassName }}</div> }
                              @if (e.overrideReason) { <div class="text-teal-800 mt-1">{{ fr() ? 'Motif' : 'Reason' }}: {{ e.overrideReason }}</div> }
                            </div>
                          }
                        </div>
                        @if (canWrite && !e.promotionBatchId) {
                          <div class="flex items-center gap-1 self-center opacity-0 group-hover:opacity-100 transition">
                            <button (click)="edit(e)"
                              class="w-7 h-7 rounded text-mute hover:text-brand-600 hover:bg-brand-50 flex items-center justify-center"
                              [title]="fr() ? 'Modifier' : 'Edit'">
                              <bbc-icon name="edit" [s]="14" />
                            </button>
                             <button (click)="askVoid(e)"
                              class="w-7 h-7 rounded text-mute hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center"
                              [title]="fr() ? 'Supprimer' : 'Delete'">
                              <bbc-icon name="x" [s]="14" />
                            </button>
                          </div>
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
     @if (voidCandidate(); as e) {
       <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" (click)="closeVoid()"><div class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md p-5" (click)="$event.stopPropagation()"><h2 class="font-display text-xl font-bold text-ink">{{ fr() ? 'Annuler une note historique ?' : 'Void historical note?' }}</h2><p class="text-sm text-mute mt-2">{{ e.academicYear }} · {{ e.className }}. {{ fr() ? 'Le contenu original restera dans l’audit et ne sera pas supprimé.' : 'The original content stays in the audit history and is not deleted.' }}</p><label class="field-label mt-4">{{ fr() ? 'Motif' : 'Reason' }} <span class="required">*</span><textarea [(ngModel)]="voidReason" rows="3" [class]="fieldClass(!voidReason.trim())"></textarea>@if (voidAttempted() && !voidReason.trim()) { <span class="field-error">{{ fr() ? 'Le motif est obligatoire.' : 'Reason is required.' }}</span> }</label><div class="mt-5 flex justify-end gap-2"><button (click)="closeVoid()" class="secondary-btn">{{ fr() ? 'Retour' : 'Back' }}</button><button (click)="confirmVoid()" class="danger-btn">{{ fr() ? 'Annuler l’entrée' : 'Void entry' }}</button></div></div></div>
     }
   `,
})
export class JourneyComponent {
  protected i18n = inject(I18nService);
  private api = inject(JourneyApi);
  private setupApi = inject(SetupApi);
  private auth = inject(AuthService);

  protected readonly resultKeys = [
    'in_progress', 'promoted', 'repeated', 'transferred_in', 'transferred_out', 'graduated', 'excluded',
  ];
  protected readonly META: Record<string, ResultMeta> = {
    in_progress:     { fr: 'En cours',   en: 'In progress',     badge: 'bg-slate-100 text-slate-700' },
    promoted:        { fr: 'Admis',      en: 'Promoted',        badge: 'bg-emerald-100 text-emerald-700' },
    repeated:        { fr: 'Redoublé',   en: 'Repeated',        badge: 'bg-amber-100 text-amber-700' },
    transferred_in:  { fr: 'Arrivée',    en: 'Transferred in',  badge: 'bg-sky-100 text-sky-700' },
    transferred_out: { fr: 'Départ',     en: 'Transferred out', badge: 'bg-slate-100 text-slate-600' },
    graduated:       { fr: 'Diplômé',    en: 'Graduated',       badge: 'bg-violet-100 text-violet-700' },
    excluded:        { fr: 'Exclu',      en: 'Excluded',        badge: 'bg-rose-100 text-rose-700' },
  };

  protected setupClasses = signal<ClassView[]>([]);
  protected selectedId = signal<string | null>(null);
  protected selectedHue = signal(210);
  protected journey = signal<StudentJourney | null>(null);

  protected showForm = signal(false);
  protected editingId = signal<string | null>(null);
  protected draft: JourneyUpsert = this.blank();
  protected voidCandidate = signal<JourneyView | null>(null);
  protected voidReason = '';
  protected voidAttempted = signal(false);

  protected canWrite = this.auth.can('journey', 'write');
  protected fr = () => this.i18n.lang() === 'fr';

  protected repeats = computed(() =>
    (this.journey()?.entries ?? []).filter((e) => e.result === 'repeated').length);

  protected trend = computed<Pt[]>(() =>
    (this.journey()?.entries ?? [])
      .filter((e) => e.generalAverage != null)
      .map((e) => ({ label: e.academicYear, value: Number(e.generalAverage) })));

  constructor() {
    this.setupApi.listClasses().subscribe({ next: (c) => this.setupClasses.set(c), error: () => {} });
  }

  protected select(s: Student): void {
    this.selectedId.set(s.id);
    this.selectedHue.set(s.photoHue);
    this.showForm.set(false);
    this.editingId.set(null);
    this.api.forStudent(s.id).subscribe((j) => this.journey.set(j));
  }

  protected toggleForm(): void {
    this.editingId.set(null);
    this.draft = this.blank();
    this.showForm.update((v) => !v);
  }

  protected edit(e: JourneyView): void {
    this.editingId.set(e.id);
    this.draft = {
      studentId: e.studentId, academicYear: e.academicYear, className: e.className,
      level: e.level ?? undefined, subsystem: e.subsystem ?? undefined, result: e.result,
      generalAverage: e.generalAverage, rank: e.rank, classSize: e.classSize,
      decision: e.decision ?? '', note: e.note ?? '',
    };
    this.showForm.set(true);
  }

  protected cancel(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.draft = this.blank();
  }

  protected save(): void {
    const id = this.selectedId();
    if (!id || !this.draft.academicYear || !this.draft.className) return;
    this.api.upsert({ ...this.draft, studentId: id }).subscribe(() => {
      this.cancel();
      this.api.forStudent(id).subscribe((j) => this.journey.set(j));
    });
  }

  protected askVoid(e: JourneyView): void { this.voidCandidate.set(e); this.voidReason = ''; this.voidAttempted.set(false); }
  protected closeVoid(): void { this.voidCandidate.set(null); this.voidReason = ''; this.voidAttempted.set(false); }
  protected confirmVoid(): void {
    const e = this.voidCandidate(); const id = this.selectedId(); this.voidAttempted.set(true);
    if (!e || !id || !this.voidReason.trim()) return;
    this.api.voidEntry(e.id, this.voidReason.trim()).subscribe({ next: () => { this.closeVoid(); this.api.forStudent(id).subscribe((j) => this.journey.set(j)); } });
  }
  protected fieldClass(invalid: boolean): string { return `field ${invalid && this.voidAttempted() ? 'field-invalid' : ''}`; }

  protected dotColor(result: string): string {
    const map: Record<string, string> = {
      in_progress: '#94A3B8', promoted: '#10B981', repeated: '#F59E0B',
      transferred_in: '#0EA5E9', transferred_out: '#94A3B8', graduated: '#8B5CF6', excluded: '#F43F5E',
    };
    return map[result] ?? '#94A3B8';
  }

  protected promotionLabel(value: string | null): string {
    const fr: Record<string, string> = { PROMOTE: 'Promouvoir', REPEAT: 'Redoubler', REVIEW: 'À réviser', GRADUATE: 'Diplômer', HOLD: 'Maintenir' };
    const en: Record<string, string> = { PROMOTE: 'Promote', REPEAT: 'Repeat', REVIEW: 'Review', GRADUATE: 'Graduate', HOLD: 'Hold back' };
    return value ? ((this.fr() ? fr : en)[value] ?? value) : '—';
  }

  private blank(): JourneyUpsert {
    return { studentId: '', academicYear: '', className: '', result: 'in_progress',
      generalAverage: null, rank: null, classSize: null, decision: '', note: '' };
  }
}
