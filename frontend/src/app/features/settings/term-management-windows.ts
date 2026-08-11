import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AcademicSessionView, FoundationApi, TermManagementWindowView, TermManagementWindowUpsert } from '../../core/foundation.api';

type Draft = { limited: boolean; opensAt: string; closesAt: string };
type Field = 'limited' | 'opensAt' | 'closesAt';

@Component({
  selector: 'bbc-term-management-windows',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="term-access-panel" aria-labelledby="term-access-title">
      <div class="term-access-heading">
        <div>
          <h3 id="term-access-title">{{ english ? 'Trimester access (optional)' : 'Accès par trimestre (facultatif)' }}</h3>
          <p>{{ english
            ? 'These limits are optional. Without a limit, operations remain available without a date restriction while the session, your permissions, and the record state allow them. One limit covers every sequence and result in the trimester.'
            : 'Ces limites sont facultatives. Sans limite, les opérations restent disponibles sans restriction de date tant que la session, vos droits et l’état du dossier les autorisent. Une seule limite couvre toutes les séquences et tous les résultats du trimestre.' }}</p>
        </div>
      </div>

      @if (toast(); as toast) {
        <div class="term-toast" [class.term-toast-error]="!toast.ok" role="status">{{ toast.text }}</div>
      }

      <div class="term-access-grid">
        @for (term of windows(); track term.termId) {
          <article class="term-access-card" [attr.data-term-code]="term.termCode">
            <div class="term-card-topline">
              <div>
                <h4>{{ term.termCode }} · {{ term.termLabel }}</h4>
                <p>{{ term.termStartDate }} → {{ term.termEndDate }} · {{ term.timezone }}</p>
              </div>
              <span class="term-badge" [class]="badgeClass(term)">{{ badgeLabel(term) }}</span>
            </div>

            <div class="term-chips" aria-label="Milestones governed by this trimester">
              @for (code of term.governedPeriodCodes; track code) { <span>{{ readableCode(code) }}</span> }
            </div>

            <label class="term-toggle">
              <input type="checkbox" [checked]="draft(term).limited" [disabled]="!canManage || savingId() === term.termId"
                (change)="set(term, 'limited', ($any($event.target)).checked)" />
              <span>{{ english ? 'Limit management dates' : 'Limiter les dates de gestion' }}</span>
            </label>

            @if (draft(term).limited) {
              <div class="term-date-grid">
                <label>
                  <span>{{ english ? 'Available from' : 'Disponible à partir du' }}</span>
                  <input [id]="fieldId(term, 'opensAt')" type="datetime-local" [value]="draft(term).opensAt"
                    [disabled]="!canManage || savingId() === term.termId"
                    [class.term-field-invalid]="invalid(term, 'opensAt')"
                    (input)="set(term, 'opensAt', ($any($event.target)).value)" (blur)="blur(term, 'opensAt')" />
                  @if (errorFor(term, 'opensAt'); as error) { <small class="term-field-error">{{ error }}</small> }
                </label>
                <label>
                  <span>{{ english ? 'Available until' : 'Disponible jusqu’au' }}</span>
                  <input [id]="fieldId(term, 'closesAt')" type="datetime-local" [value]="draft(term).closesAt"
                    [disabled]="!canManage || savingId() === term.termId"
                    [class.term-field-invalid]="invalid(term, 'closesAt')"
                    (input)="set(term, 'closesAt', ($any($event.target)).value)" (blur)="blur(term, 'closesAt')" />
                  @if (errorFor(term, 'closesAt'); as error) { <small class="term-field-error">{{ error }}</small> }
                </label>
              </div>
            }

            <p class="term-summary">{{ summary(term) }}</p>
            <div class="term-card-actions">
              <button type="button" class="term-save-button" [disabled]="!canManage || savingId() === term.termId" (click)="save(term)">
                {{ savingId() === term.termId ? '…' : (english ? 'Save ' + term.termCode : 'Enregistrer ' + term.termCode) }}
              </button>
            </div>
          </article>
        } @empty {
          <div class="term-empty">{{ english ? 'No academic trimesters are configured yet.' : 'Aucun trimestre académique n’est encore configuré.' }}</div>
        }
      </div>
    </section>

    @if (pendingRemoval(); as term) {
      <div class="term-modal-backdrop" role="presentation">
        <section class="term-modal" role="dialog" aria-modal="true" aria-labelledby="remove-term-window-title">
          <h3 id="remove-term-window-title">{{ english ? 'Remove the date restriction for ' + term.termCode + '?' : 'Retirer la restriction de date de ' + term.termCode + ' ?' }}</h3>
          <p>{{ english
            ? 'Every governed milestone will become free of date restrictions. Permissions, session state, and workflow prerequisites will still apply.'
            : 'Toutes les opérations de ce trimestre deviendront libres de restriction de date. Vos droits, l’état de la session et les prérequis du dossier continueront de s’appliquer.' }}</p>
          <div class="term-modal-actions">
            <button type="button" class="term-cancel-button" (click)="cancelRemoval()">{{ english ? 'Cancel' : 'Annuler' }}</button>
            <button type="button" class="term-save-button" (click)="confirmRemoval()">{{ english ? 'Remove restriction' : 'Retirer la restriction' }}</button>
          </div>
        </section>
      </div>
    }
   `,
  styles: [`
    :host { display:block; }
    .term-access-panel { border:1px solid #c7d2fe; border-radius:1rem; background:#f8faff; padding:1rem; }
    .term-access-heading h3 { color:#172554; font-size:1rem; font-weight:800; margin:0; }
    .term-access-heading p { color:#475569; font-size:.78rem; line-height:1.5; margin:.35rem 0 0; max-width:70rem; }
    .term-toast { margin-top:.75rem; border:1px solid #a7f3d0; border-radius:.55rem; background:#ecfdf5; color:#065f46; padding:.55rem .7rem; font-size:.78rem; }
    .term-toast-error { border-color:#fecaca; background:#fff1f2; color:#9f1239; }
    .term-access-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:.8rem; margin-top:1rem; }
    .term-access-card { container-type:inline-size; display:flex; min-width:0; flex-direction:column; gap:.7rem; border:1px solid #dbe4f0; border-radius:.85rem; background:#fff; padding:.85rem; box-shadow:0 8px 24px rgba(30,41,59,.05); }
    .term-card-topline { display:flex; align-items:flex-start; justify-content:space-between; gap:.55rem; }
    .term-card-topline h4 { color:#172033; font-size:.92rem; font-weight:800; margin:0; }
    .term-card-topline p { color:#64748b; font-size:.7rem; margin:.25rem 0 0; }
    .term-badge { display:inline-flex; flex:none; border-radius:999px; padding:.25rem .5rem; font-size:.65rem; font-weight:800; }
    .term-badge-open { color:#047857; background:#d1fae5; }
    .term-badge-scheduled { color:#92400e; background:#fef3c7; }
    .term-badge-closed { color:#be123c; background:#ffe4e6; }
    .term-badge-invalid { color:#991b1b; background:#fee2e2; }
    .term-chips { display:flex; flex-wrap:wrap; gap:.3rem; }
    .term-chips span { border:1px solid #dbeafe; border-radius:999px; background:#eff6ff; color:#1e40af; padding:.22rem .45rem; font-size:.64rem; font-weight:700; }
    .term-toggle { display:flex; align-items:center; gap:.45rem; color:#1e293b; font-size:.78rem; font-weight:750; }
    .term-toggle input { accent-color:#3453b8; }
    .term-date-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); min-width:0; gap:.6rem; }
    .term-date-grid label { min-width:0; color:#475569; font-size:.68rem; font-weight:700; }
    .term-date-grid label > span { display:block; min-width:0; }
    .term-date-grid input { display:block; box-sizing:border-box; min-width:0; max-width:100%; width:100%; height:2.35rem; margin-top:.3rem; border:1px solid #94a3b8; border-radius:.5rem; background:#fff; color:#0f172a; padding:0 .55rem; font-size:.75rem; }
    .term-date-grid input:focus { outline:2px solid #a5b4fc; outline-offset:1px; border-color:#4f46e5; }
    .term-date-grid input.term-field-invalid { border:2px solid #dc2626; background:#fff7f7; }
    .term-field-error { display:block; color:#b91c1c; font-size:.67rem; font-weight:650; line-height:1.3; margin-top:.25rem; }
    .term-summary { min-height:2.1rem; color:#334155; font-size:.73rem; line-height:1.45; margin:0; }
    .term-card-actions { display:flex; align-items:flex-end; justify-content:space-between; gap:.5rem; margin-top:auto; }
    .term-hint { color:#b45309; font-size:.67rem; line-height:1.3; }
    .term-save-button,.term-cancel-button { border:0; border-radius:.5rem; padding:.55rem .7rem; font-size:.72rem; font-weight:800; cursor:pointer; }
    .term-save-button { background:#3453b8; color:#fff; }
    .term-save-button:disabled { cursor:not-allowed; opacity:.45; }
    .term-cancel-button { background:#e2e8f0; color:#1e293b; }
    .term-empty { grid-column:1/-1; border:1px dashed #cbd5e1; border-radius:.7rem; background:#fff; color:#64748b; padding:1rem; font-size:.78rem; }
    .term-modal-backdrop { position:fixed; inset:0; z-index:70; display:flex; align-items:center; justify-content:center; padding:1rem; background:rgba(15,23,42,.55); }
    .term-modal { width:100%; max-width:32rem; border-radius:1rem; background:#fff; padding:1.25rem; box-shadow:0 24px 70px rgba(15,23,42,.3); }
    .term-modal h3 { color:#172033; font-size:1rem; font-weight:800; margin:0; }
    .term-modal p { color:#475569; font-size:.8rem; line-height:1.5; margin:.65rem 0 0; }
    .term-modal-actions { display:flex; justify-content:flex-end; gap:.5rem; margin-top:1rem; }
    @container (max-width:28rem) { .term-date-grid { grid-template-columns:1fr; } }
    @media (max-width: 900px) { .term-access-grid { grid-template-columns:1fr; } }
  `],
})
export class TermManagementWindowsComponent implements OnChanges {
  private api = inject(FoundationApi);
  @Input({ required: true }) target!: AcademicSessionView;
  @Input() windowRows: TermManagementWindowView[] = [];
  @Input() canManage = false;
  @Input() english = false;
  @Output() changed = new EventEmitter<TermManagementWindowView>();

  protected readonly rows = signal<TermManagementWindowView[]>([]);
  protected readonly drafts = signal<Record<string, Draft>>({});
  protected readonly savingId = signal<string | null>(null);
  protected readonly attempted = signal<Record<string, boolean>>({});
  protected readonly errors = signal<Record<string, Partial<Record<Field, string>>>>({});
  protected readonly toast = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly pendingRemoval = signal<TermManagementWindowView | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['windowRows'] || changes['target']) this.sync(this.windowRows);
  }

  protected sync(rows: TermManagementWindowView[]): void {
    const sorted = [...(rows ?? [])].sort((a, b) => a.termSequenceNo - b.termSequenceNo);
    this.rows.set(sorted);
    const next: Record<string, Draft> = {};
    for (const row of sorted) next[row.termId] = {
      limited: row.limited,
      opensAt: this.localDateTime(row.opensAt),
      closesAt: this.localDateTime(row.closesAt),
    };
    this.drafts.set(next);
  }

  protected windows(): TermManagementWindowView[] { return this.rows(); }
  protected draft(row: TermManagementWindowView): Draft {
    return this.drafts()[row.termId] ?? { limited: row.limited, opensAt: this.localDateTime(row.opensAt), closesAt: this.localDateTime(row.closesAt) };
  }
  protected set(row: TermManagementWindowView, field: Field, value: string | boolean): void {
    this.drafts.update((all) => ({ ...all, [row.termId]: { ...this.draft(row), [field]: value } }));
    if (this.attempted()[row.termId]) this.validate(row);
  }
  protected blur(row: TermManagementWindowView, field: Field): void {
    this.attempted.update((all) => ({ ...all, [row.termId]: true }));
    this.validate(row);
    if (this.invalid(row, field)) this.focus(row, field);
  }
  protected submitted(row: TermManagementWindowView): boolean { return !!this.attempted()[row.termId]; }
  protected invalid(row: TermManagementWindowView, field: Field): boolean { return !!this.errorFor(row, field); }
  protected errorFor(row: TermManagementWindowView, field: Field): string | null { return this.errors()[row.termId]?.[field] ?? null; }
  protected fieldId(row: TermManagementWindowView, field: Field): string { return `term-window-${row.termId}-${field}`; }

  protected save(row: TermManagementWindowView): void {
    this.attempted.update((all) => ({ ...all, [row.termId]: true }));
    if (!this.validate(row)) {
      this.focusFirstInvalid(row);
      return;
    }
    if (row.limited && !this.draft(row).limited) {
      this.pendingRemoval.set(row);
      return;
    }
    this.saveNow(row);
  }

  protected confirmRemoval(): void {
    const row = this.pendingRemoval();
    if (row) this.saveNow(row);
    this.pendingRemoval.set(null);
  }
  protected cancelRemoval(): void {
    const row = this.pendingRemoval();
    if (row) this.drafts.update((all) => ({ ...all, [row.termId]: { limited: row.limited, opensAt: this.localDateTime(row.opensAt), closesAt: this.localDateTime(row.closesAt) } }));
    this.pendingRemoval.set(null);
  }

  private saveNow(row: TermManagementWindowView): void {
    const draft = this.draft(row);
    const body: TermManagementWindowUpsert = {
      limited: draft.limited,
      opensAt: draft.limited ? this.instant(draft.opensAt) : null,
      closesAt: draft.limited ? this.instant(draft.closesAt) : null,
      version: row.version,
    };
    this.savingId.set(row.termId);
    this.toast.set(null);
    this.api.updateTermManagementWindow(this.target.id, row.termId, body).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.errors.update((all) => ({ ...all, [row.termId]: {} }));
        this.toast.set({ ok: true, text: this.english ? `${row.termCode} saved.` : `${row.termCode} enregistré.` });
        this.changed.emit(updated);
      },
      error: (error) => {
        this.savingId.set(null);
        const fieldErrors = error?.error?.fieldErrors ?? {};
        this.errors.update((all) => ({ ...all, [row.termId]: { ...(all[row.termId] ?? {}), ...fieldErrors } }));
        this.toast.set({ ok: false, text: error?.error?.message ?? (this.english ? 'The trimester access limit could not be saved.' : 'La limite d’accès du trimestre n’a pas pu être enregistrée.') });
        this.focusFirstInvalid(row);
      },
    });
  }

  private validate(row: TermManagementWindowView): boolean {
    const draft = this.draft(row);
    const next: Partial<Record<Field, string>> = {};
    if (draft.limited && draft.opensAt && draft.closesAt && new Date(draft.closesAt).getTime() <= new Date(draft.opensAt).getTime()) {
      next.closesAt = this.english ? 'The closing date must be after the opening date.' : 'La fermeture doit être postérieure à l’ouverture.';
    }
    this.errors.update((all) => ({ ...all, [row.termId]: next }));
    return Object.keys(next).length === 0;
  }

  protected badgeLabel(row: TermManagementWindowView): string {
    const draft = this.draft(row);
    if (!draft.limited || (!draft.opensAt && !draft.closesAt)) return this.english ? 'No date restriction' : 'Aucune restriction de date';
    if (row.state === 'SCHEDULED') return this.english ? 'Opening scheduled' : 'Ouverture programmée';
    if (row.state === 'CLOSED') return this.english ? 'Window ended' : 'Fenêtre terminée';
    if (row.state === 'INVALID') return this.english ? 'Fix this limit' : 'Limite à corriger';
    return this.english ? 'Management allowed now' : 'Gestion autorisée maintenant';
  }
  protected badgeClass(row: TermManagementWindowView): string {
    const draft = this.draft(row);
    if (!draft.limited || (!draft.opensAt && !draft.closesAt) || row.state === 'OPEN') return 'term-badge-open';
    if (row.state === 'SCHEDULED') return 'term-badge-scheduled';
    if (row.state === 'CLOSED') return 'term-badge-closed';
    return 'term-badge-invalid';
  }
  protected summary(row: TermManagementWindowView): string {
    const codes = row.governedPeriodCodes ?? [];
    const label = this.milestoneSentence(codes);
    const draft = this.draft(row);
    if (!draft.limited || (!draft.opensAt && !draft.closesAt)) return this.english ? `${label} have no date restriction.` : `${label} n’ont aucune restriction de date.`;
    if (draft.opensAt && draft.closesAt) return this.english
      ? `Available from ${this.display(draft.opensAt, row.timezone)} until ${this.display(draft.closesAt, row.timezone)}.`
      : `Disponibles du ${this.display(draft.opensAt, row.timezone)} au ${this.display(draft.closesAt, row.timezone)}.`;
    if (draft.opensAt) return this.english
      ? `Available from ${this.display(draft.opensAt, row.timezone)}, with no closing date.`
      : `Disponibles à partir du ${this.display(draft.opensAt, row.timezone)}, sans date de fermeture.`;
    if (draft.closesAt) return this.english
      ? `Available immediately until ${this.display(draft.closesAt, row.timezone)}.`
      : `Disponibles immédiatement jusqu’au ${this.display(draft.closesAt, row.timezone)}.`;
    return this.english ? `${label} need an opening or closing date.` : `${label} nécessitent une date d’ouverture ou de fermeture.`;
  }
  protected readableCode(code: string): string { return code === 'ANNUAL' ? (this.english ? 'Annual result' : 'Résultat annuel') : code.replace('_RESULT', this.english ? ' result' : ' · résultat'); }
  private milestoneSentence(codes: string[]): string {
    const readable = codes.map((code) => this.readableCode(code));
    if (readable.length <= 1) return readable[0] ?? (this.english ? 'These milestones' : 'Ces opérations');
    const last = readable.pop();
    return this.english ? `${readable.join(', ')} and ${last}` : `${readable.join(', ')} et ${last}`;
  }
  protected focus(row: TermManagementWindowView, field: Field): void { queueMicrotask(() => document.getElementById(this.fieldId(row, field))?.focus()); }
  private focusFirstInvalid(row: TermManagementWindowView): void { const field: Field = this.errorFor(row, 'opensAt') ? 'opensAt' : 'closesAt'; this.focus(row, field); }
  private localDateTime(value: string | null): string { if (!value) return ''; const d = new Date(value); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  private instant(value: string): string | null { return value ? new Date(value).toISOString() : null; }
  private display(value: string, timezone: string): string { return new Intl.DateTimeFormat(this.english ? 'en-GB' : 'fr-FR', { dateStyle: 'short', timeStyle: 'short', timeZone: timezone }).format(new Date(value)); }
}
