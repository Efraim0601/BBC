import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { FoundationApi, AcademicSessionView } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';
import {
  PrimaryApplyResult, PrimaryClassStatus, PrimaryCompetenceApi, PrimaryGridItem, PrimaryGridView,
} from '../../core/primary-competence.api';

type Part = 'oral' | 'written' | 'practical' | 'attitude';

/**
 * Primary competence grids: for each subsystem and level (I = SIL/CP · Class 1-2, II = CE1/CE2 ·
 * Class 3-4, III = CM1/CM2 · Class 5-6), the sub-competences taught as subjects, grouped by
 * competence, with the points of each evaluation type. Applying a grid to a class builds its
 * subjects and its sequence evaluations.
 */
@Component({
  selector: 'bbc-primary-competence-grid',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-5">
      <div class="rounded-2xl border border-brand-100 bg-brand-50/70 p-5 text-sm leading-6 text-mute">
        <div class="text-base font-bold text-ink">{{ fr() ? 'Bulletin du primaire par compétences' : 'Competence-based primary report card' }}</div>
        {{ fr()
          ? 'Chaque niveau a une grille : les 6 compétences, leurs sous-compétences (les matières du bulletin) et les points de l’oral, de l’écrit, de la pratique et du savoir-être. Les deux classes d’un niveau partagent la même grille. Laissez une case vide quand un type d’évaluation n’est pas utilisé.'
          : 'Each level has one grid: the 6 competences, their sub-competences (the report-card subjects) and the points for oral, written, practical and attitude. Both classes of a level share it. Leave a box empty when an evaluation type is not used.' }}
      </div>

      @if (error(); as message) { <div class="rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ message }}</div> }
      @if (notice(); as message) { <div class="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{{ message }}</div> }

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="grid gap-3 md:grid-cols-3">
          <label class="block"><span class="meta">{{ fr() ? 'Section' : 'Section' }}</span>
            <select class="field" [ngModel]="subsystem()" (ngModelChange)="pick($event, level())">
              <option value="FR">{{ fr() ? 'Francophone' : 'Francophone' }}</option>
              <option value="EN">{{ fr() ? 'Anglophone' : 'Anglophone' }}</option>
            </select>
          </label>
          <label class="block"><span class="meta">{{ fr() ? 'Niveau' : 'Level' }}</span>
            <select class="field" [ngModel]="level()" (ngModelChange)="pick(subsystem(), +$event)">
              @for (option of levels; track option.value) { <option [ngValue]="option.value">{{ levelLabel(option.value) }}</option> }
            </select>
          </label>
          <div class="flex items-end justify-between gap-3">
            <div class="text-sm text-mute">{{ fr() ? 'Version' : 'Version' }} {{ current()?.version ?? '—' }} · <strong class="text-ink">{{ grandTotal() }} {{ fr() ? 'points' : 'marks' }}</strong></div>
          </div>
        </div>

        <div class="mt-4 overflow-x-auto">
          <table class="w-full min-w-[860px] text-sm">
            <thead><tr class="text-left text-xs uppercase text-mute">
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Code' : 'Code' }}</th>
              <th class="py-2 pr-2">{{ fr() ? 'Sous-compétence (FR / EN)' : 'Sub-competence (FR / EN)' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Compét.' : 'Comp.' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Oral' : 'Oral' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Écrit' : 'Written' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Pratique' : 'Practical' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Savoir-être' : 'Attitude' }}</th>
              <th class="py-2 pr-2 w-14 text-right">{{ fr() ? 'Total' : 'Total' }}</th>
              <th class="py-2 pr-2 w-16">{{ fr() ? 'Au choix' : 'Either-or' }}</th>
              <th class="py-2 pr-2 w-14">{{ fr() ? 'Compté' : 'Counted' }}</th>
              <th class="py-2 w-8"></th>
            </tr></thead>
            <tbody>
              @for (row of rows(); track $index; let i = $index) {
                @if (i === 0 || rows()[i - 1].competenceCode !== row.competenceCode) {
                  <tr class="bg-slate-50"><td colspan="11" class="px-2 py-1.5 text-xs font-bold text-brand-800">
                    {{ competenceTitle(row) }} · {{ competenceTotal(row.competenceCode) }} {{ fr() ? 'points' : 'marks' }}
                  </td></tr>
                }
                <tr class="border-t border-slate-100 align-top">
                  <td class="py-1.5 pr-2"><input class="field" [(ngModel)]="row.code" [disabled]="!canWrite" (ngModelChange)="touch()" /></td>
                  <td class="py-1.5 pr-2 space-y-1">
                    <input class="field" [(ngModel)]="row.label['fr']" [disabled]="!canWrite" placeholder="Français" />
                    <input class="field" [(ngModel)]="row.label['en']" [disabled]="!canWrite" placeholder="English" />
                  </td>
                  <td class="py-1.5 pr-2">
                    <select class="field" [(ngModel)]="row.competenceCode" [disabled]="!canWrite" (ngModelChange)="touch()">
                      @for (code of competenceCodes; track code) { <option [value]="code">{{ code }}</option> }
                    </select>
                  </td>
                  @for (part of parts; track part) {
                    <td class="py-1.5 pr-2"><input class="field" type="number" min="1" [ngModel]="row[part]" [disabled]="!canWrite"
                      (ngModelChange)="setPart(row, part, $event)" /></td>
                  }
                  <td class="py-1.5 pr-2 text-right font-bold text-ink">{{ rowTotal(row) }}</td>
                  <td class="py-1.5 pr-2"><input class="field" [(ngModel)]="row.alternativeGroup" [disabled]="!canWrite" (ngModelChange)="touch()" placeholder="6A" /></td>
                  <td class="py-1.5 pr-2 text-center"><input type="checkbox" [(ngModel)]="row.countsInTotal" [disabled]="!canWrite" (ngModelChange)="touch()" /></td>
                  <td class="py-1.5 text-right">@if (canWrite) { <button type="button" class="text-rose-600" (click)="remove(i)" [attr.aria-label]="fr() ? 'Supprimer' : 'Remove'">✕</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (canWrite) {
          <div class="mt-4 flex flex-wrap items-center gap-3">
            <button type="button" class="btn-secondary" (click)="add()">{{ fr() ? 'Ajouter une sous-compétence' : 'Add a sub-competence' }}</button>
            <button type="button" class="btn-primary" [disabled]="busy() || !dirty()" (click)="save()">{{ fr() ? 'Publier une nouvelle version' : 'Publish a new version' }}</button>
            <span class="text-xs text-mute">{{ fr() ? 'Les classes gardent leur version jusqu’à ce que vous appliquiez la nouvelle.' : 'Classes keep their version until you apply the new one.' }}</span>
          </div>
        }
      </section>

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h3 class="text-base font-bold text-ink">{{ fr() ? 'Appliquer aux classes' : 'Apply to classes' }}</h3>
            <p class="text-sm text-mute">{{ fr()
              ? 'Crée les matières (une par sous-compétence) et les évaluations des séquences 1 à 5. Les notes déjà saisies ne sont jamais modifiées.'
              : 'Creates the subjects (one per sub-competence) and the evaluations of sequences 1 to 5. Marks already entered are never changed.' }}</p>
          </div>
          <label class="block min-w-[220px]"><span class="meta">{{ fr() ? 'Session scolaire' : 'School year' }}</span>
            <select class="field" [ngModel]="sessionId()" (ngModelChange)="selectSession($event)">
              @for (session of sessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }
            </select>
          </label>
        </div>
        <table class="mt-4 w-full text-sm">
          <thead><tr class="text-left text-xs uppercase text-mute">
            <th class="py-2">{{ fr() ? 'Classe' : 'Class' }}</th><th class="py-2">{{ fr() ? 'Niveau' : 'Level' }}</th>
            <th class="py-2">{{ fr() ? 'Grille appliquée' : 'Applied grid' }}</th><th class="py-2"></th>
          </tr></thead>
          <tbody>
            @for (klass of classes(); track klass.classId) {
              <tr class="border-t border-slate-100">
                <td class="py-2 font-semibold text-ink">{{ klass.className }}</td>
                <td class="py-2">
                  <select class="field max-w-[180px]" [ngModel]="chosenLevel(klass)" (ngModelChange)="chooseLevel(klass, +$event)" [disabled]="!canWrite">
                    @for (option of levels; track option.value) { <option [ngValue]="option.value">{{ levelLabel(option.value) }}</option> }
                  </select>
                </td>
                <td class="py-2 text-mute">
                  @if (klass.appliedVersion) { {{ levelLabel(klass.appliedLevel ?? 0) }} · v{{ klass.appliedVersion }}
                    @if (klass.latestVersion && klass.appliedVersion < klass.latestVersion) { <span class="chip bg-amber-50 text-amber-700">{{ fr() ? 'nouvelle version' : 'new version' }}</span> }
                  } @else { {{ fr() ? 'Pas encore' : 'Not yet' }} }
                </td>
                <td class="py-2 text-right">@if (canWrite) {
                  <button type="button" class="btn-secondary" [disabled]="busy() || !chosenLevel(klass)" (click)="apply(klass)">{{ klass.appliedVersion ? (fr() ? 'Mettre à jour' : 'Update') : (fr() ? 'Appliquer' : 'Apply') }}</button>
                }</td>
              </tr>
            } @empty { <tr><td colspan="4" class="py-3 text-mute">{{ fr() ? 'Aucune classe du primaire.' : 'No primary class.' }}</td></tr> }
          </tbody>
        </table>
      </section>
    </div>
  `,
})
export class PrimaryCompetenceGridComponent {
  private api = inject(PrimaryCompetenceApi);
  private foundation = inject(FoundationApi);
  private auth = inject(AuthService);
  private i18n = inject(I18nService);

  protected fr = () => this.i18n.lang() === 'fr';
  protected readonly parts: Part[] = ['oral', 'written', 'practical', 'attitude'];
  protected readonly competenceCodes = ['C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'CX'];
  protected readonly levels = [{ value: 1 }, { value: 2 }, { value: 3 }];
  protected canWrite = this.auth.canAction('CURRICULUM_CATALOG_MANAGE');

  protected grids = signal<PrimaryGridView[]>([]);
  protected subsystem = signal<'FR' | 'EN'>('FR');
  protected level = signal(1);
  protected rows = signal<PrimaryGridItem[]>([]);
  protected dirty = signal(false);
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected notice = signal<string | null>(null);
  protected sessions = signal<AcademicSessionView[]>([]);
  protected sessionId = signal('');
  protected classes = signal<PrimaryClassStatus[]>([]);
  private levelChoices = signal<Record<string, number>>({});
  private revision = signal(0);

  protected current = computed(() => this.grids().find(g => g.subsystem === this.subsystem() && g.level === this.level()) ?? null);
  protected grandTotal = computed(() => {
    this.revision();
    const seen = new Set<string>();
    return this.rows().reduce((sum, row) => {
      if (!row.countsInTotal || row.competenceCode === 'CX') return sum;
      const group = (row.alternativeGroup ?? '').trim().toUpperCase();
      if (group && seen.has(group)) return sum;
      if (group) seen.add(group);
      return sum + this.rowTotal(row);
    }, 0);
  });

  constructor() {
    this.loadGrids();
    this.foundation.listSessions().subscribe({ next: sessions => {
      this.sessions.set(sessions);
      const current = sessions.find(s => s.current) ?? sessions.find(s => s.status === 'OPEN') ?? sessions[0];
      if (current) this.selectSession(current.id);
    }, error: e => this.fail(e) });
  }

  protected levelLabel(level: number): string {
    const fr = this.fr();
    return level === 1 ? (fr ? 'Niveau I · SIL, CP / Class 1-2' : 'Level I · SIL, CP / Class 1-2')
      : level === 2 ? (fr ? 'Niveau II · CE1, CE2 / Class 3-4' : 'Level II · CE1, CE2 / Class 3-4')
      : level === 3 ? (fr ? 'Niveau III · CM1, CM2 / Class 5-6' : 'Level III · CM1, CM2 / Class 5-6') : '—';
  }

  protected pick(subsystem: 'FR' | 'EN', level: number): void {
    this.subsystem.set(subsystem); this.level.set(level); this.resetRows();
  }

  protected competenceTitle(row: PrimaryGridItem): string {
    const label = row.competenceLabel?.[this.fr() ? 'fr' : 'en'] ?? '';
    return row.competenceCode === 'CX' ? label || (this.fr() ? 'Non compté' : 'Not counted') : row.competenceCode + (label ? ' — ' + label : '');
  }

  protected rowTotal(row: PrimaryGridItem): number {
    return this.parts.reduce((sum, part) => sum + (Number(row[part]) || 0), 0);
  }

  protected competenceTotal(code: string): number {
    this.revision();
    const seen = new Set<string>();
    return this.rows().filter(row => row.competenceCode === code).reduce((sum, row) => {
      const group = (row.alternativeGroup ?? '').trim().toUpperCase();
      if (group && seen.has(group)) return sum;
      if (group) seen.add(group);
      return sum + this.rowTotal(row);
    }, 0);
  }

  protected setPart(row: PrimaryGridItem, part: Part, value: unknown): void {
    const number = value === '' || value === null || value === undefined ? null : Number(value);
    row[part] = number && number > 0 ? number : null;
    this.touch();
  }

  protected touch(): void { this.dirty.set(true); this.revision.update(v => v + 1); }

  protected add(): void {
    this.rows.update(rows => [...rows, {
      competenceCode: 'C1', competenceLabel: {}, code: '', label: { fr: '', en: '' }, points: 0,
      oral: null, written: null, practical: null, attitude: null, alternativeGroup: null, countsInTotal: true,
    }]);
    this.touch();
  }

  protected remove(index: number): void { this.rows.update(rows => rows.filter((_, i) => i !== index)); this.touch(); }

  protected save(): void {
    const items = this.rows().map(row => ({
      ...row, code: row.code.trim().toUpperCase(), points: this.rowTotal(row),
      alternativeGroup: row.alternativeGroup?.trim() ? row.alternativeGroup.trim().toUpperCase() : null,
      competenceLabel: row.competenceLabel ?? {},
    }));
    this.busy.set(true); this.error.set(null); this.notice.set(null);
    this.api.save(this.subsystem(), this.level(), items).subscribe({
      next: grid => {
        this.grids.update(grids => grids.map(g => g.subsystem === grid.subsystem && g.level === grid.level ? grid : g));
        this.resetRows(); this.busy.set(false); this.loadClasses();
        this.notice.set(this.fr() ? 'Version ' + grid.version + ' publiée (' + grid.totalPoints + ' points).' : 'Version ' + grid.version + ' published (' + grid.totalPoints + ' marks).');
      },
      error: e => this.fail(e),
    });
  }

  protected selectSession(id: string): void { this.sessionId.set(id); this.loadClasses(); }

  protected chosenLevel(klass: PrimaryClassStatus): number | null {
    return this.levelChoices()[klass.classId] ?? klass.appliedLevel ?? klass.suggestedLevel;
  }

  protected chooseLevel(klass: PrimaryClassStatus, level: number): void {
    this.levelChoices.update(choices => ({ ...choices, [klass.classId]: level }));
  }

  protected apply(klass: PrimaryClassStatus): void {
    const level = this.chosenLevel(klass);
    if (!level || !this.sessionId()) return;
    this.busy.set(true); this.error.set(null); this.notice.set(null);
    this.api.apply(klass.classId, this.sessionId(), level).subscribe({
      next: (result: PrimaryApplyResult) => {
        this.busy.set(false); this.loadClasses();
        const fr = this.fr();
        this.notice.set(result.className + ' : ' + result.subjects + (fr ? ' matières, ' : ' subjects, ')
          + result.assessmentsCreated + (fr ? ' évaluations créées, ' : ' evaluations created, ')
          + result.assessmentsUpdated + (fr ? ' mises à jour' : ' updated')
          + (result.retiredSubjects.length ? (fr ? ' · anciennes matières retirées : ' : ' · former subjects retired: ') + result.retiredSubjects.join(', ') : ''));
      },
      error: e => this.fail(e),
    });
  }

  private loadGrids(): void {
    this.api.grids().subscribe({ next: grids => { this.grids.set(grids); this.resetRows(); }, error: e => this.fail(e) });
  }

  private loadClasses(): void {
    const id = this.sessionId(); if (!id) return;
    this.api.classes(id).subscribe({ next: rows => this.classes.set(rows), error: e => this.fail(e) });
  }

  private resetRows(): void {
    const grid = this.current();
    this.rows.set(grid ? grid.items.map(item => ({ ...item, label: { ...item.label }, competenceLabel: { ...item.competenceLabel } })) : []);
    this.dirty.set(false); this.revision.update(v => v + 1);
  }

  private fail(error: any): void {
    this.busy.set(false);
    this.error.set(error?.error?.message || error?.message || (this.fr() ? 'L’opération a échoué.' : 'The operation failed.'));
  }
}
