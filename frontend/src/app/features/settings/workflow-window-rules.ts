import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AcademicSessionView, AcademicTermView, AcademicReportingPeriodView, FoundationApi, WorkflowAction, WorkflowWindowRuleView, WindowMode } from '../../core/foundation.api';

@Component({
  selector: 'bbc-workflow-window-rules',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rounded-xl border border-sky-200 bg-sky-50/40 p-4">
      <div class="flex items-start justify-between gap-3"><div><h3 class="font-bold text-ink">Workflow windows</h3><p class="text-xs text-mute mt-1">Use Inherit, Unrestricted, or Limited. Limited windows may have only an opening or only a closing date.</p></div><button type="button" (click)="load()" class="h-8 px-3 rounded-lg bg-white border border-slate-200 text-xs font-semibold">Refresh</button></div>
      @if (message(); as m) { <div class="mt-3 rounded-lg px-3 py-2 text-xs" [class]="m.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'">{{ m.text }}</div> }
      <div class="mt-3 space-y-2 max-h-[30rem] overflow-auto">
        @for (rule of rules(); track rule.id) {
          <div class="grid grid-cols-1 md:grid-cols-[1fr_150px_1fr_1fr_auto] gap-2 items-end rounded-lg border border-white bg-white p-2">
            <div><div class="text-xs font-bold text-ink">{{ ruleLabel(rule) }}</div><div class="text-[10px] text-mute">{{ rule.scopeType }} · {{ rule.action }}</div></div>
            <label><span class="text-[10px] text-mute">Mode</span><select [ngModel]="draft(rule).mode" (ngModelChange)="set(rule, 'mode', $event)" class="h-8 w-full px-2 border border-slate-200 rounded text-xs"><option value="INHERIT">Inherit</option><option value="UNRESTRICTED">Unrestricted</option><option value="LIMITED">Limited</option></select></label>
            <label><span class="text-[10px] text-mute">Opens (optional)</span><input type="datetime-local" [ngModel]="draft(rule).opensAt" (ngModelChange)="set(rule, 'opensAt', $event)" [disabled]="draft(rule).mode !== 'LIMITED'" class="h-8 w-full px-2 border border-slate-200 rounded text-xs disabled:bg-slate-100" /></label>
            <label><span class="text-[10px] text-mute">Closes (optional)</span><input type="datetime-local" [ngModel]="draft(rule).closesAt" (ngModelChange)="set(rule, 'closesAt', $event)" [disabled]="draft(rule).mode !== 'LIMITED'" class="h-8 w-full px-2 border border-slate-200 rounded text-xs disabled:bg-slate-100" /></label>
            <button type="button" (click)="save(rule)" [disabled]="!canManage || savingId() === rule.id" class="h-8 px-2 rounded-lg bg-brand-600 text-white text-xs font-semibold disabled:opacity-50">{{ savingId() === rule.id ? '…' : 'Save' }}</button>
          </div>
        } @empty { <div class="rounded-lg border border-dashed border-slate-300 bg-white p-4 text-xs text-mute">No normalized window rules are available yet. Apply the database migration, then refresh.</div> }
      </div>
    </section>
  `,
})
export class WorkflowWindowRulesComponent implements OnChanges {
  private api = inject(FoundationApi);
  @Input({ required: true }) target!: AcademicSessionView;
  @Input() terms: AcademicTermView[] = [];
  @Input() periods: AcademicReportingPeriodView[] = [];
  @Input() canManage = false;
  protected rules = signal<WorkflowWindowRuleView[]>([]);
  protected drafts = signal<Record<string, { mode: WindowMode; opensAt: string; closesAt: string }>>({});
  protected savingId = signal<string | null>(null);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  ngOnChanges(changes: SimpleChanges): void { if (changes['target'] && this.target?.id) this.load(); }
  protected load(): void {
    if (!this.target?.id) return;
    this.api.workflowWindowRules(this.target.id).subscribe({
      next: (rows) => { this.rules.set(rows); const drafts: Record<string, { mode: WindowMode; opensAt: string; closesAt: string }> = {}; for (const row of rows) drafts[row.id] = { mode: row.mode, opensAt: this.local(row.opensAt), closesAt: this.local(row.closesAt) }; this.drafts.set(drafts); },
      error: (e) => this.message.set({ ok: false, text: e?.error?.message ?? 'Workflow windows could not be loaded.' }),
    });
  }
  protected draft(rule: WorkflowWindowRuleView) { return this.drafts()[rule.id] ?? { mode: rule.mode, opensAt: this.local(rule.opensAt), closesAt: this.local(rule.closesAt) }; }
  protected set(rule: WorkflowWindowRuleView, field: 'mode' | 'opensAt' | 'closesAt', value: string): void { this.drafts.update((all) => ({ ...all, [rule.id]: { ...this.draft(rule), [field]: value } as any })); }
  protected ruleLabel(rule: WorkflowWindowRuleView): string { const term = this.terms.find((x) => x.id === rule.academicTermId); const period = this.periods.find((x) => x.id === rule.reportingPeriodId); return rule.scopeType === 'SESSION' ? this.target.label : (term?.code ?? period?.code ?? 'Scope'); }
  protected save(rule: WorkflowWindowRuleView): void {
    const d = this.draft(rule); this.savingId.set(rule.id); this.message.set(null);
    this.api.saveWorkflowWindowRule(this.target.id, { scopeType: rule.scopeType, academicTermId: rule.academicTermId, reportingPeriodId: rule.reportingPeriodId, action: rule.action as WorkflowAction, mode: d.mode, opensAt: d.mode === 'LIMITED' ? this.instant(d.opensAt) : null, closesAt: d.mode === 'LIMITED' ? this.instant(d.closesAt) : null, timezone: rule.timezone, version: rule.version }).subscribe({
      next: () => { this.savingId.set(null); this.message.set({ ok: true, text: 'Workflow window saved.' }); this.load(); },
      error: (e) => { this.savingId.set(null); this.message.set({ ok: false, text: e?.error?.message ?? 'Window save failed.' }); },
    });
  }
  private local(value: string | null): string { return value ? value.slice(0, 16) : ''; }
  private instant(value: string): string | null { return value ? new Date(value).toISOString() : null; }
}
