import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n.service';
import { ChargesApi, ChargeContext } from './charges.api';
import { CollectionsApi, StudentSearchView } from './collections.api';
import {
  BatchInvoiceRequest, BatchJobView, BatchPreviewView, FinanceDocumentApiError, FinanceDocumentView,
  FinanceDocumentsApi, DocumentDetailView, InvoicePreview, InvoiceView,
} from './finance-documents.api';

type DocumentsTab = 'list' | 'invoice' | 'batch';

@Component({
  selector: 'bbc-finance-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './finance-documents.scss',
  template: `
    <div class="documents-shell">
      <header class="documents-hero">
        <div>
          <div class="eyebrow">Finance / {{ fr() ? 'Documents financiers' : 'Financial documents' }} <b>BAY-48 · V64 · XAF</b></div>
          <h1>{{ fr() ? 'Factures, reçus & documents vérifiables' : 'Invoices, receipts & verifiable documents' }}</h1>
          <p>{{ fr() ? 'Chaque document est un instantané serveur immuable, numéroté par école et téléchargeable en PDF.' : 'Every document is an immutable server snapshot, school-sequenced and downloadable as a PDF.' }}</p>
        </div>
        <div class="hero-links"><a routerLink="/finance/collections">{{ fr() ? 'Encaissements' : 'Collections' }}</a><a routerLink="/finance/charges">{{ fr() ? 'Charges' : 'Charges' }}</a><a routerLink="/finance/accounting">{{ fr() ? 'Comptabilité' : 'Accounting' }}</a></div>
      </header>

      @if (error()) { <div class="state state-error" role="alert"><span>{{ error() }} @if (correlationId()) { <small>· {{ correlationId() }}</small> }</span><button type="button" class="icon-button" (click)="clearMessage()">×</button></div> }
      @if (success()) { <div class="state state-success" role="status">{{ success() }}</div> }
      @if (loading()) { <div class="loading-card" aria-live="polite">{{ fr() ? 'Chargement des documents…' : 'Loading financial documents…' }}</div> }

      @if (!loading()) {
        <nav class="document-tabs" aria-label="Financial document workspace">
          <button type="button" [class.active]="tab() === 'list'" (click)="setTab('list')">{{ fr() ? 'Tous les documents' : 'All documents' }}</button>
          <button type="button" [class.active]="tab() === 'invoice'" (click)="setTab('invoice')">{{ fr() ? 'Émettre une facture' : 'Create invoice' }}</button>
          <button type="button" [class.active]="tab() === 'batch'" (click)="setTab('batch')">{{ fr() ? 'Factures de classe / session' : 'Class / session batch' }}</button>
        </nav>

        @switch (tab()) {
          @case ('list') {
            <section class="panel filter-panel" aria-label="Document filters">
              <div class="panel-heading"><div><h2>{{ fr() ? 'Recherche documentaire' : 'Document search' }}</h2><p>{{ fr() ? 'Filtrez par numéro, type, statut, élève ou destinataire.' : 'Filter by number, type, status, student or recipient.' }}</p></div><span class="count-badge">{{ documents().length }}</span></div>
              <div class="filter-grid">
                <label class="field-label">{{ fr() ? 'Type' : 'Type' }}<select class="document-field" [(ngModel)]="typeFilter" (ngModelChange)="loadDocuments()"><option value="">{{ fr() ? 'Tous' : 'All' }}</option><option value="INVOICE">{{ fr() ? 'Factures' : 'Invoices' }}</option><option value="RECEIPT">{{ fr() ? 'Reçus' : 'Receipts' }}</option></select></label>
                <label class="field-label">{{ fr() ? 'Numéro' : 'Number' }}<input class="document-field" [(ngModel)]="numberFilter" (keyup.enter)="loadDocuments()" placeholder="INV/… or RCT/…"></label>
                <label class="field-label">{{ fr() ? 'Statut' : 'Status' }}<select class="document-field" [(ngModel)]="statusFilter" (ngModelChange)="loadDocuments()"><option value="">{{ fr() ? 'Tous' : 'All' }}</option><option value="ISSUED">ISSUED</option><option value="PARTIALLY_PAID">PARTIALLY_PAID</option><option value="PAID">PAID</option><option value="VOIDED">VOIDED</option></select></label>
                <label class="field-label">{{ fr() ? 'Destinataire' : 'Recipient' }}<input class="document-field" [(ngModel)]="recipientFilter" (keyup.enter)="loadDocuments()"></label>
                <label class="field-label">{{ fr() ? 'Du' : 'From' }}<input type="date" class="document-field" [(ngModel)]="fromDate" (change)="loadDocuments()"></label>
                <label class="field-label">{{ fr() ? 'Au' : 'To' }}<input type="date" class="document-field" [(ngModel)]="toDate" (change)="loadDocuments()"></label>
              </div>
              <div class="filter-actions"><button type="button" class="btn-primary" (click)="loadDocuments()">{{ fr() ? 'Rechercher' : 'Search' }}</button><button type="button" class="btn-secondary" (click)="clearFilters()">{{ fr() ? 'Réinitialiser' : 'Reset' }}</button></div>
            </section>
            @if (!documents().length) { <section class="empty-card"><strong>{{ fr() ? 'Aucun document trouvé' : 'No documents found' }}</strong><p>{{ fr() ? 'Émettez une première facture depuis un compte élève ou ouvrez la génération de classe.' : 'Create the first invoice from a student account or open the class batch flow.' }}</p><div class="empty-actions"><button type="button" class="btn-primary" (click)="setTab('invoice')">{{ fr() ? 'Créer une facture' : 'Create invoice' }}</button><button type="button" class="btn-secondary" (click)="setTab('batch')">{{ fr() ? 'Générer en lot' : 'Review batch' }}</button></div></section> }
            @if (documents().length) {
              <section class="panel table-panel"><div class="table-scroll"><table><thead><tr><th>{{ fr() ? 'Document' : 'Document' }}</th><th>{{ fr() ? 'Élève / destinataire' : 'Student / recipient' }}</th><th>{{ fr() ? 'Dates' : 'Dates' }}</th><th>{{ fr() ? 'Montant / solde' : 'Amount / balance' }}</th><th>{{ fr() ? 'État' : 'Status' }}</th><th></th></tr></thead><tbody>@for (doc of documents(); track doc.id) { <tr><td><button type="button" class="document-link" (click)="openDetail(doc)"><strong>{{ doc.documentNumber }}</strong><small>{{ doc.documentType === 'INVOICE' ? (fr() ? 'Facture' : 'Invoice') : (fr() ? 'Reçu' : 'Receipt') }}</small></button></td><td><strong>{{ doc.studentName }}</strong><small>{{ doc.className || '—' }} · {{ doc.recipientName || '—' }}</small></td><td><span>{{ doc.issueDate }}</span><small>{{ doc.dueDate ? ((fr() ? 'Échéance ' : 'Due ') + doc.dueDate) : '' }}</small></td><td><strong>{{ money(doc.totalMinor, doc.currency) }}</strong><small>{{ fr() ? 'Solde' : 'Balance' }} {{ money(doc.outstandingMinor, doc.currency) }}</small></td><td><span class="status-pill" [class.good]="doc.status === 'ISSUED' || doc.status === 'PAID'">{{ doc.status }}</span><small>{{ doc.generatedDocumentStatus || (fr() ? 'PDF indisponible' : 'PDF unavailable') }}</small></td><td><button type="button" class="btn-small" (click)="downloadDocument(doc)" [disabled]="!doc.generatedDocumentId || doc.generatedDocumentStatus !== 'ISSUED'">{{ fr() ? 'PDF' : 'PDF' }}</button></td></tr> }</tbody></table></div></section>
            }
          }
          @case ('invoice') {
            <section class="editor-layout">
              <div class="panel"><div class="panel-heading"><div><h2>{{ fr() ? 'Facture individuelle' : 'Single-student invoice' }}</h2><p>{{ fr() ? 'Choisissez un contexte d’inscription, puis prévisualisez les lignes issues de charges postées.' : 'Choose an enrollment context, then preview lines from posted charge installments.' }}</p></div><span class="status-pill">{{ fr() ? 'Instantané' : 'Snapshot' }}</span></div>
                <label class="field-label">{{ fr() ? 'Rechercher un élève' : 'Search student' }} *<div class="inline-search"><input class="document-field" [(ngModel)]="studentQuery" (keyup.enter)="searchStudents()" placeholder="Nom · matricule · classe"><button type="button" class="btn-secondary" (click)="searchStudents()">{{ fr() ? 'Rechercher' : 'Search' }}</button></div></label>
                @if (studentResults().length) { <div class="choice-list" role="listbox" aria-label="Student search results">@for (student of studentResults(); track student.enrollmentId) { <button type="button" class="choice" [class.selected]="selectedStudent()?.enrollmentId === student.enrollmentId" (click)="chooseStudent(student)"><span><strong>{{ student.studentName }}</strong><small>{{ student.matricule || '—' }} · {{ student.className || '—' }}</small></span><b>{{ money(student.outstandingMinor) }}</b></button> }</div> }
                @if (selectedStudent(); as student) { <div class="selected-context"><strong>{{ student.studentName }}</strong><span>{{ student.matricule || '—' }} · {{ student.className || '—' }} · {{ fr() ? 'Inscription active sélectionnée' : 'Active enrollment selected' }}</span></div> }
                <div class="form-grid"><label class="field-label">{{ fr() ? 'Date d’émission' : 'Issue date' }} *<input type="date" class="document-field" [(ngModel)]="issueDate"></label><label class="field-label">{{ fr() ? 'Date d’échéance' : 'Due date' }} *<input type="date" class="document-field" [(ngModel)]="dueDate"></label></div>
                <button type="button" class="btn-primary full-button" [disabled]="busy() || !selectedStudent()" (click)="previewInvoice()">{{ busy() ? '…' : (fr() ? 'Prévisualiser les lignes' : 'Preview charge lines') }}</button>
              </div>
              <div class="panel preview-panel"><div class="panel-heading"><div><h2>{{ fr() ? 'Revue avant émission' : 'Review before issue' }}</h2><p>{{ fr() ? 'Le PDF et le numéro ne sont créés qu’après confirmation.' : 'The PDF and number are created only after confirmation.' }}</p></div></div>
                @if (!invoicePreview()) { <div class="empty-inline">{{ fr() ? 'Sélectionnez un élève pour afficher les charges et le destinataire.' : 'Select a student to show charges and recipient.' }}</div> }
                @if (invoicePreview(); as preview) { <div class="summary-strip"><div><small>{{ fr() ? 'Total' : 'Total' }}</small><strong>{{ money(preview.totalMinor, preview.currency) }}</strong></div><div><small>{{ fr() ? 'Solde' : 'Balance' }}</small><strong>{{ money(preview.outstandingMinor, preview.currency) }}</strong></div><div><small>{{ fr() ? 'Lignes' : 'Lines' }}</small><strong>{{ preview.lines.length }}</strong></div></div><div class="recipient-card"><small>{{ fr() ? 'Destinataire' : 'Recipient' }}</small><strong>{{ preview.recipient.name || '—' }}</strong><span>{{ preview.recipient.email || preview.recipient.phone || '' }}</span>@if (preview.recipient.warning) { <em>{{ preview.recipient.warning }}</em> }</div>@if (preview.blockers.length) { <div class="blocked-card"><strong>{{ fr() ? 'Émission bloquée' : 'Issue blocked' }}</strong><ul>@for (blocker of preview.blockers; track blocker.code) { <li><b>{{ blocker.code }}</b> — {{ blocker.message }}</li> }</ul></div> }<div class="line-list">@for (line of preview.lines; track line.installmentId) { <div class="line-item"><span><strong>{{ line.feeTypeCode }}</strong><small>{{ line.descriptionFr }} / {{ line.descriptionEn }} · {{ line.dueDate }}</small></span><b>{{ money(line.amountMinor, line.currency) }}</b></div> }</div><button type="button" class="btn-primary full-button" [disabled]="busy() || !preview.ready || preview.alreadyIssued" (click)="issueInvoice()">{{ preview.alreadyIssued ? (fr() ? 'Déjà facturée' : 'Already invoiced') : (fr() ? 'Émettre la facture PDF' : 'Issue invoice PDF') }}</button> }
              </div>
            </section>
          }
          @case ('batch') {
                <section class="editor-layout batch-layout"><div class="panel"><div class="panel-heading"><div><h2>{{ fr() ? 'Facturation de classe / session' : 'Class / session invoicing' }}</h2><p>{{ fr() ? 'Prévisualisez les élèves, destinataires, montants et blocages avant le lot.' : 'Preview students, recipients, amounts and blockers before the batch.' }}</p></div><span class="status-pill">{{ fr() ? 'Revue obligatoire' : 'Review required' }}</span></div><div class="form-grid"><label class="field-label">{{ fr() ? 'Session académique' : 'Academic session' }} *<select class="document-field" [(ngModel)]="batchSessionId"><option value="">{{ fr() ? 'Choisir une session' : 'Choose a session' }}</option>@for (session of context()?.sessions || []; track session.id) { <option [value]="session.id">{{ session.code }} · {{ session.label }}</option> }</select></label><label class="field-label">{{ fr() ? 'Classe (optionnel)' : 'Class (optional)' }}<select class="document-field" [(ngModel)]="batchClassId"><option [ngValue]="null">{{ fr() ? 'Toutes les classes' : 'All classes' }}</option>@for (klass of context()?.classes || []; track klass.id) { <option [ngValue]="klass.id">{{ klass.code }} · {{ klass.name }}</option> }</select></label><label class="field-label">{{ fr() ? 'Date d’émission' : 'Issue date' }} *<input type="date" class="document-field" [(ngModel)]="batchIssueDate"></label><label class="field-label">{{ fr() ? 'Date d’échéance' : 'Due date' }} *<input type="date" class="document-field" [(ngModel)]="batchDueDate"></label></div><button type="button" class="btn-primary full-button" [disabled]="busy() || !batchSessionId" (click)="previewBatch()">{{ fr() ? 'Prévisualiser le lot' : 'Preview batch' }}</button></div>
              <div class="panel preview-panel"><div class="panel-heading"><div><h2>{{ fr() ? 'Impact et blocages' : 'Impact and blockers' }}</h2><p>{{ fr() ? 'Les lignes bloquées restent visibles et exportables.' : 'Blocked rows remain visible and exportable.' }}</p></div></div>@if (!batchPreview()) { <div class="empty-inline">{{ fr() ? 'Choisissez une session pour commencer.' : 'Choose a session to begin.' }}</div> }@if (batchPreview(); as preview) { <div class="summary-strip"><div><small>{{ fr() ? 'Élèves' : 'Students' }}</small><strong>{{ preview.affectedCount }}</strong></div><div><small>{{ fr() ? 'Total prévu' : 'Projected total' }}</small><strong>{{ money(preview.totalMinor) }}</strong></div><div><small>{{ fr() ? 'Déjà émis' : 'Already issued' }}</small><strong>{{ preview.alreadyIssuedCount }}</strong></div><div><small>{{ fr() ? 'Bloqués' : 'Blocked' }}</small><strong class="danger-text">{{ preview.blockedCount }}</strong></div></div><div class="batch-table"><div class="batch-head"><span>{{ fr() ? 'Élève' : 'Student' }}</span><span>{{ fr() ? 'Destinataire' : 'Recipient' }}</span><span>{{ fr() ? 'Montant' : 'Amount' }}</span><span>{{ fr() ? 'Résultat' : 'Result' }}</span></div>@for (row of preview.rows; track row.enrollmentId) { <div class="batch-row"><span><strong>{{ row.studentName }}</strong><small>{{ row.matricule || '—' }} · {{ row.className || '—' }}</small></span><span>{{ row.recipientName || '—' }}</span><span>{{ money(row.amountMinor) }}</span><span><b [class.danger-text]="row.resultStatus === 'BLOCKED'">{{ row.resultStatus }}</b>@if (row.blockerMessage) { <small>{{ row.blockerMessage }}</small> }</span></div> }</div><button type="button" class="btn-primary full-button" [disabled]="busy() || !preview.affectedCount" (click)="issueBatch()">{{ fr() ? 'Confirmer et émettre le lot' : 'Confirm and issue batch' }}</button> }
                @if (batchJob(); as job) { <div class="job-card"><strong>{{ fr() ? 'Résultat du lot' : 'Batch result' }} · {{ job.status }}</strong><span>{{ job.issuedCount }} {{ fr() ? 'émis' : 'issued' }} · {{ job.blockedCount }} {{ fr() ? 'bloqués' : 'blocked' }} · {{ job.failedCount }} {{ fr() ? 'échoués' : 'failed' }}</span><div class="job-actions"><button type="button" class="btn-secondary" (click)="downloadFailures(job)">{{ fr() ? 'Télécharger les lignes bloquées' : 'Download blocked rows' }}</button><button type="button" class="btn-secondary" [disabled]="!job.failedCount || busy()" (click)="retryBatch(job)">{{ fr() ? 'Réessayer les échecs' : 'Retry failed only' }}</button></div></div> }
              </div>
            </section>
          }
        }
      }

      @if (selectedDetail(); as detail) { <div class="detail-backdrop" (click)="closeDetail()"><aside class="detail-drawer" (click)="$event.stopPropagation()" aria-label="Document detail"><div class="drawer-header"><div><span class="eyebrow">{{ detail.documentType }}</span><h2>{{ detail.invoice?.invoiceNumber || detail.receipt?.receiptNumber }}</h2></div><button type="button" class="icon-button" (click)="closeDetail()">×</button></div>@if (detail.invoice; as invoice) { <div class="detail-summary"><strong>{{ invoice.studentName }}</strong><span>{{ invoice.className || '—' }} · {{ invoice.recipient.name || '—' }}</span><b>{{ money(invoice.totalMinor, invoice.currency) }}</b></div><dl class="detail-list"><div><dt>{{ fr() ? 'État' : 'Status' }}</dt><dd>{{ invoice.status }}</dd></div><div><dt>{{ fr() ? 'Émis' : 'Issued' }}</dt><dd>{{ invoice.issueDate }}</dd></div><div><dt>{{ fr() ? 'Solde' : 'Balance' }}</dt><dd>{{ money(invoice.outstandingMinor, invoice.currency) }}</dd></div><div><dt>SHA-256</dt><dd class="hash">{{ invoice.snapshotHash }}</dd></div></dl><h3>{{ fr() ? 'Lignes instantanées' : 'Snapshot lines' }}</h3><div class="line-list">@for (line of invoice.lines; track line.id) { <div class="line-item"><span><strong>{{ line.feeTypeCode }}</strong><small>{{ line.descriptionFr }} · {{ line.dueDate }}</small></span><b>{{ money(line.amountMinor, line.currency) }}</b></div> }</div> }@if (detail.receipt; as receipt) { <div class="detail-summary"><strong>{{ receipt.studentName }}</strong><span>{{ receipt.channelCode }} · {{ receipt.reference || (fr() ? 'Sans référence' : 'No reference') }}</span><b>{{ money(receipt.amountMinor, receipt.currency) }}</b></div><dl class="detail-list"><div><dt>{{ fr() ? 'Affecté' : 'Allocated' }}</dt><dd>{{ money(receipt.allocatedMinor, receipt.currency) }}</dd></div><div><dt>{{ fr() ? 'Crédit' : 'Credit' }}</dt><dd>{{ money(receipt.creditMinor, receipt.currency) }}</dd></div><div><dt>SHA-256</dt><dd class="hash">{{ receipt.snapshotHash }}</dd></div></dl> }@if (detail.generatedDocument; as generated) { <div class="generated-card"><strong>{{ generated.documentNumber }}</strong><span>{{ generated.visibility }} · {{ generated.status }} · {{ generated.sizeBytes }} bytes</span><small>Vérification / Verification: /api/official-documents/verify/{{ generated.documentNumber }}</small></div> }<h3>{{ fr() ? 'Historique d’audit' : 'Audit history' }}</h3><div class="audit-list">@for (event of detail.audit; track event.id) { <div><strong>{{ event.action }}</strong><small>{{ event.createdAt }} · {{ event.reason || '' }}</small></div> }@empty { <p class="muted">{{ fr() ? 'Aucun événement supplémentaire.' : 'No additional audit events.' }}</p> }</div><div class="drawer-actions"><button type="button" class="btn-primary" [disabled]="!detail.generatedDocument || detail.generatedDocument.status !== 'ISSUED'" (click)="downloadDetail(detail)">{{ fr() ? 'Télécharger le PDF' : 'Download PDF' }}</button>@if (detail.invoice && detail.invoice.status !== 'VOIDED' && detail.invoice.status !== 'SUPERSEDED') { <button type="button" class="btn-danger" (click)="voidSelected(detail.invoice)">{{ fr() ? 'Annuler avec motif' : 'Void with reason' }}</button> }</div></aside></div> }
    </div>
  `,
})
export class FinanceDocumentsComponent implements OnInit {
  private api = inject(FinanceDocumentsApi);
  private collections = inject(CollectionsApi);
  private charges = inject(ChargesApi);
  private i18n = inject(I18nService);
  protected fr = () => this.i18n.lang() === 'fr';
  protected tab = signal<DocumentsTab>('list');
  protected loading = signal(true);
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected success = signal<string | null>(null);
  protected correlationId = signal<string | null>(null);
  protected documents = signal<FinanceDocumentView[]>([]);
  protected selectedDetail = signal<DocumentDetailView | null>(null);
  protected context = signal<ChargeContext | null>(null);
  protected studentResults = signal<StudentSearchView[]>([]);
  protected selectedStudent = signal<StudentSearchView | null>(null);
  protected invoicePreview = signal<InvoicePreview | null>(null);
  protected batchPreview = signal<BatchPreviewView | null>(null);
  protected batchJob = signal<BatchJobView | null>(null);
  protected typeFilter = '';
  protected numberFilter = '';
  protected statusFilter = '';
  protected recipientFilter = '';
  protected fromDate = '';
  protected toDate = '';
  protected studentQuery = '';
  protected issueDate = today();
  protected dueDate = today();
  protected batchSessionId = '';
  protected batchClassId: string | null = null;
  protected batchIssueDate = today();
  protected batchDueDate = today();

  ngOnInit(): void { this.loadDocuments(); this.charges.context().subscribe({ next: value => this.context.set(value), error: () => undefined }); }
  protected setTab(tab: DocumentsTab): void { this.tab.set(tab); this.clearMessage(); if (tab === 'list') this.loadDocuments(); }
  protected clearMessage(): void { this.error.set(null); this.correlationId.set(null); }
  protected clearFilters(): void { this.typeFilter = ''; this.numberFilter = ''; this.statusFilter = ''; this.recipientFilter = ''; this.fromDate = ''; this.toDate = ''; this.loadDocuments(); }
  protected loadDocuments(): void { this.loading.set(true); this.api.list({ type: this.typeFilter, number: this.numberFilter, status: this.statusFilter, recipient: this.recipientFilter, fromDate: this.fromDate, toDate: this.toDate }).subscribe({ next: value => { this.documents.set(value); this.loading.set(false); }, error: err => { this.loading.set(false); this.applyError(err); } }); }
  protected searchStudents(): void { if (!this.studentQuery.trim()) { this.error.set(this.fr() ? 'Saisissez un nom, matricule ou classe.' : 'Enter a name, matricule or class.'); return; } this.collections.search(this.studentQuery.trim()).subscribe({ next: value => this.studentResults.set(value), error: err => this.applyError(err) }); }
  protected chooseStudent(value: StudentSearchView): void { this.selectedStudent.set(value); this.invoicePreview.set(null); }
  protected previewInvoice(): void { const student = this.selectedStudent(); if (!student) return; this.busy.set(true); this.clearMessage(); this.api.previewInvoice({ enrollmentId: student.enrollmentId, issueDate: this.issueDate, dueDate: this.dueDate, installmentIds: [], recipientGuardianId: null, locale: this.fr() ? 'fr' : 'en' }).subscribe({ next: value => { this.invoicePreview.set(value); this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected issueInvoice(): void { const student = this.selectedStudent(); const preview = this.invoicePreview(); if (!student || !preview?.ready || preview.alreadyIssued) return; this.busy.set(true); this.api.issueInvoice({ enrollmentId: student.enrollmentId, issueDate: this.issueDate, dueDate: this.dueDate, installmentIds: preview.lines.map(line => line.installmentId), recipientGuardianId: preview.recipient.guardianId, locale: this.fr() ? 'fr' : 'en' }, `invoice-ui-${Date.now()}-${Math.random().toString(36).slice(2)}`).subscribe({ next: value => { this.busy.set(false); this.success.set((this.fr() ? 'Facture émise : ' : 'Invoice issued: ') + value.invoiceNumber); this.openDetailByType('INVOICE', value.id); this.loadDocuments(); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected previewBatch(): void { if (!this.batchSessionId) return; this.busy.set(true); this.clearMessage(); this.api.previewBatch(this.batchRequest()).subscribe({ next: value => { this.batchPreview.set(value); this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected issueBatch(): void { const preview = this.batchPreview(); if (!preview) return; this.busy.set(true); this.api.issueBatch(this.batchRequest(), `invoice-batch-ui-${Date.now()}-${Math.random().toString(36).slice(2)}`).subscribe({ next: value => { this.batchJob.set(value); this.busy.set(false); this.success.set(this.fr() ? 'Lot traité : les lignes bloquées restent téléchargeables.' : 'Batch processed; blocked rows remain downloadable.'); this.loadDocuments(); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected retryBatch(job: BatchJobView): void { this.busy.set(true); this.api.retryFailed(job.id, `invoice-batch-retry-${Date.now()}`).subscribe({ next: value => { this.batchJob.set(value); this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected downloadFailures(job: BatchJobView): void { this.api.batchFailures(job.id).subscribe({ next: blob => this.saveBlob(blob, `invoice-batch-${job.id}-failures.csv`), error: err => this.applyError(err) }); }
  protected openDetail(doc: FinanceDocumentView): void { this.openDetailByType(doc.documentType, doc.id); }
  private openDetailByType(type: string, id: string): void { this.busy.set(true); this.api.detail(type, id).subscribe({ next: value => { this.selectedDetail.set(value); this.busy.set(false); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected closeDetail(): void { this.selectedDetail.set(null); }
  protected downloadDocument(doc: FinanceDocumentView): void { this.api.download(doc.documentType, doc.id).subscribe({ next: blob => this.saveBlob(blob, `${doc.documentNumber}.pdf`), error: err => this.applyError(err) }); }
  protected downloadDetail(detail: DocumentDetailView): void { const type = detail.documentType; const id = detail.invoice?.id || detail.receipt?.id; if (!id) return; this.api.download(type, id).subscribe({ next: blob => this.saveBlob(blob, `${detail.generatedDocument?.documentNumber || 'financial-document'}.pdf`), error: err => this.applyError(err) }); }
  protected voidSelected(invoice: InvoiceView): void { const reason = window.prompt(this.fr() ? 'Motif obligatoire pour annuler cette facture :' : 'Reason required to void this invoice:'); if (!reason?.trim()) return; this.busy.set(true); this.api.voidInvoice(invoice.id, reason.trim(), invoice.version).subscribe({ next: value => { this.busy.set(false); this.success.set(this.fr() ? 'Facture annulée et conservée dans l’historique.' : 'Invoice voided and retained in history.'); this.openDetailByType('INVOICE', value.id); this.loadDocuments(); }, error: err => { this.busy.set(false); this.applyError(err); } }); }
  protected money(value: number, currency = 'XAF'): string { return new Intl.NumberFormat(this.fr() ? 'fr-FR' : 'en-US', { maximumFractionDigits: 0 }).format(value || 0) + ' ' + currency; }
  private batchRequest(): BatchInvoiceRequest { return { academicSessionId: this.batchSessionId, schoolClassId: this.batchClassId || null, issueDate: this.batchIssueDate, dueDate: this.batchDueDate, locale: this.fr() ? 'fr' : 'en' }; }
  private saveBlob(blob: Blob, filename: string): void { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url); }
  private applyError(err: FinanceDocumentApiError): void { this.error.set(err?.error?.message || err?.message || (this.fr() ? 'Le serveur n’a pas pu traiter le document.' : 'The server could not process the document.')); this.correlationId.set(err?.error?.correlationId || null); }
}

function today(): string { return new Date().toISOString().slice(0, 10); }
