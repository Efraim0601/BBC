import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PayrollApiError {
  error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { label?: string; action?: string }[]; correlationId?: string };
  message?: string;
}
export interface PayrollComponent {
  id: string; code: string; nameFr: string; nameEn: string; componentKind: string; calculationMode: string;
  defaultAmountMinor: number; defaultRateBps: number; expenseAccountId: string | null; liabilityAccountId: string | null;
  active: boolean; effectiveFrom: string | null; effectiveTo: string | null; version: number;
}
export interface PayrollPeriod {
  id: string; code: string; startDate: string; endDate: string; paymentDate: string; accountingPeriodId: string;
  status: string; version: number;
}
export interface AccountingPeriodOption {
  id: string; code: string; nameFr: string; nameEn: string; startDate: string; endDate: string; status: string; version: number;
}
export interface PayrollRun {
  id: string; payrollPeriodId: string; runNumber: number; status: string; prorationMode: string; defaultHours: number;
  segregationEnabled: boolean; employeeCount: number; exceptionCount: number; grossMinor: number; deductionMinor: number;
  netMinor: number; employerCostMinor: number; currency: string; calculationSnapshotHash: string | null;
  previousSnapshotHash: string | null; snapshotLocked: boolean; accrualJournalId: string | null; paymentJournalId: string | null;
  calculatedBy: string | null; calculatedAt: string | null; reviewedBy: string | null; reviewedAt: string | null;
  approvedBy: string | null; approvedAt: string | null; paidBy: string | null; paidAt: string | null; version: number;
}
export interface PayrollLine {
  id: string; lineNo: number; componentTypeId: string | null; componentCode: string; componentNameFr: string; componentNameEn: string;
  componentKind: string; calculationMode: string; quantity: number; rateBps: number; amountMinor: number; source: string;
  reason: string | null; expenseAccountId: string | null; liabilityAccountId: string | null; version: number;
}
export interface PayrollPayment {
  id: string; channelCode: string; paymentReference: string | null; amountMinor: number; currency: string; paymentDate: string;
  status: string; journalEntryId: string | null; version: number;
}
export interface PayrollEmployee {
  id: string; employeeId: string; employeeCode: string; employeeName: string; employeeEmail: string | null; employmentType: string;
  employmentMode: string; hiredOn: string | null; exitedOn: string | null; monthlySalaryMinor: number; hourlyRateMinor: number;
  approvedHours: number; eligible: boolean; status: string; exceptionCode: string | null; exceptionMessage: string | null;
  formula: string | null; grossMinor: number; deductionMinor: number; netMinor: number; employerCostMinor: number; snapshotHash: string;
  version: number; lines: PayrollLine[]; payments: PayrollPayment[];
}
export interface PayrollRunDetail { run: PayrollRun; period: PayrollPeriod; employees: PayrollEmployee[]; }
export interface PayrollEligibility {
  employeeId: string; employeeCode: string; employeeName: string; employmentType: string; employmentMode: string;
  hiredOn: string | null; exitedOn: string | null; monthlySalaryMinor: number; hourlyRateMinor: number; approvedHours: number;
  active: boolean; eligible: boolean; status: string; exceptionCode: string | null; exceptionMessage: string | null; formula: string | null;
}
export interface PayrollBlocker { code: string; message: string; actionLink: string; }
export interface PayrollPreview {
  payrollPeriodId: string; periodCode: string; startDate: string; endDate: string; prorationMode: string; defaultHours: number;
  employeeCount: number; eligibleCount: number; exceptionCount: number; grossMinor: number; deductionMinor: number; netMinor: number;
  employerCostMinor: number; currency: string; employees: PayrollEligibility[]; blockers: PayrollBlocker[];
}
export interface PayrollPaymentOption { id: string; code: string; labelFr: string; labelEn: string; requiresReference: boolean; enabled: boolean; debitAccountId: string | null; }
export interface PayrollAccountOption { id: string; code: string; nameFr: string; nameEn: string; accountType: string; currency: string; }
export interface PayrollPaymentOptions { channels: PayrollPaymentOption[]; accounts: PayrollAccountOption[]; }
export interface PayrollPayResult { runId: string; status: string; totalPaidMinor: number; paidCount: number; failedCount: number; results: unknown[]; payslipJob: PayslipJob; }
export interface Payslip {
  id: string; employeePayrollId: string; employeeId: string | null; employeeName: string | null; payslipNumber: string;
  versionNo: number; locale: string; status: string; generatedDocumentId: string | null; generatedDocumentStatus: string | null;
  snapshotHash: string; generationError: string | null; version: number;
}
export interface PayslipJob { id: string; payrollRunId: string; status: string; totalCount: number; issuedCount: number; failedCount: number; lastError: string | null; version: number; }
export interface PayslipJobResult { id: string; employeePayrollId: string; payslipId: string | null; resultStatus: string; errorDetail: string | null; }

@Injectable({ providedIn: 'root' })
export class FinancePayrollApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/finance/v2/payroll`;

  components(): Observable<PayrollComponent[]> { return this.http.get<PayrollComponent[]>(`${this.base}/components`); }
  createComponent(body: Partial<PayrollComponent>): Observable<PayrollComponent> { return this.http.post<PayrollComponent>(`${this.base}/components`, body); }
  updateComponent(id: string, body: Partial<PayrollComponent>): Observable<PayrollComponent> { return this.http.put<PayrollComponent>(`${this.base}/components/${id}`, body); }
  periods(): Observable<PayrollPeriod[]> { return this.http.get<PayrollPeriod[]>(`${this.base}/periods`); }
  createPeriod(body: Record<string, unknown>): Observable<PayrollPeriod> { return this.http.post<PayrollPeriod>(`${this.base}/periods`, body); }
  accountingPeriods(): Observable<AccountingPeriodOption[]> { return this.http.get<AccountingPeriodOption[]>(`${environment.apiUrl}/finance/v2/accounting/periods`); }
  paymentOptions(): Observable<PayrollPaymentOptions> { return this.http.get<PayrollPaymentOptions>(`${this.base}/payment-options`); }
  runs(): Observable<PayrollRun[]> { return this.http.get<PayrollRun[]>(`${this.base}/runs`); }
  preview(body: Record<string, unknown>): Observable<PayrollPreview> { return this.http.post<PayrollPreview>(`${this.base}/preview`, body); }
  createRun(body: Record<string, unknown>): Observable<PayrollRunDetail> { return this.http.post<PayrollRunDetail>(`${this.base}/runs`, body); }
  detail(id: string): Observable<PayrollRunDetail> { return this.http.get<PayrollRunDetail>(`${this.base}/runs/${id}`); }
  calculate(id: string, key: string): Observable<PayrollRunDetail> { return this.http.post<PayrollRunDetail>(`${this.base}/runs/${id}/calculate`, {}, { headers: this.key(key) }); }
  adjust(body: Record<string, unknown>): Observable<PayrollRunDetail> { return this.http.post<PayrollRunDetail>(`${this.base}/adjustments`, body); }
  review(id: string, version: number, reason: string): Observable<PayrollRunDetail> { return this.http.post<PayrollRunDetail>(`${this.base}/runs/${id}/review`, { version, reason }); }
  approve(id: string, version: number, reason: string): Observable<PayrollRunDetail> { return this.http.post<PayrollRunDetail>(`${this.base}/runs/${id}/approve`, { version, reason }); }
  voidRun(id: string, version: number, reason: string, key: string): Observable<PayrollRunDetail> {
    return this.http.post<PayrollRunDetail>(`${this.base}/runs/${id}/void`, { version, reason }, { headers: this.key(key) });
  }
  pay(id: string, body: Record<string, unknown>, key: string): Observable<PayrollPayResult> {
    return this.http.post<PayrollPayResult>(`${this.base}/runs/${id}/pay`, body, { headers: this.key(key) });
  }
  payslipJobs(id: string): Observable<PayslipJob> { return this.http.get<PayslipJob>(`${this.base}/payslip-jobs/${id}`); }
  payslipJobResults(id: string): Observable<PayslipJobResult[]> { return this.http.get<PayslipJobResult[]>(`${this.base}/payslip-jobs/${id}/results`); }
  retryPayslipJob(id: string, key: string): Observable<PayslipJob> { return this.http.post<PayslipJob>(`${this.base}/payslip-jobs/${id}/retry`, {}, { headers: this.key(key) }); }
  payslips(): Observable<Payslip[]> { return this.http.get<Payslip[]>(`${this.base}/payslips`); }
  payslip(id: string): Observable<Payslip> { return this.http.get<Payslip>(`${this.base}/payslips/${id}`); }
  payslipPdf(id: string): Observable<Blob> { return this.http.get(`${this.base}/payslips/${id}/download`, { responseType: 'blob' }); }
  regeneratePayslip(id: string, key: string): Observable<Payslip> { return this.http.post<Payslip>(`${this.base}/payslips/${id}/regenerate`, {}, { headers: this.key(key) }); }
  selfPayslips(): Observable<Payslip[]> { return this.http.get<Payslip[]>(`${this.base}/self/payslips`); }
  selfPayslipPdf(id: string): Observable<Blob> { return this.http.get(`${this.base}/self/payslips/${id}/download`, { responseType: 'blob' }); }
  private key(value: string): HttpHeaders { return new HttpHeaders({ 'Idempotency-Key': value }); }
}
