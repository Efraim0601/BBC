import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AcademicContextService } from '../../core/academic-context.service';
import {
  AcademicReportingPeriodView, AcademicSessionUpsert, AcademicSessionView, AcademicTermUpsert,
  AcademicTermView, CalendarDayView, FoundationApi, GenerationResult,
  SessionReadinessView, StandardStructureView, StructureDependencyView, TermManagementWindowProposal,
  TermManagementWindowView,
} from '../../core/foundation.api';
import { CardComponent, EmptyComponent, IconComponent } from '../../core/ui';
import { SessionConfigurationCopyComponent } from './session-configuration-copy';
import { TermManagementWindowsComponent } from './term-management-windows';

const cleanDisplay = (value: string | null | undefined): string => {
  if (!value) return value ?? '';
  return value
    .replace(/ÃƒÂ‰|Ã‰/g, 'É').replace(/ÃƒÂ©|Ã©/g, 'é').replace(/ÃƒÂ¨|Ã¨/g, 'è')
    .replace(/ÃƒÂª|Ãª/g, 'ê').replace(/ÃƒÂ |Ã /g, 'à').replace(/ÃƒÂ§|Ã§/g, 'ç')
    .replace(/ÃƒÂ´|Ã´/g, 'ô').replace(/ÃƒÂ»|Ã»/g, 'û').replace(/ÃƒÂ¯|Ã¯/g, 'ï')
    .replace(/Ã‚Â·|Ã‚Â /g, (match) => match.includes('·') ? '·' : ' ');
};

@Component({
  selector: 'bbc-foundation-settings',
  standalone: true,
  imports: [FormsModule, CardComponent, EmptyComponent, IconComponent, SessionConfigurationCopyComponent, TermManagementWindowsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-5">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-lg font-bold text-ink">{{ fr() ? 'Années et périodes académiques' : 'Academic sessions and terms' }}</h2>
          <p class="text-sm text-mute mt-1">{{ fr() ? 'Les sessions sont la référence historique des inscriptions, calendriers et publications.' : 'Sessions are the historical source for enrollment, calendars, and publication.' }}</p>
        </div>
        @if (canManage() && !showSessionForm()) {
          <button (click)="newSession()" class="btn-primary"><bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Nouvelle session' : 'New session' }}</button>
        }
      </div>

      @if (message(); as m) { <div class="px-3 py-2 rounded-lg text-sm" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ m.text }}</div> }

      @if (showSessionForm()) {
        <bbc-card [title]="editingId() ? (fr() ? 'Modifier la session' : 'Edit session') : (fr() ? 'Créer une session' : 'Create session')">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
            <label><span class="meta">Code</span><input [(ngModel)]="sessionDraft.code" class="field" placeholder="2026-2027" /></label>
            <label class="md:col-span-2"><span class="meta">{{ fr() ? 'Libellé' : 'Label' }}</span><input [(ngModel)]="sessionDraft.label" class="field" placeholder="Session 2026-2027" /></label>
            <label class="flex items-center gap-2 pt-6"><input type="checkbox" [(ngModel)]="sessionDraft.current" /> <span class="text-sm">{{ fr() ? 'Session courante' : 'Current session' }}</span></label>
            <label><span class="meta">{{ fr() ? 'Début' : 'Start' }}</span><input type="date" [(ngModel)]="sessionDraft.startDate" class="field" /></label>
            <label><span class="meta">{{ fr() ? 'Fin' : 'End' }}</span><input type="date" [(ngModel)]="sessionDraft.endDate" class="field" /></label>
          </div>
          <p class="text-xs text-mute mt-3">{{ fr() ? 'Les limites de gestion se configurent séparément, par trimestre, après l’enregistrement de la session.' : 'Management limits are configured separately for each trimester after the session is saved.' }}</p>
          <div class="flex justify-end gap-2 mt-4"><button (click)="cancelSession()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="saveSession()" [disabled]="saving() || !sessionDraft.code || !sessionDraft.label || !sessionDraft.startDate || !sessionDraft.endDate" class="btn-primary">{{ saving() ? '…' : (fr() ? 'Enregistrer' : 'Save') }}</button></div>
        </bbc-card>
      }

      @if (loading()) {
        <div class="py-10 text-center text-mute">{{ fr() ? 'Chargement…' : 'Loading…' }}</div>
      } @else if (!sessions().length) {
        <bbc-empty icon="calendar" [label]="fr() ? 'Aucune session académique' : 'No academic sessions'" />
      } @else {
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div class="space-y-2">
            @for (s of sessions(); track s.id) {
              <button (click)="select(s)" class="w-full text-left p-4 rounded-xl border transition" [class]="selected()?.id === s.id ? 'border-brand-400 bg-brand-50' : 'border-slate-200 bg-white hover:border-slate-300'">
                <div class="flex items-center justify-between gap-2"><span class="font-bold text-ink">{{ s.label }}</span>@if (s.current) { <span class="chip bg-emerald-100 text-emerald-700">{{ fr() ? 'Courante' : 'Current' }}</span> }</div>
                <div class="text-xs text-mute mt-1">{{ s.startDate }} → {{ s.endDate }}</div><div class="text-[11px] font-semibold mt-2" [class]="statusClass(s.status)">{{ statusLabel(s.status) }}</div>
              </button>
            }
          </div>

          @if (selected(); as s) {
            <div class="lg:col-span-2 space-y-4">
              <!-- 1. Session summary and status actions -->
              <bbc-card [title]="s.label">
                <div action class="flex gap-2">
                  @if (canManage() && s.status !== 'ARCHIVED') { <button (click)="editSession(s)" class="btn-secondary">{{ fr() ? 'Modifier' : 'Edit' }}</button> }
                  @if (canManage() && s.status === 'DRAFT') { <button (click)="requestStateChange(s, 'OPEN')" class="btn-primary">{{ fr() ? 'Ouvrir' : 'Open' }}</button> }
                  @if (canManage() && s.status === 'OPEN') { <button (click)="requestStateChange(s, 'CLOSED')" class="btn-secondary">{{ fr() ? 'Clôturer' : 'Close' }}</button> }
                  @if (canManage() && s.status === 'CLOSED') { <button (click)="requestStateChange(s, 'ARCHIVED')" class="btn-secondary">{{ fr() ? 'Archiver' : 'Archive' }}</button> }
                </div>
                <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm"><div><div class="meta">Code</div><div class="font-semibold">{{ s.code }}</div></div><div><div class="meta">{{ fr() ? 'Statut' : 'Status' }}</div><div class="font-semibold">{{ statusLabel(s.status) }}</div></div><div><div class="meta">{{ fr() ? 'Trimestres' : 'Trimesters' }}</div><div class="font-semibold">{{ s.terms.length }}</div></div><div><div class="meta">Version</div><div class="font-semibold">{{ s.version }}</div></div></div>
              </bbc-card>

              <!-- 2. Readiness -->
              @if (readiness(); as r) {
                <section class="rounded-xl border p-3" [class]="r.ready ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'">
                  <div class="flex items-center justify-between gap-3"><strong class="text-sm">{{ fr() ? 'État de préparation' : 'Readiness' }}</strong><span class="chip" [class]="r.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'">{{ r.phase }}</span></div>
                  <p class="text-xs mt-1">{{ r.nextAction }}</p>
                  @if (allAccessUnrestricted()) { <p class="text-xs mt-1 font-semibold text-emerald-800">{{ fr() ? 'Aucune restriction de date n’est configurée pour les trimestres.' : 'No date restriction is configured for the trimesters.' }}</p> }
                  @if (r.blockers.length) { <div class="mt-2 text-xs text-rose-800">@for (b of r.blockers; track b) { <div>• {{ b }}</div> }</div> }
                </section>
              }

              <!-- 3. Reuse a previous session -->
              @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <bbc-session-configuration-copy [target]="s" [sessions]="sessions()" [canManage]="canManage()" (applied)="reload(s.id)" /> }

              <!-- 4. Trimesters académiques -->
              <bbc-card [title]="fr() ? 'Trimestres académiques' : 'Academic trimesters'">
                <div class="space-y-1">
                  @for (t of s.terms; track t.id) {
                    <div class="flex items-center gap-3 py-2.5 border-b border-slate-100 last:border-0"><div class="w-8 h-8 rounded-full bg-brand-50 text-brand-700 flex items-center justify-center text-xs font-bold">{{ t.sequenceNo }}</div><div class="flex-1"><div class="font-semibold text-sm">{{ t.code }} · {{ t.label }}</div><div class="text-xs text-mute">{{ t.startDate }} → {{ t.endDate }}</div></div>@if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="editTerm(t)" class="text-xs text-brand-700">{{ fr() ? 'Modifier' : 'Edit' }}</button><button (click)="requestTermRemoval(t.id)" class="text-xs text-rose-600">{{ fr() ? 'Retirer' : 'Remove' }}</button> }</div>
                  } @empty { <div class="text-sm text-mute py-3">{{ fr() ? 'Aucun trimestre configuré.' : 'No trimesters configured.' }}</div> }
                </div>
                @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                  <div class="grid grid-cols-2 md:grid-cols-5 gap-2 pt-4 mt-2 border-t border-slate-100"><input [(ngModel)]="termDraft.code" class="field" placeholder="T1" /><input [(ngModel)]="termDraft.label" class="field" [placeholder]="fr() ? '1er trimestre' : 'Term 1'" /><input type="number" min="1" [(ngModel)]="termDraft.sequenceNo" class="field" /><input type="date" [(ngModel)]="termDraft.startDate" class="field" /><input type="date" [(ngModel)]="termDraft.endDate" class="field" /></div>
                  <div class="flex gap-2 mt-3"><button (click)="saveTerm()" [disabled]="!termDraft.code || !termDraft.label || !termDraft.startDate || !termDraft.endDate || saving()" class="btn-primary">{{ editingTermId() ? (fr() ? 'Enregistrer le trimestre' : 'Save trimester') : (fr() ? 'Ajouter le trimestre' : 'Add trimester') }}</button>@if (editingTermId()) { <button (click)="cancelTermEdit()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button> }</div>
                }
              </bbc-card>

              <!-- 5. One optional management window per term -->
              <bbc-term-management-windows [target]="s" [windowRows]="termManagementWindows()" [canManage]="canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED'" [english]="!fr()" (changed)="onTermWindowChanged($event)" />

              <!-- 6. Five-step academic configuration wizard -->
              <section class="rounded-xl border border-brand-200 bg-brand-50/30 p-4" aria-labelledby="academic-structure-wizard-title">
                <div class="flex flex-wrap items-start justify-between gap-3"><div><h3 id="academic-structure-wizard-title" class="text-base font-bold text-ink">{{ fr() ? 'Assistant de configuration académique' : 'Academic configuration wizard' }}</h3><p class="text-xs text-mute mt-1">{{ fr() ? 'Cinq étapes orientées produit. Les limites par trimestre restent locales jusqu’à la confirmation finale.' : 'Five product-oriented steps. Trimester access edits stay local until final confirmation.' }}</p></div><span class="chip bg-white text-brand-800 border border-brand-200">{{ fr() ? 'Étape ' + wizardStep() + ' sur 5' : 'Step ' + wizardStep() + ' of 5' }}</span></div>
                <div class="grid grid-cols-2 md:grid-cols-5 gap-2 mt-4">@for (step of wizardSteps; track step) { <button type="button" (click)="setWizardStep(step)" class="rounded-lg border px-2 py-2 text-left text-xs transition" [class]="wizardStep() === step ? 'border-brand-500 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300'"><span class="font-bold">{{ step }}.</span> {{ wizardStepLabel(step) }}</button> }</div>

                @if (wizardStep() === 1) {
                  <div class="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3"><div class="rounded-lg border border-slate-200 bg-white p-3"><div class="meta">Session</div><div class="font-semibold text-sm mt-1">{{ s.label }}</div><div class="text-xs text-mute mt-1">{{ s.startDate }} → {{ s.endDate }}</div><div class="text-xs text-slate-500 mt-1">{{ fr() ? 'Fuseau' : 'Timezone' }}: {{ s.timezone }}</div></div><div class="rounded-lg border border-slate-200 bg-white p-3 md:col-span-2"><div class="meta">{{ fr() ? 'Trimestres et dates' : 'Trimesters and dates' }}</div><div class="grid grid-cols-1 md:grid-cols-3 gap-2 mt-2">@for (term of s.terms; track term.id) { <div class="rounded-md border border-slate-100 px-2 py-1 text-xs"><strong>{{ term.code }}</strong><div>{{ term.startDate }} → {{ term.endDate }}</div></div> }</div></div></div>
                }
                @if (wizardStep() === 2) {
                  <div class="mt-4 space-y-3"><div class="flex flex-wrap items-center justify-between gap-2"><p class="text-xs text-slate-600">{{ fr() ? 'S1–S6, T1/T2/T3 et Annuel sont des produits distincts. Prévisualisez les dates sans écrire.' : 'S1–S6, T1/T2/T3, and Annual are distinct products. Preview dates without writing.' }}</p><button type="button" (click)="previewStandardStructure()" class="btn-secondary">{{ fr() ? 'Prévisualiser les dates' : 'Preview result dates' }}</button></div>@if (structurePreview(); as preview) { <div class="grid grid-cols-1 md:grid-cols-3 gap-2">@for (period of preview.periods; track period.code) { <div class="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs"><div class="flex justify-between gap-2"><strong>{{ period.code }}</strong><span class="chip bg-slate-100">{{ periodTypeLabel(period.periodType) }}</span></div><div class="mt-1">{{ period.startDate }} → {{ period.endDate }}</div><div class="text-slate-500 mt-1">{{ calculationSummary(period) }}</div></div> }</div> } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'Prévisualisez la structure pour continuer.' : 'Preview the structure to continue.' }}</div> }</div>
                }
                @if (wizardStep() === 3) {
                  <div class="mt-4 space-y-3"><div class="flex flex-wrap items-center justify-between gap-2"><p class="text-xs text-slate-600">{{ fr() ? 'Les dépendances et poids sont proposés localement puis appliqués avec la structure.' : 'Dependencies and weights are staged locally and applied with the structure.' }}</p><button type="button" (click)="previewStandardStructure()" class="btn-secondary">{{ fr() ? 'Recharger la proposition' : 'Reload proposal' }}</button></div>@if (wizardDependencies().length) { <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white"><table class="w-full text-xs"><thead class="bg-slate-50"><tr><th class="text-left p-2">Parent</th><th class="text-left p-2">{{ fr() ? 'Composant' : 'Component' }}</th><th class="text-left p-2">{{ fr() ? 'Poids' : 'Weight' }}</th><th class="text-left p-2">{{ fr() ? 'Optionnel' : 'Optional' }}</th></tr></thead><tbody>@for (dependency of wizardDependencies(); track dependency.parentPeriodId + dependency.childPeriodId) { <tr class="border-t border-slate-100"><td class="p-2 font-semibold">{{ dependency.parentCode }}</td><td class="p-2">{{ dependency.childCode }}</td><td class="p-2"><input type="number" min="0" step="0.01" [(ngModel)]="dependency.weight" class="field w-24" /></td><td class="p-2"><label class="inline-flex items-center gap-2"><input type="checkbox" [(ngModel)]="dependency.optional" /><span>{{ dependency.optional ? (fr() ? 'Oui' : 'Yes') : (fr() ? 'Non' : 'No') }}</span></label></td></tr> }</tbody></table></div> } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'Prévisualisez la structure pour éditer les dépendances.' : 'Preview the structure to edit dependencies.' }}</div> }</div>
                }
                @if (wizardStep() === 4) {
                  <div class="mt-4 space-y-3"><p class="text-xs text-slate-600">{{ fr() ? 'Ces trois brouillons utilisent les mêmes limites facultatives et la même validation que les cartes ci-dessus. Rien n’est envoyé à ce stade.' : 'These three drafts use the same optional-limit rules and validation as the cards above. Nothing is sent at this stage.' }}</p><div class="grid grid-cols-1 md:grid-cols-3 gap-2">@for (window of wizardTermWindows(); track window.sequenceNo) { <div class="rounded-lg border border-slate-200 bg-white p-3"><div class="flex items-center justify-between"><strong>{{ window.code }}</strong><span class="text-[10px] text-slate-500">{{ window.limited ? (fr() ? 'Limitée' : 'Limited') : (fr() ? 'Sans restriction' : 'Unrestricted') }}</span></div><div class="text-[11px] text-slate-500 mt-1">{{ governedLabels(window.sequenceNo) }}</div><label class="flex items-center gap-2 mt-3 text-xs font-semibold"><input type="checkbox" [checked]="window.limited" (change)="setWizardWindow(window.sequenceNo, 'limited', ($any($event.target)).checked)" /> {{ fr() ? 'Limiter les dates de gestion' : 'Limit management dates' }}</label>@if (window.limited) { <label class="block mt-2 text-[11px] font-semibold"><span>{{ fr() ? 'Disponible à partir du' : 'Available from' }}</span><input type="datetime-local" [value]="wizardLocal(window.opensAt)" (input)="setWizardWindow(window.sequenceNo, 'opensAt', ($any($event.target)).value)" class="field mt-1" [class.field-invalid]="wizardError(window.sequenceNo, 'opensAt')" />@if (wizardError(window.sequenceNo, 'opensAt'); as e) { <small class="field-error">{{ e }}</small> }</label><label class="block mt-2 text-[11px] font-semibold"><span>{{ fr() ? 'Disponible jusqu’au' : 'Available until' }}</span><input type="datetime-local" [value]="wizardLocal(window.closesAt)" (input)="setWizardWindow(window.sequenceNo, 'closesAt', ($any($event.target)).value)" class="field mt-1" [class.field-invalid]="wizardError(window.sequenceNo, 'closesAt')" />@if (wizardError(window.sequenceNo, 'closesAt'); as e) { <small class="field-error">{{ e }}</small> }</label> }<p class="text-[11px] text-slate-600 mt-2">{{ wizardSummary(window) }}</p></div> }</div></div>
                }
                @if (wizardStep() === 5) {
                  <div class="mt-4 space-y-3">@if (structurePreview(); as preview) { <div class="grid grid-cols-2 md:grid-cols-4 gap-2"><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Jalons' : 'Milestones' }}</div><strong>{{ preview.periods.length }}</strong></div><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Dépendances' : 'Dependencies' }}</div><strong>{{ wizardDependencies().length || preview.dependencies.length }}</strong></div><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Trimestres limités' : 'Limited trimesters' }}</div><strong>{{ limitedWizardCount() }}</strong></div><details class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><summary class="cursor-pointer font-semibold">{{ fr() ? 'Détails techniques' : 'Technical details' }}</summary><span class="font-mono text-[10px] break-all">{{ preview.fingerprint }}</span></details></div>@for (warning of preview.warnings; track warning) { <div class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">{{ warning }}</div> } } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'La validation nécessite une proposition prévisualisée.' : 'Validation requires a previewed proposal.' }}</div> }<label class="block"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><textarea [(ngModel)]="wizardReason" rows="3" class="field h-auto py-2" [placeholder]="fr() ? 'Expliquez la structure à appliquer…' : 'Explain the structure to apply…'"></textarea></label></div>
                }
                <div class="flex justify-between gap-2 mt-4 pt-3 border-t border-brand-100"><button type="button" (click)="wizardBack()" [disabled]="wizardStep() === 1" class="btn-secondary">{{ fr() ? 'Précédent' : 'Back' }}</button><div class="flex gap-2">@if (wizardStep() < 5) { <button type="button" (click)="wizardNext()" class="btn-primary">{{ fr() ? 'Suivant' : 'Next' }}</button> } @else { <button type="button" (click)="wizardApply()" [disabled]="!structurePreview() || !wizardReason.trim() || saving()" class="btn-primary">{{ fr() ? 'Demander la confirmation' : 'Request confirmation' }}</button> }</div></div>
              </section>

              <!-- 7. Result structure -->
              <bbc-card [title]="fr() ? 'Structure des résultats' : 'Results structure'">
                <p class="text-xs text-mute leading-relaxed mb-3">{{ fr() ? 'Les dix jalons restent visibles comme produits académiques. Chaque jalon indique le trimestre qui gère son accès.' : 'The ten milestones remain visible as academic products. Each milestone shows the trimester that governs its access.' }}</p>
                @if (reportingPeriods().length) { <div class="grid grid-cols-1 md:grid-cols-2 gap-2">@for (p of reportingPeriods(); track p.id) { <div class="rounded-lg border border-slate-200 px-3 py-2" [class]="p.periodType === 'ANNUAL_RESULT' ? 'bg-amber-50 border-amber-200' : 'bg-white'"><div class="flex items-center justify-between gap-2"><span class="font-semibold text-sm">{{ p.code }} · {{ p.label }}</span><span class="chip bg-slate-100 text-slate-600">{{ periodTypeLabel(p.periodType) }}</span></div><div class="text-xs text-mute mt-1">{{ p.startDate }} → {{ p.endDate }}</div><div class="text-[11px] text-slate-600 mt-1">{{ calculationSummary(p) }}</div><div class="mt-2 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-700"><span class="font-semibold">{{ fr() ? 'Accès géré par ' : 'Access managed by ' }}{{ governingTermCode(p) }}</span>@if (windowForPeriod(p); as access) { <span> · {{ accessStateLabel(access) }}</span> }</div></div> }</div> } @else { <div class="text-sm text-mute py-3">{{ fr() ? 'Aucune structure de résultats configurée.' : 'No result structure configured.' }}</div> }
              </bbc-card>

              <!-- 8. Calendar and expected-attendance generation -->
              <bbc-card [title]="fr() ? 'Calendrier et séances attendues' : 'Calendar and expected sessions'">
                <div class="mb-4 px-3 py-2.5 rounded-lg bg-sky-50 border border-sky-100 text-xs text-sky-900 leading-relaxed"><strong>{{ fr() ? 'À quoi servent ces séances ?' : 'What are these sessions for?' }}</strong> {{ fr() ? 'Le système calcule une séance attendue pour chaque jour de classe et chaque classe, en excluant les jours fériés. La prévisualisation ne modifie rien.' : 'The system calculates one expected session for every teaching day and class, excluding holidays. Preview changes nothing.' }}</div>
                <div class="space-y-2">@for (d of calendarDays(); track d.dayOfWeek) { <div class="grid grid-cols-[7rem_1fr_1fr_auto] gap-2 items-center"><label class="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" [(ngModel)]="d.teachingDay" [disabled]="!canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" /> {{ dayLabel(d.dayOfWeek) }}</label><input type="time" [(ngModel)]="d.startTime" [disabled]="!d.teachingDay || !canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" class="field" /><input type="time" [(ngModel)]="d.endTime" [disabled]="!d.teachingDay || !canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" class="field" />@if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="saveDay(d)" class="btn-secondary">{{ fr() ? 'Sauver' : 'Save' }}</button> }</div> }</div>
                <div class="flex flex-wrap gap-2 mt-4 pt-4 border-t border-slate-100"><button (click)="previewCalendar()" class="btn-secondary">{{ fr() ? 'Prévisualiser la génération' : 'Preview generation' }}</button>@if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="generateCalendar()" class="btn-primary">{{ fr() ? 'Générer les séances' : 'Generate sessions' }}</button> }</div>
                @if (generation(); as g) { <div class="mt-3 p-3 rounded-lg border text-sm" [class]="g.dryRun ? 'bg-amber-50 border-amber-200' : 'bg-emerald-50 border-emerald-200'"><div class="font-bold">{{ g.dryRun ? (fr() ? 'Simulation — aucune donnée modifiée' : 'Preview — no data changed') : (fr() ? 'Génération terminée' : 'Generation completed') }}</div><div class="mt-1"><strong>{{ g.expectedRows }}</strong> {{ fr() ? 'séances attendues' : 'expected sessions' }} = {{ g.teachingDates }} {{ fr() ? 'jours de classe' : 'teaching days' }} × {{ g.classes }} {{ fr() ? 'classes' : 'classes' }}.</div></div> }
              </bbc-card>
            </div>
          }
        </div>
      }

      @if (pendingState(); as transition) { <div class="modal-backdrop"><section class="modal-panel" role="dialog" aria-modal="true"><h3 class="text-lg font-bold text-ink">{{ stateConfirmationTitle(transition.target) }}</h3><p class="text-sm text-mute mt-1">{{ transition.session.label }} · {{ statusLabel(transition.session.status) }} → {{ statusLabel(transition.target) }}</p><div class="mt-4 px-3 py-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-950">{{ stateImpact(transition.target) }}</div><label class="block mt-4"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><textarea [(ngModel)]="transitionReason" rows="3" class="field h-auto py-2"></textarea></label><div class="flex justify-end gap-2 mt-5"><button (click)="cancelStateChange()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmStateChange()" [disabled]="!transitionReason.trim() || saving()" class="btn-primary">{{ fr() ? 'Confirmer' : 'Confirm' }}</button></div></section></div> }
      @if (generationConfirmation()) { <div class="modal-backdrop"><section class="modal-panel" role="dialog" aria-modal="true"><h3 class="text-lg font-bold text-ink">{{ fr() ? 'Générer les séances attendues ?' : 'Generate expected sessions?' }}</h3><p class="text-sm text-slate-700 mt-2">{{ fr() ? 'Les présences historiques sont conservées ; seules les lignes futures devenues inutiles peuvent être supprimées.' : 'Historical attendance is preserved; only obsolete future rows may be removed.' }}</p><div class="flex justify-end gap-2 mt-5"><button (click)="generationConfirmation.set(false)" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmGeneration()" class="btn-primary">{{ fr() ? 'Confirmer la génération' : 'Confirm generation' }}</button></div></section></div> }
      @if (structureConfirmation()) { <div class="modal-backdrop"><section class="modal-panel" role="dialog" aria-modal="true"><h3 class="text-lg font-bold text-ink">{{ fr() ? 'Confirmer la structure académique ?' : 'Confirm the academic structure?' }}</h3><p class="text-sm text-slate-700 mt-2">{{ fr() ? 'La structure, les dépendances et les limites par trimestre seront appliquées dans une seule transaction et auditées.' : 'The structure, dependencies, and trimester limits will be applied in one transaction and audited.' }}</p><div class="flex justify-end gap-2 mt-5"><button (click)="cancelStructureApply()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmStructureApply()" [disabled]="saving()" class="btn-primary">{{ fr() ? 'Confirmer et appliquer' : 'Confirm and apply' }}</button></div></section></div> }
      @if (pendingTermRemoval(); as termId) { <div class="modal-backdrop"><section class="modal-panel" role="dialog" aria-modal="true"><h3 class="text-lg font-bold text-ink">{{ fr() ? 'Retirer ce trimestre ?' : 'Remove this trimester?' }}</h3><p class="text-sm text-slate-700 mt-2">{{ fr() ? 'Le trimestre et les jalons qui lui sont liés seront retirés. Cette action est auditée.' : 'The trimester and its linked milestones will be removed. This action is audited.' }}</p><label class="block mt-4"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><textarea [(ngModel)]="termRemovalReason" rows="3" class="field h-auto py-2"></textarea></label><div class="flex justify-end gap-2 mt-5"><button (click)="cancelTermRemoval()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmTermRemoval()" [disabled]="!termRemovalReason.trim()" class="btn-primary">{{ fr() ? 'Confirmer le retrait' : 'Confirm removal' }}</button></div></section></div> }
    </div>
  `,
  styles: [`
    .field { width:100%; min-height:2.35rem; padding:0 .75rem; border:1px solid #cbd5e1; border-radius:.5rem; font-size:.82rem; background:white; }
    .field:focus { outline:2px solid #a5b4fc; outline-offset:1px; border-color:#4f46e5; }
    .field-invalid { border:2px solid #dc2626; background:#fff7f7; }
    .field-error { display:block; color:#b91c1c; margin-top:.25rem; }
    .btn-primary,.btn-secondary { height:2.25rem; padding:0 .9rem; border-radius:.5rem; font-size:.75rem; font-weight:700; display:inline-flex; align-items:center; gap:.35rem; }
    .btn-primary { background:#3453b8; color:white; }.btn-primary:disabled { opacity:.45; }
    .btn-secondary { background:white; border:1px solid #cbd5e1; color:#334155; }
    .chip { font-size:.65rem; font-weight:700; padding:.2rem .5rem; border-radius:999px; }
    .meta { color:#64748b; text-transform:uppercase; letter-spacing:.04em; font-size:.65rem; display:block; margin-bottom:.25rem; }
    .modal-backdrop { position:fixed; inset:0; z-index:60; display:flex; align-items:center; justify-content:center; padding:1rem; background:rgba(15,23,42,.55); }
    .modal-panel { width:100%; max-width:34rem; max-height:90vh; overflow:auto; border-radius:1rem; background:white; padding:1.25rem; box-shadow:0 24px 70px rgba(15,23,42,.3); }
  `],
})
export class FoundationSettingsComponent {
  private api = inject(FoundationApi);
  private auth = inject(AuthService);
  private context = inject(AcademicContextService);
  protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected sessions = signal<AcademicSessionView[]>([]);
  protected selectedId = signal<string | null>(null);
  protected selected = computed(() => this.sessions().find((s) => s.id === this.selectedId()) ?? null);
  protected calendarDays = signal<CalendarDayView[]>([]);
  protected reportingPeriods = signal<AcademicReportingPeriodView[]>([]);
  protected termManagementWindows = signal<TermManagementWindowView[]>([]);
  protected readiness = signal<SessionReadinessView | null>(null);
  protected structurePreview = signal<StandardStructureView | null>(null);
  protected wizardStep = signal(1);
  protected wizardSteps = [1, 2, 3, 4, 5];
  protected wizardDependencies = signal<StructureDependencyView[]>([]);
  protected wizardTermWindows = signal<TermManagementWindowProposal[]>([]);
  protected wizardWindowErrors = signal<Record<number, Partial<Record<'limited' | 'opensAt' | 'closesAt', string>>>>({});
  protected wizardReason = '';
  protected loading = signal(true);
  protected saving = signal(false);
  protected showSessionForm = signal(false);
  protected editingId = signal<string | null>(null);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  protected generation = signal<GenerationResult | null>(null);
  protected actionPermissions = signal<Record<string, boolean>>({});
  protected canManage = computed(() => this.actionPermissions()['SESSION_MANAGE'] ?? this.auth.can('settings', 'write'));
  protected sessionDraft: AcademicSessionUpsert = this.blankSession();
  protected termDraft: AcademicTermUpsert = { code: '', label: '', sequenceNo: 1, startDate: '', endDate: '' };
  protected editingTermId = signal<string | null>(null);
  protected pendingState = signal<{ session: AcademicSessionView; target: string } | null>(null);
  protected transitionReason = '';
  protected generationConfirmation = signal(false);
  protected pendingTermRemoval = signal<string | null>(null);
  protected termRemovalReason = '';
  protected structureConfirmation = signal(false);
  protected structureReason = '';

  constructor() { this.reload(); this.api.actionPermissions().subscribe((p) => this.actionPermissions.set(p)); }

  protected select(s: AcademicSessionView): void { this.selectedId.set(s.id); this.structurePreview.set(null); this.wizardStep.set(1); this.generation.set(null); this.message.set(null); this.loadData(s.id); }
  protected newSession(): void { this.editingId.set(null); this.sessionDraft = this.blankSession(); this.showSessionForm.set(true); }
  protected editSession(s: AcademicSessionView): void { this.editingId.set(s.id); this.sessionDraft = { code: s.code, label: s.label, startDate: s.startDate, endDate: s.endDate, status: s.status, current: s.current, version: s.version, timezone: s.timezone }; this.showSessionForm.set(true); }
  protected cancelSession(): void { this.showSessionForm.set(false); this.editingId.set(null); }
  protected saveSession(): void {
    const body: AcademicSessionUpsert = { code: this.sessionDraft.code, label: this.sessionDraft.label, startDate: this.sessionDraft.startDate, endDate: this.sessionDraft.endDate, status: this.sessionDraft.status, current: this.sessionDraft.current, version: this.sessionDraft.version, timezone: this.sessionDraft.timezone };
    const request = this.editingId() ? this.api.updateSession(this.editingId()!, body) : this.api.createSession(body);
    this.saving.set(true); request.subscribe({ next: (s) => { this.saving.set(false); this.cancelSession(); this.reload(s.id); this.message.set({ ok: true, text: this.fr() ? 'Session enregistrée.' : 'Session saved.' }); }, error: (e) => this.fail(e) });
  }
  protected requestStateChange(session: AcademicSessionView, target: string): void { this.transitionReason = ''; this.pendingState.set({ session, target }); }
  protected cancelStateChange(): void { this.pendingState.set(null); this.transitionReason = ''; }
  protected confirmStateChange(): void { const transition = this.pendingState(); if (!transition || !this.transitionReason.trim()) return; this.saving.set(true); this.api.changeSessionState(transition.session.id, transition.target, this.transitionReason.trim(), transition.session.version).subscribe({ next: () => { this.saving.set(false); this.cancelStateChange(); this.reload(transition.session.id); }, error: (e) => this.fail(e) }); }
  protected stateConfirmationTitle(target: string): string { return ({ OPEN: this.fr() ? 'Ouvrir cette session ?' : 'Open this session?', CLOSED: this.fr() ? 'Clôturer cette session ?' : 'Close this session?', ARCHIVED: this.fr() ? 'Archiver cette session ?' : 'Archive this session?' } as Record<string, string>)[target] ?? (this.fr() ? 'Changer le statut ?' : 'Change status?'); }
  protected stateImpact(target: string): string { return ({ OPEN: this.fr() ? 'La session deviendra active et courante.' : 'The session becomes active and current.', CLOSED: this.fr() ? 'La session ne recevra plus de modifications ordinaires.' : 'The session will no longer accept ordinary changes.', ARCHIVED: this.fr() ? 'La session restera disponible comme historique.' : 'The session remains available as history.' } as Record<string, string>)[target] ?? ''; }

  protected editTerm(t: AcademicTermView): void { this.editingTermId.set(t.id); this.termDraft = { code: t.code, label: t.label, sequenceNo: t.sequenceNo, startDate: t.startDate, endDate: t.endDate, version: t.version, timezone: t.timezone }; }
  protected cancelTermEdit(): void { this.resetTermDraft(); }
  protected saveTerm(): void { const s = this.selected(); if (!s) return; const body: AcademicTermUpsert = { code: this.termDraft.code, label: this.termDraft.label, sequenceNo: Number(this.termDraft.sequenceNo), startDate: this.termDraft.startDate, endDate: this.termDraft.endDate, version: this.editingTermId() ? this.termDraft.version : undefined, timezone: this.termDraft.timezone }; const request = this.editingTermId() ? this.api.updateTerm(this.editingTermId()!, body) : this.api.addTerm(s.id, body); this.saving.set(true); request.subscribe({ next: () => { this.saving.set(false); this.resetTermDraft(s.terms.length + 1); this.reload(s.id); }, error: (e) => this.fail(e) }); }
  protected requestTermRemoval(id: string): void { this.termRemovalReason = ''; this.pendingTermRemoval.set(id); }
  protected cancelTermRemoval(): void { this.pendingTermRemoval.set(null); this.termRemovalReason = ''; }
  protected confirmTermRemoval(): void { const id = this.pendingTermRemoval(); if (!id || !this.termRemovalReason.trim()) return; this.api.deleteTerm(id, this.termRemovalReason.trim()).subscribe({ next: () => { this.cancelTermRemoval(); this.reload(this.selectedId() ?? undefined); }, error: (e) => this.fail(e) }); }
  protected onTermWindowChanged(updated: TermManagementWindowView): void { this.termManagementWindows.update((rows) => rows.map((row) => row.termId === updated.termId ? updated : row)); this.api.readiness(updated.academicSessionId).subscribe({ next: (r) => this.readiness.set(r), error: () => undefined }); }

  protected previewStandardStructure(): void { const s = this.selected(); if (!s) return; this.api.previewStandardStructure(s.id).subscribe({ next: (p) => { this.setPreview(p); }, error: (e) => this.fail(e) }); }
  protected wizardStepLabel(step: number): string { const labels = this.fr() ? ['Session / trimestres', 'Dates des résultats', 'Dépendances / calculs', 'Accès par trimestre (facultatif)', 'Vérification et confirmation'] : ['Session / trimesters', 'Result dates', 'Dependencies / calculations', 'Trimester access (optional)', 'Verification and confirmation']; return labels[step - 1] ?? ''; }
  protected setWizardStep(step: number): void { this.wizardStep.set(Math.max(1, Math.min(5, step))); if (step >= 2 && !this.structurePreview()) this.previewStandardStructure(); }
  protected wizardNext(): void { this.setWizardStep(this.wizardStep() + 1); }
  protected wizardBack(): void { this.setWizardStep(this.wizardStep() - 1); }
  protected wizardApply(): void { if (!this.structurePreview() || !this.validateWizardWindows() || !this.wizardReason.trim()) return; this.structureReason = this.wizardReason.trim(); this.structurePreview.set(this.wizardProposal()); this.structureConfirmation.set(true); }
  protected cancelStructureApply(): void { this.structureConfirmation.set(false); this.structureReason = ''; }
  protected confirmStructureApply(): void { const s = this.selected(); const proposal = this.wizardProposal(); if (!s || !proposal || !this.structureReason.trim()) return; this.saving.set(true); this.api.applyStandardStructure(s.id, this.structureReason.trim(), proposal.fingerprint, proposal).subscribe({ next: (p) => { this.saving.set(false); this.cancelStructureApply(); this.setPreview(p); this.reload(s.id); this.message.set({ ok: true, text: this.fr() ? 'Structure académique appliquée.' : 'Academic structure applied.' }); }, error: (e) => this.fail(e) }); }
  private wizardProposal(): StandardStructureView { const preview = this.structurePreview()!; return { ...preview, dependencies: this.wizardDependencies().length ? this.wizardDependencies().map((d) => ({ ...d })) : preview.dependencies, termManagementWindows: this.wizardTermWindows().map((w) => ({ ...w })) }; }
  private setPreview(p: StandardStructureView): void { this.structurePreview.set(p); this.wizardDependencies.set(p.dependencies.map((d) => ({ ...d }))); this.wizardTermWindows.set((p.termManagementWindows ?? []).map((w) => ({ ...w }))); this.wizardWindowErrors.set({}); }
  protected setWizardWindow(sequenceNo: number, field: 'limited' | 'opensAt' | 'closesAt', value: string | boolean): void { this.wizardTermWindows.update((rows) => rows.map((row) => row.sequenceNo === sequenceNo ? { ...row, [field]: field === 'limited' ? value : this.wizardInstant(String(value)) } : row)); this.validateWizardWindow(sequenceNo); }
  protected wizardError(sequenceNo: number, field: 'limited' | 'opensAt' | 'closesAt'): string | null { return this.wizardWindowErrors()[sequenceNo]?.[field] ?? null; }
  private validateWizardWindow(sequenceNo: number): boolean { const row = this.wizardTermWindows().find((w) => w.sequenceNo === sequenceNo); if (!row) return true; const errors: Partial<Record<'limited' | 'opensAt' | 'closesAt', string>> = {}; if (row.limited && !row.opensAt && !row.closesAt) { const msg = this.fr() ? 'Indiquez une date d’ouverture, une date de fermeture, ou les deux.' : 'Add an opening date, a closing date, or both.'; errors.opensAt = msg; errors.closesAt = msg; } else if (row.limited && row.opensAt && row.closesAt && new Date(row.closesAt).getTime() <= new Date(row.opensAt).getTime()) errors.closesAt = this.fr() ? 'La fermeture doit être postérieure à l’ouverture.' : 'The closing date must be after the opening date.'; this.wizardWindowErrors.update((all) => ({ ...all, [sequenceNo]: errors })); return !Object.keys(errors).length; }
  private validateWizardWindows(): boolean { return this.wizardTermWindows().every((row) => this.validateWizardWindow(row.sequenceNo)); }
  protected wizardLocal(value: string | null): string { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  private wizardInstant(value: string): string | null { return value ? new Date(value).toISOString() : null; }
  protected wizardSummary(row: TermManagementWindowProposal): string { const codes = this.governedLabels(row.sequenceNo); if (!row.limited) return `${codes} ${this.fr() ? 'n’ont aucune restriction de date.' : 'have no date restriction.'}`; if (row.opensAt && row.closesAt) return `${this.fr() ? 'Disponibles du' : 'Available from'} ${this.wizardLocal(row.opensAt)} ${this.fr() ? 'au' : 'until'} ${this.wizardLocal(row.closesAt)}.`; if (row.opensAt) return `${this.fr() ? 'Disponibles à partir du' : 'Available from'} ${this.wizardLocal(row.opensAt)}.`; if (row.closesAt) return `${this.fr() ? 'Disponibles immédiatement jusqu’au' : 'Available immediately until'} ${this.wizardLocal(row.closesAt)}.`; return this.fr() ? 'Une date est nécessaire.' : 'A date is required.'; }
  protected governedLabels(sequenceNo: number): string { return ({ 1: 'S1, S2 et résultat T1', 2: 'S3, S4 et résultat T2', 3: 'S5, S6, résultat T3 et résultat annuel' } as Record<number, string>)[sequenceNo] ?? ''; }
  protected limitedWizardCount(): number { return this.wizardTermWindows().filter((w) => w.limited).length; }

  protected periodTypeLabel(type: string): string { return ({ SEQUENCE: this.fr() ? 'Séquence' : 'Sequence', TERM_RESULT: this.fr() ? 'Résultat du trimestre' : 'Trimester result', ANNUAL_RESULT: this.fr() ? 'Résultat annuel' : 'Annual result' } as Record<string, string>)[type] ?? type; }
  protected calculationSummary(p: AcademicReportingPeriodView): string { if (p.periodType === 'SEQUENCE') return this.fr() ? 'Saisie des évaluations' : 'Assessment entry'; if (p.periodType === 'ANNUAL_RESULT') return this.fr() ? 'Calculé à partir des trois résultats trimestriels' : 'Calculated from the three trimester results'; return this.fr() ? 'Calculé à partir des deux séquences du trimestre' : 'Calculated from the two trimester sequences'; }
  protected governingTermCode(p: AcademicReportingPeriodView): string { if (p.periodType === 'ANNUAL_RESULT') return 'T3'; return this.selected()?.terms.find((t) => t.id === p.academicTermId)?.code ?? '—'; }
  protected windowForPeriod(p: AcademicReportingPeriodView): TermManagementWindowView | null { const code = this.governingTermCode(p); return this.termManagementWindows().find((w) => w.termCode.toUpperCase() === code.toUpperCase()) ?? null; }
  protected accessStateLabel(row: TermManagementWindowView): string { if (!row.limited) return this.fr() ? 'Aucune restriction de date' : 'No date restriction'; if (row.state === 'SCHEDULED') return this.fr() ? 'Ouverture programmée' : 'Opening scheduled'; if (row.state === 'CLOSED') return this.fr() ? 'Fenêtre terminée' : 'Window ended'; return this.fr() ? 'Gestion autorisée maintenant' : 'Management allowed now'; }
  protected allAccessUnrestricted(): boolean { return this.termManagementWindows().length > 0 && this.termManagementWindows().every((w) => !w.limited); }

  protected saveDay(d: CalendarDayView): void { const s = this.selected(); if (!s) return; this.api.saveCalendarDay(s.id, { dayOfWeek: d.dayOfWeek, teachingDay: d.teachingDay, startTime: d.startTime, endTime: d.endTime, version: d.version }).subscribe({ next: () => this.loadCalendar(s.id), error: (e) => this.fail(e) }); }
  protected previewCalendar(): void { this.runGeneration(true); }
  protected generateCalendar(): void { this.generationConfirmation.set(true); }
  protected confirmGeneration(): void { this.generationConfirmation.set(false); this.runGeneration(false); }
  protected dayLabel(day: number): string { return (this.fr() ? ['Lun','Mar','Mer','Jeu','Ven','Sam','Dim'] : ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'])[day - 1] ?? '?'; }
  protected statusLabel(s: string): string { const labels: Record<string, string> = { DRAFT: 'Brouillon', OPEN: 'Ouverte', CLOSED: 'Clôturée', ARCHIVED: 'Archivée' }; return this.fr() ? (labels[s] ?? s) : s[0] + s.slice(1).toLowerCase(); }
  protected statusClass(s: string): string { return s === 'OPEN' ? 'text-emerald-700' : s === 'DRAFT' ? 'text-amber-700' : 'text-slate-500'; }

  protected reload(selectId?: string): void { this.loading.set(true); this.api.listSessions().subscribe({ next: (rows) => { this.sessions.set(rows); const id = selectId ?? this.selectedId() ?? rows.find((s) => s.current)?.id ?? rows[0]?.id ?? null; this.selectedId.set(id); this.loading.set(false); if (id) this.loadData(id); this.context.load(true); }, error: (e) => { this.loading.set(false); this.fail(e); } }); }
  private loadData(id: string): void { this.loadCalendar(id); this.loadReportingPeriods(id); this.api.termManagementWindows(id).subscribe({ next: (rows) => this.termManagementWindows.set(rows), error: (e) => this.fail(e) }); this.api.readiness(id).subscribe({ next: (r) => this.readiness.set(r), error: () => this.readiness.set(null) }); }
  private loadCalendar(id: string): void { this.api.calendarDays(id).subscribe({ next: (d) => this.calendarDays.set(d.map((x) => ({ ...x }))), error: (e) => this.fail(e) }); }
  private loadReportingPeriods(id: string): void { this.api.reportingPeriods(id).subscribe({ next: (p) => this.reportingPeriods.set(p.map((period) => ({ ...period, label: cleanDisplay(period.label) }))), error: (e) => this.fail(e) }); }
  private runGeneration(dryRun: boolean): void { const s = this.selected(); if (!s) return; this.api.generateCalendar(s.id, s.startDate, s.endDate, dryRun).subscribe({ next: (g) => this.generation.set(g), error: (e) => this.fail(e) }); }
  private resetTermDraft(sequenceNo = 1): void { this.editingTermId.set(null); this.termDraft = { code: '', label: '', sequenceNo, startDate: '', endDate: '' }; }
  private blankSession(): AcademicSessionUpsert { const year = new Date().getFullYear(); return { code: `${year}-${year + 1}`, label: `Session ${year}-${year + 1}`, startDate: `${year}-09-01`, endDate: `${year + 1}-07-31`, status: 'DRAFT', current: false, timezone: 'Africa/Douala' }; }
  private fail(e: any): void { this.saving.set(false); this.message.set({ ok: false, text: typeof e?.error?.message === 'string' ? e.error.message : (this.fr() ? 'Opération impossible.' : 'Operation failed.') }); }
}
