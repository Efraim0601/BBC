import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TreasuryAccountView {
  id: string;
  chartAccountId: string;
  chartAccountCode: string;
  kind: 'CASH' | 'BANK' | 'MOBILE_WALLET' | 'OTHER';
  displayName: string;
  institutionName: string | null;
  accountNumberLast4: string | null;
  currency: string;
  active: boolean;
  defaultAccount: boolean;
  balanceMinor: number;
  version: number;
}

export interface TreasuryAccountCreate {
  kind: string;
  displayName: string;
  institutionName: string | null;
  accountNumberLast4: string | null;
  currency: string;
  openingBalanceMinor: number;
  openingBalanceDate: string;
  chartAccountCode: string | null;
}

export interface TreasuryMovementRequest {
  movementType: string;
  entryDate: string;
  fromAccountId: string | null;
  toAccountId: string | null;
  offsetAccountId: string | null;
  amountMinor: number;
  currency: string;
  reason: string;
  reference: string | null;
}

export interface TreasuryMovementView {
  id: string;
  movementNo: string;
  movementType: string;
  entryDate: string;
  fromAccountId: string | null;
  fromAccountName: string | null;
  toAccountId: string | null;
  toAccountName: string | null;
  offsetAccountId: string | null;
  offsetAccountCode: string | null;
  amountMinor: number;
  currency: string;
  reason: string;
  reference: string | null;
  status: string;
  journalEntryId: string | null;
  journalNumber: string | null;
  createdBy: string | null;
  createdAt: string;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class TreasuryApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/treasury`;

  accounts(): Observable<TreasuryAccountView[]> { return this.http.get<TreasuryAccountView[]>(`${this.base}/accounts`); }
  createAccount(body: TreasuryAccountCreate): Observable<TreasuryAccountView> { return this.http.post<TreasuryAccountView>(`${this.base}/accounts`, body); }
  archiveAccount(id: string, body: { version: number; reason: string }): Observable<TreasuryAccountView> { return this.http.put<TreasuryAccountView>(`${this.base}/accounts/${id}/archive`, body); }
  movements(limit = 100): Observable<TreasuryMovementView[]> { return this.http.get<TreasuryMovementView[]>(`${this.base}/movements`, { params: { limit } }); }
  createMovement(body: TreasuryMovementRequest): Observable<TreasuryMovementView> {
    return this.http.post<TreasuryMovementView>(`${this.base}/movements`, body, { headers: { 'Idempotency-Key': crypto.randomUUID() } });
  }
}
