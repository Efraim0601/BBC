import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { AcademicContextService } from '../../core/academic-context.service';
import {
  AcademicSessionUpsert, AcademicSessionView, AcademicTermUpsert, AcademicTermView,
  AcademicReportingPeriodView, AcademicReportingPeriodUpsert, CalendarDayView, FoundationApi, GenerationResult, StandardStructureView,
} from '../../core/foundation.api';
import { CardComponent, EmptyComponent, IconComponent } from '../../core/ui';

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
  imports: [FormsModule, CardComponent, EmptyComponent, IconComponent],
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

              <bbc-card [title]="fr() ? 'Structure des résultats : séquences, trimestres et annuel' : 'Results structure: sequences, terms, and annual'">
                <div class="flex items-start justify-between gap-3 mb-3">
                  <p class="text-xs text-mute leading-relaxed">{{ fr() ? 'Cette structure relie les séquences 1 à 6 aux trois trimestres et au résultat annuel. Les fenêtres héritent du trimestre ou de la session tant qu’elles ne sont pas définies ici.' : 'This structure connects sequences 1–6 to the three terms and annual result. Windows inherit from the term or session until explicitly set here.' }}</p>
                  @if (canManage() && s.status !== 'CLOSED' && s.status !== 'ARCHIVED') {
                    <div class="flex gap-2 shrink-0">
                      <button (click)="previewStandardStructure()" class="btn-secondary">{{ fr() ? 'Prévisualiser la structure' : 'Preview structure' }}</button>
                      <button (click)="requestStructureApply()" class="btn-primary">{{ fr() ? 'Créer la structure standard' : 'Create standard structure' }}</button>
                    </div>
                  }
                </div>
                @if (reportingPeriods().length) {
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                    @for (p of reportingPeriods(); track p.id) {
                      <div class="rounded-lg border border-slate-200 px-3 py-2" [class]="p.periodType === 'ANNUAL_RESULT' ? 'bg-amber-50 border-amber-200' : p.periodType === 'TERM_RESULT' ? 'bg-slate-50' : 'bg-white'">
                        <div class="flex items-center justify-between gap-2"><span class="font-semibold text-sm">{{ p.code }} · {{ p.label }}</span><span class="chip bg-white text-slate-600">{{ p.periodType }}</span></div>
                        <div class="text-xs text-mute mt-1">{{ p.startDate }} → {{ p.endDate }} · {{ p.calculationPolicy }}</div>
                        <div class="text-[11px] text-slate-500 mt-1">{{ p.bulletinPublishOpensAt ? (fr() ? 'Publication configurée' : 'Publication configured') : (fr() ? 'Publication héritée' : 'Publication inherited') }}</div>
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
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
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
  protected structurePreview = signal<StandardStructureView | null>(null);
  protected loading = signal(true);
  protected saving = signal(false);
  protected showSessionForm = signal(false);
  protected editingId = signal<string | null>(null);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  protected generation = signal<GenerationResult | null>(null);
  protected actionPermissions = signal<Record<string, boolean>>({});
  protected canManage = computed(() => this.actionPermissions()['SESSION_MANAGE'] ?? this.auth.can('settings', 'write'));
  protected sessionDraft: AcademicSessionUpsert = this.blankSession();
  protected sessionWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '' };
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

  constructor() { this.reload(); this.api.actionPermissions().subscribe((p) => this.actionPermissions.set(p)); }

  protected select(s: AcademicSessionView): void { this.selectedId.set(s.id); this.loadCalendar(s.id); this.loadReportingPeriods(s.id); this.generation.set(null); this.message.set(null); }
  protected newSession(): void { this.editingId.set(null); this.sessionDraft = this.blankSession(); this.sessionWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '' }; this.showSessionForm.set(true); }
  protected editSession(s: AcademicSessionView): void {
    this.editingId.set(s.id);
    this.sessionDraft = { ...s };
    this.sessionWindows = { gradeOpen: this.localDateTime(s.gradeEntryOpensAt), gradeClose: this.localDateTime(s.gradeEntryClosesAt), publishOpen: this.localDateTime(s.bulletinPublishOpensAt), publishClose: this.localDateTime(s.bulletinPublishClosesAt) };
    this.showSessionForm.set(true);
  }
  protected cancelSession(): void { this.showSessionForm.set(false); this.editingId.set(null); }
  protected saveSession(): void {
    this.saving.set(true); this.message.set(null);
    const body: AcademicSessionUpsert = { ...this.sessionDraft,
      gradeEntryOpensAt: this.instant(this.sessionWindows.gradeOpen), gradeEntryClosesAt: this.instant(this.sessionWindows.gradeClose),
      bulletinPublishOpensAt: this.instant(this.sessionWindows.publishOpen), bulletinPublishClosesAt: this.instant(this.sessionWindows.publishClose) };
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
  protected previewStandardStructure(): void { const s = this.selected(); if (!s) return; this.api.previewStandardStructure(s.id).subscribe({ next: (p) => this.structurePreview.set(p), error: (e) => this.fail(e) }); }
  protected requestStructureApply(): void { this.structureReason = ''; this.structureConfirmation.set(true); }
  protected cancelStructureApply(): void { this.structureConfirmation.set(false); this.structureReason = ''; }
  protected confirmStructureApply(): void { const s = this.selected(); const reason = this.structureReason.trim(); if (!s || !reason) return; this.saving.set(true); this.api.applyStandardStructure(s.id, reason).subscribe({ next: (p) => { this.saving.set(false); this.structurePreview.set(p); this.cancelStructureApply(); this.reload(s.id); this.message.set({ ok: true, text: this.fr() ? 'Structure académique créée.' : 'Academic structure created.' }); }, error: (e) => this.fail(e) }); }
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

  private reload(selectId?: string): void {
    this.loading.set(true);
    this.api.listSessions().subscribe({ next: (rows) => { this.sessions.set(rows); const id = selectId ?? this.selectedId() ?? rows.find((s) => s.current)?.id ?? rows[0]?.id ?? null; this.selectedId.set(id); this.loading.set(false); if (id) { this.loadCalendar(id); this.loadReportingPeriods(id); } this.context.load(true); }, error: (e) => { this.loading.set(false); this.fail(e); } });
  }
  private loadCalendar(id: string): void { this.api.calendarDays(id).subscribe({ next: (d) => this.calendarDays.set(d.map((x) => ({ ...x }))), error: (e) => this.fail(e) }); }
  private loadReportingPeriods(id: string): void { this.api.reportingPeriods(id).subscribe({ next: (p) => this.reportingPeriods.set(p.map((period) => ({ ...period, label: cleanDisplay(period.label) }))), error: (e) => this.fail(e) }); }
  private runGeneration(dryRun: boolean): void { const s = this.selected(); if (!s) return; this.api.generateCalendar(s.id, s.startDate, s.endDate, dryRun).subscribe({ next: (g) => this.generation.set(g), error: (e) => this.fail(e) }); }
  private resetTermDraft(sequenceNo = 1): void { this.editingTermId.set(null); this.termDraft = { code: '', label: '', sequenceNo, startDate: '', endDate: '' }; this.termWindows = { gradeOpen: '', gradeClose: '', publishOpen: '', publishClose: '' }; }
  private blankSession(): AcademicSessionUpsert { const year = new Date().getFullYear(); return { code: `${year}-${year + 1}`, label: `Session ${year}-${year + 1}`, startDate: `${year}-09-01`, endDate: `${year + 1}-07-31`, status: 'DRAFT', current: false }; }
  private instant(value: string): string | null { return value ? new Date(value).toISOString() : null; }
  private localDateTime(value: string | null): string { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  private fail(e: any): void { this.saving.set(false); this.message.set({ ok: false, text: typeof e?.error?.message === 'string' ? e.error.message : (this.fr() ? 'Opération impossible.' : 'Operation failed.') }); }
}
