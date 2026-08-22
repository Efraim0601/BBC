import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StudentSearchView } from './collections.api';

export interface FinanceAccountPayment {
  id: string; source: string; receiptNo: string | null; paymentDate: string; amountMinor: number;
  refundedMinor: number; netAmountMinor: number; currency: string; channelCode: string;
  channelLabel: string; treasuryAccountName: string | null; reference: string | null;
  allocatedMinor: number; creditMinor: number; status: string; journalEntryId: string | null;
}
export interface StudentFinanceAccount {
  studentId: string; studentName: string; matricule: string | null; className: string | null;
  sessionLabel: string | null; billedMinor: number; paidMinor: number; outstandingMinor: number;
  creditMinor: number; currency: string; snapshotHash: string; payments: FinanceAccountPayment[];
}
export interface ConsolidatedReceipt {
  studentId: string; studentName: string; matricule: string | null; className: string | null;
  sessionLabel: string | null; receiptNumber: string; issueDate: string; billedMinor: number;
  paidMinor: number; outstandingMinor: number; creditMinor: number; currency: string; status: string;
  snapshotHash: string; generatedDocumentId: string; generatedDocumentNumber: string;
  generatedDocumentStatus: string; payments: FinanceAccountPayment[];
}

@Injectable({ providedIn: 'root' })
export class FinanceAccountApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/finance/v2/accounts`;

  search(q: string): Observable<StudentSearchView[]> {
    return this.http.get<StudentSearchView[]>(`${this.base}/students/search`, { params: { q } });
  }
  student(studentId: string): Observable<StudentFinanceAccount> {
    return this.http.get<StudentFinanceAccount>(`${this.base}/students/${studentId}`);
  }
  consolidatedReceipt(studentId: string): Observable<ConsolidatedReceipt> {
    return this.http.post<ConsolidatedReceipt>(`${this.base}/students/${studentId}/consolidated-receipt`, {});
  }
  consolidatedReceiptPdf(studentId: string): Observable<Blob> {
    return this.http.get(`${this.base}/students/${studentId}/consolidated-receipt.pdf`, { responseType: 'blob' });
  }
}
