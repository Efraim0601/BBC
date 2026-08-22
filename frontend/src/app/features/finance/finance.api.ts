import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FinanceSummary, PaymentView } from '../../core/models';

export interface PaymentRequest {
  studentId: string;
  amount: number;
  /** Code du canal encaissé : CASH, OM, MOMO, MPGS, TRANSFER… */
  method: string;
  /** Référence de transaction de l'opérateur — exigée par certains canaux. */
  reference?: string | null;
  tranche?: number;
  paidOn?: string;
  /** Compte de trésorerie réellement crédité par cette opération. */
  treasuryAccountId: string;
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
  treasuryAccountId?: string | null;
  treasuryAccountName?: string | null;
  journalEntryId?: string | null;
  status?: string;
}

export interface ExpenseRequest {
  spentOn: string;
  category: string;
  label: string;
  amount: number;
  treasuryAccountId: string;
}

export interface FinanceContextClassOption {
  id: string;
  code: string;
  name: string;
  level: string;
  subsystem: string;
}

export interface FinanceContextView {
  sessions: { id: string; code: string; label: string; startDate: string; endDate: string; status: string }[];
  classes: FinanceContextClassOption[];
}

/** Une tranche de la grille : libellé, montant et échéance facultative. */
export interface TrancheView {
  label: string;
  amount: number;
  dueOn: string | null;
}

export interface FeeConfigView {
  id: string;
  level: string;
  subsystem: string | null;
  /** Null = grille du niveau ; renseigné = surcharge appliquée à cette classe. */
  classId: string | null;
  className: string | null;
  total: number;
  tranches: TrancheView[];
  items: Record<string, unknown>[] | null;
}

export interface FeeConfigUpdate {
  level: string;
  subsystem?: string | null;
  classId?: string | null;
  total: number;
  tranches: TrancheView[];
  items?: Record<string, unknown>[] | null;
}

/** Moyen de paiement accepté par l'établissement. */
export interface PaymentChannelView {
  id: string;
  code: string;
  labelFr: string;
  labelEn: string;
  /** Numéro Orange Money / MoMo, identifiant marchand MPGS, RIB… */
  accountRef: string | null;
  accountName: string | null;
  instructionsFr: string | null;
  instructionsEn: string | null;
  requiresReference: boolean;
  enabled: boolean;
  visibleToParents: boolean;
  sortOrder: number;
  debitAccountId: string | null;
}

export interface PaymentChannelUpdate {
  labelFr?: string;
  labelEn?: string;
  accountRef?: string | null;
  accountName?: string | null;
  instructionsFr?: string | null;
  instructionsEn?: string | null;
  requiresReference?: boolean;
  enabled?: boolean;
  visibleToParents?: boolean;
  sortOrder?: number;
}

export interface TrancheStatusView {
  index: number;
  label: string;
  amount: number;
  dueOn: string | null;
  paid: number;
  remaining: number;
  status: 'paid' | 'partial' | 'pending';
  overdue: boolean;
}

export interface PaymentLineView {
  receiptNo: string;
  paidOn: string;
  amount: number;
  method: string;
  methodLabelFr: string;
  methodLabelEn: string;
  reference: string | null;
  tranche: number | null;
}

/** Situation de scolarité d'un élève, telle que la voient l'économat et le parent. */
export interface StudentFeeStatementView {
  studentId: string;
  studentName: string;
  matricule: string;
  className: string;
  gridSource: 'class' | 'level' | null;
  total: number;
  paid: number;
  balance: number;
  progressPct: number;
  status: 'paid' | 'partial' | 'unpaid';
  tranches: TrancheStatusView[];
  payments: PaymentLineView[];
}

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance`;

  summary(): Observable<FinanceSummary> {
    return this.http.get<FinanceSummary>(`${this.base}/summary`);
  }
  /** Finance-scoped class/session options; never call academic-setup for finance filters. */
  context(): Observable<FinanceContextView> {
    return this.http.get<FinanceContextView>(`${environment.apiUrl}/finance/v2/charges/context`);
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
  deleteFeeConfig(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/fees/config/${id}`);
  }
  statement(studentId: string): Observable<StudentFeeStatementView> {
    return this.http.get<StudentFeeStatementView>(`${this.base}/students/${studentId}/statement`);
  }
  channels(): Observable<PaymentChannelView[]> {
    return this.http.get<PaymentChannelView[]>(`${this.base}/channels`);
  }
  updateChannel(code: string, body: PaymentChannelUpdate): Observable<PaymentChannelView> {
    return this.http.put<PaymentChannelView>(`${this.base}/channels/${encodeURIComponent(code)}`, body);
  }
}
