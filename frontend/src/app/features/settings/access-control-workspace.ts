import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import {
  AccessAction, AccessActionGroup, AccessControlApi, AccessMutation, AccessPolicyPreview,
  AccessRole, AccessRoleWorkspace, AccessRule, AccessRuleInput, AccessTemplate, AccessUser, AccessUserWorkspace,
} from './access-control.api';

type TargetMode = 'role' | 'user';
type DraftRule = {
  effect: string; scopeMode: string; scopePayload: unknown;
  effectiveFrom: string | null; effectiveTo: string | null;
  // Angular's template type checker models @let index lookups as possibly
  // undefined; keep the runtime value boolean while allowing the native
  // disabled binding to consume that narrowed template value.
  permanent: any;
};

@Component({
  selector: 'bbc-access-control-workspace',
  standalone: true,
  imports: [FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fade-in max-w-7xl mx-auto space-y-5">
      <header class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div class="text-xs font-bold uppercase tracking-[.18em] text-brand-600">{{ fr() ? 'Accès & responsabilités' : 'Access & responsibilities' }}</div>
          <h1 class="text-2xl font-bold text-ink mt-1">{{ fr() ? 'Espace de contrôle des accès' : 'Access Control workspace' }}</h1>
          <p class="text-sm text-mute mt-1 max-w-3xl">{{ fr() ? 'Préparez un profil complet, examinez son impact puis confirmez une seule modification auditée.' : 'Stage a complete profile, review its impact, then confirm one audited change.' }}</p>
        </div>
        <div class="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs text-mute">
          <div class="font-semibold text-ink">{{ fr() ? 'Version de politique' : 'Policy version' }}</div>
          <div class="text-lg font-bold text-brand-700">{{ policyVersion() || '—' }}</div>
          <div>{{ fr() ? 'Un changement concurrent demande une nouvelle prévisualisation.' : 'A concurrent change requires a fresh preview.' }}</div>
        </div>
      </header>

      @if (!canView()) {
        @if (auth.capabilitiesLoading()) {
          <section class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-mute">{{ fr() ? 'Vérification des droits…' : 'Checking access…' }}</section>
        } @else {
          <section class="rounded-xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-800">
            <div class="font-semibold">{{ fr() ? 'Cet espace est réservé aux administrateurs des droits.' : 'This workspace is restricted to permission administrators.' }}</div>
            <div class="mt-1">{{ fr() ? 'Aucune modification n’est proposée tant que les capacités serveur ne sont pas disponibles.' : 'No change is offered while server capabilities are unavailable.' }}</div>
            @if (auth.capabilitiesError()) { <button class="mt-3 btn" type="button" (click)="auth.retryCapabilities()">{{ fr() ? 'Réessayer' : 'Retry' }}</button> }
          </section>
        }
      } @else {
        <section class="grid gap-4 lg:grid-cols-[18rem_1fr]">
          <aside class="rounded-xl border border-slate-200 bg-white p-4 space-y-4">
            <div class="flex rounded-lg bg-slate-100 p-1 text-sm font-semibold">
              <button type="button" class="flex-1 rounded-md px-2 py-2" [class.bg-white]="mode()==='role'" [class.shadow-sm]="mode()==='role'" (click)="setMode('role')">{{ fr() ? 'Profils' : 'Roles' }}</button>
              <button type="button" class="flex-1 rounded-md px-2 py-2" [class.bg-white]="mode()==='user'" [class.shadow-sm]="mode()==='user'" (click)="setMode('user')">{{ fr() ? 'Utilisateurs' : 'Users' }}</button>
            </div>
            @if (mode() === 'role') {
              <label class="block"><span class="label">{{ fr() ? 'Profil à examiner' : 'Role profile' }}</span>
                <select class="field mt-1" [ngModel]="selectedRole()" (ngModelChange)="selectRole($event)">
                  @for (role of roles(); track role) { <option [value]="role">{{ roleLabel(role) }}</option> }
                </select>
              </label>
              <div class="rounded-lg bg-indigo-50 p-3 text-xs text-indigo-900">{{ fr() ? 'Les règles sont préparées en mémoire. Enregistrer remplace le profil en une opération optimiste et journalisée.' : 'Rules are staged in memory. Save replaces the profile in one optimistic, audited operation.' }}</div>
            } @else {
              <input class="field" [value]="userSearch()" (input)="searchUsers($any($event.target).value)" [placeholder]="fr() ? 'Rechercher un compte' : 'Search an account'" />
              <div class="max-h-80 overflow-auto space-y-1">
                @for (user of users(); track user.id) {
                  <button type="button" class="w-full text-left rounded-lg px-3 py-2 hover:bg-slate-50" [class.bg-brand-50]="selectedUser()===user.id" (click)="selectUser(user.id)">
                    <div class="font-semibold text-sm text-ink">{{ user.displayName }}</div><div class="text-xs text-mute">{{ user.username }} · {{ user.roles.join(', ') || user.roleCode }}</div>
                  </button>
                } @empty { <div class="text-xs text-mute p-2">{{ fr() ? 'Aucun compte.' : 'No accounts.' }}</div> }
              </div>
            }
            <div class="border-t border-slate-100 pt-3">
              <div class="label">{{ fr() ? 'Modèles sûrs' : 'Safe templates' }}</div>
              <select class="field mt-1" [ngModel]="selectedTemplate()" (ngModelChange)="selectedTemplate.set($event)">
                <option value="">{{ fr() ? 'Choisir un modèle' : 'Choose a template' }}</option>
                @for (template of templates(); track template.code) { <option [value]="template.code">{{ fr() ? template.labelFr : template.labelEn }}</option> }
              </select>
              @if (mode()==='role' && selectedTemplate()) {
                <button type="button" class="btn w-full mt-2" (click)="previewSelectedTemplate()">{{ fr() ? 'Prévisualiser le modèle' : 'Preview template' }}</button>
              }
            </div>
          </aside>

          <main class="space-y-4 min-w-0">
            @if (targetLabel(); as label) {
              <section class="rounded-xl border border-slate-200 bg-white p-4 flex flex-wrap items-start justify-between gap-3">
                <div><div class="text-xs uppercase tracking-wide font-bold text-mute">{{ mode()==='role' ? (fr() ? 'Profil sélectionné' : 'Selected role') : (fr() ? 'Compte sélectionné' : 'Selected account') }}</div><h2 class="text-xl font-bold text-ink mt-1">{{ label }}</h2><p class="text-sm text-mute mt-1">{{ targetSummary() }}</p></div>
                <div class="text-right text-xs text-mute"><div>{{ fr() ? 'Règles préparées' : 'Staged rules' }}</div><div class="text-xl font-bold text-ink">{{ stagedCount() }}</div></div>
              </section>
            }

            @if (mode() === 'user' && userWorkspace(); as uw) {
              <section class="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
                <div>
                  <h3 class="font-bold text-ink">{{ fr() ? 'Rôles et responsabilités du compte' : 'Account roles and responsibilities' }}</h3>
                  <p class="text-xs text-mute mt-1">{{ fr() ? 'Les rôles sont attribués ensemble, avec une date et une justification auditables. Les exceptions d’actions restent séparées ci-dessous.' : 'Assign roles together with dates and an auditable reason. Action exceptions remain separate below.' }}</p>
                </div>
                <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                  @for (role of roleOptions(); track role.code) {
                    <label class="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      <input type="checkbox" [checked]="hasAssignedRole(role.code)" (change)="toggleRoleAssignment(role.code, $any($event.target).checked)" />
                      <span>{{ fr() ? role.labelFr : role.labelEn }}</span>
                    </label>
                  }
                </div>
                <div class="grid gap-3 md:grid-cols-3 items-end">
                  <label><span class="label">{{ fr() ? 'Rôle principal' : 'Primary role' }}</span><select class="field mt-1" [ngModel]="primaryAssignment()" (ngModelChange)="setPrimaryAssignment($event)">@for (assignment of assignmentDraft(); track assignment.roleCode) { <option [value]="assignment.roleCode">{{ roleLabel(assignment.roleCode) }}</option> }</select></label>
                  <label><span class="label">{{ fr() ? 'Début' : 'Effective from' }}</span><input class="field mt-1" type="date" [ngModel]="assignmentEffectiveFrom()" (ngModelChange)="assignmentEffectiveFrom.set($event)" /></label>
                  <label><span class="label">{{ fr() ? 'Fin (facultative)' : 'Effective to (optional)' }}</span><input class="field mt-1" type="date" [ngModel]="assignmentEffectiveTo()" (ngModelChange)="assignmentEffectiveTo.set($event)" /></label>
                </div>
                <div class="flex flex-wrap items-end gap-3">
                  <label class="block flex-1 min-w-[16rem]"><span class="label">{{ fr() ? 'Justification de l’attribution' : 'Assignment reason' }}</span><input class="field mt-1" [ngModel]="assignmentReason()" (ngModelChange)="assignmentReason.set($event)" /></label>
                  <label class="flex items-center gap-2 text-xs pb-2"><input type="checkbox" [ngModel]="assignmentConfirm()" (ngModelChange)="assignmentConfirm.set($event)" /> {{ fr() ? 'Confirmer si un rôle sensible est ajouté' : 'Confirm if a sensitive role is added' }}</label>
                  <button type="button" class="btn primary" [disabled]="assignmentBusy() || !assignmentReason().trim() || !assignmentDraft().length" (click)="saveRoleAssignments()">{{ assignmentBusy() ? '…' : (fr() ? 'Enregistrer les rôles' : 'Save roles') }}</button>
                </div>
              </section>
            }

            @if (groups().length === 0) {
              <section class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-mute">{{ fr() ? 'Chargement du catalogue…' : 'Loading catalogue…' }}</section>
            } @else {
              @for (group of groups(); track group.code) {
                <section class="rounded-xl border border-slate-200 bg-white overflow-hidden">
                  <div class="px-4 py-3 border-b border-slate-100 bg-slate-50"><h3 class="font-bold text-ink">{{ fr() ? group.labelFr : group.labelEn }}</h3></div>
                  <div class="divide-y divide-slate-100">
                    @for (action of group.actions; track action.code) {
                      @let rule = draft()[action.code] ?? emptyDraft(action);
                      <div class="px-4 py-3 grid gap-3 lg:grid-cols-[minmax(0,1fr)_8rem_13rem_11rem] items-center">
                        <div class="min-w-0"><div class="font-semibold text-sm text-ink">{{ fr() ? action.labelFr : action.labelEn }}</div><div class="text-xs text-mute truncate">{{ fr() ? action.descriptionFr : action.descriptionEn }}</div></div>
                        <select class="field !min-h-9" [ngModel]="rule?.effect" (ngModelChange)="setEffect(action, $event)"><option value="INHERIT">{{ fr() ? 'Hérité' : 'Inherited' }}</option><option value="ALLOW">{{ fr() ? 'Autorisé' : 'Allowed' }}</option><option value="DENY">{{ fr() ? 'Refusé' : 'Denied' }}</option></select>
                        @if (isConfiguredSetScope(rule?.scopeMode)) {
                          <div class="rounded-lg border border-amber-200 bg-amber-50 px-2.5 py-2 text-xs text-amber-900" [title]="fr() ? 'Ce périmètre existant est conservé, mais aucun sélecteur générique ne peut le modifier sans une sélection de ressources.' : 'This existing scope is preserved, but cannot be edited without a resource picker.'">
                            <div class="font-semibold">{{ scopeLabel(rule?.scopeMode || '') }}</div>
                            <div class="mt-0.5">{{ fr() ? 'Sélection de ressources requise' : 'Resource picker required' }}</div>
                          </div>
                        } @else {
                          <select class="field !min-h-9" [ngModel]="rule?.scopeMode" [disabled]="rule?.effect==='INHERIT'" (ngModelChange)="setScope(action.code, $event)">@for (scope of scopesFor(action); track scope) { <option [value]="scope">{{ scopeLabel(scope) }}</option> }</select>
                        }
                        <div class="flex items-center gap-2 text-xs"><span class="rounded-full px-2 py-1 font-bold" [class]="riskClass(action.riskLevel)">{{ action.riskLevel }}</span><span class="text-mute">{{ action.requiredLevel }}</span></div>
                      </div>
                      @if (showDateControls(action)) {
                        <div class="px-4 pb-3 grid gap-2 sm:grid-cols-[10rem_10rem_auto] items-end text-xs">
                          <label><span class="label">{{ fr() ? 'Valide à partir du' : 'Effective from' }}</span><input class="field mt-1 !min-h-8" type="date" [ngModel]="rule?.effectiveFrom || ''" (ngModelChange)="setEffectiveFrom(action.code, $event)" /></label>
                          <label><span class="label">{{ fr() ? 'Jusqu’au' : 'Effective to' }}</span><input class="field mt-1 !min-h-8" type="date" [disabled]="rule?.permanent" [ngModel]="rule?.effectiveTo || ''" (ngModelChange)="setEffectiveTo(action.code, $event)" /></label>
                          <label class="flex items-center gap-2 h-8"><input type="checkbox" [ngModel]="rule?.permanent" (ngModelChange)="setPermanent(action.code, $event)" /> {{ fr() ? 'Permanent' : 'Permanent' }}</label>
                        </div>
                      }
                    }
                  </div>
                </section>
              }
            }

            <section class="rounded-xl border border-slate-200 bg-white p-4 space-y-3 sticky bottom-3 shadow-lg shadow-slate-200/50">
              <div class="grid gap-3 md:grid-cols-[1fr_auto_auto] items-end">
                <label class="block"><span class="label">{{ fr() ? 'Justification obligatoire' : 'Required reason' }}</span><input class="field mt-1" [ngModel]="reason()" (ngModelChange)="reason.set($event)" [placeholder]="fr() ? 'Expliquez le besoin, le périmètre et la durée' : 'Explain need, scope and duration'" /></label>
                <label class="flex items-center gap-2 text-sm pb-2"><input type="checkbox" [ngModel]="confirmHighRisk()" (ngModelChange)="confirmHighRisk.set($event)" /> {{ fr() ? 'J’ai vérifié les risques' : 'I reviewed the risks' }}</label>
                <button type="button" class="btn primary" [disabled]="busy() || !targetReady() || !reason().trim()" (click)="previewChanges()">{{ busy() ? '…' : (fr() ? 'Prévisualiser' : 'Preview changes') }}</button>
              </div>
              @if (preview(); as p) {
                <div class="rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-950">
                  <div class="font-bold">{{ fr() ? 'Aperçu de l’accès effectif' : 'Effective access preview' }} · {{ p.changes.length }} {{ fr() ? 'changement(s)' : 'change(s)' }}</div>
                  <div class="mt-2 grid gap-1 sm:grid-cols-2 text-xs"><div>{{ fr() ? 'Ajouts' : 'Additions' }}: {{ changeCount('ADDITION') }}</div><div>{{ fr() ? 'Retraits' : 'Removals' }}: {{ changeCount('REMOVAL') }}</div><div>{{ fr() ? 'Utilisateurs concernés' : 'Affected users' }}: {{ p.affectedUsers.length }}</div><div>{{ fr() ? 'Exceptions conservées' : 'Preserved exceptions' }}: {{ p.preservedUserExceptions.length }}</div></div>
                  @if (p.warnings.length) { <div class="mt-3 space-y-1">@for (warning of p.warnings; track warning.code) { <div class="text-xs rounded bg-white/70 px-2 py-1"><b>{{ warning.severity }}</b> · {{ fr() ? warning.messageFr : warning.messageEn }}</div> }</div> }
                  @if (p.changes.length) { <div class="mt-3 max-h-36 overflow-auto space-y-1">@for (change of p.changes; track change.actionCode) { <div class="text-xs flex justify-between gap-3"><span>{{ actionLabel(change.actionCode) }}</span><span class="font-semibold">{{ change.changeType }} · {{ change.beforeEffect }} → {{ change.afterEffect }}</span></div> }</div> }
                  <div class="mt-3 flex flex-wrap gap-2">
                    @if (templateStaged()) { <button type="button" class="btn primary" [disabled]="busy() || !reason().trim() || (p.requiresConfirmation && !confirmHighRisk())" (click)="applyStagedTemplate()">{{ fr() ? 'Confirmer et appliquer le modèle' : 'Confirm and apply template' }}</button> }
                    @else { <button type="button" class="btn primary" [disabled]="busy() || (p.requiresConfirmation && !confirmHighRisk())" (click)="save()">{{ fr() ? 'Confirmer et enregistrer' : 'Confirm and save' }}</button> }
                    <span class="text-xs self-center text-indigo-800">{{ fr() ? 'Version attendue' : 'Expected version' }} {{ p.currentPolicyVersion }}</span>
                  </div>
                </div>
              }
              @if (message(); as m) { <div class="rounded-lg px-3 py-2 text-sm" [class]="m.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'">{{ m.text }}</div> }
            </section>
          </main>
        </section>

        <section class="rounded-xl border border-slate-200 bg-white p-4">
          <div class="flex items-center justify-between gap-3"><div><h2 class="font-bold text-ink">{{ fr() ? 'Journal récent' : 'Recent audit' }}</h2><p class="text-xs text-mute mt-1">{{ fr() ? 'Chaque remplacement de profil conserve l’acteur, la raison et la version.' : 'Every profile replacement keeps actor, reason and version.' }}</p></div><button type="button" class="btn" (click)="loadAudit()">{{ fr() ? 'Actualiser' : 'Refresh' }}</button></div>
          <div class="mt-3 divide-y divide-slate-100">@for (entry of audit(); track entry.id) { <div class="py-2 text-xs flex flex-wrap justify-between gap-2"><span class="font-semibold">{{ entry.mutationType }}</span><span class="text-mute">{{ entry.reason }}</span><span class="text-mute">{{ entry.occurredAt | date:'short' }}</span></div> } @empty { <div class="py-3 text-xs text-mute">{{ fr() ? 'Aucun événement.' : 'No events.' }}</div> }</div>
        </section>
      }
    </div>
  `,
})
export class AccessControlWorkspaceComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(AccessControlApi);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);

  protected readonly fr = computed(() => this.i18n.lang() === 'fr');
  protected readonly groups = signal<AccessActionGroup[]>([]);
  protected readonly roleOptions = signal<AccessRole[]>([]);
  protected readonly templates = signal<AccessTemplate[]>([]);
  protected readonly users = signal<AccessUser[]>([]);
  protected readonly audit = signal<import('./access-control.api').AccessAudit[]>([]);
  protected readonly mode = signal<TargetMode>('role');
  protected readonly selectedRole = signal('principal');
  protected readonly selectedUser = signal<string | null>(null);
  protected readonly selectedTemplate = signal('');
  protected readonly userSearch = signal('');
  protected readonly workspace = signal<AccessRoleWorkspace | null>(null);
  protected readonly userWorkspace = signal<AccessUserWorkspace | null>(null);
  protected readonly draft = signal<Record<string, DraftRule>>({});
  protected readonly reason = signal('');
  protected readonly confirmHighRisk = signal(false);
  protected readonly templateStaged = signal(false);
  protected readonly preview = signal<AccessPolicyPreview | null>(null);
  protected readonly busy = signal(false);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly assignmentDraft = signal<import('./access-control.api').AccessRoleAssignment[]>([]);
  protected readonly assignmentReason = signal('');
  protected readonly assignmentEffectiveFrom = signal('');
  protected readonly assignmentEffectiveTo = signal('');
  protected readonly assignmentConfirm = signal(false);
  protected readonly assignmentBusy = signal(false);

  protected readonly roles = computed(() => {
    return this.roleOptions().map((role) => role.code);
  });
  protected readonly policyVersion = computed(() => this.mode() === 'role' ? this.workspace()?.policyVersion ?? 0 : this.userWorkspace()?.policyVersion ?? 0);
  protected readonly targetReady = computed(() => this.mode() === 'role' ? !!this.workspace() : !!this.userWorkspace());
  protected readonly canView = computed(() => this.auth.canAction('PERMISSION_VIEW'));
  protected readonly targetLabel = computed(() => this.mode() === 'role'
    ? (this.workspace()?.labelFr || this.workspace()?.labelEn || this.selectedRole())
    : this.userWorkspace()?.user.displayName || this.selectedUser());

  constructor() {
    this.api.catalog().subscribe({ next: (groups) => this.groups.set(groups), error: (err) => this.fail(err) });
    this.api.roles().subscribe({ next: (roles) => { this.roleOptions.set(roles); const initial = roles.find((role) => role.code === this.selectedRole())?.code ?? roles[0]?.code; if (initial) { this.selectedRole.set(initial); this.loadTargetRole(initial); } }, error: (err) => this.fail(err) });
    this.api.templates().subscribe({ next: (templates) => this.templates.set(templates), error: (err) => this.fail(err) });
    const requestedUserId = this.route.snapshot.queryParamMap.get('userId');
    this.api.users().subscribe({ next: (users) => {
      this.users.set(users);
      const requested = requestedUserId ? users.find((user) => user.id === requestedUserId) : undefined;
      const first = requested ?? users[0];
      if (first && !this.selectedUser()) {
        this.selectedUser.set(first.id);
        if (requested) { this.mode.set('user'); this.loadTargetUser(first.id); }
      }
    }, error: (err) => this.fail(err) });
    this.loadAudit();
  }

  protected setMode(mode: TargetMode): void { this.mode.set(mode); this.preview.set(null); this.message.set(null); if (mode === 'role') this.loadTargetRole(this.selectedRole()); else if (this.selectedUser()) this.loadTargetUser(this.selectedUser()!); }
  protected selectRole(role: string): void { this.selectedRole.set(role); this.mode.set('role'); this.loadTargetRole(role); }
  protected selectUser(userId: string): void { this.selectedUser.set(userId); this.mode.set('user'); this.loadTargetUser(userId); }
  protected searchUsers(term: string): void { this.userSearch.set(term); this.api.users(term).subscribe({ next: (users) => this.users.set(users), error: (err) => this.fail(err) }); }

  protected setEffect(action: AccessAction, effect: string): void {
    const current = this.draft()[action.code] ?? this.emptyDraft(action);
    this.draft.update((draft) => ({ ...draft, [action.code]: { ...current, effect, scopeMode: effect === 'INHERIT' ? 'NONE' : current.scopeMode, scopePayload: effect === 'INHERIT' ? null : current.scopePayload } }));
    this.preview.set(null);
  }
  protected setScope(actionCode: string, scopeMode: string): void { this.draft.update((draft) => ({ ...draft, [actionCode]: { ...draft[actionCode], scopeMode, scopePayload: null } })); this.preview.set(null); }
  protected setEffectiveFrom(actionCode: string, value: string): void { this.draft.update((draft) => ({ ...draft, [actionCode]: { ...draft[actionCode], effectiveFrom: value || null } })); this.preview.set(null); }
  protected setEffectiveTo(actionCode: string, value: string): void { this.draft.update((draft) => ({ ...draft, [actionCode]: { ...draft[actionCode], effectiveTo: value || null, permanent: false } })); this.preview.set(null); }
  protected setPermanent(actionCode: string, permanent: boolean): void { this.draft.update((draft) => ({ ...draft, [actionCode]: { ...draft[actionCode], permanent, effectiveTo: permanent ? null : draft[actionCode].effectiveTo } })); this.preview.set(null); }
  protected scopesFor(action: AccessAction): string[] {
    if (action.scopeType === 'NONE') return ['NONE'];
    if (action.scopeType === 'SELF') return ['SELF'];
    // Set scopes require typed resource pickers and a non-null JSON payload.
    // Keep them out of this editor until those pickers are available.
    return ['SCHOOL_ALL', 'PARCOURS_ALLOWED', 'ASSIGNED_CLASSES', 'TITULAIRE_CLASSES', 'ASSIGNED_CLASS_SUBJECTS', 'TIMETABLE_OCCURRENCES_ASSIGNED', 'LINKED_CHILDREN'];
  }
  protected isConfiguredSetScope(scope: string | undefined): boolean { return !!scope && ['CLASS_SET', 'SUBJECT_SET', 'CLASS_SUBJECT_SET', 'PARCOURS_SET'].includes(scope); }
  protected showDateControls(action: AccessAction): boolean { return this.mode() === 'user' || action.riskLevel === 'HIGH' || action.riskLevel === 'CRITICAL'; }
  protected scopeLabel(scope: string): string {
    const labels: Record<string, string> = { NONE: this.fr() ? 'Aucune ressource' : 'No resource', SCHOOL_ALL: this.fr() ? 'Établissement entier' : 'Whole school', PARCOURS_ALLOWED: 'Parcours autorisés', ASSIGNED_CLASSES: 'Classes assignées', TITULAIRE_CLASSES: 'Classes titulaire', ASSIGNED_CLASS_SUBJECTS: 'Classes-matières assignées', TIMETABLE_OCCURRENCES_ASSIGNED: 'Occurrences publiées assignées', LINKED_CHILDREN: 'Enfants liés', SELF: 'Mon compte', CLASS_SET: 'Ensemble de classes', SUBJECT_SET: 'Ensemble de matières', CLASS_SUBJECT_SET: 'Ensemble classe-matière', PARCOURS_SET: 'Ensemble de parcours' };
    return labels[scope] ?? scope;
  }
  protected riskClass(risk: string): string { return risk === 'CRITICAL' ? 'bg-rose-100 text-rose-700' : risk === 'HIGH' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'; }
  protected actionLabel(code: string): string { const action = this.groups().flatMap((group) => group.actions).find((item) => item.code === code); return this.fr() ? action?.labelFr ?? code : action?.labelEn ?? code; }
  protected stagedCount(): number { return Object.values(this.draft()).filter((rule) => rule.effect !== 'INHERIT').length; }
  protected targetSummary(): string { const role = this.workspace(); const user = this.userWorkspace()?.user; return this.mode() === 'role' ? (role?.builtin ? (this.fr() ? 'Profil intégré' : 'Built-in profile') : (this.fr() ? 'Profil personnalisé' : 'Custom profile')) : `${user?.username ?? ''} · ${(user?.roles ?? []).join(', ')}`; }
  protected changeCount(type: string): number { return this.preview()?.changes.filter((change) => change.changeType === type).length ?? 0; }

  protected previewChanges(): void {
    if (!this.targetReady()) return;
    this.templateStaged.set(false);
    this.busy.set(true); this.message.set(null);
    const body = this.mutation();
    const request = this.mode() === 'role' ? this.api.previewRole(this.selectedRole(), body) : this.api.previewUser(this.selectedUser()!, body);
    request.subscribe({ next: (preview) => { this.preview.set(preview); this.busy.set(false); }, error: (err) => this.fail(err) });
  }
  protected save(): void {
    const preview = this.preview(); if (!preview || (preview.requiresConfirmation && !this.confirmHighRisk())) return;
    this.busy.set(true); const body = this.mutation();
    const done = () => { this.message.set({ ok: true, text: this.fr() ? 'Modification enregistrée et auditée.' : 'Change saved and audited.' }); this.preview.set(null); this.busy.set(false); if (this.mode() === 'role') this.loadTargetRole(this.selectedRole()); else this.loadTargetUser(this.selectedUser()!); this.loadAudit(); };
    if (this.mode() === 'role') {
      this.api.updateRole(this.selectedRole(), body).subscribe({ next: () => done(), error: (err: unknown) => this.fail(err) });
    } else {
      this.api.updateUser(this.selectedUser()!, body).subscribe({ next: () => done(), error: (err: unknown) => this.fail(err) });
    }
  }
  protected previewSelectedTemplate(): void {
    if (!this.selectedTemplate() || !this.selectedRole()) return;
    const template = this.templates().find((item) => item.code === this.selectedTemplate());
    if (!template) return;
    this.templateStaged.set(true);
    this.hydrate(template.rules);
    this.busy.set(true);
    this.api.previewTemplate(this.selectedRole(), this.selectedTemplate()).subscribe({ next: (preview) => { this.preview.set(preview); this.busy.set(false); }, error: (err) => this.fail(err) });
  }
  protected applyStagedTemplate(): void {
    const preview = this.preview();
    if (!preview || !this.templateStaged() || !this.reason().trim()
      || (preview.requiresConfirmation && !this.confirmHighRisk())) return;
    this.busy.set(true);
    this.api.applyTemplate(this.selectedRole(), this.selectedTemplate(), preview.currentPolicyVersion,
      this.reason().trim(), this.confirmHighRisk()).subscribe({
        next: () => {
          this.message.set({ ok: true, text: this.fr() ? 'Modèle appliqué et audité.' : 'Template applied and audited.' });
          this.busy.set(false); this.preview.set(null); this.templateStaged.set(false); this.loadTargetRole(this.selectedRole()); this.loadAudit();
        },
        error: (err: unknown) => this.fail(err),
      });
  }
  protected loadAudit(): void { this.api.audit(30).subscribe({ next: (audit) => this.audit.set(audit), error: () => undefined }); }

  private mutation(): AccessMutation {
    const rules: AccessRuleInput[] = this.groups().flatMap((group) => group.actions).map((action) => {
      const rule = this.draft()[action.code] ?? this.emptyDraft(action);
      return { actionCode: action.code, effect: rule.effect, scopeMode: rule.effect === 'INHERIT' ? 'NONE' : rule.scopeMode, scopePayload: rule.effect === 'INHERIT' ? null : rule.scopePayload, effectiveFrom: rule.effectiveFrom, effectiveTo: rule.permanent ? null : rule.effectiveTo, permanent: rule.permanent, reason: this.reason().trim() || 'Staged access-control review' };
    });
    return { expectedPolicyVersion: this.policyVersion(), reason: this.reason().trim(), rules, confirmHighRisk: this.confirmHighRisk(), separationOfDutiesOverride: false };
  }
  protected emptyDraft(action: AccessAction): DraftRule { return { effect: 'INHERIT', scopeMode: 'NONE', scopePayload: null, effectiveFrom: null, effectiveTo: null, permanent: false }; }
  private hydrate(rules: AccessRule[]): void {
    const draft: Record<string, DraftRule> = {};
    for (const action of this.groups().flatMap((group) => group.actions)) {
      const existing = rules.find((rule) => rule.actionCode === action.code && rule.effect !== 'INHERIT');
      const unusableSetScope = existing && this.isConfiguredSetScope(existing.scopeMode) && existing.scopePayload == null;
      draft[action.code] = existing && !unusableSetScope ? { effect: existing.effect, scopeMode: existing.scopeMode, scopePayload: existing.scopePayload, effectiveFrom: existing.effectiveFrom, effectiveTo: existing.effectiveTo, permanent: existing.permanent } : this.emptyDraft(action);
    }
    this.draft.set(draft); this.preview.set(null);
  }
  private loadTargetRole(roleCode: string): void { if (!roleCode) return; this.api.role(roleCode).subscribe({ next: (workspace) => { this.workspace.set(workspace); this.userWorkspace.set(null); this.templateStaged.set(false); this.hydrate(workspace.rules); }, error: (err) => this.fail(err) }); }
  private loadTargetUser(userId: string): void { this.api.user(userId).subscribe({ next: (workspace) => { this.userWorkspace.set(workspace); this.workspace.set(null); this.templateStaged.set(false); this.hydrate(workspace.overrides); this.assignmentDraft.set(workspace.user.roles.map((roleCode, index) => ({ roleCode, primary: roleCode === workspace.user.roleCode || index === 0, effectiveFrom: null, effectiveTo: null, reason: 'Existing role assignment' }))); this.assignmentReason.set(''); this.assignmentEffectiveFrom.set(''); this.assignmentEffectiveTo.set(''); this.assignmentConfirm.set(false); }, error: (err) => this.fail(err) }); }
  protected hasAssignedRole(roleCode: string): boolean { return this.assignmentDraft().some((assignment) => assignment.roleCode === roleCode); }
  protected primaryAssignment(): string { return this.assignmentDraft().find((assignment) => assignment.primary)?.roleCode ?? ''; }
  protected toggleRoleAssignment(roleCode: string, enabled: boolean): void {
    if (enabled) {
      if (!this.hasAssignedRole(roleCode)) this.assignmentDraft.update((assignments) => [...assignments, { roleCode, primary: assignments.length === 0, effectiveFrom: null, effectiveTo: null, reason: '' }]);
    } else {
      this.assignmentDraft.update((assignments) => {
        const remaining = assignments.filter((assignment) => assignment.roleCode !== roleCode);
        if (remaining.length && !remaining.some((assignment) => assignment.primary)) remaining[0] = { ...remaining[0], primary: true };
        return remaining;
      });
    }
  }
  protected setPrimaryAssignment(roleCode: string): void { this.assignmentDraft.update((assignments) => assignments.map((assignment) => ({ ...assignment, primary: assignment.roleCode === roleCode }))); }
  protected saveRoleAssignments(): void {
    const workspace = this.userWorkspace();
    if (!workspace || !this.assignmentReason().trim() || !this.assignmentDraft().length) return;
    this.assignmentBusy.set(true);
    const from = this.assignmentEffectiveFrom() || null;
    const to = this.assignmentEffectiveTo() || null;
    const assignments = this.assignmentDraft().map((assignment) => ({ ...assignment, effectiveFrom: from, effectiveTo: to, reason: this.assignmentReason().trim() }));
    this.api.updateUserRoles(workspace.user.id, { expectedPolicyVersion: workspace.policyVersion, reason: this.assignmentReason().trim(), assignments, confirmHighRisk: this.assignmentConfirm() }).subscribe({
      next: () => { this.assignmentBusy.set(false); this.message.set({ ok: true, text: this.fr() ? 'Rôles attribués et journalisés.' : 'Roles assigned and audited.' }); this.loadTargetUser(workspace.user.id); this.loadAudit(); },
      error: (err: unknown) => { this.assignmentBusy.set(false); this.fail(err); },
    });
  }
  protected roleLabel(role: string): string { const option = this.roleOptions().find((item) => item.code === role); return option ? (this.fr() ? option.labelFr : option.labelEn) : role; }
  private fail(err: unknown): void { this.busy.set(false); const message = (err as { error?: { message?: string } })?.error?.message; this.message.set({ ok: false, text: message || (this.fr() ? 'La demande a échoué. Vérifiez les droits et réessayez.' : 'The request failed. Check access and retry.') }); }
}
