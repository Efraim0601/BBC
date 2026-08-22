import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CohortApi, CohortClassOption, CohortUpsert, CohortView } from '../../core/cohort.api';
import { FoundationApi, AcademicSessionView } from '../../core/foundation.api';
import { I18nService } from '../../core/i18n.service';
import { IconComponent } from '../../core/ui';

@Component({
  selector: 'bbc-bilingual-groups',
  standalone: true,
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-5">
      <div class="rounded-2xl border border-brand-100 bg-brand-50/70 p-5">
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-lg font-black text-brand-700 shadow-sm">↔</div>
          <div>
            <div class="text-base font-bold text-ink">{{ fr() ? 'Un même groupe d’élèves, deux vues de programme' : 'One student group, two programme views' }}</div>
            <p class="mt-1 max-w-3xl text-sm leading-6 text-mute">{{ fr()
              ? 'Utilisez cette page lorsque les élèves de la classe francophone et de la classe anglophone sont les mêmes. Chaque élève est inscrit une seule fois, apparaît dans les deux classes, et l’appel quotidien est fait une seule fois.'
              : 'Use this page when the Francophone and English classes contain the same students. Each student is enrolled once, appears in both classes, and daily attendance is taken once.' }}</p>
          </div>
        </div>
        <div class="mt-4 grid gap-2 text-xs font-semibold text-brand-900 sm:grid-cols-3">
          <div class="rounded-lg bg-white/80 px-3 py-2">1. {{ fr() ? 'Associer les deux classes' : 'Link the two classes' }}</div>
          <div class="rounded-lg bg-white/80 px-3 py-2">2. {{ fr() ? 'Inscrire chaque élève une fois' : 'Enroll each student once' }}</div>
          <div class="rounded-lg bg-white/80 px-3 py-2">3. {{ fr() ? 'Obtenir deux vues de bulletin' : 'Get two report-card views' }}</div>
        </div>
      </div>

      @if (error(); as message) { <div class="rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ message }}</div> }
      @if (notice(); as message) { <div class="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{{ message }}</div> }
      @if (!canWrite) {
        <div class="rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {{ fr() ? 'Vous pouvez consulter les groupes, mais la permission de gérer les classes est nécessaire pour en créer un.' : 'You can view groups, but class-management permission is required to create one.' }}
        </div>
      }

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-brand-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-100">1</span>{{ fr() ? 'Commencer' : 'Start here' }}</div>
          <h3 class="text-base font-bold text-ink">{{ fr() ? 'Choisissez la session et le niveau' : 'Choose the session and level' }}</h3>
          <p class="text-sm text-mute">{{ fr() ? 'Les classes proposées seront filtrées automatiquement.' : 'The class choices will be filtered automatically.' }}</p>
        </div>
        <div class="mt-4 grid gap-3 md:grid-cols-2">
          <label class="block"><span class="meta">{{ fr() ? 'Session scolaire' : 'School year' }}</span>
            <select class="field" [ngModel]="sessionId()" (ngModelChange)="selectSession($event)">
              @for (session of sessions(); track session.id) { <option [value]="session.id">{{ session.label }}</option> }
            </select>
          </label>
          <label class="block"><span class="meta">{{ fr() ? 'Niveau' : 'Stage' }}</span>
            <select class="field" [ngModel]="level()" (ngModelChange)="selectLevel($event)">
              <option value="maternelle">{{ fr() ? 'Maternelle' : 'Kindergarten' }}</option>
              <option value="primary">{{ fr() ? 'Primaire' : 'Primary' }}</option>
            </select>
          </label>
        </div>
      </section>

      <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-brand-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-100">2</span>{{ fr() ? 'Associer les classes' : 'Link the classes' }}</div>
        <h3 class="mt-1 text-base font-bold text-ink">{{ fr() ? 'Quelles sont les deux classes des mêmes élèves ?' : 'Which two classes contain the same students?' }}</h3>
        <p class="mt-1 text-sm text-mute">{{ fr() ? 'Choisissez une classe dans chaque parcours. Ne les associez que si les élèves sont réellement les mêmes.' : 'Choose one class from each programme. Link them only when the students are genuinely the same group.' }}</p>

        <div class="mt-5 grid items-stretch gap-3 lg:grid-cols-[1fr_auto_1fr]">
          <label class="block rounded-xl border border-violet-200 bg-violet-50/50 p-4"><span class="flex items-center gap-2 text-sm font-bold text-violet-900"><span class="rounded-md bg-violet-100 px-2 py-1 text-[10px] font-black uppercase tracking-wide text-violet-700">FR</span>{{ fr() ? 'Classe francophone' : 'Francophone class' }}</span>
            <select class="field mt-3 bg-white" [(ngModel)]="frClassId">
              <option value="">{{ fr() ? 'Choisir la classe francophone' : 'Choose the Francophone class' }}</option>
              @for (klass of frClasses(); track klass.id) { <option [value]="klass.id">{{ klass.name }} · {{ klass.sectionLabel || klass.level }}</option> }
            </select>
            @if (frClassId && pairedGroupFor(frClassId); as group) {
              <p class="mt-2 text-xs font-semibold text-amber-700">{{ fr() ? 'Déjà associée à' : 'Already linked to' }} {{ group.displayName }}.</p>
            }
          </label>

          <div class="flex items-center justify-center text-2xl font-bold text-slate-300 lg:pt-7">↔</div>

          <label class="block rounded-xl border border-amber-200 bg-amber-50/50 p-4"><span class="flex items-center gap-2 text-sm font-bold text-amber-900"><span class="rounded-md bg-amber-100 px-2 py-1 text-[10px] font-black uppercase tracking-wide text-amber-700">EN</span>{{ fr() ? 'Classe anglophone' : 'English class' }}</span>
            <select class="field mt-3 bg-white" [(ngModel)]="enClassId">
              <option value="">{{ fr() ? 'Choisir la classe anglophone' : 'Choose the English class' }}</option>
              @for (klass of enClasses(); track klass.id) { <option [value]="klass.id">{{ klass.name }} · {{ klass.sectionLabel || klass.level }}</option> }
            </select>
            @if (enClassId && pairedGroupFor(enClassId); as group) {
              <p class="mt-2 text-xs font-semibold text-amber-700">{{ fr() ? 'Déjà associée à' : 'Already linked to' }} {{ group.displayName }}.</p>
            }
          </label>
        </div>

        @if (frClassId && enClassId) {
          <div class="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div class="text-xs font-bold uppercase tracking-wide text-mute">{{ fr() ? 'Aperçu' : 'Preview' }}</div>
            <div class="mt-2 flex flex-wrap items-center gap-2 text-sm font-bold text-ink">
              <span class="rounded-full bg-violet-100 px-3 py-1.5 text-violet-800">{{ selectedFrClass()?.name }}</span>
              <span class="text-slate-400">↔</span>
              <span class="rounded-full bg-amber-100 px-3 py-1.5 text-amber-800">{{ selectedEnClass()?.name }}</span>
            </div>
            <div class="mt-3 grid gap-2 text-xs text-mute sm:grid-cols-3">
              <div><span class="font-bold text-ink">{{ fr() ? 'Inscription' : 'Enrollment' }}:</span> {{ fr() ? 'une seule fois' : 'once' }}</div>
              <div><span class="font-bold text-ink">{{ fr() ? 'Appel' : 'Attendance' }}:</span> {{ fr() ? 'un roster quotidien partagé' : 'one shared daily roster' }}</div>
              <div><span class="font-bold text-ink">{{ fr() ? 'Bulletins' : 'Report cards' }}:</span> {{ fr() ? 'un par parcours' : 'one per programme' }}</div>
            </div>
          </div>
        }

        @if (pairAlreadyConfigured()) {
          <div class="mt-4 rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-sm text-amber-800">{{ fr() ? 'Cette paire est déjà configurée. Utilisez le groupe existant ci-dessous.' : 'This pair is already configured. Use the existing group below.' }}</div>
        } @else if (pairHasConflict()) {
          <div class="mt-4 rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ fr() ? 'Une des classes est déjà associée à un autre groupe. Chaque classe ne peut avoir qu’un seul groupe partagé pour une session.' : 'One of these classes is already linked to another group. A class can belong to only one shared group per session.' }}</div>
        }

        <div class="mt-5 flex flex-col gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:items-end sm:justify-between">
          <label class="block sm:max-w-sm"><span class="meta">{{ fr() ? 'Nom du groupe (facultatif)' : 'Group name (optional)' }}</span>
            <input class="field" [(ngModel)]="displayName" [placeholder]="generatedName() || (fr() ? 'SIL A / Classe 1 A' : 'SIL A / Class 1 A')" maxlength="160" />
            <span class="mt-1 block text-[11px] text-mute">{{ fr() ? 'Si vous le laissez vide, le nom sera créé à partir des deux classes.' : 'Leave blank to create the name from the two classes.' }}</span>
          </label>
          <button type="button" (click)="create()" [disabled]="!canSubmit()" class="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-brand-600 px-5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"><bbc-icon name="plus" [s]="16" />{{ fr() ? 'Associer ces classes' : 'Link these classes' }}</button>
        </div>
      </section>

      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div class="flex flex-col gap-1 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div><h3 class="font-bold text-ink">{{ fr() ? 'Groupes bilingues configurés' : 'Configured bilingual groups' }}</h3><p class="text-xs text-mute">{{ selectedSessionLabel() || '—' }} · {{ fr() ? 'Les classes ordinaires n’apparaissent pas ici.' : 'Regular single-programme classes are not shown here.' }}</p></div><button type="button" (click)="load()" class="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-mute hover:text-ink" [disabled]="busy()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div>
        @if (busy()) { <div class="px-5 py-8 text-center text-sm text-mute">{{ fr() ? 'Chargement…' : 'Loading…' }}</div> }
        @else if (!sharedCohorts().length) { <div class="px-5 py-8 text-center text-sm text-mute">{{ fr() ? 'Aucun groupe bilingue pour cette session.' : 'No bilingual groups for this session.' }}</div> }
        @else { <div class="divide-y divide-slate-100">@for (group of sharedCohorts(); track group.id) { <div class="flex flex-col gap-3 px-5 py-4 md:flex-row md:items-center"><div class="min-w-0 flex-1"><div class="font-semibold text-ink">{{ group.displayName }}</div><div class="mt-2 flex flex-wrap items-center gap-1.5">@for (programme of group.programmes; track programme.id) { <span class="rounded-full px-2.5 py-1 text-[11px] font-semibold" [class]="programme.subsystem === 'FR' ? 'bg-violet-50 text-violet-700' : 'bg-amber-50 text-amber-700'">{{ programme.subsystem }} · {{ programme.className }}</span> }<span class="text-xs text-slate-400">↔ {{ fr() ? 'même groupe d’élèves' : 'same student group' }}</span></div></div><div class="flex items-center gap-3 md:text-right"><div><div class="text-lg font-bold text-ink">{{ group.studentCount }}</div><div class="text-[10px] uppercase tracking-wide text-mute">{{ fr() ? 'élèves partagés' : 'shared students' }}</div></div><span class="rounded-full bg-emerald-50 px-2 py-1 text-[10px] font-bold text-emerald-700">{{ fr() ? 'Appel partagé' : 'Shared attendance' }}</span></div></div> }</div> }
      </section>
    </div>
  `,
})
export class BilingualGroupsComponent {
  private api = inject(CohortApi);
  private foundation = inject(FoundationApi);
  private auth = inject(AuthService);
  private i18n = inject(I18nService);

  protected fr = () => this.i18n.lang() === 'fr';
  protected sessions = signal<AcademicSessionView[]>([]);
  protected classes = signal<CohortClassOption[]>([]);
  protected cohorts = signal<CohortView[]>([]);
  protected sessionId = signal('');
  protected selectedSessionLabel = computed(() => this.sessions().find(s => s.id === this.sessionId())?.label ?? '');
  protected sharedCohorts = computed(() => this.cohorts().filter(group => group.mode === 'SHARED_BILINGUAL' && group.status !== 'ARCHIVED'));
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected notice = signal<string | null>(null);
  protected level = signal('primary');
  protected displayName = '';
  protected frClassId = '';
  protected enClassId = '';
  protected canWrite = this.auth.canAction('CLASS_MANAGE');

  protected frClasses = computed(() => this.classes().filter(c => c.level.toLowerCase() === this.level() && c.subsystem.toUpperCase() === 'FR'));
  protected enClasses = computed(() => this.classes().filter(c => c.level.toLowerCase() === this.level() && c.subsystem.toUpperCase() === 'EN'));

  constructor() {
    this.foundation.listSessions().subscribe({ next: sessions => {
      this.sessions.set(sessions);
      const current = sessions.find(s => s.current) ?? sessions.find(s => s.status === 'OPEN') ?? sessions[0];
      if (current) { this.sessionId.set(current.id); this.load(); }
    }, error: e => this.fail(e) });
  }

  protected selectSession(id: string): void { this.sessionId.set(id); this.resetClasses(); this.load(); }
  protected selectLevel(value: string): void { this.level.set(value); this.resetClasses(); }
  protected resetClasses(): void { this.frClassId = ''; this.enClassId = ''; }
  protected selectedFrClass(): CohortClassOption | null { return this.frClasses().find(klass => klass.id === this.frClassId) ?? null; }
  protected selectedEnClass(): CohortClassOption | null { return this.enClasses().find(klass => klass.id === this.enClassId) ?? null; }
  protected generatedName(): string {
    const fr = this.selectedFrClass()?.name;
    const en = this.selectedEnClass()?.name;
    return fr && en ? `${fr} / ${en}` : fr || en || '';
  }
  protected pairedGroupFor(classId: string): CohortView | null {
    return this.sharedCohorts().find(group => group.programmes.some(programme => programme.classId === classId && programme.active)) ?? null;
  }
  protected pairAlreadyConfigured(): boolean {
    if (!this.frClassId || !this.enClassId) return false;
    return this.sharedCohorts().some(group => {
      const ids = group.programmes.filter(programme => programme.active).map(programme => programme.classId);
      return ids.includes(this.frClassId) && ids.includes(this.enClassId);
    });
  }
  protected pairHasConflict(): boolean {
    if (!this.frClassId || !this.enClassId || this.pairAlreadyConfigured()) return false;
    return !!this.pairedGroupFor(this.frClassId) || !!this.pairedGroupFor(this.enClassId);
  }
  protected canSubmit(): boolean {
    return this.canWrite && !this.busy() && !!this.sessionId() && !!this.frClassId && !!this.enClassId
      && !this.pairAlreadyConfigured() && !this.pairHasConflict();
  }
  protected load(): void {
    const id = this.sessionId(); if (!id) return;
    this.busy.set(true); this.error.set(null);
    this.api.classOptions(id).subscribe({ next: rows => this.classes.set(rows), error: e => this.fail(e) });
    this.api.list(id).subscribe({ next: rows => { this.cohorts.set(rows); this.busy.set(false); }, error: e => this.fail(e) });
  }
  protected create(): void {
    if (!this.canSubmit()) return;
    const displayName = this.displayName.trim() || this.generatedName();
    const body: CohortUpsert = {
      academicSessionId: this.sessionId(),
      code: this.slug(displayName),
      displayName,
      level: this.level(),
      mode: 'SHARED_BILINGUAL',
      francophoneClassId: this.frClassId,
      anglophoneClassId: this.enClassId,
      attendanceMode: 'DAILY_SHARED',
    };
    this.busy.set(true); this.error.set(null); this.notice.set(null);
    this.api.create(body).subscribe({ next: group => { this.cohorts.update(rows => [...rows.filter(r => r.id !== group.id), group].sort((a, b) => a.displayName.localeCompare(b.displayName))); this.displayName = ''; this.resetClasses(); this.busy.set(false); this.notice.set(this.fr() ? 'Classes associées. Les inscriptions utilisent maintenant un groupe partagé.' : 'Classes linked. Enrollments now use one shared student group.'); }, error: e => this.fail(e) });
  }
  private slug(value: string): string { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toUpperCase() || 'GROUP'; }
  private fail(error: any): void { this.busy.set(false); this.error.set(error?.error?.message || error?.message || (this.fr() ? 'Impossible de charger les groupes.' : 'Could not load class groups.')); }
}
