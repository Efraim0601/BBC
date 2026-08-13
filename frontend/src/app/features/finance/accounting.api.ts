import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AccountingApiError {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
  blockers?: { entityType: string; entityId: string | null; label: string; action: string }[];
  correlationId?: string;
}

export interface AccountView {
  id: string; code: string; nameFr: string; nameEn: string; accountType: string; normalSide: string;
  currency: string | null; parentId: string | null; postingAllowed: boolean; active: boolean;
  effectiveFrom: string | null; effectiveTo: string | null; version: number; postedUsageCount: number;
}
export interface AccountUpsert {
  code: string; nameFr: string; nameEn: string; accountType: string; normalSide: string;
  currency: string | null; parentId: string | null; postingAllowed: boolean; active: boolean;
  effectiveFrom: string | null; effectiveTo: string | null; version?: number;
}

export interface PostingRuleView {
  id: string; eventType: string; side: string; scopeCode: string | null; feeTypeCode: string | null;
  paymentChannelCode: string | null; componentCode: string | null; targetAccountId: string;
  targetAccountCode: string; priority: number; effectiveFrom: string | null; effectiveTo: string | null;
  enabled: boolean; version: number;
}
export interface PostingRuleUpsert {
  eventType: string; side: string; scopeCode: string | null; feeTypeCode: string | null;
  paymentChannelCode: string | null; componentCode: string | null; targetAccountId: string;
  priority: number; effectiveFrom: string | null; effectiveTo: string | null; enabled: boolean; version?: number;
}

export interface PeriodView {
  id: string; code: string; nameFr: string; nameEn: string; startDate: string; endDate: string;
  academicSessionId: string | null; status: 'OPEN' | 'CLOSED'; closedAt: string | null; closedBy: string | null;
  closeReason: string | null; reopenedAt: string | null; reopenedBy: string | null;
  reopenReason: string | null; version: number;
}
export interface PeriodActionRequest { version: number; reason: string; }
export interface ClosePreview {
  periodId: string; periodCode: string; draftJournals: number; unreconciledItems: number;
  blockers: { entityType: string; entityId: string | null; label: string; action: string }[]; ready: boolean;
}

export interface JournalLineView {
  id: string; lineNumber: number; accountId: string; accountCode: string; accountName: string;
  debitMinor: number; creditMinor: number; studentId: string | null; enrollmentId: string | null;
  employeeId: string | null; classId: string | null; feeTypeCode: string | null; description: string | null; version: number;
}
export interface JournalView {
  id: string; number: string; entryDate: string; status: 'DRAFT' | 'POSTED' | 'REVERSED';
  sourceType: string | null; sourceId: string | null; sourceEventKey: string | null; description: string;
  currency: string; accountingPeriodId: string; reversalOfId: string | null; reversedBy: string | null;
  postedAt: string | null; postedBy: string | null; version: number; totalDebitMinor: number;
  totalCreditMinor: number; lines: JournalLineView[];
}
export interface JournalLineInput {
  accountId: string; debitMinor: number; creditMinor: number; studentId?: string | null;
  enrollmentId?: string | null; employeeId?: string | null; classId?: string | null;
  feeTypeCode?: string | null; description?: string | null;
}
export interface JournalUpsert {
  entryDate: string; description: string; currency: string; accountingPeriodId: string;
  sourceType?: string | null; sourceId?: string | null; sourceEventKey?: string | null;
  lines: JournalLineInput[]; version?: number;
}
export interface JournalPage { items: JournalView[]; page: number; size: number; totalItems: number; totalPages: number; }

export interface TrialBalanceRow {
  accountId: string; accountCode: string; accountName: string; accountType: string; currency: string;
  debitMinor: number; creditMinor: number; balanceMinor: number;
}
export interface TrialBalanceView {
  asOfDate: string; currency: string; rows: TrialBalanceRow[]; totalDebitMinor: number;
  totalCreditMinor: number; balanced: boolean;
}
export interface GeneralLedgerLine {
  journalId: string; journalNumber: string; entryDate: string; status: string; sourceType: string | null;
  description: string; debitMinor: number; creditMinor: number; runningBalanceMinor: number;
}
export interface GeneralLedgerView {
  accountId: string; accountCode: string; accountName: string; fromDate: string; toDate: string;
  lines: GeneralLedgerLine[]; totalDebitMinor: number; totalCreditMinor: number;
}

export interface ReconciliationView {
  id: string; sourceType: string; sourceId: string | null; expectedAmount: number; postedAmount: number;
  currency: string; state: 'MATCHED' | 'MISSING' | 'MISMATCH' | 'IGNORED'; reason: string;
  resolvedAt: string | null; resolvedBy: string | null; resolutionNote: string | null; version: number;
}
export interface ReconciliationResolveRequest { state: 'MATCHED' | 'IGNORED'; reason: string; version: number; }
export interface ReadinessCheck {
  key: string; label: string; detail: string; status: 'READY' | 'BLOCKED' | 'WARN'; action: string;
  blockers: { entityType: string; entityId: string | null; label: string; action: string }[];
}
export interface ReadinessView { ready: boolean; checks: ReadinessCheck[]; generatedAt: string; }

@Injectable({ providedIn: 'root' })
export class FinanceAccountingApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/accounting`;

  readiness(): Observable<ReadinessView> { return this.http.get<ReadinessView>(`${this.base}/readiness`); }
  accounts(query?: string, activeOnly = false): Observable<AccountView[]> {
    let params = new HttpParams().set('activeOnly', activeOnly);
    if (query?.trim()) params = params.set('query', query.trim());
    return this.http.get<AccountView[]>(`${this.base}/accounts`, { params });
  }
  createAccount(body: AccountUpsert): Observable<AccountView> { return this.http.post<AccountView>(`${this.base}/accounts`, body); }
  updateAccount(id: string, body: AccountUpsert): Observable<AccountView> { return this.http.put<AccountView>(`${this.base}/accounts/${id}`, body); }

  postingRules(): Observable<PostingRuleView[]> { return this.http.get<PostingRuleView[]>(`${this.base}/posting-rules`); }
  createPostingRule(body: PostingRuleUpsert): Observable<PostingRuleView> { return this.http.post<PostingRuleView>(`${this.base}/posting-rules`, body); }
  updatePostingRule(id: string, body: PostingRuleUpsert): Observable<PostingRuleView> { return this.http.put<PostingRuleView>(`${this.base}/posting-rules/${id}`, body); }

  periods(): Observable<PeriodView[]> { return this.http.get<PeriodView[]>(`${this.base}/periods`); }
  generatePeriods(body: { startDate: string; endDate: string; academicSessionId?: string | null; prefix?: string | null }): Observable<PeriodView[]> {
    return this.http.post<PeriodView[]>(`${this.base}/periods/generate`, body);
  }
  closePreview(id: string): Observable<ClosePreview> { return this.http.post<ClosePreview>(`${this.base}/periods/${id}/close-preview`, {}); }
  closePeriod(id: string, body: PeriodActionRequest): Observable<PeriodView> { return this.http.post<PeriodView>(`${this.base}/periods/${id}/close`, body); }
  reopenPeriod(id: string, body: PeriodActionRequest): Observable<PeriodView> { return this.http.post<PeriodView>(`${this.base}/periods/${id}/reopen`, body); }

  journals(status?: string): Observable<JournalPage> {
    let params = new HttpParams().set('page', 0).set('size', 50);
    if (status) params = params.set('status', status);
    return this.http.get<JournalPage>(`${this.base}/journals`, { params });
  }
  journal(id: string): Observable<JournalView> { return this.http.get<JournalView>(`${this.base}/journals/${id}`); }
  createJournal(body: JournalUpsert): Observable<JournalView> { return this.http.post<JournalView>(`${this.base}/journals`, body); }
  updateJournal(id: string, body: JournalUpsert): Observable<JournalView> { return this.http.put<JournalView>(`${this.base}/journals/${id}`, body); }
  postJournal(id: string): Observable<JournalView> {
    return this.http.post<JournalView>(`${this.base}/journals/${id}/post`, {}, { headers: { 'Idempotency-Key': crypto.randomUUID() } });
  }
  reverseJournal(id: string, body: { entryDate: string; reason: string; version: number }): Observable<JournalView> {
    return this.http.post<JournalView>(`${this.base}/journals/${id}/reverse`, body, { headers: { 'Idempotency-Key': crypto.randomUUID() } });
  }

  trialBalance(asOfDate?: string, includeZero = false): Observable<TrialBalanceView> {
    let params = new HttpParams().set('includeZero', includeZero);
    if (asOfDate) params = params.set('asOfDate', asOfDate);
    return this.http.get<TrialBalanceView>(`${this.base}/trial-balance`, { params });
  }
  generalLedger(accountId: string, from?: string, to?: string): Observable<GeneralLedgerView> {
    let params = new HttpParams().set('accountId', accountId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<GeneralLedgerView>(`${this.base}/general-ledger`, { params });
  }
  reconciliation(state?: string): Observable<ReconciliationView[]> {
    let params = new HttpParams();
    if (state) params = params.set('state', state);
    return this.http.get<ReconciliationView[]>(`${this.base}/reconciliation`, { params });
  }
  resolveReconciliation(id: string, body: ReconciliationResolveRequest): Observable<ReconciliationView> {
    return this.http.post<ReconciliationView>(`${this.base}/reconciliation/${id}/resolve`, body);
  }
}
