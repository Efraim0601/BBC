import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { FeeTypeView, FeeTypesApi } from './fee-types.api';
import {
  ActivationPreview, CopyPreview, ElectionView, FeePlansApi, ImpactPreview, InstallmentPreview,
  PlanApiError, PlanContext, PlanLineRequest, PlanView, ResolutionView, StudentContextView,
  TemplateLineRequest, TemplateView,
} from './plans.api';

type WorkspaceTab = 'plans' | 'editor' | 'copy' | 'student';

interface TemplateDraft {
  id: string | null;
  version: number | null;
  code: string;
  nameFr: string;
  nameEn: string;
  sourceSessionId: string | null;
  lines: TemplateLineRequest[];
}

interface TimelineRow extends TemplateLineRequest {
  calculatedAmount: number;
  dueLabel: string;
  finalAdjustmentMinor: number;
}

const blankTemplateLine = (order: number): TemplateLineRequest => ({
  lineOrder: order, labelFr: '', labelEn: '', allocationType: 'PERCENTAGE', amountMinor: null,
  percentageBasisPoints: 10000, dueRuleType: 'SESSION_START_OFFSET', absoluteDueDate: null,
  dueOffsetDays: 0, academicTermId: null,
});

const blankTemplate = (sessionId: string | null): TemplateDraft => ({
  id: null, version: null, code: '', nameFr: '', nameEn: '', sourceSessionId: sessionId,
  lines: [blankTemplateLine(1)],
});

@Component({
  selector: 'bbc-finance-plans',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="plans-shell mx-auto max-w-7xl space-y-5">
      <header class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div class="mb-2 text-xs font-bold uppercase tracking-[.16em] text-brand-600">Finance / BAY-45 · V61</div>
            <h1 class="text-2xl font-extrabold text-ink">{{ fr() ? 'Plans de frais versionnés' : 'Versioned fee plans' }}</h1>
            <p class="mt-1 max-w-3xl text-sm text-slate-500">Scope by academic session, level, subsystem and class. A class plan takes priority over a level plan.</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <a routerLink="/finance/fee-types" class="btn-secondary">{{ fr() ? 'Catalogue des frais' : 'Fee catalogue' }}</a>
            <a routerLink="/finance/accounting" class="btn-secondary">{{ fr() ? 'Comptabilité' : 'Accounting' }}</a>
            <button class="btn-primary" type="button" [disabled]="!canWrite() || loading()" (click)="createDraft()">+ {{ fr() ? 'Nouveau brouillon' : 'New draft' }}</button>
          </div>
        </div>
        <div class="mt-5 grid gap-3 border-t border-slate-100 pt-4 md:grid-cols-3">
          <label class="field-label">{{ fr() ? 'Session académique' : 'Academic session' }} *
            <select class="field" [ngModel]="sessionId()" (ngModelChange)="selectSession($event)">
              <option value="">{{ fr() ? 'Choisir une session' : 'Choose a session' }}</option>
              @for (item of context().sessions; track item.id) { <option [value]="item.id">{{ item.label }} · {{ item.code }}</option> }
            </select>
          </label>
          <label class="field-label">{{ fr() ? 'Niveau / sous-système' : 'Level / subsystem' }} *
            <div class="flex gap-2"><input class="field" [class.input-error]="scopeError()" [ngModel]="level()" (ngModelChange)="level.set($event)" placeholder="primary"><input class="field uppercase" [ngModel]="subsystem()" (ngModelChange)="subsystem.set($event)" placeholder="FR"></div>
            @if (scopeError()) { <span class="field-error">{{ scopeError() }}</span> }
          </label>
          <label class="field-label">{{ fr() ? 'Classe (optionnelle)' : 'Class (optional)' }}
            <select class="field" [ngModel]="classId()" (ngModelChange)="classId.set($event)">
              <option value="">{{ fr() ? 'Portée niveau' : 'Level scope' }}</option>
              @for (item of context().classes; track item.id) { <option [value]="item.id" [disabled]="item.level !== level() || item.subsystem !== subsystem()">{{ item.name }} · {{ item.level }} / {{ item.subsystem }}</option> }
            </select>
            <span class="field-help">{{ classId() ? 'Class override selected.' : 'No class override.' }}</span>
          </label>
        </div>
      </header>

      @if (error()) {
        <div role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800"><div class="font-bold">{{ error() }}</div>@if (correlationId()) { <div class="mt-1 text-xs">Correlation: {{ correlationId() }}</div> }<button class="mt-2 font-bold underline" type="button" (click)="reload()">Retry</button></div>
      }
      @if (success()) { <div role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{{ success() }}</div> }
      @if (loading()) {
        <div class="grid gap-4 md:grid-cols-3" aria-label="Loading plans"><div class="h-28 animate-pulse rounded-2xl bg-slate-100"></div><div class="h-28 animate-pulse rounded-2xl bg-slate-100"></div><div class="h-28 animate-pulse rounded-2xl bg-slate-100"></div></div>
      } @else {
        <nav class="flex gap-2 overflow-x-auto rounded-xl border border-slate-200 bg-white p-2 shadow-sm" aria-label="Fee plan workspace">
          <button type="button" class="tab-button" [class.tab-active]="tab() === 'plans'" (click)="tab.set('plans')">Plans & coverage</button>
          <button type="button" class="tab-button" [class.tab-active]="tab() === 'editor'" [disabled]="!selectedPlan()" (click)="tab.set('editor')">Editor & installments</button>
          <button type="button" class="tab-button" [class.tab-active]="tab() === 'copy'" (click)="tab.set('copy')">Copy session</button>
          <button type="button" class="tab-button" [class.tab-active]="tab() === 'student'" (click)="tab.set('student')">Student elections & overrides</button>
        </nav>

        @if (tab() === 'plans') {
          <section class="space-y-4">
            @if (!sessionId()) {
              <div class="empty-state"><h2>Choose a session to begin</h2><p>Plans are always filtered by session and school scope.</p></div>
            } @else if (!visiblePlans().length) {
              <div class="empty-state"><div class="text-3xl">□</div><h2>No plan for this scope</h2><p>Create a level plan or a class override. Nothing applies before activation.</p><button class="btn-primary mt-4" type="button" [disabled]="!canWrite()" (click)="createDraft()">Create the first draft</button></div>
            } @else {
              <div class="grid gap-4 lg:grid-cols-2">
                @for (plan of visiblePlans(); track plan.id) {
                  <article class="plan-card" [class.selected-card]="selectedPlan()?.id === plan.id">
                    <div class="flex items-start justify-between gap-3"><div><div class="font-mono text-xs font-extrabold text-brand-700">{{ plan.scopeType }} · v{{ plan.planVersionNo }}</div><h2 class="mt-1 text-lg font-extrabold text-ink">{{ plan.level }} / {{ plan.subsystem }}{{ className(plan.schoolClassId) ? ' · ' + className(plan.schoolClassId) : '' }}</h2></div><span class="status-pill" [class]="statusClass(plan.lifecycle)">{{ plan.lifecycle }}</span></div>
                    <div class="mt-4 grid grid-cols-2 gap-3 text-sm"><div class="detail-field"><div class="detail-label">Inheritance</div><div class="font-bold">{{ plan.inheritanceSource === 'CLASS_OVERRIDE' ? 'Class override' : 'Inherited level plan' }}</div></div><div class="detail-field"><div class="detail-label">Effective status</div><div class="font-bold">{{ plan.effectiveStatus }}</div></div><div class="detail-field"><div class="detail-label">Total</div><div class="font-extrabold">{{ money(plan.totalMinor, plan.currency) }}</div></div><div class="detail-field"><div class="detail-label">Lines</div><div class="font-extrabold">{{ plan.lines.length }} · {{ plan.optionalLineCount }} optional</div></div></div>
                    <div class="mt-4 flex flex-wrap gap-2"><button class="btn-secondary" type="button" (click)="selectPlan(plan)">Open</button>@if (plan.lifecycle === 'DRAFT') { <button class="btn-primary" type="button" [disabled]="!canWrite()" (click)="openActivation(plan)">Review activation</button> }</div>
                  </article>
                }
              </div>
            }
          </section>
        }

        @if (tab() === 'editor') {
          <section class="space-y-5">
            @if (selectedPlan(); as plan) {
              <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div class="text-xs font-bold uppercase tracking-wide text-brand-600">{{ plan.academicSessionId }} · {{ plan.scopeType }} · {{ plan.inheritanceSource }}</div><h2 class="mt-1 text-xl font-extrabold">{{ plan.level }} / {{ plan.subsystem }} · v{{ plan.planVersionNo }}</h2><p class="mt-1 text-sm text-slate-500">{{ plan.effectiveFrom }} → {{ plan.effectiveTo || '∞' }} · {{ plan.effectiveStatus }}</p><span class="status-pill mt-3" [class]="statusClass(plan.lifecycle)">{{ plan.lifecycle }}</span></div>
              <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
                <div class="space-y-5">
                  <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="flex items-center justify-between gap-3"><div><h3 class="text-lg font-extrabold">Plan lines</h3><p class="text-sm text-slate-500">Only active/effective catalogue revisions are offered.</p></div><button class="btn-primary" type="button" [disabled]="!canWrite() || plan.lifecycle !== 'DRAFT'" (click)="addLine()">Add line</button></div>
                    <div class="mt-4 grid gap-3 border-t border-slate-100 pt-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_130px_180px_auto_auto]">
                      <label class="field-label">Fee type *<select class="field" [class.input-error]="lineError()" [ngModel]="lineDraft().feeTypeId" (ngModelChange)="chooseFeeType($event)"><option value="">Choose active type</option>@for (item of activeFeeTypes(); track item.id) { <option [value]="item.id">{{ item.code }} · {{ feeName(item) }}</option> }</select></label>
                      <label class="field-label">Amount XAF *<input class="field" type="number" min="0" [ngModel]="lineDraft().amountMinor" (ngModelChange)="setLineAmount($event)"></label>
                      <label class="field-label">Installment template<select class="field" [ngModel]="lineDraft().installmentTemplateId" (ngModelChange)="setLineTemplate($event)"><option value="">One payment</option>@for (template of templates(); track template.id) { <option [value]="template.id">{{ template.code }} · {{ template.nameEn }}</option> }</select></label>
                      <label class="field-label">Priority<input class="field" type="number" min="0" [ngModel]="lineDraft().priority" (ngModelChange)="setLinePriority($event)"></label>
                      <label class="field-label flex items-center gap-2 pt-7"><input type="checkbox" [ngModel]="lineDraft().mandatory" (ngModelChange)="setLineMandatory($event)">Required</label>
                    </div>
                    @if (lineError()) { <div class="field-error mt-2">{{ lineError() }}</div> }
                    @if (plan.lines.length) { <div class="mt-5 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[760px] text-sm"><thead class="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th class="px-3 py-2">Fee type / revision</th><th class="px-3 py-2">Amount</th><th class="px-3 py-2">Rule</th><th class="px-3 py-2">Installments</th><th class="px-3 py-2">Actions</th></tr></thead><tbody>@for (line of plan.lines; track line.id) { <tr class="border-t border-slate-100"><td class="px-3 py-3 font-mono text-xs">{{ feeCode(line.feeTypeId) }} · rev {{ line.feeTypeRevisionId.slice(0, 8) }}</td><td class="px-3 py-3 font-extrabold">{{ money(line.amountMinor, line.currency) }}</td><td class="px-3 py-3">{{ line.mandatory ? 'Required' : 'Optional' }}</td><td class="px-3 py-3">{{ templateName(line.installmentTemplateId) }}<button class="ml-2 font-bold text-brand-700 underline" type="button" (click)="previewInstallments(line)">Timeline</button></td><td class="px-3 py-3"><button class="font-bold text-rose-700 underline" type="button" [disabled]="!canWrite() || plan.lifecycle !== 'DRAFT'" (click)="removeLine(line)">Remove</button></td></tr> }</tbody></table></div> } @else { <div class="empty-mini mt-4">No lines. Add the first fee to this draft.</div> }
                  </div>

                  <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between"><div><h3 class="text-lg font-extrabold">Installment template editor</h3><p class="text-sm text-slate-500">Create, select, edit and validate reusable fixed or percentage schedules.</p></div><select class="field max-w-xs" [ngModel]="selectedTemplateId()" (ngModelChange)="selectTemplate($event)" aria-label="Select installment template"><option value="NEW">New template</option>@for (template of templates(); track template.id) { <option [value]="template.id">{{ template.code }} · {{ template.nameEn }}</option> }</select></div>
                    <div class="mt-4 grid gap-3 border-t border-slate-100 pt-4 md:grid-cols-3"><label class="field-label">Code *<input class="field" [class.input-error]="!templateDraft().code.trim()" [ngModel]="templateDraft().code" (ngModelChange)="setTemplateField('code', $event)" placeholder="TERM_1"><span class="field-error" *ngIf="!templateDraft().code.trim()">Required.</span></label><label class="field-label">French name *<input class="field" [class.input-error]="!templateDraft().nameFr.trim()" [ngModel]="templateDraft().nameFr" (ngModelChange)="setTemplateField('nameFr', $event)"><span class="field-error" *ngIf="!templateDraft().nameFr.trim()">Required.</span></label><label class="field-label">English name *<input class="field" [class.input-error]="!templateDraft().nameEn.trim()" [ngModel]="templateDraft().nameEn" (ngModelChange)="setTemplateField('nameEn', $event)"><span class="field-error" *ngIf="!templateDraft().nameEn.trim()">Required.</span></label></div>
                    @if (templateError()) { <div class="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{{ templateError() }}</div> }
                    <div class="mt-4 overflow-x-auto rounded-xl border border-slate-200"><table class="w-full min-w-[920px] text-sm"><thead class="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th class="px-2 py-2">Order</th><th class="px-2 py-2">Label</th><th class="px-2 py-2">Rule</th><th class="px-2 py-2">Amount / percentage</th><th class="px-2 py-2">Due rule</th><th class="px-2 py-2">Actions</th></tr></thead><tbody>@for (row of templateDraft().lines; track $index; let i = $index) { <tr class="border-t border-slate-100 align-top"><td class="px-2 py-2"><input class="field w-20" type="number" min="1" [ngModel]="row.lineOrder" (ngModelChange)="setTemplateLine(i, 'lineOrder', toNumber($event))"></td><td class="px-2 py-2"><input class="field" [class.input-error]="!row.labelEn.trim()" [ngModel]="row.labelEn" (ngModelChange)="setTemplateLine(i, 'labelEn', $event)" placeholder="First installment"><input class="field mt-1" [ngModel]="row.labelFr" (ngModelChange)="setTemplateLine(i, 'labelFr', $event)" placeholder="Première échéance">@if (!row.labelEn.trim() || !row.labelFr.trim()) { <span class="field-error">Both labels are required.</span> }</td><td class="px-2 py-2"><select class="field" [ngModel]="row.allocationType" (ngModelChange)="setTemplateAllocation(i, $event)"><option value="PERCENTAGE">Percentage</option><option value="FIXED">Fixed XAF</option></select></td><td class="px-2 py-2">@if (row.allocationType === 'FIXED') { <input class="field w-32" type="number" min="0" [ngModel]="row.amountMinor ?? 0" (ngModelChange)="setTemplateLine(i, 'amountMinor', toNumber($event))" aria-label="Fixed amount"> } @else { <div class="flex items-center gap-1"><input class="field w-24" type="number" min="0" max="100" step="0.01" [ngModel]="(row.percentageBasisPoints || 0) / 100" (ngModelChange)="setTemplatePercentage(i, $event)" aria-label="Percentage"><span>%</span></div> }</td><td class="px-2 py-2"><select class="field" [ngModel]="row.dueRuleType" (ngModelChange)="setTemplateDueRule(i, $event)"><option value="SESSION_START_OFFSET">Session start + offset</option><option value="ABSOLUTE_DATE">Absolute date</option></select>@if (row.dueRuleType === 'ABSOLUTE_DATE') { <input class="field mt-1" type="date" [ngModel]="row.absoluteDueDate" (ngModelChange)="setTemplateLine(i, 'absoluteDueDate', $event)"> } @else { <input class="field mt-1 w-28" type="number" [ngModel]="row.dueOffsetDays ?? 0" (ngModelChange)="setTemplateLine(i, 'dueOffsetDays', toNumber($event))" placeholder="Days"><span class="field-help">Days after session start</span> }</td><td class="px-2 py-2"><div class="flex gap-2"><button class="font-bold text-brand-700 underline" type="button" [disabled]="i === 0" (click)="moveTemplateLine(i, -1)">Up</button><button class="font-bold text-brand-700 underline" type="button" [disabled]="i === templateDraft().lines.length - 1" (click)="moveTemplateLine(i, 1)">Down</button><button class="font-bold text-rose-700 underline" type="button" [disabled]="templateDraft().lines.length === 1" (click)="removeTemplateLine(i)">Remove</button></div></td></tr> }</tbody></table></div>
                    <div class="mt-3 flex flex-wrap items-center gap-3"><button class="btn-secondary" type="button" (click)="addTemplateLine()">Add installment</button><span class="text-sm text-slate-600">{{ templateAllocationSummary() }}</span><span class="text-sm" [class.text-rose-700]="templatePercentageTotal() !== 10000 && templateUsesPercentage()" [class.text-emerald-700]="templatePercentageTotal() === 10000 && templateUsesPercentage()">Percentage total: {{ templatePercentageTotal() / 100 }}%</span></div>
                    @if (templateMismatchNotice()) { <div class="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{{ templateMismatchNotice() }}</div> }
                    <div class="mt-4 flex flex-wrap gap-2"><button class="btn-primary" type="button" [disabled]="!canWrite() || !templateCanSave() || templateSaving()" (click)="saveTemplate()">{{ templateSaving() ? 'Saving…' : (templateDraft().id ? 'Save template changes' : 'Create template') }}</button>@if (templateDraft().id) { <button class="btn-secondary text-rose-700" type="button" [disabled]="!canWrite() || templateSaving()" (click)="deleteTemplate()">Delete template</button> }</div>
                  </div>
                </div>
                <aside class="space-y-5"><div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 class="font-extrabold">Live plan total</h3><div class="mt-3 text-3xl font-extrabold text-brand-700">{{ money(plan.totalMinor, plan.currency) }}</div><p class="text-sm text-slate-500">{{ plan.lines.length }} line(s) · {{ plan.optionalLineCount }} optional</p><div class="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">Integer XAF only. Percentage rows are rounded down and the exact residual is placed on the final percentage installment.</div></div><div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 class="font-extrabold">Installment timeline</h3><p class="mt-1 text-sm text-slate-500">Preview uses the amount currently entered for the new line.</p>@if (!templateDraft().lines.length) { <div class="empty-mini mt-3">Add an installment to see the timeline.</div> } @else { <ol class="timeline mt-4">@for (row of templateTimeline(); track $index) { <li class="timeline-item"><div><div class="font-bold">{{ row.labelEn || 'Unnamed installment' }}</div><div class="text-xs text-slate-500">{{ row.dueLabel }}</div></div><div class="text-right"><div class="font-extrabold">{{ money(row.calculatedAmount, plan.currency) }}</div>@if (row.finalAdjustmentMinor) { <div class="text-xs font-bold text-amber-700">Final adjustment {{ row.finalAdjustmentMinor > 0 ? '+' : '' }}{{ row.finalAdjustmentMinor }}</div> }</div></li> }</ol><div class="mt-3 border-t border-slate-100 pt-3 text-sm font-bold">Timeline total: {{ money(timelineTotal(), plan.currency) }} / {{ money(templateBaseAmount(), plan.currency) }}</div> }</div>@if (installmentPreview(); as preview) { <div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-5"><h3 class="font-extrabold">Saved-line API preview</h3><p class="mt-1 text-sm">Final adjustment: {{ preview.finalAdjustmentMinor }} XAF</p>@for (row of preview.lines; track $index) { <div class="mt-2 flex justify-between text-sm"><span>{{ row.labelEn }} · {{ row.dueDate || 'relative date' }}</span><b>{{ money(row.amountMinor, plan.currency) }}</b></div> }</div> }</aside>
              </div>
            }
          </section>
        }

        @if (tab() === 'copy') {
          <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 class="text-xl font-extrabold">Copy from a previous session</h2><p class="mt-1 text-sm text-slate-500">Compare revisions, dates, amounts, uncovered classes, existing drafts and blockers before applying.</p><div class="mt-5 grid gap-4 md:grid-cols-4"><label class="field-label">Source plan *<select class="field" [ngModel]="copySourceId()" (ngModelChange)="copySourceId.set($event)"><option value="">Choose a plan</option>@for (plan of allPlans(); track plan.id) { <option [value]="plan.id">{{ plan.level }} / {{ plan.subsystem }} · v{{ plan.planVersionNo }} · {{ plan.lifecycle }}</option> }</select></label><label class="field-label">Target session *<select class="field" [ngModel]="copyTargetSessionId()" (ngModelChange)="copyTargetSessionId.set($event)"><option value="">Choose a session</option>@for (item of context().sessions; track item.id) { <option [value]="item.id">{{ item.label }}</option> }</select></label><label class="field-label">Target class (optional)<select class="field" [ngModel]="copyTargetClassId()" (ngModelChange)="copyTargetClassId.set($event)"><option value="">Keep source scope</option>@for (item of context().classes; track item.id) { <option [value]="item.id">{{ item.name }} · {{ item.level }} / {{ item.subsystem }}</option> }</select></label><label class="field-label">Merge mode *<select class="field" [ngModel]="copyMode()" (ngModelChange)="copyMode.set($event)"><option value="FILL_MISSING_ONLY">Fill missing only</option><option value="REPLACE_TARGET_DRAFTS">Replace target drafts</option><option value="CREATE_NEW_VERSION">Create new draft version</option></select></label></div><div class="mt-5 flex flex-wrap gap-2"><button class="btn-secondary" type="button" [disabled]="!copySourceId() || !copyTargetSessionId()" (click)="previewCopy()">Compare</button><button class="btn-primary" type="button" [disabled]="!copyPreview() || copyPreview()!.blockers.length || !canWrite()" (click)="applyCopy()">Apply copy</button></div>@if (copySuccess()) { <div class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{{ copySuccess() }}</div> }@if (copyPreview(); as preview) { <div class="mt-5 grid gap-3 md:grid-cols-2"><div class="rounded-xl border border-slate-200 p-4"><h3 class="font-bold">Revision changes</h3>@if (preview.changedRevisions.length || preview.changedAmounts.length) { <ul class="mt-2 list-disc pl-5 text-sm">@for (item of preview.changedRevisions; track item) { <li>{{ item }}</li> }@for (item of preview.changedAmounts; track item) { <li>{{ item }}</li> }</ul> } @else { <p class="mt-2 text-sm text-slate-500">No revision or amount changes detected.</p> }<div class="mt-3 text-sm font-bold">{{ preview.dateShift }}</div></div><div class="rounded-xl border border-slate-200 p-4"><h3 class="font-bold">Coverage and target drafts</h3>@if (preview.missingClasses.length) { <div class="mt-2 font-semibold text-amber-800">Missing class coverage</div><ul class="list-disc pl-5 text-sm">@for (item of preview.missingClasses; track item) { <li>{{ item }}</li> }</ul> } @else { <p class="mt-2 text-sm text-slate-500">No uncovered class scope reported.</p> }@if (preview.existingTargetDrafts.length) { <div class="mt-3 font-semibold">Existing target drafts</div><ul class="list-disc pl-5 text-sm">@for (item of preview.existingTargetDrafts; track item) { <li>{{ item }}</li> }</ul> } @else { <p class="mt-2 text-sm text-slate-500">No target draft exists.</p> }</div><div class="rounded-xl border p-4 md:col-span-2" [class.border-rose-300]="preview.blockers.length" [class.border-emerald-300]="!preview.blockers.length"><h3 class="font-bold">Blockers · selected mode: {{ preview.mergeMode }}</h3>@if (preview.blockers.length) { <ul class="mt-2 list-disc pl-5 text-sm text-rose-700">@for (item of preview.blockers; track item) { <li>{{ item }}</li> }</ul> } @else { <p class="mt-2 text-sm text-emerald-700">Ready to apply. Active plans are never replaced and posted charges are not mutated.</p> }</div></div> }</section>
        }

        @if (tab() === 'student') {
          <section class="grid gap-5 xl:grid-cols-[340px_minmax(0,1fr)]"><div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 class="text-xl font-extrabold">Student context</h2><p class="mt-1 text-sm text-slate-500">Search by matricule or name, then select an enrollment snapshot. No UUID entry is required.</p><label class="field-label mt-5">Search student<input class="field" [ngModel]="studentQuery()" (ngModelChange)="studentQuery.set($event)" (keyup.enter)="searchStudents()" placeholder="Name or matricule"></label><button class="btn-secondary mt-3" type="button" [disabled]="studentLoading()" (click)="searchStudents()">{{ studentLoading() ? 'Searching…' : 'Search enrollments' }}</button>@if (!studentContexts().length && !studentLoading()) { <div class="empty-mini mt-4">Search for a student to review elections and overrides.</div> }@else { <label class="field-label mt-4">Enrollment context<select class="field" [ngModel]="enrollmentId()" (ngModelChange)="selectStudentContext($event)"><option value="">Choose a student enrollment</option>@for (item of studentContexts(); track item.enrollmentId) { <option [value]="item.enrollmentId">{{ item.matricule }} · {{ item.studentName }} · {{ item.className || item.level }} · {{ item.sessionLabel }}</option> }</select></label> }@if (selectedStudentContext(); as student) { <div class="mt-4 rounded-xl border border-brand-200 bg-brand-50 p-3 text-sm"><div class="font-extrabold">{{ student.studentName }} · {{ student.matricule }}</div><div>{{ student.sessionLabel }} · {{ student.className || student.level }} / {{ student.subsystem }}</div><div class="text-xs text-slate-500">Enrollment snapshot: {{ student.enrollmentStatus }}</div></div> }</div><div class="space-y-5"><div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 class="text-xl font-extrabold">Resolved plan and optional fees</h2>@if (studentError()) { <div class="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{{ studentError() }}</div> }@if (resolution(); as result) { <div class="mt-3 rounded-xl border border-slate-200 p-4"><div class="text-xs font-bold uppercase text-slate-500">{{ result.source }}</div>@if (result.plan) { <div class="mt-1 text-lg font-extrabold">{{ result.plan.level }} / {{ result.plan.subsystem }} · v{{ result.plan.planVersionNo }}</div><p class="text-sm text-emerald-700">This is the plan used for future charges; class scope wins over level scope.</p><div class="mt-4 overflow-x-auto rounded-lg border"><table class="w-full min-w-[680px] text-sm"><thead class="bg-slate-50 text-left"><tr><th class="px-3 py-2">Fee</th><th class="px-3 py-2">Amount</th><th class="px-3 py-2">Election</th><th class="px-3 py-2">Action</th></tr></thead><tbody>@for (line of optionalLines(result.plan); track line.id) { <tr class="border-t"><td class="px-3 py-2">{{ feeCode(line.feeTypeId) }}</td><td class="px-3 py-2">{{ money(line.amountMinor, line.currency) }}</td><td class="px-3 py-2">{{ electionStatus(line.id) }}</td><td class="px-3 py-2"><button class="font-bold text-brand-700 underline" type="button" [disabled]="!canWrite()" (click)="setElection(line.id, 'ACCEPTED')">Accept</button><button class="ml-3 font-bold text-rose-700 underline" type="button" [disabled]="!canWrite()" (click)="setElection(line.id, 'DECLINED')">Decline</button></td></tr> }@if (!optionalLines(result.plan).length) { <tr><td class="px-3 py-3 text-slate-500" colspan="4">No optional fee lines on this plan.</td></tr> }</tbody></table></div> } @else { <div class="mt-2 font-bold text-rose-700">{{ result.blocker || 'NO_ACTIVE_FEE_PLAN' }}</div><p class="text-sm text-slate-500">No fee is generated without an explicit active plan.</p> }</div> } @else { <div class="empty-mini mt-4">Select an enrollment context to resolve its plan.</div> }</div>
                  @if (resolution()?.plan) { <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 class="text-xl font-extrabold">Request or review override</h2><div class="mt-4 grid gap-3 md:grid-cols-2"><label class="field-label">Plan line *<select class="field" [ngModel]="overrideLineId()" (ngModelChange)="overrideLineId.set($event)"><option value="">Choose a fee line</option>@for (line of resolution()!.plan!.lines; track line.id) { <option [value]="line.id">{{ feeCode(line.feeTypeId) }} · {{ money(line.amountMinor, line.currency) }}</option> }</select></label><label class="field-label">Override type *<select class="field" [ngModel]="overrideType()" (ngModelChange)="overrideType.set($event)"><option value="AMOUNT">Fixed amount</option><option value="DISCOUNT">Percentage discount</option><option value="EXEMPTION">Exemption</option></select></label>@if (overrideType() === 'AMOUNT') { <label class="field-label">New amount XAF *<input class="field" type="number" min="0" [ngModel]="overrideAmount()" (ngModelChange)="overrideAmount.set(toNumber($event))"></label> } @if (overrideType() === 'DISCOUNT') { <label class="field-label">Discount % *<input class="field" type="number" min="0" max="100" step="0.01" [ngModel]="overridePercentage() / 100" (ngModelChange)="overridePercentage.set(toBasisPoints($event))"></label> }<label class="field-label md:col-span-2">Reason *<textarea class="field min-h-20" [ngModel]="overrideReason()" (ngModelChange)="overrideReason.set($event)" placeholder="Explain the approved business reason"></textarea></label><label class="field-label">Effective from *<input class="field" type="date" [ngModel]="overrideFrom()" (ngModelChange)="overrideFrom.set($event)"></label><label class="field-label">Effective to<input class="field" type="date" [ngModel]="overrideTo()" (ngModelChange)="overrideTo.set($event)"></label></div>@if (overrideError()) { <div class="field-error mt-3">{{ overrideError() }}</div> }<div class="mt-4 flex flex-wrap gap-2"><button class="btn-secondary" type="button" [disabled]="!overrideLineId()" (click)="previewImpact()">Preview impact</button><button class="btn-primary" type="button" [disabled]="!canWrite() || !overrideCanSubmit()" (click)="requestOverride()">Request override</button></div>@if (impactPreview(); as impact) { <div class="mt-4 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm"><div class="font-bold">Impact preview</div><div class="mt-1">{{ money(impact.baseAmountMinor, 'XAF') }} → {{ money(impact.adjustedAmountMinor, 'XAF') }} ({{ impact.deltaMinor >= 0 ? '+' : '' }}{{ money(impact.deltaMinor, 'XAF') }})</div><p class="mt-1">{{ impact.explanation }}</p>@for (item of impact.blockers; track item) { <div class="text-rose-700">{{ item }}</div> }</div> }<h3 class="mt-6 font-extrabold">Override history</h3>@if (!overrides().length) { <div class="empty-mini mt-3">No override requests for this enrollment.</div> } @else { <div class="mt-3 space-y-2">@for (item of overrides(); track item.id) { <div class="rounded-xl border border-slate-200 p-3 text-sm"><div class="flex flex-wrap items-center justify-between gap-2"><b>{{ item.overrideType }} · {{ item.status }}</b><span>{{ item.effectiveFrom }}{{ item.effectiveTo ? ' → ' + item.effectiveTo : '' }}</span></div><p class="mt-1">{{ item.reason }}</p>@if (item.status === 'REQUESTED') { <div class="mt-2 flex gap-3"><button class="font-bold text-emerald-700 underline" type="button" [disabled]="!canWrite()" (click)="decideOverride(item, true)">Approve</button><button class="font-bold text-rose-700 underline" type="button" [disabled]="!canWrite()" (click)="decideOverride(item, false)">Reject</button></div> }</div> }</div> }</div> }
                </div></section>
        }
      }
    </div>

    @if (activationTarget(); as plan) { <div class="modal-backdrop" role="presentation" (click)="closeActivation()"><section class="modal-card" role="dialog" aria-modal="true" aria-labelledby="activation-title" (click)="$event.stopPropagation()"><div class="flex items-start justify-between"><div><div class="text-xs font-bold uppercase text-brand-600">Activation review</div><h2 id="activation-title" class="mt-1 text-xl font-extrabold">{{ plan.level }} / {{ plan.subsystem }} · v{{ plan.planVersionNo }}</h2></div><button type="button" class="btn-secondary" (click)="closeActivation()" aria-label="Close">×</button></div>@if (activationLoading()) { <div class="mt-5 h-24 animate-pulse rounded-xl bg-slate-100"></div> } @else if (activation(); as review) { <div class="mt-5 grid gap-3 sm:grid-cols-3"><div class="detail-field"><div class="detail-label">Enrollments</div><div class="font-extrabold">{{ review.affectedEnrollmentCount }}</div></div><div class="detail-field"><div class="detail-label">Optional fees</div><div class="font-extrabold">{{ review.optionalFeeCount }}</div></div><div class="detail-field"><div class="detail-label">Charge boundary</div><div class="text-xs font-bold">{{ review.chargeImpact }}</div></div></div>@if (review.blockers.length) { <div class="mt-4 rounded-xl border border-rose-300 bg-rose-50 p-4 text-sm text-rose-800"><div class="font-bold">Activation blocked</div><ul class="mt-2 list-disc pl-5">@for (item of review.blockers; track item) { <li>{{ item }}</li> }</ul></div> } @else { <div class="mt-4 rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800">Ready: the previous version retires atomically; no posted amount changes.</div> }<div class="mt-5 flex justify-end gap-2"><button class="btn-secondary" type="button" (click)="closeActivation()">Cancel</button><button class="btn-primary" type="button" [disabled]="review.blockers.length > 0 || !canWrite()" (click)="activate(plan)">Activate</button></div> }</section></div> }
  `,
  styleUrl: './finance-plans.scss',
})
export class FinancePlansComponent {
  protected api = inject(FeePlansApi);
  protected feeApi = inject(FeeTypesApi);
  protected auth = inject(AuthService);
  protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected tab = signal<WorkspaceTab>('plans');
  protected loading = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected correlationId = signal<string | null>(null);
  protected scopeError = signal<string | null>(null);
  protected lineError = signal<string | null>(null);
  protected templateError = signal<string | null>(null);
  protected studentError = signal<string | null>(null);
  protected context = signal<PlanContext>({ sessions: [], classes: [], plans: [] });
  protected feeTypes = signal<FeeTypeView[]>([]);
  protected templates = signal<TemplateView[]>([]);
  protected sessionId = signal('');
  protected level = signal('');
  protected subsystem = signal('');
  protected classId = signal('');
  protected selectedPlan = signal<PlanView | null>(null);
  protected lineDraft = signal({ feeTypeId: '', amountMinor: 0, priority: 0, mandatory: true, installmentTemplateId: '' });
  protected activationTarget = signal<PlanView | null>(null);
  protected activation = signal<ActivationPreview | null>(null);
  protected activationLoading = signal(false);
  protected copySourceId = signal('');
  protected copyTargetSessionId = signal('');
  protected copyTargetClassId = signal('');
  protected copyMode = signal('FILL_MISSING_ONLY');
  protected copyPreview = signal<CopyPreview | null>(null);
  protected copySuccess = signal<string | null>(null);
  protected studentQuery = signal('');
  protected studentContexts = signal<StudentContextView[]>([]);
  protected selectedStudentContext = signal<StudentContextView | null>(null);
  protected studentLoading = signal(false);
  protected enrollmentId = signal('');
  protected resolution = signal<ResolutionView | null>(null);
  protected elections = signal<ElectionView[]>([]);
  protected overrides = signal<import('./plans.api').OverrideView[]>([]);
  protected overrideLineId = signal('');
  protected overrideType = signal('AMOUNT');
  protected overrideAmount = signal(0);
  protected overridePercentage = signal(0);
  protected overrideReason = signal('');
  protected overrideFrom = signal('');
  protected overrideTo = signal('');
  protected overrideError = signal<string | null>(null);
  protected impactPreview = signal<ImpactPreview | null>(null);
  protected templateDraft = signal<TemplateDraft>(blankTemplate(null));
  protected selectedTemplateId = signal('NEW');
  protected templateSaving = signal(false);
  protected installmentPreview = signal<InstallmentPreview | null>(null);

  protected activeFeeTypes = computed(() => this.feeTypes().filter((item) => item.lifecycle === 'ACTIVE' && !!item.currentRevision));
  protected allPlans = computed(() => this.context().plans);
  protected visiblePlans = computed(() => this.context().plans.filter((p) => !!this.sessionId() && p.academicSessionId === this.sessionId() && p.level === this.level() && p.subsystem === this.subsystem() && (this.classId() ? p.schoolClassId === this.classId() : !p.schoolClassId)));
  protected templateUsesPercentage = computed(() => this.templateDraft().lines.some((line) => line.allocationType === 'PERCENTAGE'));
  protected templatePercentageTotal = computed(() => this.templateDraft().lines.filter((line) => line.allocationType === 'PERCENTAGE').reduce((sum, line) => sum + (line.percentageBasisPoints || 0), 0));
  protected templateFixedTotal = computed(() => this.templateDraft().lines.filter((line) => line.allocationType === 'FIXED').reduce((sum, line) => sum + (line.amountMinor || 0), 0));
  protected templateTimeline = computed<TimelineRow[]>(() => {
    const base = this.templateBaseAmount();
    let allocated = 0;
    const rows = this.templateDraft().lines;
    return rows.map((line, index) => {
      const isLastPercentage = line.allocationType === 'PERCENTAGE' && index === rows.map((r) => r.allocationType).lastIndexOf('PERCENTAGE');
      const proportional = line.allocationType === 'FIXED' ? (line.amountMinor || 0) : Math.floor(base * (line.percentageBasisPoints || 0) / 10000);
      const calculatedAmount = isLastPercentage ? base - allocated : proportional;
      const finalAdjustmentMinor = isLastPercentage ? calculatedAmount - proportional : 0;
      allocated += calculatedAmount;
      return { ...line, calculatedAmount, finalAdjustmentMinor, dueLabel: this.dueLabel(line) };
    });
  });
  protected timelineTotal = computed(() => this.templateTimeline().reduce((sum, row) => sum + row.calculatedAmount, 0));

  constructor() { this.reload(); }

  /** Plan mutations require the server-authoritative plan-draft action. */
  protected canWrite(): boolean { return this.auth.canAction('FEE_PLAN_DRAFT'); }
  protected selectSession(id: string): void { this.sessionId.set(id); const session = this.context().sessions.find((item) => item.id === id); if (session && !this.level()) this.level.set('primary'); this.reload(id); }
  protected reload(sessionId = this.sessionId()): void {
    this.loading.set(true); this.error.set(null);
    forkJoin({ context: this.api.context(sessionId || undefined), fees: this.feeApi.list(undefined, 'ACTIVE'), templates: this.api.templates() }).subscribe({ next: (value) => { this.context.set(value.context); this.feeTypes.set(value.fees); this.templates.set(value.templates); this.loading.set(false); if (!this.sessionId()) this.sessionId.set(value.context.sessions[0]?.id || ''); this.selectFirst(); }, error: (err: PlanApiError) => { this.loading.set(false); this.applyError(err); } });
  }
  private selectFirst(): void { const item = this.visiblePlans()[0] ?? null; this.selectedPlan.set(item); if (item) { this.level.set(item.level); this.subsystem.set(item.subsystem); this.classId.set(item.schoolClassId || ''); } }
  protected selectPlan(plan: PlanView): void { this.selectedPlan.set(plan); this.level.set(plan.level); this.subsystem.set(plan.subsystem); this.classId.set(plan.schoolClassId || ''); this.tab.set('editor'); }
  protected createDraft(): void { const session = this.context().sessions.find((item) => item.id === this.sessionId()) || this.context().sessions[0]; if (!session) { this.scopeError.set('Choose an academic session.'); return; } if (!this.level().trim() || !this.subsystem().trim()) { this.scopeError.set('Level and subsystem are required.'); return; } this.api.create({ academicSessionId: session.id, scopeType: this.classId() ? 'CLASS' : 'LEVEL', level: this.level().trim(), subsystem: this.subsystem().trim(), schoolClassId: this.classId() || null, effectiveFrom: session.startDate, effectiveTo: session.endDate, currency: 'XAF' }).subscribe({ next: (plan) => { this.success.set('Draft created.'); this.context.update((c) => ({ ...c, plans: [plan, ...c.plans] })); this.selectedPlan.set(plan); this.tab.set('editor'); }, error: (err: PlanApiError) => this.applyError(err) }); }
  protected chooseFeeType(id: string): void { const item = this.feeTypes().find((fee) => fee.id === id); this.lineDraft.update((draft) => ({ ...draft, feeTypeId: id, amountMinor: item?.currentRevision?.defaultAmountMinor ?? 0 })); }
  protected setLineAmount(value: number | string): void { this.lineDraft.update((draft) => ({ ...draft, amountMinor: Number(value) || 0 })); }
  protected setLinePriority(value: number | string): void { this.lineDraft.update((draft) => ({ ...draft, priority: Number(value) || 0 })); }
  protected setLineMandatory(value: boolean): void { this.lineDraft.update((draft) => ({ ...draft, mandatory: !!value })); }
  protected setLineTemplate(value: string): void { this.lineDraft.update((draft) => ({ ...draft, installmentTemplateId: value || '' })); }
  protected addLine(): void { const plan = this.selectedPlan(); const draft = this.lineDraft(); const fee = this.feeTypes().find((item) => item.id === draft.feeTypeId); if (!plan || !fee?.currentRevision) { this.lineError.set('Select an active fee type.'); return; } const template = draft.installmentTemplateId ? this.templates().find((item) => item.id === draft.installmentTemplateId) : null; if (template && template.lines.length && template.lines.every((line) => line.allocationType === 'FIXED') && template.lines.reduce((sum, line) => sum + (line.amountMinor || 0), 0) !== draft.amountMinor) { this.lineError.set(`The fixed template totals ${template.lines.reduce((sum, line) => sum + (line.amountMinor || 0), 0)} XAF but this line is ${draft.amountMinor} XAF. Adjust the line or choose a percentage template.`); return; } this.lineError.set(null); const request: PlanLineRequest = { feeTypeId: fee.id, feeTypeRevisionId: fee.currentRevision.id, amountMinor: draft.amountMinor, currency: fee.currentRevision.defaultCurrency, mandatory: draft.mandatory, refundable: fee.currentRevision.refundable, priority: draft.priority, lineOrder: plan.lines.length + 1, installmentTemplateId: draft.installmentTemplateId || null, version: plan.version }; this.api.addLine(plan.id, request).subscribe({ next: (updated) => { this.replacePlan(updated); this.lineDraft.set({ feeTypeId: '', amountMinor: 0, priority: 0, mandatory: true, installmentTemplateId: '' }); this.success.set('Line added.'); }, error: (err: PlanApiError) => this.applyError(err) }); }
  protected removeLine(line: PlanView['lines'][number]): void { const plan = this.selectedPlan(); if (!plan) return; this.api.removeLine(plan.id, line.id, plan.version).subscribe({ next: (updated) => { this.replacePlan(updated); this.success.set('Line removed.'); }, error: (err: PlanApiError) => this.applyError(err) }); }
  protected previewInstallments(line: PlanView['lines'][number]): void { const plan = this.selectedPlan(); if (!plan || !line.installmentTemplateId) { this.installmentPreview.set(null); this.success.set('This line is configured as one payment.'); return; } this.api.installmentPreview(plan.id, line.id).subscribe({ next: (value) => this.installmentPreview.set(value), error: (err: PlanApiError) => this.applyError(err) }); }
  private replacePlan(updated: PlanView): void { this.selectedPlan.set(updated); this.context.update((c) => ({ ...c, plans: c.plans.map((p) => p.id === updated.id ? updated : p) })); }

  protected selectTemplate(id: string): void { this.templateError.set(null); this.installmentPreview.set(null); if (id === 'NEW' || !id) { this.selectedTemplateId.set('NEW'); this.templateDraft.set(blankTemplate(this.sessionId() || null)); return; } const selected = this.templates().find((template) => template.id === id); if (!selected) return; this.selectedTemplateId.set(id); this.templateDraft.set(this.templateFromView(selected)); }
  protected setTemplateField(field: 'code' | 'nameFr' | 'nameEn', value: string): void { this.templateDraft.update((draft) => ({ ...draft, [field]: value })); this.templateError.set(null); }
  protected setTemplateLine(index: number, field: keyof TemplateLineRequest, value: unknown): void { this.templateDraft.update((draft) => { const lines = draft.lines.map((line, i) => i === index ? { ...line, [field]: value } : line); return { ...draft, lines }; }); this.templateError.set(null); }
  protected setTemplateAllocation(index: number, value: string): void { const allocation: 'FIXED' | 'PERCENTAGE' = value === 'FIXED' ? 'FIXED' : 'PERCENTAGE'; this.templateDraft.update((draft) => { const lines: TemplateLineRequest[] = draft.lines.map((line, i) => i === index ? { ...line, allocationType: allocation, amountMinor: allocation === 'FIXED' ? 0 : null, percentageBasisPoints: allocation === 'FIXED' ? null : 10000 } : line); return { ...draft, lines }; }); }
  protected setTemplatePercentage(index: number, value: number | string): void { this.setTemplateLine(index, 'percentageBasisPoints', Math.round((Number(value) || 0) * 100)); }
  protected setTemplateDueRule(index: number, value: string): void { const dueRuleType = value === 'ABSOLUTE_DATE' ? 'ABSOLUTE_DATE' : 'SESSION_START_OFFSET'; this.templateDraft.update((draft) => { const lines = draft.lines.map((line, i) => i === index ? { ...line, dueRuleType, absoluteDueDate: dueRuleType === 'ABSOLUTE_DATE' ? (line.absoluteDueDate || this.sessionStart()) : null, dueOffsetDays: dueRuleType === 'ABSOLUTE_DATE' ? null : (line.dueOffsetDays ?? 0), academicTermId: null } : line); return { ...draft, lines }; }); }
  protected addTemplateLine(): void { this.templateDraft.update((draft) => ({ ...draft, lines: [...draft.lines, blankTemplateLine(draft.lines.length + 1)] })); }
  protected removeTemplateLine(index: number): void { this.templateDraft.update((draft) => ({ ...draft, lines: draft.lines.filter((_, i) => i !== index).map((line, i) => ({ ...line, lineOrder: i + 1 })) })); }
  protected moveTemplateLine(index: number, delta: number): void { this.templateDraft.update((draft) => { const target = index + delta; if (target < 0 || target >= draft.lines.length) return draft; const lines = [...draft.lines]; [lines[index], lines[target]] = [lines[target], lines[index]]; return { ...draft, lines: lines.map((line, i) => ({ ...line, lineOrder: i + 1 })) }; }); }
  protected templateCanSave(): boolean { const draft = this.templateDraft(); return !!draft.code.trim() && !!draft.nameFr.trim() && !!draft.nameEn.trim() && draft.lines.length > 0 && draft.lines.every((line) => !!line.labelFr.trim() && !!line.labelEn.trim() && line.lineOrder > 0 && (line.allocationType === 'FIXED' ? (line.amountMinor ?? -1) >= 0 : (line.percentageBasisPoints ?? -1) >= 0 && (line.percentageBasisPoints ?? 0) <= 10000) && (line.dueRuleType === 'ABSOLUTE_DATE' ? !!line.absoluteDueDate : line.dueOffsetDays !== null && line.dueOffsetDays !== undefined)) && !(this.templateUsesPercentage() && this.templatePercentageTotal() !== 10000) && !(this.templateUsesPercentage() && this.templateDraft().lines.some((line) => line.allocationType === 'FIXED')); }
  protected templateMismatchNotice(): string | null { if (!this.templateDraft().lines.length) return 'Add at least one installment.'; if (this.templateUsesPercentage() && this.templatePercentageTotal() !== 10000) return 'Percentage allocations must total exactly 100%. The final percentage row receives any XAF rounding residual.'; if (!this.templateUsesPercentage() && this.templateFixedTotal() !== this.templateBaseAmount()) return `Fixed installments total ${this.templateFixedTotal()} XAF but the current plan-line amount is ${this.templateBaseAmount()} XAF. The line cannot attach until the amounts match exactly.`; return null; }
  protected templateAllocationSummary(): string { return this.templateUsesPercentage() ? `${this.templatePercentageTotal() / 100}% percentage allocation` : `${this.templateFixedTotal().toLocaleString()} XAF fixed allocation`; }
  protected saveTemplate(): void { if (!this.templateCanSave()) { this.templateError.set(this.templateMismatchNotice() || 'Complete all required template fields.'); return; } const draft = this.templateDraft(); const body = { code: draft.code, nameFr: draft.nameFr, nameEn: draft.nameEn, sourceSessionId: draft.sourceSessionId, lines: draft.lines.map((line, index) => ({ ...line, lineOrder: index + 1 })) }; this.templateSaving.set(true); const request$ = draft.id ? this.api.updateTemplate(draft.id, { ...body, version: draft.version ?? undefined }) : this.api.createTemplate(body); request$.subscribe({ next: (saved) => { this.templates.update((items) => draft.id ? items.map((item) => item.id === saved.id ? saved : item) : [...items, saved].sort((a, b) => a.code.localeCompare(b.code))); this.selectedTemplateId.set(saved.id); this.templateDraft.set(this.templateFromView(saved)); this.templateSaving.set(false); this.success.set(draft.id ? 'Installment template updated.' : 'Installment template created.'); }, error: (err: PlanApiError) => { this.templateSaving.set(false); this.templateError.set(this.message(err)); this.applyError(err); } }); }
  protected deleteTemplate(): void { const draft = this.templateDraft(); if (!draft.id) return; this.templateSaving.set(true); this.api.deleteTemplate(draft.id, draft.version || 0).subscribe({ next: () => { this.templates.update((items) => items.filter((item) => item.id !== draft.id)); this.selectTemplate('NEW'); this.templateSaving.set(false); this.success.set('Installment template deleted.'); }, error: (err: PlanApiError) => { this.templateSaving.set(false); this.templateError.set(this.message(err)); this.applyError(err); } }); }
  private templateFromView(view: TemplateView): TemplateDraft { return { id: view.id, version: view.version, code: view.code, nameFr: view.nameFr, nameEn: view.nameEn, sourceSessionId: view.sourceSessionId, lines: view.lines.map((line) => ({ lineOrder: line.lineOrder, labelFr: line.labelFr, labelEn: line.labelEn, allocationType: line.allocationType, amountMinor: line.amountMinor, percentageBasisPoints: line.percentageBasisPoints, dueRuleType: line.dueRuleType, absoluteDueDate: line.absoluteDueDate, dueOffsetDays: line.dueOffsetDays, academicTermId: line.academicTermId })) }; }
  protected templateBaseAmount(): number { return this.lineDraft().amountMinor || this.selectedPlan()?.lines[0]?.amountMinor || 0; }
  private dueLabel(line: TemplateLineRequest): string { if (line.dueRuleType === 'ABSOLUTE_DATE') return line.absoluteDueDate || 'Absolute date required'; const session = this.context().sessions.find((item) => item.id === this.sessionId()); if (!session) return `${line.dueOffsetDays || 0} days after session start`; const date = new Date(`${session.startDate}T00:00:00`); date.setDate(date.getDate() + (line.dueOffsetDays || 0)); return `${date.toISOString().slice(0, 10)} (${line.dueOffsetDays || 0} days)`; }

  protected openActivation(plan: PlanView): void { this.activationTarget.set(plan); this.activation.set(null); this.activationLoading.set(true); this.api.activationPreview(plan.id).subscribe({ next: (review) => { this.activation.set(review); this.activationLoading.set(false); }, error: (err: PlanApiError) => { this.activationLoading.set(false); this.applyError(err); } }); }
  protected closeActivation(): void { this.activationTarget.set(null); this.activation.set(null); }
  protected activate(plan: PlanView): void { this.api.activate(plan.id, plan.version).subscribe({ next: (updated) => { this.closeActivation(); this.success.set('Plan version activated.'); this.replacePlan(updated); }, error: (err: PlanApiError) => this.applyError(err) }); }
  protected previewCopy(): void { this.copyPreview.set(null); this.copySuccess.set(null); this.api.copyPreview({ sourcePlanId: this.copySourceId(), targetSessionId: this.copyTargetSessionId(), targetClassId: this.copyTargetClassId() || null, mergeMode: this.copyMode() }).subscribe({ next: (value) => this.copyPreview.set(value), error: (err: PlanApiError) => this.applyError(err) }); }
  protected applyCopy(): void { const source = this.allPlans().find((p) => p.id === this.copySourceId()); const preview = this.copyPreview(); if (!source || !preview) return; this.api.copy({ sourcePlanId: source.id, targetSessionId: this.copyTargetSessionId(), targetClassId: this.copyTargetClassId() || null, mergeMode: this.copyMode(), sourceVersion: source.version }).subscribe({ next: (plan) => { this.context.update((c) => ({ ...c, plans: [plan, ...c.plans.filter((p) => p.id !== plan.id)] })); this.selectedPlan.set(plan); this.copySuccess.set(`Copy applied: ${plan.lines.length} line(s), draft v${plan.planVersionNo}, mode ${preview.mergeMode}. Date shift was ${preview.dateShift}.`); this.success.set('Copy applied to the target draft.'); }, error: (err: PlanApiError) => this.applyError(err) }); }

  protected searchStudents(): void { this.studentLoading.set(true); this.studentError.set(null); this.api.studentContext(this.studentQuery(), this.sessionId() || undefined).subscribe({ next: (items) => { this.studentContexts.set(items); this.studentLoading.set(false); }, error: (err: PlanApiError) => { this.studentLoading.set(false); this.studentError.set(this.message(err)); this.applyError(err); } }); }
  protected selectStudentContext(id: string): void { const selected = this.studentContexts().find((item) => item.enrollmentId === id) || null; this.selectedStudentContext.set(selected); this.enrollmentId.set(id); if (selected) { this.overrideFrom.set(this.sessionStart()); this.resolveStudent(); } }
  protected resolveStudent(): void { if (!this.enrollmentId()) { this.studentError.set('Select an enrollment context first.'); return; } this.studentError.set(null); this.api.resolve(this.enrollmentId()).subscribe({ next: (value) => { this.resolution.set(value); this.elections.set([]); this.overrides.set([]); this.impactPreview.set(null); if (value.plan) { this.overrideLineId.set(value.plan.lines[0]?.id || ''); forkJoin({ elections: this.api.elections(value.plan.id, this.enrollmentId()), overrides: this.api.overrides(value.plan.id, this.enrollmentId()) }).subscribe({ next: (records) => { this.elections.set(records.elections); this.overrides.set(records.overrides); }, error: (err: PlanApiError) => this.applyError(err) }); } }, error: (err: PlanApiError) => { this.studentError.set(this.message(err)); this.applyError(err); } }); }
  protected optionalLines(plan: PlanView): PlanView['lines'] { return plan.lines.filter((line) => !line.mandatory); }
  protected electionStatus(lineId: string): string { return this.elections().find((item) => item.feePlanLineId === lineId)?.status || 'PENDING'; }
  protected setElection(lineId: string, status: 'ACCEPTED' | 'DECLINED'): void { const plan = this.resolution()?.plan; if (!plan || !this.enrollmentId()) return; const current = this.elections().find((item) => item.feePlanLineId === lineId); this.api.saveElection(plan.id, lineId, this.enrollmentId(), { status, reason: 'Updated in the finance workspace', version: current?.version }).subscribe({ next: (saved) => { this.elections.update((items) => [...items.filter((item) => item.feePlanLineId !== lineId), saved]); this.success.set(`Optional fee election ${status.toLowerCase()}.`); }, error: (err: PlanApiError) => this.applyError(err) }); }
  protected overrideCanSubmit(): boolean { return !!this.overrideLineId() && !!this.overrideReason().trim() && !!this.overrideFrom() && (this.overrideType() !== 'AMOUNT' || this.overrideAmount() >= 0) && (this.overrideType() !== 'DISCOUNT' || (this.overridePercentage() >= 0 && this.overridePercentage() <= 10000)); }
  protected previewImpact(): void { const plan = this.resolution()?.plan; if (!plan || !this.enrollmentId() || !this.overrideLineId()) return; this.api.impactPreview(plan.id, this.enrollmentId(), this.overrideLineId()).subscribe({ next: (value) => this.impactPreview.set(value), error: (err: PlanApiError) => this.applyError(err) }); }
  protected requestOverride(): void { const plan = this.resolution()?.plan; if (!plan || !this.overrideCanSubmit()) { this.overrideError.set('Choose a line, reason, effective date and a valid override value.'); return; } this.overrideError.set(null); this.api.requestOverride(plan.id, { enrollmentId: this.enrollmentId(), feePlanLineId: this.overrideLineId(), overrideType: this.overrideType(), amountMinor: this.overrideType() === 'AMOUNT' ? this.overrideAmount() : null, percentageBasisPoints: this.overrideType() === 'DISCOUNT' ? this.overridePercentage() : null, reason: this.overrideReason().trim(), effectiveFrom: this.overrideFrom(), effectiveTo: this.overrideTo() || null, version: undefined }).subscribe({ next: (saved) => { this.overrides.update((items) => [saved, ...items]); this.success.set('Override request submitted for approval.'); this.overrideReason.set(''); }, error: (err: PlanApiError) => { this.overrideError.set(this.message(err)); this.applyError(err); } }); }
  protected decideOverride(item: import('./plans.api').OverrideView, approve: boolean): void { this.api.decideOverride(item.id, { version: item.version, approve, decisionReason: approve ? 'Approved in the finance workspace' : 'Rejected in the finance workspace' }).subscribe({ next: (saved) => { this.overrides.update((items) => items.map((current) => current.id === saved.id ? saved : current)); this.success.set(approve ? 'Override approved.' : 'Override rejected.'); }, error: (err: PlanApiError) => this.applyError(err) }); }

  protected className(id: string | null): string { return id ? this.context().classes.find((item) => item.id === id)?.name || id.slice(0, 8) : ''; }
  protected feeName(item: FeeTypeView): string { return this.fr() ? item.currentRevision?.nameFr || item.code : item.currentRevision?.nameEn || item.code; }
  protected feeCode(id: string): string { return this.feeTypes().find((item) => item.id === id)?.code || id.slice(0, 8); }
  protected templateName(id: string | null): string { return id ? this.templates().find((item) => item.id === id)?.code || id.slice(0, 8) : 'One payment'; }
  protected money(amount: number, currency: string): string { return `${Math.round(Number(amount) || 0).toLocaleString('fr-FR')} ${currency || 'XAF'}`; }
  protected statusClass(value: string): string { return value === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : value === 'RETIRED' ? 'bg-slate-200 text-slate-600' : 'bg-amber-100 text-amber-800'; }
  protected toNumber(value: number | string): number { return Number(value) || 0; }
  protected toBasisPoints(value: number | string): number { return Math.round((Number(value) || 0) * 100); }
  protected sessionStart(): string { return this.context().sessions.find((item) => item.id === this.sessionId())?.startDate || new Date().toISOString().slice(0, 10); }
  private message(err: PlanApiError): string { return err?.error?.message || err?.message || 'Something went wrong.'; }
  private applyError(err: PlanApiError): void { this.error.set(this.message(err)); this.correlationId.set(err?.error?.correlationId || null); }
}
