import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AcademicContextService } from '../../core/academic-context.service';
import {
  AcademicSessionUpsert, AcademicSessionView, AcademicTermUpsert, AcademicTermView,
  AcademicReportingPeriodView, AcademicReportingPeriodUpsert, CalendarDayView, FoundationApi, GenerationResult, StandardStructureView, StructureDependencyView, SessionReadinessView,
  EffectiveWindowView, WindowOverrideView, WindowOverrideUpsert, WorkflowAction,
} from '../../core/foundation.api';
import { CardComponent, EmptyComponent, IconComponent } from '../../core/ui';
import { SessionConfigurationCopyComponent } from './session-configuration-copy';
import { WorkflowWindowRulesComponent } from './workflow-window-rules';

const cleanDisplay = (value: string | null | undefined): string => {
  if (!value) return value ?? '';
  return value
    .replace(/\u00c3\u0083\u00c2\u2030/g, '\u00c9')
    .replace(/\u00c3\u0083\u00c2\u00a9/g, '\u00e9')
    .replace(/\u00c3\u0083\u00c2\u00a8/g, '\u00e8')
    .replace(/\u00c3\u0083\u00c2\u00aa/g, '\u00ea')
    .replace(/\u00c3\u0083\u00c2\u00a0/g, '\u00e0')
    .replace(/\u00c3\u0083\u00c2\u00a2/g, '\u00e2')
    .replace(/\u00c3\u0083\u00c2\u00a7/g, '\u00e7')
    .replace(/\u00c3\u0083\u00c2\u00b4/g, '\u00f4')
    .replace(/\u00c3\u0083\u00c2\u00bb/g, '\u00fb')
    .replace(/\u00c3\u0083\u00c2\u00af/g, '\u00ef')
    .replace(/\u00c3\u0082\u00c2\u00b7/g, '\u00b7')
    .replace(/\u00c3\u0082\u00c2\u00a0/g, ' ')
    .replace(/\u00c3\u2030/g, '\u00c9')
    .replace(/\u00c3\u00a9/g, '\u00e9')
    .replace(/\u00c3\u00a8/g, '\u00e8')
    .replace(/\u00c3\u00aa/g, '\u00ea')
    .replace(/\u00c3\u00a0/g, '\u00e0')
    .replace(/\u00c3\u00a2/g, '\u00e2')
    .replace(/\u00c3\u00a7/g, '\u00e7')
    .replace(/\u00c3\u00b4/g, '\u00f4')
    .replace(/\u00c3\u00bb/g, '\u00fb')
    .replace(/\u00c3\u00af/g, '\u00ef')
    .replace(/\u00c2\u00b7/g, '\u00b7')
    .replace(/\u00c2\u00a0/g, ' ');
};

@Component({
  selector: 'bbc-foundation-settings',
  standalone: true,
  imports: [FormsModule, CardComponent, EmptyComponent, IconComponent, SessionConfigurationCopyComponent, WorkflowWindowRulesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-5">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-lg font-bold text-ink">{{ fr() ? 'Années et périodes académiques' : 'Academic sessions and terms' }}</h2>
          <p class="text-sm text-mute mt-1">{{ fr() ? 'Les sessions sont la référence historique des inscriptions, calendriers et publications.' : 'Sessions are the historical source for enrollment, calendars, and publication.' }}</p>
        </div>
        @if (canManage() && !showSessionForm()) {
          <button (click)="newSession()" class="h-9 px-4 rounded-lg bg-brand-600 text-white text-sm font-semibold">
            <bbc-icon name="plus" [s]="14" /> {{ fr() ? 'Nouvelle session' : 'New session' }}
          </button>
        }
      </div>

      @if (message(); as m) {
        <div class="px-3 py-2 rounded-lg text-sm" [class]="m.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ m.text }}</div>
      }

      @if (showSessionForm()) {
        <bbc-card [title]="editingId() ? (fr() ? 'Modifier la session' : 'Edit session') : (fr() ? 'Créer une session' : 'Create session')">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
            <label class="block"><span class="text-xs font-semibold">Code</span><input [(ngModel)]="sessionDraft.code" class="field" placeholder="2026-2027" /></label>
            <label class="block md:col-span-2"><span class="text-xs font-semibold">{{ fr() ? 'Libellé' : 'Label' }}</span><input [(ngModel)]="sessionDraft.label" class="field" placeholder="Session 2026-2027" /></label>
            <label class="flex items-center gap-2 pt-6"><input type="checkbox" [(ngModel)]="sessionDraft.current" /> <span class="text-sm">{{ fr() ? 'Session courante' : 'Current session' }}</span></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Début' : 'Start' }}</span><input type="date" [(ngModel)]="sessionDraft.startDate" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Fin' : 'End' }}</span><input type="date" [(ngModel)]="sessionDraft.endDate" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Ouverture notes' : 'Grade entry opens' }}</span><input type="datetime-local" [(ngModel)]="sessionWindows.gradeOpen" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Clôture notes' : 'Grade entry closes' }}</span><input type="datetime-local" [(ngModel)]="sessionWindows.gradeClose" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Ouverture publication' : 'Publication opens' }}</span><input type="datetime-local" [(ngModel)]="sessionWindows.publishOpen" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Clôture publication' : 'Publication closes' }}</span><input type="datetime-local" [(ngModel)]="sessionWindows.publishClose" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Ouverture soumission enseignants' : 'Teacher submission opens' }} <span class="text-rose-600">*</span></span><input type="datetime-local" [(ngModel)]="sessionWindows.teacherOpen" class="field" /></label>
            <label class="block"><span class="text-xs font-semibold">{{ fr() ? 'Clôture soumission enseignants' : 'Teacher submission closes' }} <span class="text-rose-600">*</span></span><input type="datetime-local" [(ngModel)]="sessionWindows.teacherClose" class="field" /></label>
          </div>
          <div class="flex justify-end gap-2 mt-4">
            <button (click)="cancelSession()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button>
            <button (click)="saveSession()" [disabled]="saving() || !sessionDraft.code || !sessionDraft.label || !sessionDraft.startDate || !sessionDraft.endDate" class="btn-primary">{{ saving() ? '…' : (fr() ? 'Enregistrer' : 'Save') }}</button>
          </div>
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
              <button (click)="select(s)" class="w-full text-left p-4 rounded-xl border transition"
                [class]="selected()?.id === s.id ? 'border-brand-400 bg-brand-50' : 'border-slate-200 bg-white hover:border-slate-300'">
                <div class="flex items-center justify-between gap-2">
                  <span class="font-bold text-ink">{{ s.label }}</span>
                  @if (s.current) { <span class="chip bg-emerald-100 text-emerald-700">{{ fr() ? 'Courante' : 'Current' }}</span> }
                </div>
                <div class="text-xs text-mute mt-1">{{ s.startDate }} → {{ s.endDate }}</div>
                <div class="text-[11px] font-semibold mt-2" [class]="statusClass(s.status)">{{ statusLabel(s.status) }}</div>
              </button>
            }
          </div>

          @if (selected(); as s) {
            <div class="lg:col-span-2 space-y-4">
              <bbc-card [title]="s.label">
                <div action class="flex gap-2">
                  @if (canManage() && s.status !== 'ARCHIVED') { <button (click)="editSession(s)" class="btn-secondary">{{ fr() ? 'Modifier' : 'Edit' }}</button> }
                  @if (canManage() && s.status === 'DRAFT') { <button (click)="requestStateChange(s, 'OPEN')" class="btn-primary">{{ fr() ? 'Ouvrir' : 'Open' }}</button> }
                  @if (canManage() && s.status === 'OPEN') { <button (click)="requestStateChange(s, 'CLOSED')" class="btn-secondary">{{ fr() ? 'Clôturer' : 'Close' }}</button> }
                  @if (canManage() && s.status === 'CLOSED') { <button (click)="requestStateChange(s, 'ARCHIVED')" class="btn-secondary">{{ fr() ? 'Archiver' : 'Archive' }}</button> }
                </div>
                <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                  <div><div class="meta">Code</div><div class="font-semibold">{{ s.code }}</div></div>
                  <div><div class="meta">{{ fr() ? 'Statut' : 'Status' }}</div><div class="font-semibold">{{ statusLabel(s.status) }}</div></div>
                  <div><div class="meta">{{ fr() ? 'Périodes' : 'Terms' }}</div><div class="font-semibold">{{ s.terms.length }}</div></div>
                  <div><div class="meta">Version</div><div class="font-semibold">{{ s.version }}</div></div>
                </div>
              </bbc-card>

              @if (readiness(); as r) {
                <section class="rounded-xl border p-3" [class]="r.ready ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'">
                  <div class="flex items-center justify-between gap-3"><strong class="text-sm">{{ fr() ? 'État de préparation' : 'Readiness' }}</strong><span class="chip" [class]="r.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'">{{ r.phase }}</span></div>
                  <p class="text-xs mt-1">{{ r.nextAction }}</p>
                  @if (r.blockers.length) { <div class="mt-2 text-xs text-rose-800">@for (b of r.blockers; track b) { <div>• {{ b }}</div> }</div> }
                </section>
              }

              @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                <bbc-session-configuration-copy [target]="s" [sessions]="sessions()" [canManage]="canManage()" (applied)="select(s)" />
                <bbc-workflow-window-rules class="block mt-4" [target]="s" [terms]="s.terms" [periods]="reportingPeriods()" [canManage]="canManage()" />
              }

              @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                <div class="flex items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
                  <p class="text-xs text-amber-950">{{ fr() ? 'Une dérogation ouvre une action précise pour une durée courte. Elle est séparée de la configuration normale et auditée.' : 'An override opens one precise action for a short period. It is separate from normal configuration and audited.' }}</p>
                  <button (click)="openWindowOverride()" class="btn-secondary shrink-0">{{ fr() ? 'Nouvelle dérogation' : 'New override' }}</button>
                </div>
              }

              @if (windowOverrides().length) {
                <section class="rounded-xl border border-amber-300 bg-amber-50 p-3 text-amber-950">
                  <div class="flex items-center justify-between gap-3"><strong class="text-sm">{{ fr() ? 'Dérogations de fenêtres actives ou historiques' : 'Active or historical window overrides' }}</strong><span class="chip bg-amber-200 text-amber-900">{{ windowOverrides().length }}</span></div>
                  <p class="text-xs mt-1">{{ fr() ? 'Ces ouvertures exceptionnelles sont limitées dans le temps, séparées de la configuration normale et conservées dans l’audit.' : 'These exceptional openings are time-bounded, separate from normal configuration, and retained in the audit trail.' }}</p>
                  <div class="mt-2 space-y-1">
                    @for (override of windowOverrides(); track override.id) {
                      @if (canManage() && override.active) { <button (click)="requestWindowOverrideRevoke(override)" class="text-xs text-rose-700 font-semibold">{{ fr() ? 'Révoquer cette dérogation' : 'Revoke this override' }}</button> }
                      <div class="rounded-md border border-amber-200 bg-white/70 px-2 py-1 text-xs"><span class="font-semibold">{{ override.action }}</span> · {{ override.scope }} · {{ override.active ? (fr() ? 'active' : 'active') : (fr() ? 'expirée' : 'expired') }} · {{ override.reason }}</div>
                    }
                  </div>
                </section>
              }

              <bbc-card [title]="fr() ? 'Périodes et fenêtres de publication' : 'Terms and publication windows'">
                @for (t of s.terms; track t.id) {
                  <div class="flex items-center gap-3 py-2.5 border-b border-slate-100 last:border-0">
                    <div class="w-8 h-8 rounded-full bg-brand-50 text-brand-700 flex items-center justify-center text-xs font-bold">{{ t.sequenceNo }}</div>
                    <div class="flex-1"><div class="font-semibold text-sm">{{ t.label }}</div><div class="text-xs text-mute">{{ t.startDate }} → {{ t.endDate }} · {{ t.code }}</div></div>
                    @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                      <button (click)="editTerm(t)" class="text-xs text-brand-700">{{ fr() ? 'Modifier' : 'Edit' }}</button>
                      <button (click)="requestTermRemoval(t.id)" class="text-xs text-rose-600">{{ fr() ? 'Retirer' : 'Remove' }}</button>
                    }
                  </div>
                } @empty { <div class="text-sm text-mute py-3">{{ fr() ? 'Aucune période configurée.' : 'No terms configured.' }}</div> }
                @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                  <div class="grid grid-cols-2 md:grid-cols-5 gap-2 pt-4 mt-2 border-t border-slate-100">
                    <input [(ngModel)]="termDraft.code" class="field" placeholder="T1" />
                    <input [(ngModel)]="termDraft.label" class="field" [placeholder]="fr() ? '1er trimestre' : 'Term 1'" />
                    <input type="number" min="1" [(ngModel)]="termDraft.sequenceNo" class="field" />
                    <input type="date" [(ngModel)]="termDraft.startDate" class="field" />
                    <input type="date" [(ngModel)]="termDraft.endDate" class="field" />
                  </div>
                  <div class="grid grid-cols-1 md:grid-cols-4 gap-2 mt-2">
                    <label><span class="meta">{{ fr() ? 'Ouverture notes' : 'Grade entry opens' }}</span><input type="datetime-local" [(ngModel)]="termWindows.gradeOpen" class="field" /></label>
                    <label><span class="meta">{{ fr() ? 'Clôture notes' : 'Grade entry closes' }}</span><input type="datetime-local" [(ngModel)]="termWindows.gradeClose" class="field" /></label>
                    <label><span class="meta">{{ fr() ? 'Ouverture publication' : 'Publication opens' }}</span><input type="datetime-local" [(ngModel)]="termWindows.publishOpen" class="field" /></label>
                    <label><span class="meta">{{ fr() ? 'Clôture publication' : 'Publication closes' }}</span><input type="datetime-local" [(ngModel)]="termWindows.publishClose" class="field" /></label>
                  </div>
                  <div class="flex gap-2 mt-3">
                    <button (click)="saveTerm()" [disabled]="!termDraft.code || !termDraft.label || !termDraft.startDate || !termDraft.endDate" class="btn-primary">{{ editingTermId() ? (fr() ? 'Enregistrer la période' : 'Save term') : (fr() ? 'Ajouter la période' : 'Add term') }}</button>
                    @if (editingTermId()) { <button (click)="cancelTermEdit()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button> }
                  </div>
                }
              </bbc-card>

              <section class="rounded-xl border border-brand-200 bg-brand-50/30 p-4" aria-labelledby="academic-structure-wizard-title">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 id="academic-structure-wizard-title" class="text-base font-bold text-ink">{{ fr() ? 'Assistant de configuration académique' : 'Academic configuration wizard' }}</h3>
                    <p class="text-xs text-mute mt-1">{{ fr() ? 'Prévisualisez, éditez et appliquez la structure en six étapes. Rien n’est écrit avant la confirmation finale.' : 'Preview, edit, and apply the structure in six steps. Nothing is written before final confirmation.' }}</p>
                  </div>
                  <span class="chip bg-white text-brand-800 border border-brand-200">{{ fr() ? 'Étape ' + wizardStep() + ' sur 6' : 'Step ' + wizardStep() + ' of 6' }}</span>
                </div>
                <div class="grid grid-cols-2 md:grid-cols-6 gap-2 mt-4">
                  @for (step of wizardSteps; track step) {
                    <button type="button" (click)="setWizardStep(step)" class="rounded-lg border px-2 py-2 text-left text-xs transition" [class]="wizardStep() === step ? 'border-brand-500 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300'">
                      <span class="font-bold">{{ step }}.</span> {{ wizardStepLabel(step) }}
                    </button>
                  }
                </div>

                @if (wizardStep() === 1) {
                  <div class="mt-4 space-y-3">
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                      <div class="rounded-lg border border-slate-200 bg-white p-3"><div class="meta">{{ fr() ? 'Session' : 'Session' }}</div><div class="font-semibold text-sm mt-1">{{ s.label }}</div><div class="text-xs text-mute mt-1">{{ s.startDate }} → {{ s.endDate }}</div><div class="text-xs text-slate-500 mt-1">{{ fr() ? 'Fuseau' : 'Timezone' }}: {{ s.timezone }}</div></div>
                      <div class="rounded-lg border border-slate-200 bg-white p-3 md:col-span-2"><div class="meta">{{ fr() ? 'Trimestres et dates' : 'Terms and dates' }}</div><div class="grid grid-cols-1 md:grid-cols-3 gap-2 mt-2">@for (term of s.terms; track term.id) { <div class="rounded-md border border-slate-100 px-2 py-1 text-xs"><strong>{{ term.code }}</strong><div>{{ term.startDate }} → {{ term.endDate }}</div><button type="button" (click)="editTerm(term)" class="text-brand-700 font-semibold mt-1">{{ fr() ? 'Éditer' : 'Edit' }}</button></div> }</div></div>
                    </div>
                    <p class="text-xs text-slate-600">{{ fr() ? 'Les dates de session et de trimestre bornent toutes les périodes de résultats et leurs fenêtres.' : 'Session and term dates bound every result period and workflow window.' }}</p>
                  </div>
                }

                @if (wizardStep() === 2) {
                  <div class="mt-4 space-y-3">
                    <div class="flex flex-wrap items-center justify-between gap-2"><p class="text-xs text-slate-600">{{ fr() ? 'S1–S6, T1/T2/T3 et Annuel sont des produits distincts. Prévisualisez sans écrire.' : 'S1–S6, T1/T2/T3, and Annual are distinct products. Preview without writing.' }}</p><button type="button" (click)="previewStandardStructure()" class="btn-secondary">{{ fr() ? 'Prévisualiser les dates' : 'Preview result dates' }}</button></div>
                    @if (structurePreview(); as preview) { <div class="grid grid-cols-1 md:grid-cols-3 gap-2">@for (period of preview.periods; track period.id) { <div class="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs"><div class="flex justify-between gap-2"><strong>{{ period.code }}</strong><span class="chip bg-slate-100">{{ period.periodType }}</span></div><div class="mt-1">{{ period.startDate }} → {{ period.endDate }}</div><div class="text-slate-500 mt-1">{{ period.timezone }}</div><button type="button" (click)="editReportingPeriod(period)" class="text-brand-700 font-semibold mt-1">{{ fr() ? 'Configurer les fenêtres' : 'Configure windows' }}</button></div> }</div> } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'Prévisualisez la structure pour continuer.' : 'Preview the structure to continue.' }}</div> }
                  </div>
                }

                @if (wizardStep() === 3) {
                  <div class="mt-4 space-y-3">
                    <div class="flex flex-wrap items-center justify-between gap-2"><p class="text-xs text-slate-600">{{ fr() ? 'Les poids et la règle COMP sont gelés dans l’empreinte de la proposition. Une ligne optionnelle ne bloque pas le résultat.' : 'Weights and the COMP rule are frozen in the proposal fingerprint. An optional row does not block the result.' }}</p><button type="button" (click)="previewStandardStructure()" class="btn-secondary">{{ fr() ? 'Recharger la proposition' : 'Reload proposal' }}</button></div>
                    @if (wizardDependencies.length) { <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white"><table class="w-full text-xs"><thead class="bg-slate-50"><tr><th class="text-left p-2">{{ fr() ? 'Parent' : 'Parent' }}</th><th class="text-left p-2">{{ fr() ? 'Composant' : 'Component' }}</th><th class="text-left p-2">{{ fr() ? 'Poids' : 'Weight' }}</th><th class="text-left p-2">{{ fr() ? 'Optionnel' : 'Optional' }}</th></tr></thead><tbody>@for (dependency of wizardDependencies; track dependency.parentPeriodId + dependency.childPeriodId) { <tr class="border-t border-slate-100"><td class="p-2 font-semibold">{{ dependency.parentCode }}</td><td class="p-2">{{ dependency.childCode }}</td><td class="p-2"><input type="number" min="0" step="0.01" [(ngModel)]="dependency.weight" class="field w-24" aria-label="Weight" /></td><td class="p-2"><label class="inline-flex items-center gap-2"><input type="checkbox" [(ngModel)]="dependency.optional" /><span>{{ dependency.optional ? (fr() ? 'Oui' : 'Yes') : (fr() ? 'Non' : 'No') }}</span></label></td></tr> }</tbody></table></div> } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'Prévisualisez la structure pour éditer les dépendances et poids.' : 'Preview the structure to edit dependencies and weights.' }}</div> }
                  </div>
                }

                @if (wizardStep() === 4) {
                  <div class="mt-4 space-y-3"><p class="text-xs text-slate-600">{{ fr() ? 'Chaque jalon expose les six actions : saisie, soumission enseignant, revue, validation, publication et correction.' : 'Each milestone exposes six actions: entry, teacher submission, review, validation, publication, and correction.' }}</p><div class="grid grid-cols-1 md:grid-cols-2 gap-2">@for (period of reportingPeriods(); track period.id) { <div class="rounded-lg border border-slate-200 bg-white p-3"><div class="flex items-center justify-between gap-2"><strong class="text-sm">{{ period.code }} · {{ period.label }}</strong><button type="button" (click)="editReportingPeriod(period)" class="text-xs text-brand-700 font-semibold">{{ fr() ? 'Éditer' : 'Edit' }}</button></div><div class="grid grid-cols-1 md:grid-cols-2 gap-1 mt-2">@for (action of workflowActions; track action) { @if (effectiveWindowFor(period, action); as window) { <div class="rounded border border-slate-100 px-2 py-1 text-[11px]"><span class="font-semibold">{{ action }}</span><div>{{ window.opensAt || '—' }} → {{ window.closesAt || '—' }}</div><div class="text-slate-500">{{ window.source }} · {{ window.timezone }}</div></div> } }</div></div> }</div></div>
                }

                @if (wizardStep() === 5) {
                  <div class="mt-4 space-y-3">@if (structurePreview(); as preview) { <div class="grid grid-cols-2 md:grid-cols-4 gap-2"><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Jalons' : 'Milestones' }}</div><strong>{{ preview.periods.length }}</strong></div><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Dépendances' : 'Dependencies' }}</div><strong>{{ wizardDependencies.length || preview.dependencies.length }}</strong></div><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Fenêtres' : 'Windows' }}</div><strong>{{ effectiveWindows().length }}</strong></div><div class="rounded-lg border border-slate-200 bg-white p-3 text-xs"><div class="meta">{{ fr() ? 'Empreinte' : 'Fingerprint' }}</div><strong class="font-mono text-[10px] break-all">{{ preview.fingerprint }}</strong></div></div>@for (warning of preview.warnings; track warning) { <div class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">{{ warning }}</div> } } @else { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-slate-500">{{ fr() ? 'La validation nécessite une proposition prévisualisée.' : 'Validation requires a previewed proposal.' }}</div> }</div>
                }

                @if (wizardStep() === 6) {
                  <div class="mt-4 space-y-3"><div class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-xs text-amber-950"><strong>{{ fr() ? 'Confirmation transactionnelle' : 'Transactional confirmation' }}</strong><p class="mt-1">{{ fr() ? 'Cette étape appliquera la proposition avec son empreinte. Un motif est obligatoire et l’action sera auditée.' : 'This step applies the proposal with its fingerprint. A reason is required and the action is audited.' }}</p></div><label class="block"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} <span class="text-rose-600">*</span></span><textarea [(ngModel)]="wizardReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm" [placeholder]="fr() ? 'Expliquez la structure et les fenêtres à appliquer…' : 'Explain the structure and windows to apply…'"></textarea></label></div>
                }
                <div class="flex justify-between gap-2 mt-4 pt-3 border-t border-brand-100"><button type="button" (click)="wizardBack()" [disabled]="wizardStep() === 1" class="btn-secondary">{{ fr() ? 'Précédent' : 'Back' }}</button><div class="flex gap-2">@if (wizardStep() < 6) { <button type="button" (click)="wizardNext()" class="btn-primary">{{ fr() ? 'Suivant' : 'Next' }}</button> } @else { <button type="button" (click)="wizardApply()" [disabled]="!structurePreview() || !wizardReason.trim() || saving()" class="btn-primary">{{ fr() ? 'Demander la confirmation' : 'Request confirmation' }}</button> }</div></div>
              </section>

              <bbc-card [title]="fr() ? 'Structure des résultats : séquences, trimestres et annuel' : 'Results structure: sequences, terms, and annual'">
                <div class="flex items-start justify-between gap-3 mb-3">
                  <p class="text-xs text-mute leading-relaxed">{{ fr() ? 'Cette structure relie les séquences 1 à 6 aux trois trimestres et au résultat annuel. Les fenêtres héritent du trimestre ou de la session tant qu’elles ne sont pas définies ici.' : 'This structure connects sequences 1–6 to the three terms and annual result. Windows inherit from the term or session until explicitly set here.' }}</p>
                  <span class="chip bg-brand-50 text-brand-800 border border-brand-200 shrink-0">{{ fr() ? 'Géré par l’assistant ci-dessus' : 'Managed by the wizard above' }}</span>
                </div>
                @if (reportingPeriods().length) {
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                    @for (p of reportingPeriods(); track p.id) {
                      <div class="rounded-lg border border-slate-200 px-3 py-2" [class]="p.periodType === 'ANNUAL_RESULT' ? 'bg-amber-50 border-amber-200' : p.periodType === 'TERM_RESULT' ? 'bg-slate-50' : 'bg-white'">
                        <div class="flex items-center justify-between gap-2"><span class="font-semibold text-sm">{{ p.code }} · {{ p.label }}</span><span class="chip bg-white text-slate-600">{{ p.periodType }}</span></div>
                        <div class="text-xs text-mute mt-1">{{ p.startDate }} → {{ p.endDate }} · {{ p.calculationPolicy }}</div>
                        <div class="text-[11px] text-slate-500 mt-1">{{ p.bulletinPublishOpensAt ? (fr() ? 'Publication configurée' : 'Publication configured') : (fr() ? 'Publication héritée' : 'Publication inherited') }}</div>
                        @if (p.periodType === 'SEQUENCE') {
                          <div class="text-[11px] text-slate-500 mt-1">{{ p.teacherSubmissionOpensAt && p.teacherSubmissionClosesAt ? (fr() ? 'Soumission enseignants configurée' : 'Teacher submission configured') : (fr() ? 'Soumission enseignants requise' : 'Teacher submission required') }}</div>
                        } @else {
                          <div class="text-[11px] text-indigo-700 mt-1">{{ fr() ? 'Jalon calculé : saisie et soumission enseignants non applicables' : 'Computed milestone: grade entry and teacher submission do not apply' }}</div>
                        }
                        @if (effectiveWindowFor(p, 'TEACHER_SUBMISSION'); as window) {
                          <div class="mt-2 rounded-md border px-2 py-1 text-[11px]" [class]="window.source === 'EMERGENCY_OVERRIDE' ? 'border-amber-300 bg-amber-50 text-amber-900' : 'border-slate-200 bg-slate-50 text-slate-600'">
                            <span class="font-semibold">{{ window.source }}</span> · {{ window.state }}
                            @if (window.nextTransition) { <span> · {{ fr() ? 'transition' : 'next' }} {{ window.nextTransition }}</span> }
                          </div>
                        }
                        @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="editReportingPeriod(p)" class="mt-2 text-xs font-semibold text-brand-700 hover:text-brand-900">{{ fr() ? 'Configurer les fenêtres' : 'Configure windows' }}</button> }
                      </div>
                    }
                  </div>
                } @else {
                  <div class="text-sm text-mute py-3">{{ fr() ? 'Aucune structure de résultats configurée. Prévisualisez puis appliquez la structure standard.' : 'No result structure configured. Preview and apply the standard structure.' }}</div>
                }
                @if (structurePreview(); as preview) {
                  <div class="mt-3 rounded-lg border border-brand-200 bg-brand-50 p-3 text-sm">
                    <div class="font-semibold text-brand-900">{{ preview.applied ? (fr() ? 'Structure appliquée' : 'Structure applied') : (fr() ? 'Aperçu' : 'Preview') }}</div>
                    <div class="text-xs text-brand-800 mt-1">{{ preview.periods.length }} {{ fr() ? 'jalons disponibles.' : 'milestones available.' }}</div>
                    @for (warning of preview.warnings; track warning) { <div class="text-xs text-amber-800 mt-1">{{ warning }}</div> }
                  </div>
                }
              </bbc-card>

              <bbc-card [title]="fr() ? 'Calendrier et séances attendues' : 'Calendar and expected sessions'">
                <div class="mb-4 px-3 py-2.5 rounded-lg bg-sky-50 border border-sky-100 text-xs text-sky-900 leading-relaxed">
                  <strong>{{ fr() ? 'À quoi servent ces séances ?' : 'What are these sessions for?' }}</strong>
                  {{ fr()
                    ? ' Le système calcule une séance de présence attendue pour chaque jour de classe et pour chaque classe, en excluant les jours fériés. La prévisualisation ne modifie rien. La génération enregistre ou actualise ces lignes afin que les modules de présence et d’analyse sachent quelles journées étaient attendues.'
                    : ' The system calculates one expected attendance session for every teaching day and class, excluding holidays. Preview changes nothing. Generation saves or updates those rows so attendance and analytics know which school days were expected.' }}
                </div>
                <div class="space-y-2">
                  @for (d of calendarDays(); track d.dayOfWeek) {
                    <div class="grid grid-cols-[7rem_1fr_1fr_auto] gap-2 items-center">
                      <label class="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" [(ngModel)]="d.teachingDay" [disabled]="!canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" /> {{ dayLabel(d.dayOfWeek) }}</label>
                      <input type="time" [(ngModel)]="d.startTime" [disabled]="!d.teachingDay || !canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" class="field" />
                      <input type="time" [(ngModel)]="d.endTime" [disabled]="!d.teachingDay || !canManage() || s.status === 'CLOSED' || s.status === 'ARCHIVED'" class="field" />
                      @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="saveDay(d)" class="btn-secondary">{{ fr() ? 'Sauver' : 'Save' }}</button> }
                    </div>
                  }
                </div>
                <div class="flex flex-wrap gap-2 mt-4 pt-4 border-t border-slate-100">
                  <button (click)="previewCalendar()" class="btn-secondary">{{ fr() ? 'Prévisualiser la génération' : 'Preview generation' }}</button>
                  @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') { <button (click)="generateCalendar()" class="btn-primary">{{ fr() ? 'Générer les séances' : 'Generate sessions' }}</button> }
                </div>
                @if (generation(); as g) {
                  <div class="mt-3 p-3 rounded-lg border text-sm" [class]="g.dryRun ? 'bg-amber-50 border-amber-200' : 'bg-emerald-50 border-emerald-200'">
                    <div class="font-bold" [class]="g.dryRun ? 'text-amber-800' : 'text-emerald-800'">
                      {{ g.dryRun ? (fr() ? 'Simulation — aucune donnée modifiée' : 'Preview — no data changed') : (fr() ? 'Génération terminée' : 'Generation completed') }}
                    </div>
                    <div class="mt-1"><strong>{{ g.expectedRows }}</strong> {{ fr() ? 'séances attendues' : 'expected sessions' }} =
                      {{ g.teachingDates }} {{ fr() ? 'jours de classe' : 'teaching days' }} × {{ g.classes }} {{ fr() ? 'classes' : 'classes' }}.</div>
                    <div class="mt-1 text-xs text-slate-700">
                      @if (g.dryRun) {
                        {{ fr() ? g.existingRows + ' lignes sont déjà enregistrées. Cliquez sur « Générer les séances » pour les synchroniser.' : g.existingRows + ' rows already exist. Click “Generate sessions” to synchronize them.' }}
                      } @else {
                        {{ fr() ? g.insertedRows + ' lignes créées ou actualisées ; ' + g.removedFutureRows + ' anciennes lignes futures devenues inutiles supprimées.' : g.insertedRows + ' rows created or updated; ' + g.removedFutureRows + ' obsolete future rows removed.' }}
                      }
                    </div>
                    <details class="mt-2 text-[11px] text-mute">
                      <summary class="cursor-pointer">{{ fr() ? 'Détail technique' : 'Technical detail' }}</summary>
                      <div class="mt-1"><span class="font-semibold">{{ fr() ? 'Référence de configuration :' : 'Configuration reference:' }}</span> <span class="font-mono">{{ g.sourceVersion }}</span></div>
                      <div>{{ fr() ? 'Cette empreinte identifie la version exacte de la session, des jours de classe et des jours fériés utilisée pour ce calcul.' : 'This fingerprint identifies the exact session, teaching-day, and holiday configuration used for the calculation.' }}</div>
                    </details>
                  </div>
                }
              </bbc-card>
            </div>
          }
        </div>
      }

      @if (pendingState(); as transition) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="session-state-title">
            <div class="flex items-start justify-between gap-3">
              <div>
                <h3 id="session-state-title" class="text-lg font-bold text-ink">{{ stateConfirmationTitle(transition.target) }}</h3>
                <p class="text-sm text-mute mt-1">{{ transition.session.label }} · {{ statusLabel(transition.session.status) }} → {{ statusLabel(transition.target) }}</p>
              </div>
              <button (click)="cancelStateChange()" class="w-8 h-8 rounded-lg hover:bg-slate-100" [attr.aria-label]="fr() ? 'Fermer' : 'Close'">×</button>
            </div>
            <div class="mt-4 px-3 py-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-950 leading-relaxed">
              <strong>{{ fr() ? 'Conséquence :' : 'Impact:' }}</strong> {{ stateImpact(transition.target) }}
            </div>
            @if (message(); as modalMessage) { @if (!modalMessage.ok) { <div class="mt-3 px-3 py-2 rounded-lg bg-rose-50 text-sm text-rose-700">{{ modalMessage.text }}</div> } }
            <label class="block mt-4">
              <span class="text-xs font-semibold text-slate-700">{{ fr() ? 'Motif obligatoire — il sera conservé dans le journal d’audit' : 'Required reason — it will be kept in the audit trail' }}</span>
              <textarea [(ngModel)]="transitionReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm" [placeholder]="fr() ? 'Expliquez pourquoi ce changement est effectué…' : 'Explain why this change is being made…'"></textarea>
            </label>
            <div class="flex justify-end gap-2 mt-5">
              <button (click)="cancelStateChange()" class="btn-secondary">{{ fr() ? 'Annuler — ne rien changer' : 'Cancel — make no change' }}</button>
              <button (click)="confirmStateChange()" [disabled]="!transitionReason.trim() || saving()" class="btn-primary">{{ saving() ? '…' : stateConfirmationButton(transition.target) }}</button>
            </div>
          </section>
        </div>
      }

      @if (generationConfirmation()) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="generation-title">
            <h3 id="generation-title" class="text-lg font-bold text-ink">{{ fr() ? 'Générer les séances attendues ?' : 'Generate expected sessions?' }}</h3>
            <p class="text-sm text-mute mt-2">{{ selected()?.label }} · {{ selected()?.startDate }} → {{ selected()?.endDate }}</p>
            <div class="mt-4 space-y-2 text-sm text-slate-700">
              <p>{{ fr() ? 'Cette opération va enregistrer ou actualiser une ligne de séance attendue pour chaque combinaison jour de classe × classe.' : 'This operation will save or update one expected-session row for each teaching day × class combination.' }}</p>
              <p>{{ fr() ? 'Les jours fériés sont exclus. Les présences historiques déjà saisies sont conservées. Seules les lignes futures devenues inutiles peuvent être supprimées.' : 'Holidays are excluded. Existing historical attendance is preserved. Only obsolete future rows may be removed.' }}</p>
              @if (generation()?.dryRun) { <p class="font-semibold text-brand-700">{{ fr() ? 'Dernière simulation : ' + generation()!.expectedRows + ' lignes attendues.' : 'Latest preview: ' + generation()!.expectedRows + ' expected rows.' }}</p> }
            </div>
            <div class="flex justify-end gap-2 mt-5">
              <button (click)="generationConfirmation.set(false)" class="btn-secondary">{{ fr() ? 'Annuler — ne rien modifier' : 'Cancel — make no change' }}</button>
              <button (click)="confirmGeneration()" class="btn-primary">{{ fr() ? 'Confirmer la génération' : 'Confirm generation' }}</button>
            </div>
          </section>
        </div>
      }

      @if (structureConfirmation()) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="structure-title">
            <h3 id="structure-title" class="text-lg font-bold text-ink">{{ fr() ? 'Créer la structure académique standard ?' : 'Create the standard academic structure?' }}</h3>
            <p class="text-sm text-slate-700 mt-3">{{ fr() ? 'Cette action crée ou met à jour les trois trimestres, les six séquences, les trois résultats trimestriels et le résultat annuel. Les fenêtres déjà définies sont conservées.' : 'This creates or updates three terms, six sequences, three term results, and the annual result. Existing windows are preserved.' }}</p>
            <label class="block mt-4"><span class="text-xs font-semibold">{{ fr() ? 'Motif de configuration' : 'Configuration reason' }}</span><textarea [(ngModel)]="structureReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm"></textarea></label>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelStructureApply()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmStructureApply()" [disabled]="!structureReason.trim() || saving()" class="btn-primary">{{ fr() ? 'Confirmer la création' : 'Confirm creation' }}</button></div>
          </section>
        </div>
      }

      @if (editingReportingPeriod(); as p) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="period-window-title">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3 rounded-lg border border-brand-100 bg-brand-50/40 p-3">
              <label><span class="meta">{{ fr() ? 'Ouverture revue' : 'Review opens' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.reviewOpen" class="field" /></label>
<label><span class="meta">{{ fr() ? 'Cloture revue' : 'Review closes' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.reviewClose" class="field" /></label>
            </div>
            <h3 id="period-window-title" class="text-lg font-bold text-ink">{{ fr() ? 'Fenêtres du jalon ' + p.code : 'Windows for ' + p.code }}</h3>
            <p class="text-sm text-mute mt-2">{{ fr() ? 'Ces fenêtres contrôlent directement la saisie, la validation et la publication de ce résultat. Une fenêtre vide hérite du trimestre puis de la session.' : 'These windows directly control grade entry, validation, and publication for this result. An empty window inherits from the term, then the session.' }}</p>
            @if (p.periodType !== 'SEQUENCE') { <div class="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm text-indigo-950">{{ fr() ? 'Résultat calculé : les fenêtres de saisie et de soumission ne s’appliquent pas à ce jalon. Configurez uniquement la revue, validation, publication et correction.' : 'Computed result: grade entry and teacher submission windows do not apply to this milestone. Configure review, validation, publication, and correction only.' }}</div> }
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4" [class.hidden]="p.periodType !== 'SEQUENCE'">
              <label><span class="meta">{{ fr() ? 'Ouverture notes' : 'Grade entry opens' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.gradeOpen" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Clôture notes' : 'Grade entry closes' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.gradeClose" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Ouverture validation' : 'Validation opens' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.validationOpen" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Clôture validation' : 'Validation closes' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.validationClose" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Ouverture publication' : 'Publication opens' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.publishOpen" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Clôture publication' : 'Publication closes' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.publishClose" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Ouverture corrections' : 'Correction opens' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.correctionOpen" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Clôture corrections' : 'Correction closes' }}</span><input type="datetime-local" [(ngModel)]="periodWindowDraft.correctionClose" class="field" /></label>
            </div>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelReportingPeriodEdit()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="saveReportingPeriodWindows()" [disabled]="saving()" class="btn-primary">{{ saving() ? '…' : (fr() ? 'Enregistrer les fenêtres' : 'Save windows') }}</button></div>
          </section>
        </div>
      }

      @if (windowOverrideForm()) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="window-override-title">
            <h3 id="window-override-title" class="text-lg font-bold text-ink">{{ fr() ? 'Créer une dérogation de fenêtre ?' : 'Create a workflow-window override?' }}</h3>
            <p class="text-sm text-slate-700 mt-2">{{ fr() ? 'Cette ouverture exceptionnelle est limitée à 31 jours, n’efface pas la configuration normale et sera conservée dans l’audit.' : 'This exceptional opening is limited to 31 days, does not erase normal configuration, and is retained in the audit trail.' }}</p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
              <label><span class="meta">{{ fr() ? 'Action' : 'Action' }} <span class="text-rose-600">*</span></span><select [(ngModel)]="windowOverrideDraft.action" class="field"><option value="GRADE_ENTRY">{{ fr() ? 'Saisie des notes' : 'Grade entry' }}</option><option value="TEACHER_SUBMISSION">{{ fr() ? 'Soumission enseignant' : 'Teacher submission' }}</option><option value="REVIEW">{{ fr() ? 'Revue' : 'Review' }}</option><option value="VALIDATION">{{ fr() ? 'Validation' : 'Validation' }}</option><option value="PUBLICATION">{{ fr() ? 'Publication' : 'Publication' }}</option><option value="CORRECTION">{{ fr() ? 'Correction' : 'Correction' }}</option></select></label>
              <label><span class="meta">{{ fr() ? 'Jalon (vide = session)' : 'Milestone (blank = session)' }}</span><select [(ngModel)]="windowOverrideDraft.reportingPeriodId" class="field"><option [ngValue]="null">{{ fr() ? 'Toute la session' : 'Whole session' }}</option>@for (p of reportingPeriods(); track p.id) { <option [ngValue]="p.id">{{ p.code }} · {{ p.label }}</option> }</select></label>
              <label><span class="meta">{{ fr() ? 'Ouverture' : 'Opens' }} <span class="text-rose-600">*</span></span><input type="datetime-local" [(ngModel)]="windowOverrideDraft.opensAt" class="field" /></label>
              <label><span class="meta">{{ fr() ? 'Expiration' : 'Expires' }} <span class="text-rose-600">*</span></span><input type="datetime-local" [(ngModel)]="windowOverrideDraft.expiresAt" class="field" /></label>
            </div>
            <label class="block mt-3"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} <span class="text-rose-600">*</span></span><textarea [(ngModel)]="windowOverrideDraft.reason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm" [placeholder]="fr() ? 'Expliquez la correction exceptionnelle…' : 'Explain the exceptional correction…'"></textarea></label>
            <p class="text-xs text-slate-500 mt-2">{{ fr() ? 'Le fuseau utilisé est celui de la session sélectionnée.' : 'The selected session timezone will be used.' }}</p>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelWindowOverride()" class="btn-secondary">{{ fr() ? 'Annuler — ne rien changer' : 'Cancel — make no change' }}</button><button (click)="confirmWindowOverride()" [disabled]="saving() || !windowOverrideDraft.reason.trim() || !windowOverrideDraft.opensAt || !windowOverrideDraft.expiresAt" class="btn-primary">{{ saving() ? '…' : (fr() ? 'Confirmer la dérogation' : 'Confirm override') }}</button></div>
          </section>
        </div>
      }

      @if (windowOverrideRevocation(); as override) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="window-revoke-title">
            <h3 id="window-revoke-title" class="text-lg font-bold text-ink">{{ fr() ? 'Révoquer cette dérogation ?' : 'Revoke this override?' }}</h3>
            <p class="text-sm text-slate-700 mt-2">{{ override.action }} · {{ override.scope }}. {{ fr() ? 'L’action redeviendra soumise à la fenêtre configurée ou héritée. L’historique restera conservé.' : 'The action will return to its configured or inherited window. History will remain available.' }}</p>
            <label class="block mt-4"><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }} <span class="text-rose-600">*</span></span><textarea [(ngModel)]="windowOverrideReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm"></textarea></label>
            <div class="flex justify-end gap-2 mt-5"><button (click)="cancelWindowOverrideRevoke()" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button><button (click)="confirmWindowOverrideRevoke()" [disabled]="!windowOverrideReason.trim() || saving()" class="btn-primary">{{ fr() ? 'Révoquer' : 'Revoke' }}</button></div>
          </section>
        </div>
      }

      @if (pendingTermRemoval()) {
        <div class="modal-backdrop" role="presentation">
          <section class="modal-panel" role="dialog" aria-modal="true" aria-labelledby="term-removal-title">
            <h3 id="term-removal-title" class="text-lg font-bold text-ink">{{ fr() ? 'Retirer cette période ?' : 'Remove this term?' }}</h3>
            <p class="text-sm text-slate-700 mt-3">{{ fr() ? 'La période et ses fenêtres de saisie/publication seront supprimées. Cette action peut rendre des données académiques associées indisponibles.' : 'The term and its entry/publication windows will be deleted. This may make associated academic data unavailable.' }}</p>
            <label class="block mt-4"><span class="text-xs font-semibold">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><textarea [(ngModel)]="termRemovalReason" rows="3" class="w-full mt-1.5 px-3 py-2 border border-slate-200 rounded-lg text-sm"></textarea></label>
            <div class="flex justify-end gap-2 mt-5">
              <button (click)="cancelTermRemoval()" class="btn-secondary">{{ fr() ? 'Annuler — conserver la période' : 'Cancel — keep term' }}</button>
              <button (click)="confirmTermRemoval()" [disabled]="!termRemovalReason.trim()" class="btn-primary">{{ fr() ? 'Confirmer le retrait' : 'Confirm removal' }}</button>
            </div>
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .field { width:100%; height:2.5rem; padding:0 .75rem; border:1px solid #e2e8f0; border-radius:.5rem; font-size:.875rem; background:white; }
    .field:focus { outline:none; border-color:#818cf8; }
    .field:disabled { background:#f8fafc; color:#94a3b8; }
    .btn-primary,.btn-secondary { height:2.25rem; padding:0 .9rem; border-radius:.5rem; font-size:.75rem; font-weight:700; display:inline-flex; align-items:center; gap:.35rem; }
    .btn-primary { background:#3453b8; color:white; }
    .btn-primary:disabled { opacity:.45; }
    .btn-secondary { background:white; border:1px solid #e2e8f0; color:#334155; }
    .chip { font-size:.65rem; font-weight:700; padding:.2rem .5rem; border-radius:999px; }
    .meta { color:#64748b; text-transform:uppercase; letter-spacing:.04em; font-size:.65rem; }
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
  protected readiness = signal<SessionReadinessView | null>(null);
  protected effectiveWindows = signal<EffectiveWindowView[]>([]);
  protected windowOverrides = signal<WindowOverrideView[]>([]);
  protected structurePreview = signal<StandardStructureView | null>(null);
  protected wizardStep = signal(1);
  protected wizardSteps = [1, 2, 3, 4, 5, 6];
  protected wizardDependencies: StructureDependencyView[] = [];
  protected wizardReason = '';
  protected workflowActions: WorkflowAction[] = ['GRADE_ENTRY', 'TEACHER_SUBMISSION', 'REVIEW', 'VALIDATION', 'PUBLICATION', 'CORRECTION'];
  protected loading = signal(true);
  protected saving = signal(false);
  protected showSessionForm = signal(false);
  protected editingId = signal<string | null>(null);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  protected generation = signal<GenerationResult | null>(null);
  protected actionPermissions = signal<Record<string, boolean>>({});
  protected canManage = computed(() => this.actionPermissions()['SESSION_MANAGE'] ?? this.auth.can('settings', 'write'));
  protected sessionDraft: AcademicSessionUpsert = this.blankSession();
  protected sessionWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '', teacherOpen: '', teacherClose: '' };
  protected termDraft: AcademicTermUpsert = { code: '', label: '', sequenceNo: 1, startDate: '', endDate: '' };
  protected editingTermId = signal<string | null>(null);
  protected termWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '' };
  protected pendingState = signal<{ session: AcademicSessionView; target: string } | null>(null);
  protected transitionReason = '';
  protected generationConfirmation = signal(false);
  protected pendingTermRemoval = signal<string | null>(null);
  protected termRemovalReason = '';
  protected structureConfirmation = signal(false);
  protected structureReason = '';
  protected editingReportingPeriod = signal<AcademicReportingPeriodView | null>(null);
  protected periodWindowDraft = { gradeOpen: '', gradeClose: '', reviewOpen: '', reviewClose: '', validationOpen: '', validationClose: '', publishOpen: '', publishClose: '', correctionOpen: '', correctionClose: '' };
  protected windowOverrideForm = signal(false);
  protected windowOverrideRevocation = signal<WindowOverrideView | null>(null);
  protected windowOverrideReason = '';
  protected windowOverrideDraft: WindowOverrideUpsert = { action: 'GRADE_ENTRY', scope: 'SESSION', reason: '', opensAt: '', expiresAt: '', reportingPeriodId: null };

  constructor() { this.reload(); this.api.actionPermissions().subscribe((p) => this.actionPermissions.set(p)); }

  protected select(s: AcademicSessionView): void { this.selectedId.set(s.id); this.loadCalendar(s.id); this.loadReportingPeriods(s.id); this.generation.set(null); this.message.set(null); }
  protected newSession(): void { this.editingId.set(null); this.sessionDraft = this.blankSession(); this.sessionWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '', teacherOpen: '', teacherClose: '' }; this.showSessionForm.set(true); }
  protected editSession(s: AcademicSessionView): void {
    this.editingId.set(s.id);
    this.sessionDraft = { ...s };
    this.sessionWindows = { gradeOpen: this.localDateTime(s.gradeEntryOpensAt), gradeClose: this.localDateTime(s.gradeEntryClosesAt), publishOpen: this.localDateTime(s.bulletinPublishOpensAt), publishClose: this.localDateTime(s.bulletinPublishClosesAt), teacherOpen: this.localDateTime(s.teacherSubmissionOpensAt), teacherClose: this.localDateTime(s.teacherSubmissionClosesAt) };
    this.showSessionForm.set(true);
  }
  protected cancelSession(): void { this.showSessionForm.set(false); this.editingId.set(null); }
  protected saveSession(): void {
    this.saving.set(true); this.message.set(null);
    const body: AcademicSessionUpsert = { ...this.sessionDraft,
      gradeEntryOpensAt: this.instant(this.sessionWindows.gradeOpen), gradeEntryClosesAt: this.instant(this.sessionWindows.gradeClose),
      bulletinPublishOpensAt: this.instant(this.sessionWindows.publishOpen), bulletinPublishClosesAt: this.instant(this.sessionWindows.publishClose),
      teacherSubmissionOpensAt: this.instant(this.sessionWindows.teacherOpen), teacherSubmissionClosesAt: this.instant(this.sessionWindows.teacherClose), timezone: 'Africa/Douala' };
    const req = this.editingId() ? this.api.updateSession(this.editingId()!, body) : this.api.createSession(body);
    req.subscribe({ next: (s) => { this.saving.set(false); this.showSessionForm.set(false); this.reload(s.id); this.message.set({ ok: true, text: this.fr() ? 'Session enregistrée.' : 'Session saved.' }); }, error: (e) => this.fail(e) });
  }
  protected requestStateChange(session: AcademicSessionView, target: string): void {
    this.transitionReason = '';
    this.message.set(null);
    this.pendingState.set({ session, target });
  }
  protected cancelStateChange(): void { this.pendingState.set(null); this.transitionReason = ''; }
  protected confirmStateChange(): void {
    const transition = this.pendingState();
    const reason = this.transitionReason.trim();
    if (!transition || !reason) return;
    this.saving.set(true); this.message.set(null);
    this.api.changeSessionState(transition.session.id, transition.target, reason, transition.session.version).subscribe({
      next: () => { this.saving.set(false); this.cancelStateChange(); this.reload(transition.session.id); this.message.set({ ok: true, text: this.fr() ? 'Statut de la session mis à jour.' : 'Session status updated.' }); },
      error: (e) => this.fail(e),
    });
  }
  protected stateConfirmationTitle(target: string): string { const labels: Record<string,string> = this.fr()
    ? { OPEN:'Ouvrir cette session ?', CLOSED:'Clôturer cette session ?', ARCHIVED:'Archiver cette session ?' }
    : { OPEN:'Open this session?', CLOSED:'Close this session?', ARCHIVED:'Archive this session?' }; return labels[target] ?? (this.fr() ? 'Changer le statut ?' : 'Change status?'); }
  protected stateConfirmationButton(target: string): string { const labels: Record<string,string> = this.fr()
    ? { OPEN:'Confirmer l’ouverture', CLOSED:'Confirmer la clôture', ARCHIVED:'Confirmer l’archivage' }
    : { OPEN:'Confirm opening', CLOSED:'Confirm closing', ARCHIVED:'Confirm archiving' }; return labels[target] ?? (this.fr() ? 'Confirmer' : 'Confirm'); }
  protected stateImpact(target: string): string { const labels: Record<string,string> = this.fr()
    ? {
      OPEN:'la session deviendra active et courante. Les modules opérationnels l’utiliseront par défaut pour les inscriptions, le calendrier, les notes et les documents.',
      CLOSED:'la session ne pourra plus recevoir de modifications ordinaires. Vérifiez que les inscriptions, périodes et publications sont terminées avant de continuer.',
      ARCHIVED:'la session sera retirée des opérations courantes et conservée uniquement comme historique. Elle ne sera plus la session courante.',
    } : {
      OPEN:'the session becomes active and current. Operational modules will use it by default for enrollment, calendars, grades, and documents.',
      CLOSED:'the session will no longer accept ordinary changes. Make sure enrollments, terms, and publications are complete before continuing.',
      ARCHIVED:'the session leaves current operations and remains available only as history. It will no longer be current.',
    }; return labels[target] ?? '';
  }
  protected editTerm(t: AcademicTermView): void {
    this.editingTermId.set(t.id);
    this.termDraft = { ...t };
    this.termWindows = {
      gradeOpen: this.localDateTime(t.gradeEntryOpensAt), gradeClose: this.localDateTime(t.gradeEntryClosesAt),
      publishOpen: this.localDateTime(t.bulletinPublishOpensAt), publishClose: this.localDateTime(t.bulletinPublishClosesAt),
    };
  }
  protected cancelTermEdit(): void { this.resetTermDraft(); }
  protected saveTerm(): void {
    const s = this.selected(); if (!s) return;
    const body: AcademicTermUpsert = { ...this.termDraft,
      gradeEntryOpensAt: this.instant(this.termWindows.gradeOpen), gradeEntryClosesAt: this.instant(this.termWindows.gradeClose),
      bulletinPublishOpensAt: this.instant(this.termWindows.publishOpen), bulletinPublishClosesAt: this.instant(this.termWindows.publishClose) };
    const request = this.editingTermId() ? this.api.updateTerm(this.editingTermId()!, body) : this.api.addTerm(s.id, body);
    request.subscribe({ next: () => { this.resetTermDraft(s.terms.length + 2); this.reload(s.id); }, error: (e) => this.fail(e) });
  }
  protected requestTermRemoval(id: string): void { this.termRemovalReason = ''; this.pendingTermRemoval.set(id); }
  protected cancelTermRemoval(): void { this.pendingTermRemoval.set(null); this.termRemovalReason = ''; }
  protected confirmTermRemoval(): void { const id = this.pendingTermRemoval(); const reason = this.termRemovalReason.trim(); if (!id || !reason) return;
    this.api.deleteTerm(id, reason).subscribe({ next: () => { this.cancelTermRemoval(); this.reload(this.selectedId() ?? undefined); }, error: (e) => this.fail(e) }); }
  protected previewStandardStructure(): void { const s = this.selected(); if (!s) return; this.api.previewStandardStructure(s.id).subscribe({ next: (p) => { this.structurePreview.set(p); this.wizardDependencies = p.dependencies.map((dependency) => ({ ...dependency })); }, error: (e) => this.fail(e) }); }
  protected wizardStepLabel(step: number): string {
    const labels = this.fr()
      ? ['Session / trimestres', 'Dates des résultats', 'Dépendances / COMP', 'Fenêtres', 'Validation / diff', 'Confirmation']
      : ['Session / terms', 'Result dates', 'Dependencies / COMP', 'Windows', 'Validation / diff', 'Confirmation'];
    return labels[step - 1] ?? '';
  }
  protected setWizardStep(step: number): void { this.wizardStep.set(Math.max(1, Math.min(6, step))); if (step >= 2 && !this.structurePreview()) this.previewStandardStructure(); }
  protected wizardNext(): void { const next = Math.min(6, this.wizardStep() + 1); this.setWizardStep(next); }
  protected wizardBack(): void { this.wizardStep.set(Math.max(1, this.wizardStep() - 1)); }
  protected wizardApply(): void { const proposal = this.wizardProposal(); if (!proposal || !this.wizardReason.trim()) return; this.structurePreview.set(proposal); this.structureReason = this.wizardReason.trim(); this.structureConfirmation.set(true); }
  private wizardProposal(): StandardStructureView | null {
    const proposal = this.structurePreview();
    return proposal ? { ...proposal, dependencies: this.wizardDependencies.length ? this.wizardDependencies.map((dependency) => ({ ...dependency })) : proposal.dependencies } : null;
  }
  protected requestStructureApply(): void { this.structureReason = ''; this.structureConfirmation.set(true); }
  protected cancelStructureApply(): void { this.structureConfirmation.set(false); this.structureReason = ''; }
  protected confirmStructureApply(): void { const s = this.selected(); const reason = this.structureReason.trim(); if (!s || !reason) return; this.saving.set(true); const proposal = this.structurePreview(); this.api.applyStandardStructure(s.id, reason, proposal?.fingerprint, proposal).subscribe({ next: (p) => { this.saving.set(false); this.structurePreview.set(p); this.cancelStructureApply(); this.reload(s.id); this.message.set({ ok: true, text: this.fr() ? 'Structure académique créée.' : 'Academic structure created.' }); }, error: (e) => this.fail(e) }); }
  protected editReportingPeriod(p: AcademicReportingPeriodView): void { this.editingReportingPeriod.set(p); this.periodWindowDraft = { gradeOpen: this.localDateTime(p.gradeEntryOpensAt), gradeClose: this.localDateTime(p.gradeEntryClosesAt), reviewOpen: this.localDateTime(p.reviewOpensAt), reviewClose: this.localDateTime(p.reviewClosesAt), validationOpen: this.localDateTime(p.validationOpensAt), validationClose: this.localDateTime(p.validationClosesAt), publishOpen: this.localDateTime(p.bulletinPublishOpensAt), publishClose: this.localDateTime(p.bulletinPublishClosesAt), correctionOpen: this.localDateTime(p.correctionOpensAt), correctionClose: this.localDateTime(p.correctionClosesAt) }; }
  protected cancelReportingPeriodEdit(): void { this.editingReportingPeriod.set(null); }
  protected saveReportingPeriodWindows(): void {
    const s = this.selected();
    const p = this.editingReportingPeriod();
    if (!s || !p) return;
    this.saving.set(true);
    const body: AcademicReportingPeriodUpsert = {
      code: p.code,
      label: p.label,
      periodType: p.periodType,
      academicTermId: p.academicTermId,
      displayOrder: p.displayOrder,
      startDate: p.startDate,
      endDate: p.endDate,
      gradeEntryOpensAt: this.instant(this.periodWindowDraft.gradeOpen),
      gradeEntryClosesAt: this.instant(this.periodWindowDraft.gradeClose),
      reviewOpensAt: this.instant(this.periodWindowDraft.reviewOpen),
      reviewClosesAt: this.instant(this.periodWindowDraft.reviewClose),
      validationOpensAt: this.instant(this.periodWindowDraft.validationOpen),
      validationClosesAt: this.instant(this.periodWindowDraft.validationClose),
      bulletinPublishOpensAt: this.instant(this.periodWindowDraft.publishOpen),
      bulletinPublishClosesAt: this.instant(this.periodWindowDraft.publishClose),
      correctionOpensAt: this.instant(this.periodWindowDraft.correctionOpen),
      correctionClosesAt: this.instant(this.periodWindowDraft.correctionClose),
      calculationPolicy: p.calculationPolicy,
      status: p.status,
      version: p.version
    };
    this.api.updateReportingPeriod(s.id, p.id, body).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelReportingPeriodEdit();
        this.loadReportingPeriods(s.id);
        this.message.set({ ok: true, text: this.fr() ? 'Fenêtres du jalon enregistrées.' : 'Milestone windows saved.' });
      },
      error: (e) => this.fail(e)
    });
  }
  protected saveDay(d: CalendarDayView): void {
    const s = this.selected(); if (!s) return;
    this.api.saveCalendarDay(s.id, { dayOfWeek: d.dayOfWeek, teachingDay: d.teachingDay, startTime: d.startTime, endTime: d.endTime, version: d.version })
      .subscribe({ next: () => this.loadCalendar(s.id), error: (e) => this.fail(e) });
  }
  protected previewCalendar(): void { this.runGeneration(true); }
  protected generateCalendar(): void { this.generationConfirmation.set(true); }
  protected confirmGeneration(): void { this.generationConfirmation.set(false); this.runGeneration(false); }
  protected dayLabel(day: number): string { return (this.fr() ? ['Lun','Mar','Mer','Jeu','Ven','Sam','Dim'] : ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'])[day - 1] ?? '?'; }
  protected statusLabel(s: string): string { const fr: Record<string,string> = { DRAFT:'Brouillon',OPEN:'Ouverte',CLOSED:'Clôturée',ARCHIVED:'Archivée' }; return this.fr() ? (fr[s] ?? s) : s[0] + s.slice(1).toLowerCase(); }
  protected statusClass(s: string): string { return s === 'OPEN' ? 'text-emerald-700' : s === 'DRAFT' ? 'text-amber-700' : 'text-slate-500'; }
  protected effectiveWindowFor(period: AcademicReportingPeriodView, action: WorkflowAction): EffectiveWindowView | undefined {
    return this.effectiveWindows().find((window) => window.periodId === period.id && window.action === action);
  }
  protected openWindowOverride(): void {
    this.windowOverrideDraft = { action: 'GRADE_ENTRY', scope: 'PERIOD', reason: '', opensAt: '', expiresAt: '', reportingPeriodId: this.reportingPeriods()[0]?.id ?? null };
    this.windowOverrideForm.set(true);
  }
  protected cancelWindowOverride(): void { this.windowOverrideForm.set(false); this.windowOverrideDraft = { action: 'GRADE_ENTRY', scope: 'SESSION', reason: '', opensAt: '', expiresAt: '', reportingPeriodId: null }; }
  protected confirmWindowOverride(): void {
    const session = this.selected();
    const opensAt = this.instant(this.windowOverrideDraft.opensAt);
    const expiresAt = this.instant(this.windowOverrideDraft.expiresAt);
    const reason = this.windowOverrideDraft.reason.trim();
    if (!session || !opensAt || !expiresAt || !reason) return;
    this.saving.set(true);
    this.api.createWindowOverride(session.id, { ...this.windowOverrideDraft, scope: this.windowOverrideDraft.reportingPeriodId ? 'PERIOD' : 'SESSION', reason, opensAt, expiresAt }).subscribe({
      next: () => { this.saving.set(false); this.cancelWindowOverride(); this.loadReportingPeriods(session.id); this.message.set({ ok: true, text: this.fr() ? 'Dérogation enregistrée et auditée.' : 'Override saved and audited.' }); },
      error: (e) => this.fail(e),
    });
  }
  protected requestWindowOverrideRevoke(override: WindowOverrideView): void { this.windowOverrideReason = ''; this.windowOverrideRevocation.set(override); }
  protected cancelWindowOverrideRevoke(): void { this.windowOverrideRevocation.set(null); this.windowOverrideReason = ''; }
  protected confirmWindowOverrideRevoke(): void {
    const override = this.windowOverrideRevocation();
    const reason = this.windowOverrideReason.trim();
    if (!override || !reason) return;
    this.saving.set(true);
    this.api.revokeWindowOverride(override.id, reason).subscribe({
      next: () => { this.saving.set(false); this.cancelWindowOverrideRevoke(); this.loadReportingPeriods(this.selectedId() ?? ''); this.message.set({ ok: true, text: this.fr() ? 'Dérogation révoquée.' : 'Override revoked.' }); },
      error: (e) => this.fail(e),
    });
  }

  private reload(selectId?: string): void {
    this.loading.set(true);
    this.api.listSessions().subscribe({ next: (rows) => { this.sessions.set(rows); const id = selectId ?? this.selectedId() ?? rows.find((s) => s.current)?.id ?? rows[0]?.id ?? null; this.selectedId.set(id); this.loading.set(false); if (id) { this.loadCalendar(id); this.loadReportingPeriods(id); } this.context.load(true); }, error: (e) => { this.loading.set(false); this.fail(e); } });
  }
  private loadCalendar(id: string): void { this.api.calendarDays(id).subscribe({ next: (d) => this.calendarDays.set(d.map((x) => ({ ...x }))), error: (e) => this.fail(e) }); }
  private loadReportingPeriods(id: string): void {
    this.api.reportingPeriods(id).subscribe({
      next: (p) => {
        const periods = p.map((period) => ({ ...period, label: cleanDisplay(period.label) }));
        this.reportingPeriods.set(periods);
        const actions: WorkflowAction[] = ['GRADE_ENTRY', 'TEACHER_SUBMISSION', 'REVIEW', 'VALIDATION', 'PUBLICATION', 'CORRECTION'];
        const requests = periods.flatMap((period) => actions.map((action) => this.api.effectiveWindow(id, period.id, action)));
        forkJoin(requests).subscribe({ next: (windows) => this.effectiveWindows.set(windows), error: () => this.effectiveWindows.set([]) });
      },
      error: (e) => this.fail(e)
    });
    this.api.windowOverrides(id).subscribe({ next: (rows) => this.windowOverrides.set(rows), error: () => this.windowOverrides.set([]) });
    this.api.readiness(id).subscribe({ next: (r) => this.readiness.set(r), error: () => this.readiness.set(null) });
  }
  private runGeneration(dryRun: boolean): void { const s = this.selected(); if (!s) return; this.api.generateCalendar(s.id, s.startDate, s.endDate, dryRun).subscribe({ next: (g) => this.generation.set(g), error: (e) => this.fail(e) }); }
  private resetTermDraft(sequenceNo = 1): void { this.editingTermId.set(null); this.termDraft = { code: '', label: '', sequenceNo, startDate: '', endDate: '' }; this.termWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '' }; }
  private blankSession(): AcademicSessionUpsert { const year = new Date().getFullYear(); return { code: `${year}-${year + 1}`, label: `Session ${year}-${year + 1}`, startDate: `${year}-09-01`, endDate: `${year + 1}-07-31`, status: 'DRAFT', current: false }; }
  private instant(value: string): string | null { return value ? new Date(value).toISOString() : null; }
  private localDateTime(value: string | null): string { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  private fail(e: any): void { this.saving.set(false); this.message.set({ ok: false, text: typeof e?.error?.message === 'string' ? e.error.message : (this.fr() ? 'Opération impossible.' : 'Operation failed.') }); }
}
