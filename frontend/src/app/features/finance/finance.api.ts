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
}
