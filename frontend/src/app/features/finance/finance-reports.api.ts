import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface FinanceReportApiError {
  error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { label?: string; action?: string; message?: string }[]; correlationId?: string };
  message?: string;
}
export interface ReportFilters { academicSessionId: string; fromDate: string; toDate: string; asOfDate: string; classId: string; level: string; feeTypeCode: string; channelCode: string; status: string; limit?: number; offset?: number; }
export interface ReportMeta { academicSessionId: string | null; sessionCode: string | null; fromDate: string; toDate: string; asOfDate: string; generatedAt: string; dataThrough: string; refreshStatus: string; lagSeconds: number; currency: string; appliedFilters: Record<string, string>; sourceBasis: string; }
export interface ReportEnvelope<T> { meta: ReportMeta; data: T; }
export interface SessionOption { id: string; code: string; label: string; startDate: string; endDate: string; status: string; current: boolean; }
export interface ClassOption { id: string; name: string; level: string; subsystem: string; }
export interface ReportContext { sessions: SessionOption[]; classes: ClassOption[]; levels: string[]; feeTypes: string[]; channels: string[]; }
export interface ReportException { code: string; message: string; sourceType: string | null; sourceId: string | null; actionLink: string | null; }
export interface AgeingBucket { bucket: string; amountMinor: number; installmentCount: number; sourceIds: string[]; }
export interface InstallmentPerformance { label: string; dueMinor: number; paidMinor: number; outstandingMinor: number; overdueMinor: number; installmentCount: number; }
export interface ReceivableRow { sourceId: string; studentId: string; studentName: string; feeTypeCode: string; classNameSnapshot: string | null; levelSnapshot: string | null; sessionCode: string | null; chargeDate: string; billedMinor: number; collectedMinor: number; waivedMinor: number; outstandingMinor: number; sourceCount: number; }
export interface ReceivablesReport { billedMinor: number; collectedMinor: number; waivedMinor: number; outstandingMinor: number; creditedMinor: number; refundedMinor: number; recoveryPercentage: number; mismatchMinor: number; mismatchCount: number; balanced: boolean; rows: ReceivableRow[]; ageing: AgeingBucket[]; installmentPerformance: InstallmentPerformance[]; exceptions: ReportException[]; }
export interface CollectionRow { sourceId: string; studentId: string; studentName: string; sessionCode: string | null; channel: string; status: string; paymentDate: string; reference: string | null; receiptNo: string | null; amountMinor: number; allocatedMinor: number; remainingCreditMinor: number; refundedMinor: number; journalId: string | null; }
export interface ChannelSummary { channel: string; paymentMinor: number; allocatedMinor: number; creditMinor: number; refundedMinor: number; paymentCount: number; }
export interface CashierVarianceRow { sourceId: string; cashierUserId: string; status: string; openedOn: string; expectedMinor: number; declaredMinor: number; varianceMinor: number; note: string | null; }
export interface ProviderSummary { providerCode: string; status: string; transactionCount: number; amountMinor: number; }
export interface CollectionsReport { paymentTotalMinor: number; allocatedMinor: number; remainingCreditMinor: number; refundedMinor: number; reversedMinor: number; mismatchMinor: number; mismatchCount: number; balanced: boolean; rows: CollectionRow[]; channels: ChannelSummary[]; cashierVariances: CashierVarianceRow[]; providers: ProviderSummary[]; exceptions: ReportException[]; }
export interface DocumentStatusRow { type: string; status: string; count: number; amountMinor: number; }
export interface DocumentRow { sourceId: string; type: string; number: string; status: string; issueDate: string; studentName: string; sessionCode: string | null; recipient: string; amountMinor: number; outstandingMinor: number; sourcePaymentId: string | null; sourceJournalId: string | null; }
export interface DocumentsReport { invoiceTotalMinor: number; invoiceOutstandingMinor: number; invoiceCount: number; receiptTotalMinor: number; receiptCount: number; statuses: DocumentStatusRow[]; rows: DocumentRow[]; exceptions: ReportException[]; }
export interface ExpenseRow { sourceId: string; category: string; label: string; spentOn: string; amountMinor: number; status: string; journalId: string | null; }
export interface ExpensesReport { postedExpenseMinor: number; expenseCount: number; legacyAdapter: boolean; rows: ExpenseRow[]; exceptions: ReportException[]; }
export interface PayrollRunRow { sourceId: string; periodCode: string; status: string; startDate: string; endDate: string; employeeCount: number; exceptionCount: number; grossMinor: number; deductionMinor: number; netMinor: number; employerCostMinor: number; paidMinor: number; accrualJournalId: string | null; paymentJournalId: string | null; }
export interface PayrollReport { grossMinor: number; deductionMinor: number; netMinor: number; employerCostMinor: number; paidMinor: number; runCount: number; employeeCount: number; runs: PayrollRunRow[]; exceptions: ReportException[]; }
export interface TrialBalanceRow { accountId: string; code: string; name: string; type: string; debitMinor: number; creditMinor: number; balanceMinor: number; }
export interface TrialBalanceSummary { debitMinor: number; creditMinor: number; balanced: boolean; accountCount: number; rows: TrialBalanceRow[]; }
export interface IncomeRow { accountId: string; code: string; name: string; type: string; amountMinor: number; }
export interface IncomeStatement { revenueMinor: number; expenseMinor: number; netMinor: number; rows: IncomeRow[]; }
export interface LedgerRow { sourceId: string; number: string; entryDate: string; sourceType: string | null; status: string; description: string; accountCode: string; debitMinor: number; creditMinor: number; runningBalanceMinor: number; }
export interface AccountingReport { trialBalance: TrialBalanceSummary; incomeStatement: IncomeStatement; ledger: LedgerRow[]; exceptions: ReportException[]; }
export interface ReconciliationRow { sourceId: string; sourceType: string; sourceReference: string | null; expectedMinor: number; actualMinor: number; currency: string; state: string; reason: string; actionLink: string | null; }
export interface ReconciliationReport { openCount: number; mismatchMinor: number; rows: ReconciliationRow[]; exceptions: ReportException[]; }

@Injectable({ providedIn: 'root' })
export class FinanceReportsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/finance/v2/reports`;

  context(): Observable<ReportContext> { return this.http.get<ReportContext>(`${this.base}/context`); }
  receivables(filters: ReportFilters): Observable<ReportEnvelope<ReceivablesReport>> { return this.http.get<ReportEnvelope<ReceivablesReport>>(`${this.base}/receivables`, { params: this.params(filters) }); }
  collections(filters: ReportFilters): Observable<ReportEnvelope<CollectionsReport>> { return this.http.get<ReportEnvelope<CollectionsReport>>(`${this.base}/collections`, { params: this.params(filters) }); }
  documents(filters: ReportFilters): Observable<ReportEnvelope<DocumentsReport>> { return this.http.get<ReportEnvelope<DocumentsReport>>(`${this.base}/documents`, { params: this.params(filters) }); }
  expenses(filters: ReportFilters): Observable<ReportEnvelope<ExpensesReport>> { return this.http.get<ReportEnvelope<ExpensesReport>>(`${this.base}/expenses`, { params: this.params(filters) }); }
  payroll(filters: ReportFilters): Observable<ReportEnvelope<PayrollReport>> { return this.http.get<ReportEnvelope<PayrollReport>>(`${this.base}/payroll`, { params: this.params(filters) }); }
  accounting(filters: ReportFilters): Observable<ReportEnvelope<AccountingReport>> { return this.http.get<ReportEnvelope<AccountingReport>>(`${this.base}/accounting`, { params: this.params(filters) }); }
  reconciliation(filters: ReportFilters): Observable<ReportEnvelope<ReconciliationReport>> { return this.http.get<ReportEnvelope<ReconciliationReport>>(`${this.base}/reconciliation`, { params: this.params(filters) }); }
  export(report: string, filters: ReportFilters, format: 'csv' | 'pdf'): Observable<Blob> { return this.http.get(`${this.base}/${report}/export`, { params: this.params({ ...filters, format } as ReportFilters), responseType: 'blob' }); }

  private params(filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    const values = filters as unknown as Record<string, unknown>;
    for (const [key, value] of Object.entries(values)) if (value !== null && value !== undefined && String(value).trim() !== '') params = params.set(key, String(value));
    return params;
  }
}
