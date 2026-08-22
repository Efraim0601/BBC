import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CohortApi, CohortView, PathwayPreview } from '../../core/cohort.api';
import { FoundationApi, AcademicSessionView } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';
import { AvatarComponent, CardComponent, EmptyComponent, IconComponent, PageHeaderComponent } from '../../core/ui';

@Component({
  selector: 'bbc-pathway-selection',
  standalone: true,
  imports: [FormsModule, CardComponent, EmptyComponent, IconComponent, PageHeaderComponent, AvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fade-in mx-auto max-w-7xl space-y-4">
      <bbc-page-header [title]="fr() ? 'Choix manuel du parcours' : 'Manual pathway choice'"
        [subtitle]="fr() ? 'Choisissez le parcours de chaque élève pour la prochaine session — aucun transfert automatique.' : 'Choose each student’s programme for the next session — nothing is inferred automatically.'" />
      @if (error(); as message) { <div class="rounded-lg border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ message }}</div> }
      @if (notice(); as message) { <div class="rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{{ message }}</div> }

      <bbc-card [title]="fr() ? '1. Préparer la liste' : '1. Prepare the list'" [subtitle]="fr() ? 'La décision est prise d’une session à l’autre, élève par élève.' : 'The decision is made from one session to the next, one student at a time.'">
        <div class="grid gap-3 md:grid-cols-3 md:items-end">
          <label class="block"><span class="meta">{{ fr() ? 'Session actuelle' : 'Current session' }}</span><select class="field" [ngModel]="sourceSessionId()" (ngModelChange)="selectSourceSession($event)">@for (session of sessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }</select></label>
          <label class="block"><span class="meta">{{ fr() ? 'Session suivante' : 'Next session' }}</span><select class="field" [ngModel]="targetSessionId()" (ngModelChange)="selectTargetSession($event)">@for (session of targetSessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }</select></label>
          <label class="block"><span class="meta">{{ fr() ? 'Groupe source' : 'Source group' }}</span><select class="field" [ngModel]="sourceCohortId()" (ngModelChange)="sourceCohortId.set($event); loadPreview()"><option value="">{{ fr() ? 'Choisir un groupe' : 'Choose a group' }}</option>@for (group of sourceCohorts(); track group.id) { <option [value]="group.id">{{ group.displayName }} · {{ group.studentCount }} {{ fr() ? 'élèves' : 'students' }}</option> }</select></label>
        </div>
        <div class="mt-3 flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-mute"><bbc-icon name="info" [s]="15" />{{ fr() ? 'Les classes appariées restent un seul roster. Ici, vous choisissez seulement le parcours de la prochaine session.' : 'Paired classes remain one roster. Here you only choose the next session’s programme.' }}</div>
      </bbc-card>

      @if (preview(); as current) {
        <bbc-card [title]="current.sourceCohortName" [subtitle]="current.sourceSessionLabel + ' → ' + current.targetSessionLabel">
          <div class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4"><div class="rounded-lg bg-slate-50 px-3 py-2"><div class="text-[10px] uppercase tracking-wide text-mute">{{ fr() ? 'Élèves' : 'Students' }}</div><div class="text-xl font-bold text-ink">{{ current.students.length }}</div></div><div class="rounded-lg bg-slate-50 px-3 py-2"><div class="text-[10px] uppercase tracking-wide text-mute">{{ fr() ? 'Choisis' : 'Chosen' }}</div><div class="text-xl font-bold text-ink">{{ chosenCount() }}</div></div><div class="rounded-lg bg-slate-50 px-3 py-2 md:col-span-2"><div class="text-[10px] uppercase tracking-wide text-mute">{{ fr() ? 'Règle' : 'Rule' }}</div><div class="mt-1 text-sm font-semibold text-ink">{{ fr() ? 'Aucune décision automatique' : 'No automatic decision' }}</div></div></div>
          @if (!current.students.length) { <bbc-empty icon="users" [label]="fr() ? 'Aucun élève actif dans ce groupe.' : 'No active student in this group.'" /> }
          @else { <div class="overflow-x-auto"><table class="min-w-full text-sm"><thead><tr class="border-b border-slate-100 text-left text-[11px] uppercase tracking-wide text-mute"><th class="py-2 pr-3">{{ fr() ? 'Élève' : 'Student' }}</th><th class="py-2 px-3">{{ fr() ? 'Parcours choisi pour la prochaine session' : 'Programme chosen for next session' }}</th></tr></thead><tbody>@for (student of current.students; track student.studentId) { <tr class="border-b border-slate-50"><td class="py-2 pr-3"><div class="flex items-center gap-2"><bbc-avatar [name]="student.studentName" [hue]="hueFor(student.studentId)" [size]="30" /><div><div class="font-semibold text-ink">{{ student.studentName }}</div><div class="font-mono text-[11px] text-mute">{{ student.matricule }}</div></div></div></td><td class="py-2 px-3"><select class="field max-w-xl" [ngModel]="targetByStudent[student.studentId] || ''" (ngModelChange)="setTarget(student.studentId, $event)"><option value="">{{ fr() ? '— Choisir manuellement —' : '— Choose manually —' }}</option>@for (target of current.targets; track target.cohortId) { <option [value]="target.cohortId">{{ target.displayName }} · {{ target.programmeLabel }}</option> }</select></td></tr> }</tbody></table></div> }
          <div class="mt-4 flex flex-wrap items-center justify-end gap-2"><button type="button" (click)="save(false)" [disabled]="busy() || !chosenCount()" class="h-10 rounded-lg border border-slate-200 bg-white px-4 text-sm font-semibold text-ink hover:bg-slate-50 disabled:opacity-40">{{ fr() ? 'Enregistrer le brouillon' : 'Save draft' }}</button><button type="button" (click)="save(true)" [disabled]="busy() || chosenCount() !== current.students.length || !canCommit" class="inline-flex h-10 items-center gap-2 rounded-lg bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-40"><bbc-icon name="check" [s]="16" />{{ fr() ? 'Confirmer et planifier' : 'Confirm and plan' }}</button></div>
          @if (!canCommit) { <div class="mt-2 text-right text-xs text-mute">{{ fr() ? 'La confirmation nécessite le droit de validation des passages.' : 'Confirmation requires promotion commit permission.' }}</div> }
        </bbc-card>
      } @else { <bbc-card><bbc-empty icon="route" [label]="fr() ? 'Choisissez un groupe source pour commencer.' : 'Choose a source group to begin.'" /></bbc-card> }
    </div>
  `,
})
export class PathwaySelectionComponent {
  private api = inject(CohortApi);
  private foundation = inject(FoundationApi);
  private auth = inject(AuthService);
  private i18n = inject(I18nService);

  protected fr = () => this.i18n.lang() === 'fr';
  protected sessions = signal<AcademicSessionView[]>([]);
  protected sourceCohorts = signal<CohortView[]>([]);
  protected targetCohorts = signal<CohortView[]>([]);
  protected sourceSessionId = signal('');
  protected targetSessionId = signal('');
  protected sourceCohortId = signal('');
  protected preview = signal<PathwayPreview | null>(null);
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected notice = signal<string | null>(null);
  protected targetByStudent: Record<string, string> = {};
  private choiceRevision = signal(0);
  protected canCommit = this.auth.canAction('PROMOTION_COMMIT');
  protected targetSessions = computed(() => {
    const source = this.sessions().find(s => s.id === this.sourceSessionId());
    return this.sessions().filter(s => s.id !== this.sourceSessionId() && (!source || s.startDate > source.startDate));
  });
  protected chosenCount = computed(() => {
    this.choiceRevision();
    return Object.values(this.targetByStudent).filter(Boolean).length;
  });

  constructor() {
    this.foundation.listSessions().subscribe({ next: sessions => {
      this.sessions.set(sessions);
      const source = sessions.find(s => s.current) ?? sessions.find(s => s.status === 'OPEN') ?? sessions[0];
      const target = sessions.find(s => s.id !== source?.id && s.startDate > (source?.startDate ?? '') && (s.status === 'DRAFT' || s.status === 'OPEN'))
        ?? sessions.find(s => s.id !== source?.id && s.startDate > (source?.startDate ?? ''));
      if (source) this.sourceSessionId.set(source.id);
      if (target) this.targetSessionId.set(target.id);
      this.loadCohorts();
    }, error: e => this.fail(e) });
  }
  protected selectSourceSession(id: string): void {
    this.sourceSessionId.set(id); this.sourceCohortId.set(''); this.preview.set(null); this.notice.set(null);
    const next = this.targetSessions()[0];
    this.targetSessionId.set(next?.id ?? '');
    this.loadCohorts();
  }
  protected selectTargetSession(id: string): void { this.targetSessionId.set(id); this.preview.set(null); this.notice.set(null); this.loadCohorts(); }
  private loadCohorts(): void {
    const source = this.sourceSessionId(), target = this.targetSessionId(); if (!source || !target) return;
    this.busy.set(true);
    this.api.list(source).subscribe({ next: rows => { this.sourceCohorts.set(rows); this.busy.set(false); }, error: e => this.fail(e) });
    this.api.list(target).subscribe({ next: rows => this.targetCohorts.set(rows), error: e => this.fail(e) });
  }
  protected loadPreview(clearNotice = true): void {
    if (!this.sourceSessionId() || !this.targetSessionId() || !this.sourceCohortId()) return;
    this.busy.set(true); this.error.set(null); if (clearNotice) this.notice.set(null);
    this.api.pathwayPreview(this.sourceSessionId(), this.targetSessionId(), this.sourceCohortId()).subscribe({ next: value => { this.preview.set(value); this.targetByStudent = {}; for (const row of value.students) this.targetByStudent[row.studentId] = row.selectedTargetCohortId ?? ''; this.choiceRevision.update(v => v + 1); this.busy.set(false); }, error: e => this.fail(e) });
  }
  protected setTarget(studentId: string, targetCohortId: string): void {
    this.targetByStudent[studentId] = targetCohortId;
    this.choiceRevision.update(v => v + 1);
  }
  protected save(confirm: boolean): void {
    const current = this.preview(); if (!current) return;
    const choices = current.students.filter(s => this.targetByStudent[s.studentId]).map(s => ({ studentId: s.studentId, targetCohortId: this.targetByStudent[s.studentId], reason: null }));
    if (!choices.length || (confirm && choices.length !== current.students.length)) return;
    this.busy.set(true); this.api.applyPathway({ sourceSessionId: this.sourceSessionId(), targetSessionId: this.targetSessionId(), sourceCohortId: this.sourceCohortId(), choices, confirm }).subscribe({ next: result => { this.busy.set(false); this.notice.set(confirm ? (this.fr() ? `${result.confirmed} choix confirmé(s), ${result.plannedEnrollments} inscription(s) planifiée(s).` : `${result.confirmed} choice(s) confirmed, ${result.plannedEnrollments} enrollment(s) planned.`) : (this.fr() ? `${result.saved} choix enregistré(s) en brouillon.` : `${result.saved} choice(s) saved as draft.`)); this.loadPreview(false); }, error: e => this.fail(e) });
  }
  protected hueFor(id: string): number { return Array.from(id).reduce((value, char) => value + char.charCodeAt(0), 210) % 360; }
  private fail(error: any): void { this.busy.set(false); this.error.set(error?.error?.message || error?.message || (this.fr() ? 'Impossible de charger le parcours.' : 'Could not load pathway data.')); }
}
