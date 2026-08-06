import { ChangeDetectionStrategy, Component, Input, OnChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Student } from '../../core/models';
import { ClassView } from '../../core/setup.api';
import { AcademicContextService } from '../../core/academic-context.service';
import { AuditView, EnrollmentView, FoundationApi, GeneratedDocumentView } from '../../core/foundation.api';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { IconComponent } from '../../core/ui';

@Component({
  selector: 'bbc-student-enrollment-panel',
  standalone: true,
  imports: [FormsModule, DatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rounded-xl border border-slate-200 overflow-hidden">
      <div class="flex items-center justify-between px-4 py-3 bg-slate-50 border-b border-slate-200">
        <div>
          <div class="text-[11px] uppercase tracking-wider text-mute font-semibold">{{ fr() ? 'Inscriptions et historique de classe' : 'Enrollment and class history' }}</div>
          <div class="text-xs text-mute mt-0.5">{{ fr() ? 'Historique lié aux sessions académiques' : 'History linked to academic sessions' }}</div>
        </div>
        <div class="flex gap-2">
          @if (canManage() && active()) { <button (click)="transferOpen.set(!transferOpen())" class="btn-secondary"><bbc-icon name="edit" [s]="13" /> {{ fr() ? 'Transférer' : 'Transfer' }}</button> }
          @if (canGenerate()) { <button (click)="generateCertificate()" [disabled]="generating()" class="btn-primary"><bbc-icon name="file" [s]="13" /> {{ generating() ? '…' : (fr() ? 'Certificat' : 'Certificate') }}</button> }
        </div>
      </div>

      @if (error(); as e) { <div class="m-3 px-3 py-2 text-xs rounded-lg bg-rose-50 text-rose-700">{{ e }}</div> }

      @if (transferOpen() && active(); as current) {
        <div class="p-4 bg-brand-50/50 border-b border-brand-100">
          <div class="mb-3 text-xs text-slate-700 rounded-lg bg-white border border-brand-100 px-3 py-2">
            <strong>{{ fr() ? 'Ce que fera le transfert :' : 'What the transfer will do:' }}</strong>
            {{ fr()
              ? ' clôturer l’inscription active dans ' + (current.className || 'Sans classe') + ' à la date choisie, puis créer une nouvelle inscription active dans la classe cible. L’historique sera conservé.'
              : ' close the active enrollment in ' + (current.className || 'Unassigned') + ' on the selected date, then create a new active enrollment in the target class. History will be preserved.' }}
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <label><span class="meta">{{ fr() ? 'Classe cible' : 'Target class' }}</span>
              <select [(ngModel)]="transferDraft.classId" class="field"><option [ngValue]="null">{{ fr() ? 'Sans classe' : 'Unassigned' }}</option>@for (c of classes; track c.id) { <option [value]="c.id">{{ c.name }}</option> }</select>
            </label>
            <label><span class="meta">{{ fr() ? 'Date effective' : 'Effective date' }}</span><input type="date" [(ngModel)]="transferDraft.effectiveDate" [min]="current.enrolledOn" [max]="sessionEnd(current.academicSessionId)" class="field" /></label>
            <label><span class="meta">{{ fr() ? 'Motif obligatoire' : 'Required reason' }}</span><input [(ngModel)]="transferDraft.reason" class="field" /></label>
          </div>
          @if (transferValidation(current); as validation) {
            <div class="mt-2 text-xs text-rose-700">{{ validation }}</div>
          }
          <div class="flex justify-end gap-2 mt-3">
            <button (click)="transferOpen.set(false)" class="btn-secondary">{{ fr() ? 'Annuler' : 'Cancel' }}</button>
            <button (click)="transfer(current)" [disabled]="!transferDraft.reason.trim() || transferDraft.classId === current.classId || !!transferValidation(current)" class="btn-primary">{{ fr() ? 'Confirmer le transfert' : 'Confirm transfer' }}</button>
          </div>
        </div>
      }

      <div class="p-4">
        @if (loading()) { <div class="text-sm text-mute py-3">{{ fr() ? 'Chargement…' : 'Loading…' }}</div> }
        @for (e of enrollments(); track e.id) {
          <div class="relative pl-7 pb-4 last:pb-0">
            <div class="absolute left-[5px] top-3 bottom-0 w-px bg-slate-200"></div>
            <div class="absolute left-0 top-1.5 w-3 h-3 rounded-full border-2 border-white" [class]="e.status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-slate-300'"></div>
            <div class="flex items-start justify-between gap-3">
              <div><div class="font-semibold text-sm text-ink">{{ e.sessionLabel }} · {{ e.className || (fr() ? 'Sans classe' : 'Unassigned') }}</div>
                <div class="text-xs text-mute">{{ e.enrolledOn }}@if (e.exitedOn) { → {{ e.exitedOn }} } · {{ statusLabel(e.status) }} · {{ e.source }}</div>
                @if (e.reason) { <div class="text-xs text-slate-600 mt-1">{{ e.reason }}</div> }
              </div>
              @if (canManage() && e.status === 'ACTIVE') { <button (click)="withdraw(e)" class="text-xs text-rose-600">{{ fr() ? 'Retirer' : 'Withdraw' }}</button> }
            </div>
          </div>
        } @empty { @if (!loading()) { <div class="text-sm text-mute">{{ fr() ? 'Aucune inscription historique.' : 'No enrollment history.' }}</div> } }
      </div>

      @if (documents().length) {
        <div class="px-4 pb-4">
          <div class="meta mb-2">{{ fr() ? 'Documents officiels' : 'Official documents' }}</div>
          @for (d of documents(); track d.id) {
            <div class="flex items-center gap-2 py-2 border-t border-slate-100 text-sm">
              <bbc-icon name="file" [s]="14" /><span class="flex-1 font-medium">{{ d.title }}</span>
              <span class="text-[10px] font-mono text-mute">{{ d.documentNumber }}</span>
              <span class="text-[10px]" [class.text-rose-600]="d.status === 'REVOKED'">{{ d.status }}</span>
              @if (d.status !== 'REVOKED') { <button (click)="download(d)" class="text-xs text-brand-700">{{ fr() ? 'Ouvrir' : 'Open' }}</button> }
              @if (canRevoke() && d.status !== 'REVOKED') { <button (click)="revoke(d)" class="text-xs text-rose-600">{{ fr() ? 'Révoquer' : 'Revoke' }}</button> }
            </div>
          }
        </div>
      }

      <div class="border-t border-slate-100">
        <button (click)="toggleAudit()" class="w-full px-4 py-2.5 flex items-center justify-between text-xs font-semibold text-slate-600 hover:bg-slate-50">
          <span><bbc-icon name="shield" [s]="13" /> {{ fr() ? 'Journal d’audit' : 'Audit trail' }}</span><span>{{ auditOpen() ? '−' : '+' }}</span>
        </button>
        @if (auditOpen()) {
          <div class="px-4 pb-4 max-h-56 overflow-auto">
            @for (a of audits(); track a.id) { <div class="py-2 border-t border-slate-100 text-xs"><div class="font-semibold">{{ actionLabel(a.action) }}</div><div class="text-mute">{{ a.occurredAt | date:'medium' }} · {{ a.actorUsername }}@if (a.reason) { · {{ a.reason }} }</div></div> }
            @empty { <div class="text-xs text-mute py-2">{{ fr() ? 'Aucun événement.' : 'No events.' }}</div> }
          </div>
        }
      </div>
    </section>
  `,
  styles: [`
    .field{width:100%;height:2.4rem;padding:0 .7rem;border:1px solid #e2e8f0;border-radius:.5rem;background:white;font-size:.8rem}.meta{display:block;font-size:.62rem;color:#64748b;text-transform:uppercase;letter-spacing:.05em;font-weight:700;margin-bottom:.25rem}
    .btn-primary,.btn-secondary{height:2rem;padding:0 .7rem;border-radius:.45rem;font-size:.7rem;font-weight:700;display:inline-flex;align-items:center;gap:.25rem}.btn-primary{background:#3453b8;color:white}.btn-secondary{background:white;border:1px solid #e2e8f0;color:#334155}.btn-primary:disabled{opacity:.45}
  `],
})
export class StudentEnrollmentPanelComponent implements OnChanges {
  @Input({ required: true }) student!: Student;
  @Input() classes: ClassView[] = [];
  private api = inject(FoundationApi);
  private context = inject(AcademicContextService);
  private auth = inject(AuthService);
  protected i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected enrollments = signal<EnrollmentView[]>([]);
  protected documents = signal<GeneratedDocumentView[]>([]);
  protected audits = signal<AuditView[]>([]);
  protected permissions = signal<Record<string, boolean>>({});
  protected loading = signal(false);
  protected error = signal<string | null>(null);
  protected transferOpen = signal(false);
  protected auditOpen = signal(false);
  protected generating = signal(false);
  protected active = signal<EnrollmentView | null>(null);
  protected transferDraft = { classId: null as string | null, effectiveDate: this.today(), reason: '' };
  protected canManage = () => this.permissions()['ENROLLMENT_MANAGE'] ?? this.auth.can('students', 'write');
  protected canGenerate = () => this.permissions()['DOCUMENT_GENERATE'] ?? this.auth.can('documents', 'write');
  protected canRevoke = () => this.permissions()['DOCUMENT_REVOKE'] ?? this.auth.can('documents', 'write');

  ngOnChanges(): void { if (this.student?.id) this.reload(); }

  protected transfer(current: EnrollmentView): void {
    this.error.set(null);
    this.api.transfer(this.student.id, { academicSessionId: current.academicSessionId, classId: this.transferDraft.classId, effectiveDate: this.transferDraft.effectiveDate, reason: this.transferDraft.reason.trim(), version: current.version })
      .subscribe({ next: () => { this.transferOpen.set(false); this.transferDraft.reason = ''; this.reload(); }, error: (e) => this.setError(e) });
  }
  protected sessionEnd(sessionId: string): string | null { return this.context.sessions().find((s) => s.id === sessionId)?.endDate ?? null; }
  protected transferValidation(current: EnrollmentView): string | null {
    const date = this.transferDraft.effectiveDate;
    const end = this.sessionEnd(current.academicSessionId);
    if (!date) return this.fr() ? 'Choisissez une date effective.' : 'Select an effective date.';
    if (date < current.enrolledOn) return this.fr()
      ? `La date doit être le ${current.enrolledOn} ou après (date de l’inscription active).`
      : `The date must be ${current.enrolledOn} or later (active enrollment date).`;
    if (end && date > end) return this.fr()
      ? `La date doit être le ${end} ou avant (fin de la session).`
      : `The date must be ${end} or earlier (session end).`;
    return null;
  }
  protected withdraw(e: EnrollmentView): void {
    const reason = prompt(this.fr() ? 'Motif du retrait' : 'Reason for withdrawal'); if (!reason) return;
    const date = prompt(this.fr() ? 'Date effective (AAAA-MM-JJ)' : 'Effective date (YYYY-MM-DD)', this.transferDraft.effectiveDate) ?? '';
    if (!date) return;
    this.api.withdraw(e.id, { effectiveDate: date, reason, version: e.version }).subscribe({ next: () => this.reload(), error: (x) => this.setError(x) });
  }
  protected generateCertificate(): void {
    const current = this.active(); if (!current) { this.error.set(this.fr() ? 'Aucune inscription active.' : 'No active enrollment.'); return; }
    this.generating.set(true); this.error.set(null);
    const key = `enrollment-certificate-${this.student.id}-${current.id}-${current.version}-${this.i18n.lang()}`;
    this.api.generateDocument({ documentType: 'ENROLLMENT_CERTIFICATE', aggregateType: 'Student', aggregateId: this.student.id,
      aggregateVersion: `${current.id}-${current.version}`, locale: this.i18n.lang(), title: this.fr() ? 'Certificat de scolarité' : 'Enrollment certificate', visibility: 'PARENT',
      values: { studentName: this.student.name, matricule: this.student.matricule, className: current.className ?? '—', sessionLabel: current.sessionLabel } }, key)
      .subscribe({ next: (d) => { this.generating.set(false); this.loadDocuments(); this.download(d); }, error: (e) => { this.generating.set(false); this.setError(e); } });
  }
  protected download(d: GeneratedDocumentView): void { this.api.documentContent(d.id).subscribe({ next: (blob) => { const url = URL.createObjectURL(blob); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60_000); }, error: (e) => this.setError(e) }); }
  protected revoke(d: GeneratedDocumentView): void { const reason = prompt(this.fr() ? 'Motif de révocation' : 'Revocation reason'); if (!reason) return; this.api.revokeDocument(d.id, reason).subscribe({ next: () => this.loadDocuments(), error: (e) => this.setError(e) }); }
  protected toggleAudit(): void { this.auditOpen.set(!this.auditOpen()); if (this.auditOpen()) this.api.audit('Student', this.student.id).subscribe({ next: (a) => this.audits.set(a), error: (e) => this.setError(e) }); }
  protected statusLabel(s: string): string { const map: Record<string,string> = { ACTIVE: this.fr() ? 'Active' : 'Active', TRANSFERRED: this.fr() ? 'Transférée' : 'Transferred', WITHDRAWN: this.fr() ? 'Retirée' : 'Withdrawn', COMPLETED: this.fr() ? 'Terminée' : 'Completed' }; return map[s] ?? s; }
  protected actionLabel(a: string): string { return a.replaceAll('_', ' ').toLowerCase(); }

  private reload(): void {
    this.loading.set(true); this.error.set(null); this.context.load();
    this.api.enrollmentHistory(this.student.id).subscribe({ next: (rows) => { this.enrollments.set(rows); const currentId = this.context.sessionId(); const active = rows.find((e) => e.status === 'ACTIVE' && (!currentId || e.academicSessionId === currentId)) ?? rows.find((e) => e.status === 'ACTIVE') ?? null; this.active.set(active); this.transferDraft.classId = active?.classId ?? this.student.classId ?? null; const session = this.context.sessions().find((s) => s.id === active?.academicSessionId); this.transferDraft.effectiveDate = this.clampedToday(session?.startDate, session?.endDate); this.loading.set(false); }, error: (e) => { this.loading.set(false); this.setError(e); } });
    this.loadDocuments(); this.api.actionPermissions().subscribe((p) => this.permissions.set(p));
  }
  private loadDocuments(): void { this.api.listDocuments('Student', this.student.id).subscribe({ next: (d) => this.documents.set(d), error: () => this.documents.set([]) }); }
  private setError(e: any): void {
    const message = e?.error?.message;
    if (typeof message === 'string') { this.error.set(message); return; }
    if (message && typeof message === 'object') {
      const details = Object.entries(message).map(([field, value]) => `${field}: ${String(value)}`).join(' · ');
      if (details) { this.error.set(details); return; }
    }
    this.error.set(this.fr() ? 'Opération impossible. Vérifiez les données saisies puis réessayez.' : 'Operation failed. Check the entered data and try again.');
  }
  private today(): string { return new Date().toISOString().slice(0, 10); }
  private clampedToday(start?: string, end?: string): string { const today = this.today(); if (start && today < start) return start; if (end && today > end) return end; return today; }
}
