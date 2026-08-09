import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface JourneyView {
  id: string;
  studentId: string;
  academicYear: string;
  className: string;
  level: string | null;
  subsystem: string | null;
  result: string;
  generalAverage: number | null;
  rank: number | null;
  classSize: number | null;
  decision: string | null;
  note: string | null;
  sourceSessionId: string | null;
  targetSessionId: string | null;
  promotionBatchId: string | null;
  recommendation: string | null;
  finalDecision: string | null;
  targetClassName: string | null;
  overrideReason: string | null;
  decisionBy: string | null;
  decisionAt: string | null;
}

export interface StudentJourney {
  studentId: string;
  studentName: string;
  matricule: string;
  currentClass: string;
  yearsCount: number;
  bestAverage: number | null;
  entries: JourneyView[];
}

export interface JourneyUpsert {
  studentId: string;
  academicYear: string;
  className: string;
  level?: string;
  subsystem?: string;
  result?: string;
  generalAverage?: number | null;
  rank?: number | null;
  classSize?: number | null;
  decision?: string;
  note?: string;
}

export interface ProgressionPathView {
  id: string; sourceSessionId: string; sourceClassId: string; sourceClassName: string;
  targetSessionId: string; targetClassId: string | null; targetClassName: string | null;
  terminal: boolean; active: boolean; version: number; graphVersionId?: string | null;
  graphVersionNo?: number; graphStatus?: string; edgeType?: string; displayOrder?: number;
  allowSkip?: boolean; skipReason?: string | null;
}
export interface ProgressionGraphView {
  id: string; sourceSessionId: string; sourceSessionLabel: string; targetSessionId: string;
  targetSessionLabel: string; versionNo: number; status: string; copiedFromId: string | null;
  publishedAt: string | null; version: number; edgeCount: number; blockers: string[];
}
export interface PromotionRuleView {
  id: string; academicSessionId: string; subsystem: string | null; level: string | null;
  promoteMin: number; reviewMin: number; requireFinalAverage: boolean; active: boolean; version: number;
  ruleSetId?: string | null; ruleSetVersion?: number; ruleSetStatus?: string;
}
export interface PromotionTargetOption { classId: string; className: string; edgeType: string; terminal: boolean; allowSkip: boolean; }
export interface PromotionCandidateView {
  id: string; studentId: string; matricule: string; studentName: string;
  sourceEnrollmentId: string; sourceClassId: string; sourceClassName: string;
  mappedTargetClassId: string | null; mappedTargetClassName: string | null;
  targetClassId: string | null; targetClassName: string | null; finalAverage: number | null;
  recommendation: string; finalDecision: string; overrideReason: string | null;
  explanation: string; version: number; annualBulletinId?: string | null; annualAverage?: number | null;
  annualRank?: number | null; annualDecision?: string | null; councilApproved?: boolean;
  allowedTargets?: PromotionTargetOption[]; blockers?: string[];
}
export interface PromotionBatchView {
  id: string; name: string; sourceSessionId: string; sourceSessionLabel: string;
  targetSessionId: string; targetSessionLabel: string; status: string;
  candidateCount: number; promoteCount: number; repeatCount: number;
  graduateCount: number; reviewCount: number; version: number;
  createdAt: string; committedAt: string | null; candidates: PromotionCandidateView[];
  graphVersionId?: string | null; graphVersionNo?: number; ruleSetId?: string | null;
  ruleSetVersion?: number; previewFingerprint?: string | null;
}
export interface PromotionBatchListItem {
  id: string; name: string; sourceSessionId: string; sourceSessionLabel: string;
  targetSessionId: string; targetSessionLabel: string; status: string;
  candidateCount: number; blockedCount: number; createdAt: string;
  committedAt: string | null; version: number;
}
export interface PromotionCommitPreviewView {
  batchId: string; status: string; candidateCount: number; promoteCount: number;
  repeatCount: number; graduateCount: number; reviewCount: number; blockers: string[];
  graphVersionId?: string | null; graphVersionNo?: number; ruleSetId?: string | null; ruleSetVersion?: number;
}
export interface PromotionRegisterView { id:string; batchId:string; sha256:string; createdAt:string; manifest:string; }
export interface PromotionPreviewView {
  previewToken: string; name: string; sourceSessionId: string; sourceSessionLabel: string;
  targetSessionId: string; targetSessionLabel: string; fingerprint: string;
  candidateCount: number; candidates: PromotionCandidateView[];
  graphVersionId?: string | null; graphVersionNo?: number; ruleSetId?: string | null; ruleSetVersion?: number;
}

@Injectable({ providedIn: 'root' })
export class JourneyApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/journey`;

  forStudent(studentId: string): Observable<StudentJourney> {
    return this.http.get<StudentJourney>(`${this.base}/students/${studentId}`);
  }
  upsert(body: JourneyUpsert): Observable<JourneyView> {
    return this.http.post<JourneyView>(this.base, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  voidEntry(id: string, reason: string): Observable<JourneyView> {
    return this.http.post<JourneyView>(`${this.base}/${id}/void`, { reason });
  }
  progressionPaths(sourceSessionId: string, targetSessionId: string): Observable<ProgressionPathView[]> {
    return this.http.get<ProgressionPathView[]>(`${this.base}/progression/paths`, { params: { sourceSessionId, targetSessionId } });
  }
  progressionGraphs(sourceSessionId: string, targetSessionId: string): Observable<ProgressionGraphView[]> {
    return this.http.get<ProgressionGraphView[]>(`${this.base}/progression/graphs`, { params: { sourceSessionId, targetSessionId } });
  }
  publishProgressionGraph(id: string, version?: number): Observable<ProgressionGraphView> {
    const params: Record<string, string> = version == null ? {} : { expectedVersion: String(version) };
    return this.http.post<ProgressionGraphView>(`${this.base}/progression/graphs/${id}/publish`, null, { params });
  }
  saveProgressionPath(body: { sourceSessionId: string; sourceClassId: string; targetSessionId: string; targetClassId: string | null; terminal: boolean; version?: number; edgeType?: string; displayOrder?: number; allowSkip?: boolean; skipReason?: string | null; graphVersionId?: string | null }): Observable<ProgressionPathView> {
    return this.http.post<ProgressionPathView>(`${this.base}/progression/paths`, body);
  }
  deleteProgressionPath(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/progression/paths/${id}`);
  }
  promotionRules(sessionId: string): Observable<PromotionRuleView[]> {
    return this.http.get<PromotionRuleView[]>(`${this.base}/progression/rules`, { params: { sessionId } });
  }
  savePromotionRule(body: { academicSessionId: string; subsystem?: string | null; level?: string | null; promoteMin: number; reviewMin: number; requireFinalAverage: boolean; version?: number }): Observable<PromotionRuleView> {
    return this.http.post<PromotionRuleView>(`${this.base}/progression/rules`, body);
  }
  previewPromotion(body: { sourceSessionId: string; targetSessionId: string; name: string; sourceClassIds?: string[]; idempotencyKey?: string; graphVersionId?: string; ruleSetId?: string }): Observable<PromotionPreviewView> {
    return this.http.post<PromotionPreviewView>(`${this.base}/progression/promotion-previews`, body);
  }
  saveReviewBatch(body: { sourceSessionId: string; targetSessionId: string; name: string; sourceClassIds?: string[]; idempotencyKey?: string; previewFingerprint: string; graphVersionId?: string; ruleSetId?: string }): Observable<PromotionBatchView> {
    return this.http.post<PromotionBatchView>(`${this.base}/progression/batches`, body);
  }
  promotionBatch(id: string): Observable<PromotionBatchView> {
    return this.http.get<PromotionBatchView>(`${this.base}/progression/batches/${id}`);
  }
  promotionBatches(status?: string): Observable<PromotionBatchListItem[]> {
    return this.http.get<PromotionBatchListItem[]>(`${this.base}/progression/batches`, {
      params: status ? { status } : {},
    });
  }
  cancelPromotionBatch(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/progression/batches/${id}/cancel`, { reason });
  }
  promotionCommitPreview(id: string): Observable<PromotionCommitPreviewView> {
    return this.http.post<PromotionCommitPreviewView>(`${this.base}/progression/batches/${id}/commit/preview`, null);
  }
  overrideDecision(id: string, body: { finalDecision: string; targetClassId: string | null; reason: string; version: number }): Observable<PromotionCandidateView> {
    return this.http.patch<PromotionCandidateView>(`${this.base}/progression/decisions/${id}`, body);
  }
  commitPromotion(id: string, reason: string, version: number): Observable<PromotionBatchView> {
    return this.http.post<PromotionBatchView>(`${this.base}/progression/batches/${id}/commit`, { reason, version });
  }
  promotionRegister(id: string): Observable<PromotionRegisterView> {
    return this.http.get<PromotionRegisterView>(`${this.base}/progression/batches/${id}/register`);
  }
}
