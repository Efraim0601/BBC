import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AcademicSessionView, ConfigurationCopyApplyRequest, ConfigurationCopyEdit, ConfigurationCopyPreview,
  ConfigurationCopyPreviewRequest, FoundationApi,
} from '../../core/foundation.api';

@Component({
  selector: 'bbc-session-configuration-copy',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rounded-xl border border-violet-200 bg-violet-50/40 p-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 class="font-bold text-ink">Reuse a previous session</h3>
          <p class="text-xs text-mute mt-1">Preview terms, reporting milestones, dependencies, and workflow rules. Existing target values stay unchanged until you choose an update mode.</p>
        </div>
        <button type="button" (click)="preview()" [disabled]="busy() || !sourceId" class="h-9 px-3 rounded-lg bg-violet-600 text-white text-sm font-semibold disabled:opacity-50">{{ busy() ? '…' : 'Preview reuse' }}</button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3 mt-4">
        <label class="block md:col-span-2"><span class="meta">Previous session</span><select [(ngModel)]="sourceId" class="field"><option value="">Choose a source</option>@for (s of sourceSessions; track s.id) { <option [value]="s.id">{{ s.label }} · {{ s.startDate }}</option> }</select></label>
        <label class="block"><span class="meta">Merge behavior</span><select [(ngModel)]="mergeMode" class="field"><option value="FILL_MISSING">Fill missing only</option><option value="UPDATE_SELECTED">Update selected rows</option><option value="UPDATE_ALL">Update all proposed rows</option></select></label>
        <label class="block"><span class="meta">Date strategy</span><select [(ngModel)]="dateStrategy" class="field"><option value="SHIFT_FROM_SESSION_START">Shift from session start</option></select></label>
      </div>
      <div class="flex flex-wrap gap-4 mt-3 text-xs font-semibold text-ink">
        <label><input type="checkbox" [(ngModel)]="scopes.terms" /> Terms</label>
        <label><input type="checkbox" [(ngModel)]="scopes.reportingPeriods" /> Reporting milestones</label>
        <label><input type="checkbox" [(ngModel)]="scopes.dependencies" /> Dependencies</label>
        <label><input type="checkbox" [(ngModel)]="scopes.workflowWindows" /> Workflow windows</label>
      </div>
      @if (message(); as m) { <div class="mt-3 rounded-lg px-3 py-2 text-xs" [class]="m.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'">{{ m.text }}</div> }
      @if (proposal(); as p) {
        <div class="mt-4 rounded-lg border border-slate-200 bg-white p-3">
          <div class="flex flex-wrap items-center justify-between gap-2"><div class="text-sm font-bold text-ink">Preview · {{ p.createCount }} create · {{ p.updateCount }} update · {{ p.keepCount }} keep</div><span class="text-xs text-mute">Fingerprint protected</span></div>
          @if (p.blockers.length) { <div class="mt-2 text-xs text-rose-700">@for (b of p.blockers; track b) { <div>• {{ readable(b) }}</div> }</div> }
          @if (p.warnings.length) { <div class="mt-2 text-xs text-amber-700">@for (w of p.warnings; track w) { <div>• {{ readable(w) }}</div> }</div> }
          <div class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-2 max-h-64 overflow-auto">
            @for (row of allRows(p); track row.key) {
              <div class="rounded-md border border-slate-100 px-2 py-2 text-xs">
                <div class="flex items-center justify-between gap-2"><strong>{{ row.code || row.label }}</strong><span class="chip" [class]="row.status === 'CREATE' ? 'bg-emerald-50 text-emerald-700' : row.status === 'KEEP' ? 'bg-slate-100 text-slate-600' : 'bg-amber-50 text-amber-700'">{{ row.status }}</span></div>
                @if (row.existing && mergeMode === 'UPDATE_SELECTED') { <label class="flex items-center gap-2 mt-2 text-[10px] text-amber-800"><input type="checkbox" [checked]="selectedKeys.includes(row.key)" (change)="toggle(row.key, $event)" /> Replace target value</label> }
                @if (row.proposed['startDate']) { <label class="block mt-2"><span class="text-[10px] text-mute">Start date</span><input type="date" [ngModel]="row.proposed['startDate']" (ngModelChange)="edit(row, 'startDate', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label> }
                @if (row.proposed['endDate']) { <label class="block mt-2"><span class="text-[10px] text-mute">End date</span><input type="date" [ngModel]="row.proposed['endDate']" (ngModelChange)="edit(row, 'endDate', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label> }
                @if (row.kind === 'WORKFLOW_WINDOW') { <label class="block mt-2"><span class="text-[10px] text-mute">Window mode</span><select [ngModel]="row.proposed['mode']" (ngModelChange)="edit(row, 'mode', $event)" class="h-8 w-full px-2 border border-slate-200 rounded"><option value="INHERIT">Inherit</option><option value="UNRESTRICTED">Unrestricted</option><option value="LIMITED">Limited</option></select></label> }
              </div>
            }
          </div>
          <label class="block mt-3"><span class="meta">Reason required before apply</span><textarea [(ngModel)]="reason" rows="2" class="field" placeholder="Explain why this configuration is being reused"></textarea></label>
          <div class="flex justify-end mt-3"><button type="button" (click)="apply()" [disabled]="busy() || !reason.trim() || p.blockers.length > 0" class="h-9 px-3 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">Apply preview</button></div>
        </div>
      }
    </section>
  `,
})
export class SessionConfigurationCopyComponent {
  private api = inject(FoundationApi);
  @Input({ required: true }) target!: AcademicSessionView;
  @Input() sessions: AcademicSessionView[] = [];
  @Input() canManage = false;
  @Output() applied = new EventEmitter<void>();
  protected proposal = signal<ConfigurationCopyPreview | null>(null);
  protected busy = signal(false);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  protected sourceId = '';
  protected mergeMode = 'FILL_MISSING';
  protected dateStrategy = 'SHIFT_FROM_SESSION_START';
  protected scopes = { terms: true, reportingPeriods: true, dependencies: true, workflowWindows: true };
  protected reason = '';
  protected edits: ConfigurationCopyEdit[] = [];
  protected selectedKeys: string[] = [];

  get sourceSessions(): AcademicSessionView[] {
    return this.sessions.filter((s) => s.id !== this.target?.id).sort((a, b) => b.startDate.localeCompare(a.startDate));
  }

  protected allRows(p: ConfigurationCopyPreview) { return [...p.terms, ...p.reportingPeriods, ...p.dependencies, ...p.workflowWindows]; }
  protected readable(value: string): string { return value.replaceAll('_', ' ').toLowerCase(); }
  protected edit(row: { key: string; proposed: Record<string, unknown> }, field: string, value: unknown): void {
    this.edits = [
      ...this.edits.filter((edit) => !(edit.key === row.key && edit.field === field)),
      { key: row.key, field, value: value == null || value === '' ? null : String(value) },
    ];
    this.loadPreview(this.edits);
  }

  protected toggle(key: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedKeys = checked
      ? [...new Set([...this.selectedKeys, key])]
      : this.selectedKeys.filter((selected) => selected !== key);
    this.loadPreview(this.edits);
  }

  protected preview(): void {
    this.edits = [];
    this.selectedKeys = [];
    this.loadPreview(this.edits);
  }

  private loadPreview(edits: ConfigurationCopyEdit[]): void {
    if (!this.target || !this.sourceId) return;
    this.busy.set(true); this.message.set(null);
    const body: ConfigurationCopyPreviewRequest = { sourceSessionId: this.sourceId, dateStrategy: this.dateStrategy, mergeMode: this.mergeMode, scopes: this.scopes, edits, selectedKeys: this.selectedKeys };
    this.api.previewConfigurationCopy(this.target.id, body).subscribe({
      next: (p) => { this.proposal.set(p); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.message.set({ ok: false, text: e?.error?.message ?? 'Preview failed.' }); },
    });
  }

  protected apply(): void {
    const p = this.proposal(); if (!p || !this.reason.trim()) return;
    const body: ConfigurationCopyApplyRequest = { sourceSessionId: this.sourceId, dateStrategy: this.dateStrategy, mergeMode: this.mergeMode, scopes: this.scopes, edits: this.edits, selectedKeys: this.selectedKeys, reason: this.reason.trim(), previewFingerprint: p.fingerprint };
    this.busy.set(true);
    this.api.applyConfigurationCopy(this.target.id, body, crypto.randomUUID()).subscribe({
      next: () => { this.busy.set(false); this.message.set({ ok: true, text: 'Configuration copied and audited.' }); this.applied.emit(); },
      error: (e) => { this.busy.set(false); this.message.set({ ok: false, text: e?.error?.message ?? 'Apply failed; the preview may be stale.' }); },
    });
  }
}
