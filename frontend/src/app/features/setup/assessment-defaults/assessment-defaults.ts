import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { FoundationApi, AcademicReportingPeriodView, AcademicSessionView } from '../../../core/foundation.api';
import { I18nService } from '../../../core/i18n.service';
import { SetupApi, ClassView } from '../../../core/setup.api';
import {
  AcademicApi, AssessmentDefaultsMode, AssessmentDefaultsPreview, AssessmentDefaultsRequest,
  AssessmentDefaultsRow,
} from '../../academic/academic.api';
import { CardComponent, EmptyComponent, IconComponent } from '../../../core/ui';

type DraftField = 'code' | 'label' | 'maxScore' | 'weight' | 'mandatory';
type Draft = { code: string; label: string; maxScore: number; weight: number; mandatory: boolean };

@Component({
  selector: 'bbc-assessment-defaults',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, CardComponent, EmptyComponent, IconComponent],
  template: `
    <section class="space-y-4">
      <bbc-card [title]="fr() ? 'Évaluations' : 'Evaluations'"
        [subtitle]="fr() ? 'Préparez une revue de classe complète avant toute création.' : 'Review the whole class before creating anything.'">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
          <label class="block"><span class="field-label">{{ fr() ? 'Session' : 'Session' }}</span>
            <select [ngModel]="sessionId()" (ngModelChange)="selectSession($event)" class="field">
              <option value="">{{ fr() ? 'Choisir une session' : 'Choose a session' }}</option>
              @for (session of sessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }
            </select>
          </label>
          <label class="block"><span class="field-label">{{ fr() ? 'Classe' : 'Class' }}</span>
            <select [ngModel]="classId()" (ngModelChange)="selectClass($event)" class="field">
              <option value="">{{ fr() ? 'Choisir une classe' : 'Choose a class' }}</option>
              @for (klass of classes(); track klass.id) { <option [value]="klass.id">{{ klass.name }} · {{ klass.subsystem }}</option> }
            </select>
          </label>
          <label class="block"><span class="field-label">{{ fr() ? 'Portée' : 'Scope' }}</span>
            <select [ngModel]="mode()" (ngModelChange)="selectMode($event)" class="field">
              <option value="ONE_SEQUENCE">{{ fr() ? 'Une séquence' : 'One sequence' }}</option>
              <option value="ALL_SEQUENCES">{{ fr() ? 'Les six séquences' : 'All six sequences' }}</option>
            </select>
          </label>
          @if (mode() === 'ONE_SEQUENCE') {
            <label class="block"><span class="field-label">{{ fr() ? 'Séquence' : 'Sequence' }}</span>
              <select [ngModel]="periodId()" (ngModelChange)="selectPeriod($event)" class="field">
                <option value="">{{ fr() ? 'Choisir une séquence' : 'Choose a sequence' }}</option>
                @for (period of sequencePeriods(); track period.id) { <option [value]="period.id">{{ period.code }} · {{ period.label }}</option> }
              </select>
            </label>
          } @else {
            <div class="rounded-lg border border-brand-100 bg-brand-50 px-3 py-2 text-sm text-brand-900 flex items-center">{{ fr() ? 'S1 à S6 seront regroupées en six revues.' : 'S1 through S6 will be grouped into six reviews.' }}</div>
          }
        </div>
        <div class="mt-4 flex flex-wrap items-center gap-3">
          <button type="button" (click)="prepare()" [disabled]="busy() || !canPrepare()" class="inline-flex items-center gap-2 h-10 px-4 rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white text-sm font-semibold">
            <bbc-icon name="sparkles" [s]="16" /> {{ busy() ? '…' : preview() ? (fr() ? 'Actualiser' : 'Refresh') : (fr() ? 'Préparer la revue' : 'Prepare review') }}
          </button>
          @if (preview(); as p) {
            <span class="text-xs text-mute">{{ p.contentLanguage.toUpperCase() }} · {{ p.className }}</span>
            <span class="chip bg-slate-100 text-slate-700">{{ p.totalRows }} {{ fr() ? 'matières dans la revue' : 'subjects in review' }}</span>
          }
        </div>
        @if (notice(); as n) { <div class="mt-3 rounded-lg border px-3 py-2 text-sm" [class]="n.ok ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'">{{ n.text }}</div> }
        @if (curriculumEmpty()) {
          <div class="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-950">
            <strong>{{ fr() ? 'Aucune matière affectée' : 'No assigned subjects' }}</strong>
            <div class="mt-1">{{ fr() ? 'Ajoutez les matières de cette classe puis relancez la préparation.' : 'Assign class subjects, then prepare the review again.' }}</div>
            <button type="button" (click)="openCurriculumSetup()" class="mt-2 font-semibold text-brand-700 underline">{{ fr() ? 'Ouvrir les matières par classe' : 'Open class subjects' }}</button>
          </div>
        }
      </bbc-card>

      @if (preview(); as p) {
        <div class="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          <div class="px-4 py-4 border-b border-slate-100 bg-slate-50/70">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div><h2 class="font-display text-lg font-bold text-ink">{{ fr() ? 'Évaluations de ' + p.className : p.className + ' evaluations' }}</h2>
                <p class="text-sm text-mute mt-1">{{ fr() ? 'Les évaluations déjà créées sont affichées séparément des nouvelles propositions.' : 'Created evaluations are shown separately from new proposals.' }}</p></div>
              <div class="flex flex-wrap gap-2 text-xs">@if (p.proposedRows) { <span class="chip bg-brand-50 text-brand-800">{{ p.proposedRows }} {{ fr() ? 'à créer' : 'to create' }}</span> } @else { <span class="chip bg-emerald-50 text-emerald-800">{{ fr() ? 'Aucune création nécessaire' : 'Nothing to create' }}</span> }<span class="chip bg-slate-100 text-slate-700">{{ p.existingRows }} {{ fr() ? 'déjà présentes' : 'already exist' }}</span>@if (p.excludedRows) { <span class="chip bg-amber-50 text-amber-800">{{ p.excludedRows }} {{ fr() ? 'hors dates actives' : 'outside active dates' }}</span> }</div>
            </div>
          </div>
          <div class="p-4 space-y-5">
            @for (period of p.periods; track period.reportingPeriodId) {
              <section class="rounded-xl border border-slate-200 overflow-hidden">
                <div class="px-4 py-3 bg-brand-50/60 border-b border-brand-100 flex items-center justify-between"><div><h3 class="font-bold text-brand-950">{{ period.code }} · {{ period.label }}</h3><p class="text-xs text-brand-800 mt-0.5">{{ fr() ? 'Une évaluation par matière affectée' : 'One evaluation per assigned subject' }}</p></div><span class="text-xs font-semibold text-brand-700">{{ period.rows.length }} {{ fr() ? 'matières' : 'subjects' }}</span></div>
                <div class="p-4 space-y-5">
                  @if (existingRows(period).length) {
                    <section class="rounded-xl border border-emerald-200 bg-emerald-50/30 overflow-hidden">
                      <div class="px-4 py-3 border-b border-emerald-200 bg-emerald-50 flex flex-wrap items-center justify-between gap-2">
                        <div><h4 class="font-bold text-emerald-950">{{ fr() ? 'Évaluations déjà configurées' : 'Configured evaluations' }}</h4><p class="text-xs text-emerald-800 mt-0.5">{{ fr() ? 'Voici les valeurs réellement enregistrées pour cette classe et cette séquence.' : 'These are the values currently saved for this class and sequence.' }}</p></div>
                        <span class="chip bg-white text-emerald-800 border border-emerald-200">{{ existingRows(period).length }}</span>
                      </div>
                      <div class="divide-y divide-emerald-100">
                        @for (row of existingRows(period); track row.existingAssessmentId || row.clientRowId) {
                          <article class="p-4 bg-white grid grid-cols-1 xl:grid-cols-[minmax(170px,1fr)_minmax(135px,.8fr)_minmax(220px,1.4fr)_90px_80px_155px] gap-3 items-start">
                            <div><div class="flex items-center gap-2"><span class="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500"></span><div class="font-semibold text-sm text-ink">{{ row.subjectLabel }}</div></div><div class="text-[11px] text-mute mt-1 ml-[18px]">{{ row.subjectCode }} · coef {{ row.coefficient }}</div><div class="mt-2 text-[11px] font-semibold ml-[18px]" [class]="row.teacherStatus === 'RESOLVED' ? 'text-emerald-700' : 'text-amber-700'">{{ row.teacherName || (fr() ? 'Affectation manquante' : 'Assignment missing') }}</div></div>
                            <div><span class="field-label">{{ fr() ? 'Code enregistré' : 'Saved code' }}</span><div class="mt-1.5 min-h-10 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-mono font-semibold text-ink break-all">{{ row.proposedCode }}</div></div>
                            <div><span class="field-label">{{ fr() ? 'Nom enregistré' : 'Saved name' }}</span><div class="mt-1.5 min-h-10 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-ink">{{ row.proposedLabel }}</div></div>
                            <div><span class="field-label">{{ fr() ? 'Barème' : 'Max score' }}</span><div class="mt-1.5 min-h-10 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-ink">{{ row.maxScore }}</div></div>
                            <div><span class="field-label">{{ fr() ? 'Poids' : 'Weight' }}</span><div class="mt-1.5 min-h-10 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-ink">{{ row.weight }}</div></div>
                            <div><span class="field-label">{{ fr() ? 'État' : 'Status' }}</span><div class="mt-1.5 inline-flex min-h-10 items-center rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-800">{{ fr() ? 'Configurée' : 'Configured' }}</div><div class="mt-1 text-[11px] text-mute">{{ row.mandatory ? (fr() ? 'Note requise pour soumission' : 'Mark required for submission') : (fr() ? 'Note facultative' : 'Optional mark') }}</div></div>
                          </article>
                        }
                      </div>
                    </section>
                  }

                  @if (proposalRows(period).length) {
                    <section class="rounded-xl border border-brand-200 overflow-hidden">
                      <div class="px-4 py-3 border-b border-brand-100 bg-brand-50"><h4 class="font-bold text-brand-950">{{ fr() ? 'Nouvelles évaluations proposées' : 'New proposed evaluations' }}</h4><p class="text-xs text-brand-800 mt-0.5">{{ fr() ? 'Vérifiez et modifiez ces champs avant la création.' : 'Review and edit these fields before creation.' }}</p></div>
                      <div class="divide-y divide-slate-100">
                        @for (row of proposalRows(period); track row.clientRowId) {
                          <div class="p-3 grid grid-cols-1 xl:grid-cols-[minmax(150px,1fr)_minmax(190px,1.4fr)_105px_95px_145px_160px] gap-3 items-start">
                            <div><div class="font-semibold text-sm text-ink">{{ row.subjectLabel }}</div><div class="text-[11px] text-mute mt-1">{{ row.subjectCode }} · coef {{ row.coefficient }}</div><div class="mt-2 text-[11px] font-semibold" [class]="row.teacherStatus === 'RESOLVED' ? 'text-emerald-700' : 'text-amber-700'">{{ row.teacherName || (fr() ? 'Affectation manquante' : 'Assignment missing') }}</div><div class="text-[10px] text-mute">{{ fr() ? 'Enseignant affecté · lecture seule' : 'Assigned teacher · read only' }}</div></div>
                            <label class="block"><span class="field-label">{{ fr() ? 'Code' : 'Code' }}</span><input [ngModel]="draft(row).code" (ngModelChange)="setDraft(row, 'code', $event)" class="field font-mono" [class.border-rose-400]="fieldError(row, 'code')" maxlength="40" /><span class="block mt-1 text-[11px] text-rose-700 min-h-4">{{ fieldError(row, 'code') }}</span></label>
                            <label class="block"><span class="field-label">{{ fr() ? 'Nom' : 'Name' }}</span><input [ngModel]="draft(row).label" (ngModelChange)="setDraft(row, 'label', $event)" class="field" [class.border-rose-400]="fieldError(row, 'label')" maxlength="160" /><span class="block mt-1 text-[11px] text-rose-700 min-h-4">{{ fieldError(row, 'label') }}</span></label>
                            <label class="block"><span class="field-label">{{ fr() ? 'Barème' : 'Max score' }}</span><input type="number" [ngModel]="draft(row).maxScore" (ngModelChange)="setDraft(row, 'maxScore', $event)" class="field" [class.border-rose-400]="fieldError(row, 'maxScore')" min="0.01" step="0.01" /><span class="block mt-1 text-[11px] text-rose-700 min-h-4">{{ fieldError(row, 'maxScore') }}</span></label>
                            <label class="block"><span class="field-label">{{ fr() ? 'Poids' : 'Weight' }}</span><input type="number" [ngModel]="draft(row).weight" (ngModelChange)="setDraft(row, 'weight', $event)" class="field" [class.border-rose-400]="fieldError(row, 'weight')" min="0.01" step="0.1" /><span class="block mt-1 text-[11px] text-rose-700 min-h-4">{{ fieldError(row, 'weight') }}</span></label>
                            <div><span class="field-label">{{ fr() ? 'Soumission' : 'Submission' }}</span><label class="mt-2 flex items-center gap-2 text-xs text-ink"><input type="checkbox" [ngModel]="draft(row).mandatory" (ngModelChange)="setDraft(row, 'mandatory', $event)" /> {{ fr() ? 'Requis pour soumission' : 'Required for submission' }}</label><div class="mt-2 text-[11px] font-semibold text-brand-700">{{ fr() ? 'À créer' : 'To create' }}</div></div>
                          </div>
                        }
                      </div>
                    </section>
                  } @else if (existingRows(period).length) {
                    <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"><strong>{{ fr() ? 'Configuration complète.' : 'Configuration complete.' }}</strong> {{ fr() ? 'Toutes les matières applicables ont déjà une évaluation. Aucune nouvelle ligne ne sera créée.' : 'Every applicable subject already has an evaluation. No new row will be created.' }}</div>
                  } @else {
                    <div class="p-8"><bbc-empty icon="book" [label]="fr() ? 'Aucune matière applicable pour cette période.' : 'No applicable subject for this period.'" /></div>
                  }
                </div>
              </section>
            }
          </div>
          <div class="sticky bottom-0 px-4 py-3 bg-white border-t border-slate-200 flex flex-wrap items-center justify-between gap-3">@if (p.proposedRows) { <div class="text-sm text-mute">{{ p.totalRows }} {{ fr() ? 'matières ·' : 'subjects ·' }} <strong class="text-ink">{{ p.proposedRows }}</strong> {{ fr() ? 'nouvelles évaluations proposées' : 'new proposed evaluations' }}</div><button type="button" (click)="confirmApply.set(true)" [disabled]="busy() || hasValidationErrors()" class="h-10 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-semibold">{{ fr() ? 'Créer les évaluations' : 'Create evaluations' }}</button> } @else { <div class="flex items-center gap-2 text-sm font-semibold text-emerald-800"><span class="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500"></span>{{ p.existingRows }} {{ fr() ? 'évaluations configurées · aucune action requise' : 'configured evaluations · no action required' }}</div> }</div>
        </div>
      }
    </section>

    @if (confirmApply()) {
      <div class="fixed inset-0 z-50 bg-slate-950/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
        <div class="w-full max-w-md rounded-2xl bg-white shadow-2xl p-5"><h2 class="font-display text-lg font-bold text-ink">{{ fr() ? 'Confirmer la création' : 'Confirm creation' }}</h2><p class="text-sm text-mute mt-2">{{ fr() ? 'Les lignes proposées seront créées une seule fois pour la classe et les séquences sélectionnées. Les évaluations existantes resteront inchangées.' : 'The proposed rows will be created once for the selected class and sequences. Existing evaluations stay unchanged.' }}</p><div class="mt-5 flex justify-end gap-2"><button type="button" (click)="confirmApply.set(false)" class="h-10 px-4 rounded-lg bg-slate-100 text-sm font-semibold text-ink">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button type="button" (click)="apply()" [disabled]="busy()" class="h-10 px-4 rounded-lg bg-emerald-600 text-white text-sm font-semibold disabled:opacity-50">{{ busy() ? '…' : (fr() ? 'Créer maintenant' : 'Create now') }}</button></div></div>
      </div>
    }
  `,
})
export class AssessmentDefaultsComponent {
  private readonly api = inject(AcademicApi);
  private readonly setup = inject(SetupApi);
  private readonly foundation = inject(FoundationApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly fr = () => this.i18n.lang() === 'fr';
  protected readonly canWrite = this.auth.can('settings', 'write');
  protected readonly sessions = signal<AcademicSessionView[]>([]);
  protected readonly classes = signal<ClassView[]>([]);
  protected readonly periods = signal<AcademicReportingPeriodView[]>([]);
  protected readonly sessionId = signal('');
  protected readonly classId = signal('');
  protected readonly periodId = signal('');
  protected readonly mode = signal<AssessmentDefaultsMode>('ONE_SEQUENCE');
  protected readonly preview = signal<AssessmentDefaultsPreview | null>(null);
  protected readonly drafts = signal<Record<string, Draft>>({});
  protected readonly busy = signal(false);
  protected readonly confirmApply = signal(false);
  protected readonly notice = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly curriculumEmpty = signal(false);
  protected readonly sequencePeriods = computed(() => this.periods().filter((p) => p.periodType === 'SEQUENCE'));

  constructor() {
    this.setup.listClasses().subscribe((rows) => this.classes.set(rows));
    this.foundation.listSessions().subscribe((rows) => {
      this.sessions.set(rows);
      const wanted = this.route.snapshot.queryParamMap.get('sessionId');
      const current = rows.find((s) => s.id === wanted) ?? rows.find((s) => s.current) ?? rows[0];
      if (current) this.selectSession(current.id);
    });
    const wantedClass = this.route.snapshot.queryParamMap.get('classId');
    if (wantedClass) this.classId.set(wantedClass);
  }

  protected selectSession(id: string): void {
    this.sessionId.set(id); this.periodId.set(''); this.preview.set(null); this.notice.set(null); this.curriculumEmpty.set(false);
    if (!id) { this.periods.set([]); return; }
    this.foundation.reportingPeriods(id).subscribe({
      next: (rows) => { this.periods.set(rows); const wanted = this.route.snapshot.queryParamMap.get('periodId'); const first = rows.find((p) => p.id === wanted && p.periodType === 'SEQUENCE') ?? rows.find((p) => p.periodType === 'SEQUENCE'); if (first) this.periodId.set(first.id); if (this.canPrepare()) this.prepare(); },
      error: () => this.periods.set([]),
    });
  }

  protected selectClass(id: string): void {
    this.classId.set(id); this.clearPreview();
    if (this.canPrepare()) this.prepare();
  }

  protected selectMode(value: AssessmentDefaultsMode): void {
    this.mode.set(value); this.clearPreview();
    if (this.canPrepare()) this.prepare();
  }

  protected selectPeriod(id: string): void {
    this.periodId.set(id); this.clearPreview();
    if (this.canPrepare()) this.prepare();
  }

  protected clearPreview(): void { this.preview.set(null); this.notice.set(null); this.curriculumEmpty.set(false); }
  protected canPrepare(): boolean { return !!this.sessionId() && !!this.classId() && (this.mode() === 'ALL_SEQUENCES' || !!this.periodId()) && this.canWrite; }

  protected prepare(preserveNotice = false): void {
    if (!this.canPrepare()) return;
    this.busy.set(true); if (!preserveNotice) this.notice.set(null); this.curriculumEmpty.set(false);
    const body: AssessmentDefaultsRequest = { academicSessionId: this.sessionId(), classId: this.classId(), mode: this.mode(), reportingPeriodId: this.mode() === 'ONE_SEQUENCE' ? this.periodId() : undefined };
    this.api.previewAssessmentDefaults(body).subscribe({
      next: (value) => { this.busy.set(false); this.preview.set(value); const next: Record<string, Draft> = {}; value.periods.forEach((p) => p.rows.forEach((row) => next[row.clientRowId] = { code: row.proposedCode, label: row.proposedLabel, maxScore: row.maxScore, weight: row.weight, mandatory: row.mandatory })); this.drafts.set(next); },
      error: (error) => { this.busy.set(false); this.handleError(error); },
    });
  }

  protected existingRows(period: AssessmentDefaultsPreview['periods'][number]): AssessmentDefaultsRow[] { return period.rows.filter((row) => row.status === 'EXISTING'); }
  protected proposalRows(period: AssessmentDefaultsPreview['periods'][number]): AssessmentDefaultsRow[] { return period.rows.filter((row) => row.status !== 'EXISTING'); }
  protected draft(row: AssessmentDefaultsRow): Draft { return this.drafts()[row.clientRowId] ?? { code: row.proposedCode, label: row.proposedLabel, maxScore: row.maxScore, weight: row.weight, mandatory: row.mandatory }; }
  protected setDraft(row: AssessmentDefaultsRow, field: DraftField, value: unknown): void {
    const current = this.draft(row); const next = { ...current, [field]: field === 'maxScore' || field === 'weight' ? Number(value) : value } as Draft; this.drafts.update((all) => ({ ...all, [row.clientRowId]: next }));
  }
  protected fieldError(row: AssessmentDefaultsRow, field: DraftField): string {
    const draft = this.draft(row); if (field === 'code' && (!draft.code.trim() || draft.code.trim().length > 40)) return this.fr() ? 'Code obligatoire (40 caractères max).' : 'Code is required (40 characters max).'; if (field === 'label' && (!draft.label.trim() || draft.label.trim().length > 160)) return this.fr() ? 'Nom obligatoire (160 caractères max).' : 'Name is required (160 characters max).'; if (field === 'maxScore' && (!(draft.maxScore > 0) || !Number.isFinite(draft.maxScore))) return this.fr() ? 'Barème supérieur à 0 requis.' : 'Max score must be above 0.'; if (field === 'weight' && (!(draft.weight > 0) || !Number.isFinite(draft.weight))) return this.fr() ? 'Poids supérieur à 0 requis.' : 'Weight must be above 0.'; return ''; }
  protected hasValidationErrors(): boolean { return this.preview()?.periods.some((p) => p.rows.some((row) => row.status !== 'EXISTING' && ['code', 'label', 'maxScore', 'weight'].some((field) => this.fieldError(row, field as DraftField)))) ?? false; }

  protected apply(): void {
    const p = this.preview(); if (!p || this.hasValidationErrors()) return;
    this.busy.set(true);
    const rows: AssessmentDefaultsRequest['rows'] = p.periods.flatMap((period) => period.rows.filter((row) => row.status !== 'EXISTING').map((row) => { const draft = this.draft(row); return { clientRowId: row.clientRowId, reportingPeriodId: row.reportingPeriodId, subjectCode: row.subjectCode, code: draft.code.trim(), label: draft.label.trim(), maxScore: Number(draft.maxScore), weight: Number(draft.weight), mandatory: draft.mandatory }; }));
    const body: AssessmentDefaultsRequest = { academicSessionId: p.academicSessionId, classId: p.classId, mode: p.mode, reportingPeriodId: p.mode === 'ONE_SEQUENCE' ? this.periodId() : undefined, rows, scopeFingerprint: p.scopeFingerprint };
    this.api.applyAssessmentDefaults(body, this.idempotencyKey()).subscribe({
      next: (result) => { this.busy.set(false); this.confirmApply.set(false); this.notice.set({ ok: true, text: this.fr() ? `${result.createdCount} évaluation(s) créée(s). Les lignes existantes ont été conservées.` : `${result.createdCount} evaluation(s) created. Existing rows were preserved.` }); this.prepare(true); },
      error: (error) => { this.busy.set(false); this.confirmApply.set(false); this.handleError(error); },
    });
  }

  private idempotencyKey(): string { const cryptoApi = globalThis.crypto as Crypto & { randomUUID?: () => string }; return cryptoApi.randomUUID?.() ?? `assessment-defaults-${Date.now()}`; }
  protected openCurriculumSetup(): void { this.router.navigate(['/settings'], { queryParams: { tab: 'academic', subtab: 'class-subjects', sessionId: this.sessionId(), classId: this.classId() } }); }
  private handleError(error: any): void { const code = error?.error?.code; if (code === 'CLASS_CURRICULUM_EMPTY') this.curriculumEmpty.set(true); const message = error?.error?.message; this.notice.set({ ok: false, text: message || (this.fr() ? 'Impossible de préparer la revue. Vérifiez la session, la classe et les matières affectées.' : 'The review could not be prepared. Check the session, class, and assigned subjects.') }); }
}
