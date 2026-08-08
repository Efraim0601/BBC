import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// ---- Configuration ---------------------------------------------------------

export interface ProgressionView {
  classId: string;
  className: string;
  sectionId: string;
  sectionLabel: string;
  subsystem: string;
  level: string;
  gradeOrder: number;
  nextClassId: string | null;
  nextClassName: string | null;
  terminal: boolean;
  studentCount: number;
}

export interface ProgressionLine {
  classId: string;
  gradeOrder: number;
  nextClassId: string | null;
  terminal: boolean;
}

export interface RuleView {
  id: string;
  level: string | null;
  subsystem: string | null;
  classId: string | null;
  className: string | null;
  passMark: number;
  councilMargin: number;
  maxRepeats: number | null;
  scopeLabel: string;
  specificity: number;
}

export interface RuleUpsert {
  id?: string | null;
  level?: string | null;
  subsystem?: string | null;
  classId?: string | null;
  passMark: number;
  councilMargin: number;
  maxRepeats: number | null;
}

export interface PromotionConfig {
  currentYear: string;
  nextYear: string;
  progression: ProgressionView[];
  rules: RuleView[];
}

// ---- Simulation ------------------------------------------------------------

/** promoted | repeated | graduated | review | undecided */
export type ProposedResult = string;

export interface CandidateView {
  studentId: string;
  matricule: string;
  studentName: string;
  photoHue: number;
  annualAverage: number | null;
  rank: number | null;
  classSize: number | null;
  sequencesCounted: number;
  priorRepeats: number;
  proposedResult: ProposedResult;
  proposalReason: string;
  proposedClassId: string | null;
  proposedClassName: string | null;
  appliedResult: string | null;
}

export interface PromotionPreview {
  classId: string;
  className: string;
  level: string;
  subsystem: string;
  academicYear: string;
  nextAcademicYear: string;
  nextClassId: string | null;
  nextClassName: string | null;
  terminal: boolean;
  passMark: number;
  councilMargin: number;
  maxRepeats: number | null;
  ruleScope: string;
  total: number;
  graded: number;
  candidates: CandidateView[];
  warnings: string[];
}

// ---- Application -----------------------------------------------------------

export interface PromotionLine {
  studentId: string;
  result: string;
  toClassId: string | null;
  reason: string | null;
}

export interface PromotionApply {
  classId: string;
  academicYear: string;
  nextAcademicYear: string;
  lines: PromotionLine[];
}

export interface PromotionResult {
  batchId: string;
  applied: number;
  promoted: number;
  repeated: number;
  graduated: number;
  other: number;
  overridden: number;
  warnings: string[];
}

export interface BatchView {
  id: string;
  academicYear: string;
  nextAcademicYear: string;
  className: string;
  studentsTotal: number;
  promotedCount: number;
  repeatedCount: number;
  graduatedCount: number;
  otherCount: number;
  overriddenCount: number;
  appliedBy: string | null;
  appliedAt: string;
}

// ---- Clôture de l'année ----------------------------------------------------

export interface PendingClass { className: string; students: number; }

export interface ClosurePreview {
  academicYear: string;
  nextAcademicYear: string;
  /** Non nul quand l'année a déjà été clôturée — une seconde clôture est refusée. */
  closedAt: string | null;
  activeStudents: number;
  studentsDecided: number;
  studentsPending: number;
  pendingClasses: PendingClass[];
  gradesToArchive: number;
  validationsToArchive: number;
  feesToArchive: number;
  feesToCreate: number;
  warnings: string[];
}

export interface ClosureRequest {
  academicYear: string;
  nextAcademicYear: string;
  archiveGrades: boolean;
  resetFees: boolean;
  makeCurrent: boolean;
  ignorePending: boolean;
}

export interface ClosureResult {
  id: string;
  academicYear: string;
  nextAcademicYear: string;
  gradesArchived: number;
  validationsArchived: number;
  feesArchived: number;
  feesCreated: number;
  madeCurrent: boolean;
  warnings: string[];
}

export interface ClosureView {
  id: string;
  academicYear: string;
  nextAcademicYear: string;
  gradesArchived: number;
  validationsArchived: number;
  feesArchived: number;
  feesCreated: number;
  studentsActive: number;
  studentsPending: number;
  madeCurrent: boolean;
  closedBy: string | null;
  closedAt: string;
}

/** Passage de classe — configuration, simulation et application de fin d'année. */
@Injectable({ providedIn: 'root' })
export class PromotionApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/promotions`;

  config(): Observable<PromotionConfig> {
    return this.http.get<PromotionConfig>(`${this.base}/config`);
  }
  saveProgression(lines: ProgressionLine[]): Observable<ProgressionView[]> {
    return this.http.put<ProgressionView[]>(`${this.base}/progression`, { lines });
  }
  autoProgression(): Observable<ProgressionView[]> {
    return this.http.post<ProgressionView[]>(`${this.base}/progression/auto`, {});
  }
  saveRule(body: RuleUpsert): Observable<RuleView[]> {
    return this.http.post<RuleView[]>(`${this.base}/rules`, body);
  }
  deleteRule(id: string): Observable<RuleView[]> {
    return this.http.delete<RuleView[]>(`${this.base}/rules/${id}`);
  }

  preview(classId: string, academicYear?: string): Observable<PromotionPreview> {
    let params = new HttpParams().set('classId', classId);
    if (academicYear) params = params.set('academicYear', academicYear);
    return this.http.get<PromotionPreview>(`${this.base}/preview`, { params });
  }
  apply(body: PromotionApply): Observable<PromotionResult> {
    return this.http.post<PromotionResult>(`${this.base}/apply`, body);
  }
  batches(): Observable<BatchView[]> {
    return this.http.get<BatchView[]>(`${this.base}/batches`);
  }

  closurePreview(academicYear?: string): Observable<ClosurePreview> {
    let params = new HttpParams();
    if (academicYear) params = params.set('academicYear', academicYear);
    return this.http.get<ClosurePreview>(`${this.base}/closure/preview`, { params });
  }
  close(body: ClosureRequest): Observable<ClosureResult> {
    return this.http.post<ClosureResult>(`${this.base}/closure`, body);
  }
  closureHistory(): Observable<ClosureView[]> {
    return this.http.get<ClosureView[]>(`${this.base}/closure/history`);
  }
}
