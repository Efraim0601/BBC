import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CollectionApiError {
  error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { label?: string; message?: string; action?: string }[]; correlationId?: string };
  message?: string;
}
export interface StudentSearchView {
  studentId: string; studentName: string; matricule: string | null; enrollmentId: string; academicSessionId: string;
  className: string | null;
  enrolledOn: string; exitedOn: string | null;
  outstandingMinor: number; overdueMinor: number;
}
export interface InstallmentProposal {
  installmentId: string; chargeId: string; feeTypeCode: string; label: string; dueDate: string;
  outstandingMinor: number; proposedMinor: number; status: string;
}
export interface ChannelView {
  id: string; code: string; labelFr: string; labelEn: string; requiresReference: boolean;
  enabled: boolean; cashierRequired: boolean; debitAccountId: string | null; debitAccountCode: string | null;
  debitAccountName: string | null; currency: string;
}
export interface PaymentQuoteView {
  enrollmentId: string; studentId: string; studentName: string; academicSessionId: string; className: string | null;
  requestedMinor: number; existingCreditMinor: number; proposedAllocatedMinor: number; newCreditMinor: number;
  projectedOutstandingMinor: number; currency: string; postingPeriodOpen: boolean; postingPeriodCode: string | null;
  installments: InstallmentProposal[]; channels: ChannelView[]; blockers: { code: string; message: string; actionLink: string }[];
}
export interface AllocationInput { installmentId: string; amountMinor: number; }
export interface PaymentView {
  id: string; studentId: string; enrollmentId: string; academicSessionId: string; amountMinor: number; currency: string;
  paymentDate: string; channelCode: string; reference: string | null; status: string; receiptNo: string;
  legacyReceiptNo: string | null; journalEntryId: string | null; allocatedMinor: number; creditMinor: number;
  outstandingMinor: number; version: number; allocations: { id: string; installmentId: string; allocatedMinor: number; currency: string; status: string }[];
  receiptDocumentId: string | null; receiptDocumentNumber: string | null; receiptDocumentStatus: string | null; receiptGenerationError: string | null;
}
export interface CashierSessionView {
  id: string; cashierUserId: string; status: string; openedAt: string | null; closedAt: string | null;
  openingCashMinor: number; expectedCashMinor: number; declaredCashMinor: number | null; varianceMinor: number | null;
  closeNote: string | null; managerApprovedBy: string | null; version: number;
}
export interface RefundView {
  id: string; paymentId: string; refundNo: string | null; amountMinor: number; currency: string; channelCode: string;
  reference: string | null; status: string; reason: string; requestedBy: string | null; approvedBy: string | null;
  journalEntryId: string | null; version: number;
}
export interface ReversalPreview { paymentId: string; receiptNo: string; amountMinor: number; allocatedMinor: number; remainingCreditMinor: number; allowed: boolean; blockers: { code: string; message: string; actionLink: string }[]; }
export interface ProviderTransactionView { id: string | null; providerCode: string; externalReference: string | null; amountMinor: number | null; currency: string; status: string; paymentId: string | null; message: string | null; receivedAt: string | null; }

@Injectable({ providedIn: 'root' })
export class CollectionsApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/collections`;

  channels(): Observable<ChannelView[]> { return this.http.get<ChannelView[]>(`${environment.apiUrl}/finance/channels`); }

  search(query: string, sessionId?: string): Observable<StudentSearchView[]> {
    let params = new HttpParams().set('q', query || ''); if (sessionId) params = params.set('sessionId', sessionId);
    return this.http.get<StudentSearchView[]>(`${this.base}/search`, { params });
  }
  quote(body: { enrollmentId: string; amountMinor: number; paymentDate: string }): Observable<PaymentQuoteView> { return this.http.post<PaymentQuoteView>(`${this.base}/quote`, body); }
  post(body: { enrollmentId: string; amountMinor: number; paymentChannelId: string; paymentDate: string; reference: string; payerName: string; note: string; allocations: AllocationInput[]; legacyReceiptNo: string }, key: string): Observable<PaymentView> {
    return this.http.post<PaymentView>(this.base, body, { headers: new HttpHeaders({ 'Idempotency-Key': key }) });
  }
  list(filters: Record<string, string | null | undefined>): Observable<PaymentView[]> {
    let params = new HttpParams(); Object.entries(filters).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<PaymentView[]>(this.base, { params });
  }
  detail(id: string): Observable<PaymentView> { return this.http.get<PaymentView>(`${this.base}/${id}`); }
  receiptPdf(documentId: string): Observable<Blob> { return this.http.get(`${environment.apiUrl}/official-documents/${documentId}/content`, { responseType: 'blob' }); }
  cashierCurrent(): Observable<CashierSessionView | null> { return this.http.get<CashierSessionView | null>(`${this.base}/cashier/current`); }
  openCashier(openingCashMinor: number): Observable<CashierSessionView> { return this.http.post<CashierSessionView>(`${this.base}/cashier`, { openingCashMinor }); }
  closeCashier(id: string, body: { declaredCashMinor: number; closeNote: string; version: number }): Observable<CashierSessionView> { return this.http.post<CashierSessionView>(`${this.base}/cashier/${id}/close`, body); }
  approveCashier(id: string, body: { declaredCashMinor: number; closeNote: string; version: number }): Observable<CashierSessionView> { return this.http.post<CashierSessionView>(`${this.base}/cashier/${id}/approve-close`, body); }
  reversalPreview(id: string): Observable<ReversalPreview> { return this.http.get<ReversalPreview>(`${this.base}/${id}/reversal-preview`); }
  reverse(id: string, reason: string, version: number, key: string): Observable<PaymentView> { return this.http.post<PaymentView>(`${this.base}/${id}/reverse`, { reason, version }, { headers: new HttpHeaders({ 'Idempotency-Key': key }) }); }
  refunds(id: string): Observable<RefundView[]> { return this.http.get<RefundView[]>(`${this.base}/${id}/refunds`); }
  requestRefund(id: string, body: { amountMinor: number; channelCode: string; reference: string; reason: string; version: number }): Observable<RefundView> { return this.http.post<RefundView>(`${this.base}/${id}/refunds`, body); }
  decideRefund(id: string, body: { version: number; approve: boolean; decisionReason: string }): Observable<RefundView> { return this.http.post<RefundView>(`${this.base}/refunds/${id}/decision`, body); }
  ingestProvider(body: { providerCode: string; eventId: string; paymentChannelId: string; externalReference: string; amountMinor: number | null; currency: string; payload: unknown }): Observable<ProviderTransactionView> { return this.http.post<ProviderTransactionView>(`${this.base}/provider/callbacks`, body); }
}
