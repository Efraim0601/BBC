import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChargeApiError {
  error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { label?: string; message?: string }[]; correlationId?: string };
  message?: string;
}

export interface ChargeContext {
  sessions: { id: string; code: string; label: string; startDate: string; endDate: string; status: string }[];
  classes: { id: string; code: string; name: string; level: string; subsystem: string }[];
}
export interface GenerationRequest {
  academicSessionId: string; schoolClassId: string | null; level: string; subsystem: string;
  chargeDate: string; prorationPolicy: 'NONE' | 'DAILY' | 'MONTHLY'; transferPolicy: 'INCREMENTAL_ONLY' | 'FULL_REASSESSMENT';
}
export interface BlockerView { entityType: string; entityId: string | null; code: string; message: string; actionLink: string | null; }
export interface PreviewRow {
  enrollmentId: string; studentId: string; studentName: string; matricule: string | null; planId: string | null;
  planVersionNo: number; scopeType: string | null; className: string | null; planLineId: string | null;
  feeTypeCode: string | null; feeTypeName: string | null; originalAmountMinor: number; adjustedAmountMinor: number;
  installmentCount: number; optional: boolean; optionalDecision: string | null; transfer: boolean;
  prorationPolicy: string; prorationFormula: string | null; resultStatus: string; blockerCode: string | null;
  blockerMessage: string | null; actionLink: string | null;
}
export interface GenerationPreview {
  academicSessionId: string; schoolClassId: string | null; level: string | null; subsystem: string | null;
  chargeDate: string; prorationPolicy: string; transferPolicy: string; enrollmentCount: number;
  coveredEnrollmentCount: number; uncoveredEnrollmentCount: number; candidateLineCount: number; installmentCount: number;
  optionalPendingCount: number; transferCount: number; alreadyGeneratedCount: number; estimatedTotalMinor: number;
  currency: string; rows: PreviewRow[]; blockers: BlockerView[];
}
export interface GenerationJobView {
  id: string; academicSessionId: string; schoolClassId: string | null; level: string | null; subsystem: string | null;
  chargeDate: string; prorationPolicy: string; transferPolicy: string; status: string; enrollmentCount: number;
  generatedCount: number; alreadyExistsCount: number; blockedCount: number; failedCount: number;
  totalAmountMinor: number; currency: string; lastError: string | null; version: number;
}
export interface GenerationResultView {
  id: string; jobId: string; enrollmentId: string | null; studentId: string | null; feePlanId: string | null;
  feePlanLineId: string | null; chargeId: string | null; schoolClassId: string | null; classNameSnapshot: string | null;
  resultStatus: string; amountMinor: number; currency: string; blockerCode: string | null; blockerMessage: string | null;
  actionLink: string | null; errorDetail: string | null;
}
export interface ChargeInstallmentView {
  id: string; installmentNo: number; labelFr: string; labelEn: string; dueDate: string; amountMinor: number;
  paidMinor: number; waivedMinor: number; outstandingMinor: number; status: string; version: number;
}
export interface AdjustmentView {
  id: string; chargeId: string; installmentId: string | null; adjustmentType: string; amountMinor: number; currency: string;
  reason: string; evidenceReference: string | null; contraAccountId: string; effectiveDate: string; status: string;
  requestedBy: string | null; approvedBy: string | null; decisionReason: string | null; journalEntryId: string | null; version: number;
}
export interface ChargeView {
  id: string; studentEnrollmentId: string; studentId: string; academicSessionId: string; feePlanId: string; feePlanLineId: string;
  feeTypeId: string; feeTypeRevisionId: string; feePlanVersionNo: number; feeTypeCode: string; feeTypeNameFr: string;
  feeTypeNameEn: string; feeTypeCategory: string; scopeType: string; levelSnapshot: string; subsystemSnapshot: string;
  schoolClassIdSnapshot: string | null; classNameSnapshot: string | null; originalAmountMinor: number; adjustedAmountMinor: number;
  paidMinor: number; waivedMinor: number; outstandingMinor: number; currency: string; chargeDate: string;
  prorationPolicy: string; prorationFormula: string | null; transferFromEnrollmentId: string | null; transferPolicy: string;
  status: string; journalEntryId: string | null; version: number; installments: ChargeInstallmentView[]; adjustments: AdjustmentView[];
}
export interface StudentContextOption {
  enrollmentId: string; studentId: string; studentName: string; matricule: string | null; academicSessionId: string;
  className: string | null; level: string | null; subsystem: string | null;
}
export interface LedgerEntryView {
  entryType: string; chargeId: string | null; installmentId: string | null; adjustmentId: string | null;
  entryDate: string; label: string; debitMinor: number; creditMinor: number; runningBalanceMinor: number; status: string;
}
export interface StudentAccountView {
  studentId: string; studentName: string; matricule: string | null; enrollmentId: string; className: string | null;
  level: string | null; subsystem: string | null; academicSessionId: string; chargedMinor: number; paidMinor: number;
  waivedMinor: number; outstandingMinor: number; currentMinor: number; days1To30Minor: number; days31To60Minor: number;
  days61To90Minor: number; over90Minor: number; ledger: LedgerEntryView[]; placeholders: string[];
}
export interface AgeingRow {
  studentId: string; studentName: string; matricule: string | null; enrollmentId: string; className: string | null;
  currentMinor: number; days1To30Minor: number; days31To60Minor: number; days61To90Minor: number; over90Minor: number; outstandingMinor: number;
}
export interface AgeingView {
  asOfDate: string; currency: string; currentMinor: number; days1To30Minor: number; days31To60Minor: number; days61To90Minor: number;
  over90Minor: number; rows: AgeingRow[];
}
export interface AdjustmentImpact {
  chargeId: string; installmentId: string | null; currentOutstandingMinor: number; requestedAmountMinor: number;
  projectedOutstandingMinor: number; allowed: boolean; blockers: string[];
}
export interface AdjustmentRequest {
  adjustmentType: 'WAIVER' | 'ADJUSTMENT'; amountMinor: number; installmentId: string | null; reason: string;
  evidenceReference: string; contraAccountId: string; effectiveDate: string; version?: number;
}

@Injectable({ providedIn: 'root' })
export class ChargesApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/charges`;

  context(): Observable<ChargeContext> { return this.http.get<ChargeContext>(`${this.base}/context`); }
  studentOptions(query?: string, sessionId?: string): Observable<StudentContextOption[]> {
    let params = new HttpParams(); if (query) params = params.set('query', query); if (sessionId) params = params.set('sessionId', sessionId);
    return this.http.get<StudentContextOption[]>(`${this.base}/student-options`, { params });
  }
  preview(body: GenerationRequest): Observable<GenerationPreview> { return this.http.post<GenerationPreview>(`${this.base}/generation-preview`, body); }
  generate(body: GenerationRequest, key: string): Observable<GenerationJobView> {
    return this.http.post<GenerationJobView>(`${this.base}/generate`, body, { headers: new HttpHeaders({ 'Idempotency-Key': key }) });
  }
  job(id: string): Observable<GenerationJobView> { return this.http.get<GenerationJobView>(`${this.base}/jobs/${id}`); }
  results(id: string): Observable<GenerationResultView[]> { return this.http.get<GenerationResultView[]>(`${this.base}/jobs/${id}/results`); }
  retry(id: string, key: string): Observable<GenerationJobView> { return this.http.post<GenerationJobView>(`${this.base}/jobs/${id}/retry`, {}, { headers: new HttpHeaders({ 'Idempotency-Key': key }) }); }
  list(filters: Record<string, string | number | null | undefined>): Observable<ChargeView[]> {
    let params = new HttpParams(); Object.entries(filters).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') params = params.set(key, String(value)); });
    return this.http.get<ChargeView[]>(this.base, { params });
  }
  detail(id: string): Observable<ChargeView> { return this.http.get<ChargeView>(`${this.base}/${id}`); }
  account(enrollmentId: string): Observable<StudentAccountView> { return this.http.get<StudentAccountView>(`${this.base}/accounts/${enrollmentId}`); }
  ageing(asOfDate?: string, sessionId?: string, classId?: string): Observable<AgeingView> {
    let params = new HttpParams(); if (asOfDate) params = params.set('asOfDate', asOfDate); if (sessionId) params = params.set('academicSessionId', sessionId); if (classId) params = params.set('schoolClassId', classId);
    return this.http.get<AgeingView>(`${this.base}/ageing`, { params });
  }
  impact(id: string, body: AdjustmentRequest): Observable<AdjustmentImpact> { return this.http.post<AdjustmentImpact>(`${this.base}/${id}/adjustment-impact-preview`, body); }
  requestAdjustment(id: string, body: AdjustmentRequest): Observable<AdjustmentView> { return this.http.post<AdjustmentView>(`${this.base}/${id}/adjustments`, body); }
  adjustments(id: string): Observable<AdjustmentView[]> { return this.http.get<AdjustmentView[]>(`${this.base}/${id}/adjustments`); }
  decideAdjustment(id: string, body: { version: number; approve: boolean; decisionReason: string }): Observable<AdjustmentView> { return this.http.post<AdjustmentView>(`${this.base}/adjustments/${id}/decision`, body); }
}
