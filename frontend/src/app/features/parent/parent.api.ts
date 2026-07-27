import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChildView {
  studentId: string;
  matricule: string;
  name: string;
  className: string;
  balance: number;
  feeStatus: 'paid' | 'partial' | 'unpaid';
  attendanceRate: number;
}

export interface GradeView {
  subjectCode: string;
  subjectLabelFr: string;
  subjectLabelEn: string;
  /** Subject weight — the portal average must match the bulletin's, which is weighted. */
  coef: number;
  sequence: number;
  mark: number;
}

/** Une tranche vue par le parent : ce qui est couvert, ce qui reste, l'échéance. */
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

/** Situation de scolarité d'un enfant, selon la grille de sa classe. */
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

/** Moyen de paiement publié par l'école, avec ses coordonnées. */
export interface PaymentChannelView {
  id: string;
  code: string;
  labelFr: string;
  labelEn: string;
  accountRef: string | null;
  accountName: string | null;
  instructionsFr: string | null;
  instructionsEn: string | null;
  requiresReference: boolean;
  enabled: boolean;
  visibleToParents: boolean;
  sortOrder: number;
}

export interface SuggestionView {
  id: string;
  category: string;
  message: string;
  status: string;
  createdAt: string;
}

export interface SuggestionRequest {
  category: string;
  message: string;
}

export type ResourceKind = 'supplies' | 'books';
export interface ResourceItem {
  id: string;
  label: string;
  quantity: number | null;
  price: number | null;
  note: string | null;
  subjectCode: string | null;
  author: string | null;
  mandatory: boolean | null;
}
export interface ClassResourceView {
  classId: string | null;
  className: string;
  kind: ResourceKind;
  published: boolean;
  publishedAt: string | null;
  items: ResourceItem[];
}

@Injectable({ providedIn: 'root' })
export class ParentApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/parent`;

  children(): Observable<ChildView[]> {
    return this.http.get<ChildView[]>(`${this.base}/children`);
  }
  grades(studentId: string): Observable<GradeView[]> {
    return this.http.get<GradeView[]>(`${this.base}/children/${studentId}/grades`);
  }
  resources(studentId: string, kind: ResourceKind): Observable<ClassResourceView> {
    return this.http.get<ClassResourceView>(`${this.base}/children/${studentId}/resources/${kind}`);
  }
  fees(studentId: string): Observable<StudentFeeStatementView> {
    return this.http.get<StudentFeeStatementView>(`${this.base}/children/${studentId}/fees`);
  }
  paymentChannels(): Observable<PaymentChannelView[]> {
    return this.http.get<PaymentChannelView[]>(`${this.base}/payment-channels`);
  }
  addSuggestion(body: SuggestionRequest): Observable<SuggestionView> {
    return this.http.post<SuggestionView>(`${this.base}/suggestions`, body);
  }
  mySuggestions(): Observable<SuggestionView[]> {
    return this.http.get<SuggestionView[]>(`${this.base}/suggestions`);
  }
}
