import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PlanLineView {
  id: string; feeTypeId: string; feeTypeRevisionId: string; amountMinor: number; currency: string;
  mandatory: boolean; refundable: boolean; priority: number; lineOrder: number;
  installmentTemplateId: string | null; prorationPolicy?: 'NONE' | 'DAILY' | 'MONTHLY'; version: number;
}
export interface PlanView {
  id: string; academicSessionId: string; scopeType: 'LEVEL' | 'CLASS'; level: string; subsystem: string;
  schoolClassId: string | null; planVersionNo: number; lifecycle: 'DRAFT' | 'ACTIVE' | 'RETIRED';
  effectiveFrom: string; effectiveTo: string | null; currency: string; inheritanceSource: string;
  effectiveStatus: string; version: number; totalMinor: number; optionalLineCount: number;
  lines: PlanLineView[];
}
export interface SessionOption { id: string; code: string; label: string; startDate: string; endDate: string; }
export interface ClassOption { id: string; name: string; level: string; subsystem: string; }
export interface PlanContext { sessions: SessionOption[]; classes: ClassOption[]; plans: PlanView[]; }
export interface PlanCreateRequest { academicSessionId: string; scopeType: string; level: string; subsystem: string; schoolClassId: string | null; effectiveFrom: string; effectiveTo: string | null; currency: string; }
export interface PlanLineRequest { feeTypeId: string; feeTypeRevisionId: string; amountMinor: number; currency: string; mandatory: boolean; refundable: boolean; priority: number; lineOrder: number; installmentTemplateId: string | null; prorationPolicy?: 'NONE' | 'DAILY' | 'MONTHLY'; version?: number; }
export interface ActivationPreview { planId: string; canActivate: boolean; affectedEnrollmentCount: number; optionalFeeCount: number; missingMappings: string[]; duplicateCoverage: string[]; blockers: string[]; chargeImpact: string; }
export interface CopyPreview { sourcePlanId: string; targetSessionId: string; targetClassId: string | null; mergeMode: string; changedRevisions: string[]; missingClasses: string[]; changedAmounts: string[]; existingTargetDrafts: string[]; blockers: string[]; dateShift: string; }
export interface ResolutionView { enrollmentId: string; planId: string | null; source: string; blocker: string | null; plan: PlanView | null; }
export interface OverrideView { id: string; enrollmentId: string; feePlanLineId: string; overrideType: string; amountMinor: number | null; percentageBasisPoints: number | null; reason: string; status: string; effectiveFrom: string; effectiveTo: string | null; version: number; }
export interface ElectionView { id: string; enrollmentId: string; feePlanLineId: string; status: string; reason: string | null; version: number; }
export interface TemplateLineView { id: string; lineOrder: number; labelFr: string; labelEn: string; allocationType: 'FIXED' | 'PERCENTAGE'; amountMinor: number | null; percentageBasisPoints: number | null; dueRuleType: string; absoluteDueDate: string | null; dueOffsetDays: number | null; academicTermId: string | null; version: number; }
export interface TemplateView { id: string; code: string; nameFr: string; nameEn: string; lifecycle: string; sourceSessionId: string | null; version: number; lines: TemplateLineView[]; }
export interface TemplateLineRequest { lineOrder: number; labelFr: string; labelEn: string; allocationType: 'FIXED' | 'PERCENTAGE'; amountMinor: number | null; percentageBasisPoints: number | null; dueRuleType: string; absoluteDueDate: string | null; dueOffsetDays: number | null; academicTermId: string | null; }
export interface TemplateRequest { code: string; nameFr: string; nameEn: string; sourceSessionId: string | null; lines: TemplateLineRequest[]; version?: number; }
export interface InstallmentPreviewLine { lineOrder: number; labelFr: string; labelEn: string; amountMinor: number; dueDate: string | null; finalAdjustmentMinor: number; }
export interface InstallmentPreview { planId: string; feePlanLineId: string; lineAmountMinor: number; totalMinor: number; finalAdjustmentMinor: number; lines: InstallmentPreviewLine[]; blockers: string[]; }
export interface StudentContextView { enrollmentId: string; studentId: string; matricule: string; studentName: string; academicSessionId: string; sessionLabel: string; schoolClassId: string | null; className: string | null; level: string; subsystem: string; enrollmentStatus: string; }
export interface ImpactPreview { enrollmentId: string; feePlanLineId: string; baseAmountMinor: number; adjustedAmountMinor: number; deltaMinor: number; explanation: string; blockers: string[]; }
export interface PlanApiError { error?: { message?: string; code?: string; fieldErrors?: Record<string, string>; blockers?: { label?: string }[]; correlationId?: string }; message?: string; }

@Injectable({ providedIn: 'root' })
export class FeePlansApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/finance/v2/plans`;

  context(sessionId?: string): Observable<PlanContext> {
    let params = new HttpParams(); if (sessionId) params = params.set('sessionId', sessionId); return this.http.get<PlanContext>(`${this.base}/context`, { params });
  }
  list(sessionId?: string, lifecycle?: string): Observable<PlanView[]> {
    let params = new HttpParams(); if (sessionId) params = params.set('sessionId', sessionId); if (lifecycle) params = params.set('lifecycle', lifecycle); return this.http.get<PlanView[]>(this.base, { params });
  }
  create(body: PlanCreateRequest): Observable<PlanView> { return this.http.post<PlanView>(this.base, body); }
  update(id: string, body: object): Observable<PlanView> { return this.http.put<PlanView>(`${this.base}/${id}/draft`, body); }
  addLine(id: string, body: PlanLineRequest): Observable<PlanView> { return this.http.post<PlanView>(`${this.base}/${id}/lines`, body); }
  removeLine(id: string, lineId: string, version: number): Observable<PlanView> { return this.http.request<PlanView>('delete', `${this.base}/${id}/lines/${lineId}`, { body: { version } }); }
  templates(): Observable<TemplateView[]> { return this.http.get<TemplateView[]>(`${this.base}/templates`); }
  template(id: string): Observable<TemplateView> { return this.http.get<TemplateView>(`${this.base}/templates/${id}`); }
  createTemplate(body: TemplateRequest): Observable<TemplateView> { return this.http.post<TemplateView>(`${this.base}/templates`, body); }
  updateTemplate(id: string, body: TemplateRequest): Observable<TemplateView> { return this.http.put<TemplateView>(`${this.base}/templates/${id}`, body); }
  deleteTemplate(id: string, version: number): Observable<void> { return this.http.request<void>('delete', `${this.base}/templates/${id}`, { body: { version, reason: 'Removed from fee plan workspace' } }); }
  installmentPreview(planId: string, lineId: string): Observable<InstallmentPreview> { return this.http.get<InstallmentPreview>(`${this.base}/${planId}/installments-preview`, { params: { lineId } }); }
  activationPreview(id: string): Observable<ActivationPreview> { return this.http.post<ActivationPreview>(`${this.base}/${id}/activation-preview`, {}); }
  activate(id: string, version: number): Observable<PlanView> { return this.http.post<PlanView>(`${this.base}/${id}/activate`, { version }); }
  copyPreview(body: object): Observable<CopyPreview> { return this.http.post<CopyPreview>(`${this.base}/copy/preview`, body); }
  copy(body: object): Observable<PlanView> { return this.http.post<PlanView>(`${this.base}/copy`, body); }
  resolve(enrollmentId: string): Observable<ResolutionView> { return this.http.get<ResolutionView>(`${this.base}/resolve`, { params: { enrollmentId } }); }
  studentContext(query?: string, sessionId?: string): Observable<StudentContextView[]> { let params = new HttpParams(); if (query) params = params.set('query', query); if (sessionId) params = params.set('sessionId', sessionId); return this.http.get<StudentContextView[]>(`${this.base}/student-context`, { params }); }
  requestOverride(id: string, body: object): Observable<OverrideView> { return this.http.post<OverrideView>(`${this.base}/${id}/overrides`, body); }
  overrides(id: string, enrollmentId: string): Observable<OverrideView[]> { return this.http.get<OverrideView[]>(`${this.base}/${id}/overrides`, { params: { enrollmentId } }); }
  decideOverride(overrideId: string, body: object): Observable<OverrideView> { return this.http.post<OverrideView>(`${this.base}/overrides/${overrideId}/decision`, body); }
  impactPreview(planId: string, enrollmentId: string, lineId: string): Observable<ImpactPreview> { return this.http.get<ImpactPreview>(`${this.base}/${planId}/overrides/impact-preview`, { params: { enrollmentId, lineId } }); }
  elections(id: string, enrollmentId: string): Observable<ElectionView[]> { return this.http.get<ElectionView[]>(`${this.base}/${id}/elections`, { params: { enrollmentId } }); }
  saveElection(planId: string, lineId: string, enrollmentId: string, body: object): Observable<ElectionView> { return this.http.post<ElectionView>(`${this.base}/${planId}/elections/${lineId}`, body, { params: { enrollmentId } }); }
}
