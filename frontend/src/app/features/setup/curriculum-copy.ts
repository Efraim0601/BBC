import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CurriculumCopyApplyRequest, CurriculumCopyEdit, CurriculumCopyPreview, CurriculumCopyPreviewRequest,
  CurriculumCopyRow, SetupApi,
} from '../../core/setup.api';
import { AcademicSessionView } from '../../core/foundation.api';

@Component({
  selector: 'bbc-curriculum-copy',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mb-5 rounded-xl border border-violet-200 bg-violet-50/40 p-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 class="font-bold text-ink">Reuse class subjects</h3>
          <p class="text-xs text-mute mt-1">Preview class-specific coefficients, rules, groups, and optional teachers before anything is written.</p>
        </div>
        <button type="button" (click)="preview()" [disabled]="busy() || !sourceId || !targetSessionId" class="h-9 px-3 rounded-lg bg-violet-600 text-white text-sm font-semibold disabled:opacity-50">{{ busy() ? 'â€¦' : 'Preview reuse' }}</button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-5 gap-3 mt-4">
        <label class="block md:col-span-2"><span class="meta">Previous session *</span><select [(ngModel)]="sourceId" class="field"><option value="">Choose a source</option>@for (s of sourceSessions; track s.id) { <option [value]="s.id">{{ s.label }} Â· {{ s.startDate }}</option> }</select></label>
        <label class="block"><span class="meta">Merge behavior</span><select [(ngModel)]="mergeMode" class="field"><option value="FILL_MISSING">Fill missing only</option><option value="UPDATE_SELECTED">Update selected rows</option><option value="UPDATE_ALL">Update all rows</option></select></label>
        <label class="flex items-center gap-2 pt-6 text-xs font-semibold"><input type="checkbox" [(ngModel)]="includeGroups" /> Subject groups</label>
        <label class="flex items-center gap-2 pt-6 text-xs font-semibold"><input type="checkbox" [(ngModel)]="includeTeachers" /> Copy teachers (optional)</label>
      </div>
      <div class="mt-3 flex flex-wrap items-center gap-4 text-xs font-semibold text-ink">
        <label class="flex items-center gap-2"><input type="radio" name="curriculum-scope" [checked]="!!classId" [disabled]="!classId" (change)="allMatching = false" /> This class{{ classId ? '' : ' (choose a class first)' }}</label>
        <label class="flex items-center gap-2"><input type="radio" name="curriculum-scope" [checked]="allMatching || !classId" (change)="allMatching = true" /> All matching classes</label>
      </div>
      @if (message(); as m) { <div class="mt-3 rounded-lg px-3 py-2 text-xs" [class]="m.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'">{{ m.text }}</div> }
      @if (proposal(); as p) {
        <div class="mt-4 rounded-lg border border-slate-200 bg-white p-3">
          <div class="flex flex-wrap items-center justify-between gap-2"><div class="text-sm font-bold text-ink">Preview Â· {{ p.createCount }} create Â· {{ p.updateCount }} update Â· {{ p.keepCount }} keep</div><span class="text-xs text-mute">Teacher warnings do not block subject copying</span></div>
          @if (p.blockers.length) { <div class="mt-2 text-xs text-rose-700">@for (b of p.blockers; track b) { <div>â€¢ {{ readable(b) }}</div> }</div> }
          <div class="mt-3 space-y-2 max-h-80 overflow-auto">
            @for (row of p.rows; track row.key) {
              <div class="rounded-md border border-slate-100 px-3 py-2 text-xs">
                <div class="flex flex-wrap items-center justify-between gap-2"><strong>{{ row.className }} Â· {{ row.subjectCode }}</strong><span class="chip" [class]="row.status === 'CREATE' ? 'bg-emerald-50 text-emerald-700' : row.status === 'KEEP' ? 'bg-slate-100 text-slate-600' : 'bg-amber-50 text-amber-700'">{{ row.status }}</span></div>
                @if (row.existing && mergeMode === 'UPDATE_SELECTED') { <label class="flex items-center gap-2 mt-2 text-[10px] text-amber-800"><input type="checkbox" [checked]="selectedKeys.includes(row.key)" (change)="toggle(row.key, $event)" /> Replace target row</label> }
                @if (row.subjectId) {
                  <div class="grid grid-cols-2 md:grid-cols-5 gap-2 mt-2">
                    <label><span class="text-[10px] text-mute">Group code</span><input [ngModel]="row.proposed['groupCode']" (ngModelChange)="edit(row, 'groupCode', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label>
                    <label><span class="text-[10px] text-mute">Coefficient</span><input type="number" min="1" [ngModel]="row.proposed['coefficient']" (ngModelChange)="edit(row, 'coefficient', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label>
                    <label><span class="text-[10px] text-mute">Max score</span><input type="number" min="1" [ngModel]="row.proposed['maxScore']" (ngModelChange)="edit(row, 'maxScore', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label>
                    <label><span class="text-[10px] text-mute">Pass threshold</span><input type="number" min="0" [ngModel]="row.proposed['passThreshold']" (ngModelChange)="edit(row, 'passThreshold', $event)" class="h-8 w-full px-2 border border-slate-200 rounded" /></label>
                    <label class="flex items-center gap-2 pt-4"><input type="checkbox" [ngModel]="row.proposed['mandatory']" (ngModelChange)="edit(row, 'mandatory', $event)" /> Required</label>
                  </div>
                }
                @if (row.teacherStatus === 'UNAVAILABLE' || row.warnings.length) { <div class="mt-2 text-amber-700">{{ row.teacherMessage || 'Teacher validation warning: ' + row.warnings.join(', ') }}</div> }
              </div>
            }
          </div>
          <label class="block mt-3"><span class="meta">Reason required before apply *</span><textarea [(ngModel)]="reason" rows="2" class="field" placeholder="Explain why this curriculum is being reused"></textarea></label>
          <div class="flex justify-end mt-3"><button type="button" (click)="apply()" [disabled]="busy() || !reason.trim() || p.blockers.length > 0" class="h-9 px-3 rounded-lg bg-brand-600 text-white text-sm font-semibold disabled:opacity-50">Apply preview</button></div>
        </div>
      }
    </section>
  `,
})
export class CurriculumCopyComponent implements OnChanges {
  private api = inject(SetupApi);
  @Input() targetSessionId = '';
  @Input() sessions: AcademicSessionView[] = [];
  @Input() classId = '';
  @Input() canWrite = false;
  @Output() applied = new EventEmitter<void>();
  protected proposal = signal<CurriculumCopyPreview | null>(null);
  protected busy = signal(false);
  protected message = signal<{ ok: boolean; text: string } | null>(null);
  protected sourceId = '';
  protected allMatching = false;
  protected includeGroups = true;
  protected includeTeachers = true;
  protected mergeMode = 'FILL_MISSING';
  protected reason = '';
  protected edits: CurriculumCopyEdit[] = [];
  protected selectedKeys: string[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['sessions'] || changes['targetSessionId']) {
      const first = this.sourceSessions[0];
      if (first && !this.sourceId) this.sourceId = first.id;
    }
    if (changes['classId'] && this.classId) this.allMatching = false;
  }

  get sourceSessions(): AcademicSessionView[] {
    return this.sessions.filter((s) => s.id !== this.targetSessionId).sort((a, b) => b.startDate.localeCompare(a.startDate));
  }

  protected readable(value: string): string { return value.replaceAll('_', ' ').toLowerCase(); }

  protected edit(row: CurriculumCopyRow, field: string, value: unknown): void {
    this.edits = [...this.edits.filter((edit) => !(edit.key === row.key && edit.field === field)),
      { key: row.key, field, value: value == null || value === '' ? null : String(value) }];
    this.loadPreview(this.edits);
  }

  protected toggle(key: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedKeys = checked ? [...new Set([...this.selectedKeys, key])] : this.selectedKeys.filter((item) => item !== key);
    this.loadPreview(this.edits);
  }

  protected preview(): void {
    this.edits = [];
    this.selectedKeys = [];
    this.loadPreview(this.edits);
  }

  private loadPreview(edits: CurriculumCopyEdit[]): void {
    if (!this.targetSessionId || !this.sourceId) return;
    this.busy.set(true); this.message.set(null);
    const body: CurriculumCopyPreviewRequest = {
      sourceSessionId: this.sourceId, targetSessionId: this.targetSessionId,
      classIds: this.classId && !this.allMatching ? [this.classId] : [],
      allMatchingClasses: this.allMatching || !this.classId,
      includeGroups: this.includeGroups, includeTeachers: this.includeTeachers,
      mergeMode: this.mergeMode, selectedKeys: this.selectedKeys, edits,
    };
    this.api.previewCurriculumCopy(body).subscribe({
      next: (p) => { this.proposal.set(p); this.busy.set(false); },
      error: (e) => { this.busy.set(false); this.message.set({ ok: false, text: e?.error?.message ?? 'Preview failed.' }); },
    });
  }

  protected apply(): void {
    const p = this.proposal();
    if (!p || !this.reason.trim()) return;
    const body: CurriculumCopyApplyRequest = {
      sourceSessionId: this.sourceId, targetSessionId: this.targetSessionId,
      classIds: this.classId && !this.allMatching ? [this.classId] : [],
      allMatchingClasses: this.allMatching || !this.classId,
      includeGroups: this.includeGroups, includeTeachers: this.includeTeachers,
      mergeMode: this.mergeMode, selectedKeys: this.selectedKeys, edits: this.edits,
      reason: this.reason.trim(), previewFingerprint: p.fingerprint,
    };
    this.busy.set(true);
    this.api.applyCurriculumCopy(body, crypto.randomUUID()).subscribe({
      next: () => { this.busy.set(false); this.message.set({ ok: true, text: 'Curriculum copied and audited.' }); this.applied.emit(); },
      error: (e) => { this.busy.set(false); this.message.set({ ok: false, text: e?.error?.message ?? 'Apply failed; the preview may be stale.' }); },
    });
  }
}
