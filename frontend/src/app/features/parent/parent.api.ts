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
  feeStatus: 'paid' | 'partial' | 'unpaid' | null;
  attendanceRate: number;
  financeVisible: boolean;
  attendanceVisible: boolean;
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

export interface PublishedBulletinView {
  id: string; reportingPeriodCode: string; reportingPeriodLabel: string; className: string | null;
  lines: Array<{ subjectLabel: string; mark: number; coefficient: number; teacherRemark: string | null; appreciation: string }>;
  average: number; rank: number | null; classSize: number; state: string; complete: boolean;
  attendance: { absentCount: number; excusedCount: number; lateCount: number; lateMinutes: number } | null;
}

export interface ParentJourneyEventView {
  id: string; eventType: string; sessionLabel: string | null; className: string | null;
  average: number | null; decision: string | null; occurredAt: string | null; sourceId: string | null;
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

export interface ParentAttendanceView {
  studentId: string;
  total: number;
  present: number;
  late: number;
  absent: number;
  excused: number;
  attendanceRate: number;
  records: Array<{ id: string; date: string; status: string; lateMinutes: number }>;
}

export interface ParentDisciplineView {
  id: string;
  incidentDate: string;
  type: string;
  description: string;
  sanction: string | null;
}

export interface ParentHealthView {
  studentId: string;
  visits: Array<{ id: string; visitDate: string; reason: string; treatment: string }>;
}

export interface ParentEventView {
  id: string;
  title: string;
  type: string;
  eventDate: string;
  description: string;
}

export interface ParentNoticeView {
  id: string;
  category: string;
  subject: string;
  body: string;
  requiresAck: boolean;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  senderName: string;
  createdAt: string;
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
  latestPublishedBulletin(studentId: string): Observable<PublishedBulletinView> {
    return this.http.get<PublishedBulletinView>(`${this.base}/children/${studentId}/bulletins/latest`);
  }
  journey(studentId: string): Observable<ParentJourneyEventView[]> {
    return this.http.get<ParentJourneyEventView[]>(`${this.base}/children/${studentId}/journey`);
  }
  resources(studentId: string, kind: ResourceKind): Observable<ClassResourceView> {
    return this.http.get<ClassResourceView>(`${this.base}/children/${studentId}/resources/${kind}`);
  }
  attendance(studentId: string): Observable<ParentAttendanceView> {
    return this.http.get<ParentAttendanceView>(`${this.base}/children/${studentId}/attendance`);
  }
  discipline(studentId: string): Observable<ParentDisciplineView[]> {
    return this.http.get<ParentDisciplineView[]>(`${this.base}/children/${studentId}/discipline`);
  }
  health(studentId: string): Observable<ParentHealthView> {
    return this.http.get<ParentHealthView>(`${this.base}/children/${studentId}/health`);
  }
  events(studentId: string): Observable<ParentEventView[]> {
    return this.http.get<ParentEventView[]>(`${this.base}/children/${studentId}/events`);
  }
  messages(studentId: string): Observable<ParentNoticeView[]> {
    return this.http.get<ParentNoticeView[]>(`${this.base}/children/${studentId}/messages`);
  }
  acknowledgeMessage(studentId: string, messageId: string, signedBy: string): Observable<ParentNoticeView> {
    return this.http.post<ParentNoticeView>(`${this.base}/children/${studentId}/messages/${messageId}/ack`, { signedBy });
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
