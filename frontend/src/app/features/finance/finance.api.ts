import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FinanceSummary, PaymentView } from '../../core/models';

export interface PaymentRequest {
  studentId: string;
  amount: number;
  method: string;
  tranche?: number;
  paidOn?: string;
}

/** A student's running fee position — the shape behind both /situation and /debtors. */
export interface SituationView {
  studentId: string;
  studentName: string;
  className: string;
  total: number;
  paid: number;
  balance: number;
  tranchesPaid: number;
  status: 'paid' | 'partial' | 'unpaid';
  progressPct: number;
}

export interface ExpenseView {
  id: string;
  spentOn: string;
  category: string;
  label: string;
  amount: number;
}

export interface ExpenseRequest {
  spentOn: string;
  category: string;
  label: string;
  amount: number;
}

export interface FeeConfigView {
  id: string;
  level: string;
  subsystem: string | null;
  total: number;
  tranches: number[];
  items: Record<string, unknown>[] | null;
}

export interface FeeConfigUpdate {
  level: string;
  subsystem?: string | null;
  total: number;
  tranches: number[];
  items?: Record<string, unknown>[] | null;
}

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance`;

  summary(): Observable<FinanceSummary> {
    return this.http.get<FinanceSummary>(`${this.base}/summary`);
  }
  payments(): Observable<PaymentView[]> {
    return this.http.get<PaymentView[]>(`${this.base}/payments`);
  }
  recordPayment(body: PaymentRequest): Observable<PaymentView> {
    return this.http.post<PaymentView>(`${this.base}/payments`, body);
  }

  debtors(): Observable<SituationView[]> {
    return this.http.get<SituationView[]>(`${this.base}/debtors`);
  }
  situation(): Observable<SituationView[]> {
    return this.http.get<SituationView[]>(`${this.base}/situation`);
  }

  expenses(): Observable<ExpenseView[]> {
    return this.http.get<ExpenseView[]>(`${this.base}/expenses`);
  }
  addExpense(body: ExpenseRequest): Observable<ExpenseView> {
    return this.http.post<ExpenseView>(`${this.base}/expenses`, body);
  }
  removeExpense(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/expenses/${id}`);
  }

  feeConfig(): Observable<FeeConfigView[]> {
    return this.http.get<FeeConfigView[]>(`${this.base}/fees/config`);
  }
  saveFeeConfig(body: FeeConfigUpdate): Observable<FeeConfigView> {
    return this.http.put<FeeConfigView>(`${this.base}/fees/config`, body);
  }
}
