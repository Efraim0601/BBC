import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface FinanceDocumentApiError {
  error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { code?: string; message?: string; actionLink?: string }[]; correlationId?: string };
  message?: string;
}
export interface RecipientView { guardianId: string | null; name: string | null; email: string | null; phone: string | null; source: string; warning: string | null; selectionRequired: boolean; }
export interface InvoiceLinePreview { chargeId: string; installmentId: string; feeTypeCode: string; feeTypeNameFr: string; feeTypeNameEn: string; descriptionFr: string; descriptionEn: string; dueDate: string; amountMinor: number; paidMinor: number; outstandingMinor: number; currency: string; }
export interface InvoicePreview { enrollmentId: string; studentId: string; studentName: string; matricule: string | null; className: string | null; academicSessionId: string; sessionLabel: string; issueDate: string; dueDate: string; recipient: RecipientView; lines: InvoiceLinePreview[]; totalMinor: number; paidMinor: number; outstandingMinor: number; currency: string; ready: boolean; alreadyIssued: boolean; blockers: { code: string; message: string; actionLink: string }[]; }
export interface InvoiceLineView extends InvoiceLinePreview { id: string; chargeId: string; installmentId: string; }
export interface InvoiceView { id: string; studentId: string; enrollmentId: string; academicSessionId: string; studentName: string; matricule: string | null; className: string | null; sessionLabel: string | null; invoiceNumber: string; status: string; issueDate: string; dueDate: string; currency: string; totalMinor: number; paidMinor: number; outstandingMinor: number; recipient: RecipientView; snapshotHash: string; generatedDocumentId: string | null; generatedDocumentNumber: string | null; generatedDocumentStatus: string | null; sourceJournalId: string | null; supersededByInvoiceId: string | null; voidReason: string | null; version: number; lines: InvoiceLineView[]; }
export interface ReceiptLineView { id: string; allocationId: string; chargeId: string; installmentId: string; feeTypeCode: string; feeTypeNameFr: string; feeTypeNameEn: string; dueDate: string; allocatedMinor: number; installmentRemainingMinor: number; currency: string; }
export interface ReceiptView { id: string; paymentId: string; studentId: string; enrollmentId: string; academicSessionId: string; studentName: string; matricule: string | null; className: string | null; sessionLabel: string | null; receiptNumber: string; status: string; issueDate: string; currency: string; amountMinor: number; allocatedMinor: number; creditMinor: number; outstandingMinor: number; channelCode: string; reference: string | null; cashierSessionId: string | null; journalEntryId: string | null; recipient: RecipientView; snapshotHash: string; generatedDocumentId: string | null; generatedDocumentNumber: string | null; generatedDocumentStatus: string | null; generationError: string | null; version: number; lines: ReceiptLineView[]; }
export interface FinanceDocumentView { id: string; documentType: 'INVOICE' | 'RECEIPT'; documentNumber: string; status: string; issueDate: string; dueDate: string | null; studentId: string; academicSessionId: string; schoolClassId: string | null; studentName: string; className: string | null; recipientName: string | null; totalMinor: number; paidMinor: number; outstandingMinor: number; currency: string; generatedDocumentId: string | null; generatedDocumentStatus: string | null; sha256: string | null; sourcePaymentId: string | null; sourceJournalId: string | null; version: number; }
export interface AuditView { id: string; action: string; aggregateType: string; aggregateId: string; actorId: string | null; createdAt: string; reason: string | null; }
export interface GeneratedDocumentView { id: string; documentType: string; aggregateType: string; aggregateId: string; aggregateVersion: string; locale: string; documentNumber: string; title: string; sha256: string; mimeType: string; sizeBytes: number; status: string; visibility: string; generatedAt: string; issuedAt: string | null; revokedAt: string | null; revokeReason: string | null; supersededById: string | null; supersededAt: string | null; voidReason: string | null; version: number; }
export interface DocumentDetailView { documentType: string; invoice: InvoiceView | null; receipt: ReceiptView | null; generatedDocument: GeneratedDocumentView | null; audit: AuditView[]; }
export interface BatchInvoiceRequest { academicSessionId: string; schoolClassId: string | null; issueDate: string; dueDate: string; locale: string; }
export interface BatchRowView { enrollmentId: string; studentId: string; studentName: string; matricule: string | null; className: string | null; recipientName: string | null; amountMinor: number; resultStatus: string; blockerCode: string | null; blockerMessage: string | null; actionLink: string | null; invoiceId: string | null; }
export interface BatchPreviewView { academicSessionId: string; schoolClassId: string | null; issueDate: string; dueDate: string; affectedCount: number; totalMinor: number; alreadyIssuedCount: number; blockedCount: number; rows: BatchRowView[]; blockers: { code: string; message: string; actionLink: string }[]; }
export interface BatchJobView { id: string; academicSessionId: string; schoolClassId: string | null; issueDate: string; dueDate: string; status: string; enrollmentCount: number; issuedCount: number; alreadyIssuedCount: number; blockedCount: number; failedCount: number; totalAmountMinor: number; currency: string; lastError: string | null; version: number; }
export interface BatchResultView { id: string; enrollmentId: string | null; studentId: string | null; invoiceId: string | null; resultStatus: string; amountMinor: number; currency: string; blockerCode: string | null; blockerMessage: string | null; actionLink: string | null; errorDetail: string | null; }

@Injectable({ providedIn: 'root' })
export class FinanceDocumentsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/documents`;

  list(filters: Record<string, string | number | null | undefined>): Observable<FinanceDocumentView[]> {
    let params = new HttpParams(); Object.entries(filters).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') params = params.set(key, String(value)); });
    return this.http.get<FinanceDocumentView[]>(this.base, { params });
  }
  previewInvoice(body: { enrollmentId: string; issueDate: string; dueDate: string; installmentIds: string[]; recipientGuardianId: string | null; locale: string }): Observable<InvoicePreview> { return this.http.post<InvoicePreview>(`${this.base}/invoices/preview`, body); }
  issueInvoice(body: { enrollmentId: string; issueDate: string; dueDate: string; installmentIds: string[]; recipientGuardianId: string | null; locale: string }, key: string): Observable<InvoiceView> { return this.http.post<InvoiceView>(`${this.base}/invoices`, body, { headers: new HttpHeaders({ 'Idempotency-Key': key }) }); }
  previewBatch(body: BatchInvoiceRequest): Observable<BatchPreviewView> { return this.http.post<BatchPreviewView>(`${this.base}/invoices/batch/preview`, body); }
  issueBatch(body: BatchInvoiceRequest, key: string): Observable<BatchJobView> { return this.http.post<BatchJobView>(`${this.base}/invoices/batch`, body, { headers: new HttpHeaders({ 'Idempotency-Key': key }) }); }
  batchJob(id: string): Observable<BatchJobView> { return this.http.get<BatchJobView>(`${this.base}/invoices/batch/${id}`); }
  batchResults(id: string): Observable<BatchResultView[]> { return this.http.get<BatchResultView[]>(`${this.base}/invoices/batch/${id}/results`); }
  batchFailures(id: string): Observable<Blob> { return this.http.get(`${this.base}/invoices/batch/${id}/failures.csv`, { responseType: 'blob' }); }
  retryFailed(id: string, key: string): Observable<BatchJobView> { return this.http.post<BatchJobView>(`${this.base}/invoices/batch/${id}/retry-failed`, {}, { headers: new HttpHeaders({ 'Idempotency-Key': key }) }); }
  detail(type: string, id: string): Observable<DocumentDetailView> { return this.http.get<DocumentDetailView>(`${this.base}/${type}/${id}`); }
  download(type: string, id: string): Observable<Blob> { return this.http.get(`${this.base}/${type}/${id}/download`, { responseType: 'blob' }); }
  voidInvoice(id: string, reason: string, version: number): Observable<InvoiceView> { return this.http.post<InvoiceView>(`${this.base}/invoices/${id}/void`, { reason, version }); }
}
