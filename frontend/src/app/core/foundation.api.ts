import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AcademicTermView {
  id: string; code: string; label: string; sequenceNo: number;
  startDate: string; endDate: string; gradeEntryOpensAt: string | null;
  gradeEntryClosesAt: string | null; bulletinPublishOpensAt: string | null;
  bulletinPublishClosesAt: string | null; teacherSubmissionOpensAt: string | null;
  teacherSubmissionClosesAt: string | null; timezone: string; version: number;
}
export interface TermManagementWindowView {
  academicSessionId: string; termId: string; termCode: string; termLabel: string;
  termSequenceNo: number; termStartDate: string; termEndDate: string;
  limited: boolean; opensAt: string | null; closesAt: string | null; timezone: string;
  governedPeriodCodes: string[]; state: 'OPEN' | 'SCHEDULED' | 'CLOSED' | 'INVALID' | string;
  nextTransition: string | null; version: number;
}
export interface TermManagementWindowUpsert {
  limited: boolean; opensAt: string | null; closesAt: string | null; version: number;
}
export interface TermManagementWindowProposal {
  sequenceNo: number; code: string; limited: boolean; opensAt: string | null;
  closesAt: string | null; timezone: string; version?: number;
}
export interface AcademicReportingPeriodView {
  id: string; academicSessionId: string; academicTermId: string | null;
  code: string; label: string; periodType: 'SEQUENCE' | 'TERM_RESULT' | 'ANNUAL_RESULT';
  displayOrder: number; startDate: string; endDate: string;
  gradeEntryOpensAt: string | null; gradeEntryClosesAt: string | null;
  reviewOpensAt: string | null; reviewClosesAt: string | null;
  validationOpensAt: string | null; validationClosesAt: string | null;
  bulletinPublishOpensAt: string | null; bulletinPublishClosesAt: string | null;
  correctionOpensAt: string | null; correctionClosesAt: string | null;
  teacherSubmissionOpensAt: string | null; teacherSubmissionClosesAt: string | null; timezone: string;
  calculationPolicy: string; status: string; version: number;
}
export interface AcademicReportingPeriodUpsert {
  code: string; label: string; periodType: 'SEQUENCE' | 'TERM_RESULT' | 'ANNUAL_RESULT'; academicTermId: string | null;
  displayOrder: number; startDate: string; endDate: string;
  gradeEntryOpensAt?: string | null; gradeEntryClosesAt?: string | null;
  reviewOpensAt?: string | null; reviewClosesAt?: string | null;
  validationOpensAt?: string | null; validationClosesAt?: string | null;
  bulletinPublishOpensAt?: string | null; bulletinPublishClosesAt?: string | null;
  correctionOpensAt?: string | null; correctionClosesAt?: string | null;
  teacherSubmissionOpensAt?: string | null; teacherSubmissionClosesAt?: string | null; timezone?: string;
  calculationPolicy?: string; status?: string; version?: number;
}
export interface StandardStructureView {
  academicSessionId: string; periods: AcademicReportingPeriodView[]; warnings: string[]; applied: boolean;
  fingerprint: string; dependencies: StructureDependencyView[];
  termManagementWindows: TermManagementWindowProposal[];
}
export interface StructureDependencyView {
  parentPeriodId: string; parentCode: string; childPeriodId: string; childCode: string;
  weight: number; optional: boolean; displayOrder: number;
}
export interface SessionReadinessView {
  academicSessionId: string; sessionStatus: string; phase: string; ready: boolean;
  nextAction: string; blockers: string[]; actions: string[];
  sections?: Array<{ key: string; label: string; status: string; ready: boolean; issues: Array<{ code: string; severity: string; label: string; detail: string; repairTarget: string; count: number }> }>;
}
export type WorkflowAction = 'GRADE_ENTRY' | 'TEACHER_SUBMISSION' | 'REVIEW' | 'VALIDATION' | 'PUBLICATION' | 'CORRECTION';
export interface EffectiveWindowView {
  periodId: string; periodCode: string; periodLabel: string; action: WorkflowAction;
  configuredOpensAt: string | null; configuredClosesAt: string | null; configuredSource: string;
  opensAt: string | null; closesAt: string | null; open: boolean; source: string; state: string;
  nextTransition: string | null; blockers: string[]; timezone: string;
  configuredMode?: string; effectiveMode?: string; inheritedFrom?: string | null;
}
export type WindowMode = 'INHERIT' | 'UNRESTRICTED' | 'LIMITED';
export interface WorkflowWindowRuleView {
  id: string; academicSessionId: string; scopeType: 'SESSION' | 'TERM' | 'PERIOD';
  academicTermId: string | null; reportingPeriodId: string | null; action: WorkflowAction;
  mode: WindowMode; opensAt: string | null; closesAt: string | null; timezone: string; version: number;
  effectiveMode?: string | null; inheritedFrom?: string | null;
}
export interface WorkflowWindowRuleUpsert {
  scopeType: 'SESSION' | 'TERM' | 'PERIOD'; academicTermId?: string | null; reportingPeriodId?: string | null;
  action: WorkflowAction; mode: WindowMode; opensAt?: string | null; closesAt?: string | null;
  timezone?: string; version?: number;
}
export interface ConfigurationCopyScopeSelection { terms: boolean; reportingPeriods: boolean; dependencies: boolean; termManagementWindows: boolean; }
export interface ConfigurationCopyEdit { key: string; field: string; value: string | null; }
export interface ConfigurationCopyRow {
  key: string; kind: string; code: string; label: string; status: string;
  source: Record<string, unknown>; proposed: Record<string, unknown>; existing: Record<string, unknown> | null;
  warnings: string[]; blockers: string[];
}
export interface ConfigurationCopyPreview {
  sourceSessionId: string; targetSessionId: string; sourceLabel: string; targetLabel: string;
  dateStrategy: string; mergeMode: string; scopes: ConfigurationCopyScopeSelection;
  terms: ConfigurationCopyRow[]; reportingPeriods: ConfigurationCopyRow[]; dependencies: ConfigurationCopyRow[]; termManagementWindows: ConfigurationCopyRow[];
  warnings: string[]; blockers: string[]; fingerprint: string; createCount: number; updateCount: number; keepCount: number;
}
export interface ConfigurationCopyPreviewRequest {
  sourceSessionId: string; dateStrategy?: string; mergeMode?: string; scopes?: ConfigurationCopyScopeSelection; edits?: ConfigurationCopyEdit[]; selectedKeys?: string[];
}
export interface ConfigurationCopyApplyRequest extends ConfigurationCopyPreviewRequest { reason: string; previewFingerprint: string; }
export interface WindowOverrideView {
  id: string; academicSessionId: string; reportingPeriodId: string | null;
  action: WorkflowAction; scope: string; reason: string;
  opensAt: string; expiresAt: string; createdBy: string | null; createdAt: string;
  version: number; active: boolean;
}
export interface WindowOverrideUpsert {
  action: WorkflowAction; scope: string; reason: string; opensAt: string; expiresAt: string; reportingPeriodId?: string | null;
}
export interface AcademicSessionView {
  id: string; code: string; label: string; startDate: string; endDate: string;
  status: 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED'; current: boolean; version: number;
  gradeEntryOpensAt: string | null; gradeEntryClosesAt: string | null;
  bulletinPublishOpensAt: string | null; bulletinPublishClosesAt: string | null;
  teacherSubmissionOpensAt: string | null; teacherSubmissionClosesAt: string | null; timezone: string;
  terms: AcademicTermView[];
}
export interface AcademicSessionUpsert {
  code: string; label: string; startDate: string; endDate: string; status?: string;
  current?: boolean; version?: number; gradeEntryOpensAt?: string | null;
  gradeEntryClosesAt?: string | null; bulletinPublishOpensAt?: string | null;
  bulletinPublishClosesAt?: string | null; teacherSubmissionOpensAt?: string | null;
  teacherSubmissionClosesAt?: string | null; timezone?: string;
}
export interface AcademicTermUpsert {
  code: string; label: string; sequenceNo: number; startDate: string; endDate: string;
  version?: number; gradeEntryOpensAt?: string | null; gradeEntryClosesAt?: string | null;
  bulletinPublishOpensAt?: string | null; bulletinPublishClosesAt?: string | null;
  teacherSubmissionOpensAt?: string | null; teacherSubmissionClosesAt?: string | null; timezone?: string;
}
export interface CalendarDayView {
  id: string; academicSessionId: string; dayOfWeek: number; teachingDay: boolean;
  startTime: string | null; endTime: string | null; version: number;
}
export interface GenerationResult {
  academicSessionId: string; startDate: string; endDate: string; teachingDates: number;
  classes: number; expectedRows: number; existingRows: number; insertedRows: number;
  removedFutureRows: number; sourceVersion: string; dryRun: boolean; warnings: string[];
}
export interface EnrollmentView {
  id: string; studentId: string; academicSessionId: string; sessionLabel: string;
  classId: string | null; className: string | null; level: string | null;
  subsystem: string | null; status: string; enrolledOn: string; exitedOn: string | null;
  source: string; reason: string | null; previousEnrollmentId: string | null; version: number;
}
export interface AuditView {
  id: string; actorUserId: string | null; actorUsername: string; action: string;
  aggregateType: string; aggregateId: string; beforeData: unknown; afterData: unknown;
  reason: string | null; requestId: string | null; correlationId: string | null; occurredAt: string;
}
export interface GeneratedDocumentView {
  id: string; documentType: string; aggregateType: string; aggregateId: string;
  aggregateVersion: string; locale: string; documentNumber: string; title: string;
  sha256: string; mimeType: string; sizeBytes: number; status: string; visibility: string;
  generatedAt: string; issuedAt: string | null; revokedAt: string | null; revokeReason: string | null;
}
export interface DocumentTemplateVersionView {
  id: string; type: string; locale: string; name: string; version: number;
  templateFamily: string; product: string; subsystem: string | null; status: string;
  referenceFamily: string; checksum: string | null; publishedAt: string | null;
}
export interface DocumentBrandingVersionView {
  id: string; locale: string; version: number; status: string; schoolName: string;
  schoolNameEn: string | null; motto: string | null; ministryText: string | null;
  address: string | null; city: string | null; country: string | null;
  logoContentType: string | null; logoConfigured: boolean; principalName: string | null;
  principalTitle: string | null; classMasterTitle: string | null; councilTitle: string | null;
  contentHash: string; createdAt: string; publishedAt: string | null;
}
export interface DocumentDesignView {
  templates: DocumentTemplateVersionView[];
  branding: DocumentBrandingVersionView[];
}

@Injectable({ providedIn: 'root' })
export class FoundationApi {
  private http = inject(HttpClient);
  private settings = `${environment.apiUrl}/settings`;

  listSessions(): Observable<AcademicSessionView[]> { return this.http.get<AcademicSessionView[]>(`${this.settings}/academic-sessions`); }
  currentSession(): Observable<AcademicSessionView> { return this.http.get<AcademicSessionView>(`${this.settings}/academic-sessions/current`); }
  createSession(body: AcademicSessionUpsert): Observable<AcademicSessionView> { return this.http.post<AcademicSessionView>(`${this.settings}/academic-sessions`, body); }
  updateSession(id: string, body: AcademicSessionUpsert): Observable<AcademicSessionView> { return this.http.put<AcademicSessionView>(`${this.settings}/academic-sessions/${id}`, body); }
  changeSessionState(id: string, status: string, reason: string, version: number): Observable<AcademicSessionView> {
    return this.http.post<AcademicSessionView>(`${this.settings}/academic-sessions/${id}/state`, { status, reason, version });
  }
  addTerm(sessionId: string, body: AcademicTermUpsert): Observable<AcademicTermView> { return this.http.post<AcademicTermView>(`${this.settings}/academic-sessions/${sessionId}/terms`, body); }
  updateTerm(id: string, body: AcademicTermUpsert): Observable<AcademicTermView> { return this.http.put<AcademicTermView>(`${this.settings}/academic-sessions/terms/${id}`, body); }
  deleteTerm(id: string, reason?: string): Observable<void> { return this.http.delete<void>(`${this.settings}/academic-sessions/terms/${id}`, { params: reason ? { reason } : {} }); }
  reportingPeriods(sessionId: string): Observable<AcademicReportingPeriodView[]> { return this.http.get<AcademicReportingPeriodView[]>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods`); }
  termManagementWindows(sessionId: string): Observable<TermManagementWindowView[]> { return this.http.get<TermManagementWindowView[]>(`${this.settings}/academic-sessions/${sessionId}/term-management-windows`); }
  updateTermManagementWindow(sessionId: string, termId: string, body: TermManagementWindowUpsert): Observable<TermManagementWindowView> {
    return this.http.put<TermManagementWindowView>(`${this.settings}/academic-sessions/${sessionId}/terms/${termId}/management-window`, body);
  }
  readiness(sessionId: string): Observable<SessionReadinessView> { return this.http.get<SessionReadinessView>(`${this.settings}/academic-sessions/${sessionId}/readiness`); }
  workflowWindowRules(sessionId: string): Observable<WorkflowWindowRuleView[]> { return this.http.get<WorkflowWindowRuleView[]>(`${this.settings}/academic-sessions/${sessionId}/window-rules`); }
  saveWorkflowWindowRule(sessionId: string, body: WorkflowWindowRuleUpsert): Observable<WorkflowWindowRuleView> { return this.http.put<WorkflowWindowRuleView>(`${this.settings}/academic-sessions/${sessionId}/window-rules`, body); }
  previewConfigurationCopy(targetSessionId: string, body: ConfigurationCopyPreviewRequest): Observable<ConfigurationCopyPreview> {
    return this.http.post<ConfigurationCopyPreview>(`${this.settings}/academic-sessions/${targetSessionId}/configuration-copy/preview`, body);
  }
  applyConfigurationCopy(targetSessionId: string, body: ConfigurationCopyApplyRequest, key: string): Observable<ConfigurationCopyPreview> {
    return this.http.post<ConfigurationCopyPreview>(`${this.settings}/academic-sessions/${targetSessionId}/configuration-copy/apply`, body, { headers: { 'Idempotency-Key': key } });
  }
  reportingDependencies(sessionId: string): Observable<StructureDependencyView[]> { return this.http.get<StructureDependencyView[]>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/dependencies`); }
  effectiveWindow(sessionId: string, periodId: string, action: WorkflowAction): Observable<EffectiveWindowView> {
    return this.http.get<EffectiveWindowView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/${periodId}/effective-window`, { params: { action } });
  }
  windowOverrides(sessionId: string, reportingPeriodId?: string): Observable<WindowOverrideView[]> {
    let params = new HttpParams();
    if (reportingPeriodId) params = params.set('reportingPeriodId', reportingPeriodId);
    return this.http.get<WindowOverrideView[]>(`${this.settings}/academic-sessions/${sessionId}/window-overrides`, { params });
  }
  createWindowOverride(sessionId: string, body: WindowOverrideUpsert): Observable<WindowOverrideView> {
    return this.http.post<WindowOverrideView>(`${this.settings}/academic-sessions/${sessionId}/window-overrides`, body);
  }
  revokeWindowOverride(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.settings}/academic-sessions/window-overrides/${id}/revoke`, null, { params: { reason } });
  }
  previewStandardStructure(sessionId: string): Observable<StandardStructureView> { return this.http.post<StandardStructureView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/standard/preview`, {}); }
  applyStandardStructure(sessionId: string, reason: string, fingerprint?: string, proposal?: StandardStructureView | null): Observable<StandardStructureView> {
    return this.http.post<StandardStructureView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/standard/apply`, {
      reason, fingerprint: fingerprint ?? null, periods: proposal?.periods ?? [], dependencies: proposal?.dependencies ?? [],
      termManagementWindows: proposal?.termManagementWindows ?? []
    });
  }
  updateReportingPeriod(sessionId: string, periodId: string, body: AcademicReportingPeriodUpsert): Observable<AcademicReportingPeriodView> {
    return this.http.put<AcademicReportingPeriodView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/${periodId}`, body);
  }

  calendarDays(sessionId: string): Observable<CalendarDayView[]> { return this.http.get<CalendarDayView[]>(`${this.settings}/calendar/${sessionId}/days`); }
  saveCalendarDay(sessionId: string, day: Omit<CalendarDayView, 'id' | 'academicSessionId'>): Observable<CalendarDayView> {
    return this.http.put<CalendarDayView>(`${this.settings}/calendar/${sessionId}/days`, day);
  }
  generateCalendar(academicSessionId: string, startDate: string, endDate: string, dryRun: boolean): Observable<GenerationResult> {
    return this.http.post<GenerationResult>(`${this.settings}/calendar/generate`, { academicSessionId, startDate, endDate, dryRun });
  }

  enrollmentHistory(studentId: string): Observable<EnrollmentView[]> { return this.http.get<EnrollmentView[]>(`${environment.apiUrl}/enrollments/students/${studentId}`); }
  transfer(studentId: string, body: { academicSessionId?: string; classId: string | null; effectiveDate: string; reason: string; version: number }): Observable<EnrollmentView> {
    return this.http.post<EnrollmentView>(`${environment.apiUrl}/enrollments/students/${studentId}/transfer`, body);
  }
  withdraw(id: string, body: { effectiveDate: string; reason: string; version: number }): Observable<EnrollmentView> {
    return this.http.post<EnrollmentView>(`${environment.apiUrl}/enrollments/${id}/withdraw`, body);
  }

  audit(aggregateType: string, aggregateId: string): Observable<AuditView[]> {
    return this.http.get<AuditView[]>(`${environment.apiUrl}/audit/${encodeURIComponent(aggregateType)}/${encodeURIComponent(aggregateId)}`);
  }
  listDocuments(aggregateType: string, aggregateId: string): Observable<GeneratedDocumentView[]> {
    const params = new HttpParams().set('aggregateType', aggregateType).set('aggregateId', aggregateId);
    return this.http.get<GeneratedDocumentView[]>(`${environment.apiUrl}/official-documents`, { params });
  }
  generateDocument(body: object, key: string): Observable<GeneratedDocumentView> {
    return this.http.post<GeneratedDocumentView>(`${environment.apiUrl}/official-documents/generate`, body, { headers: { 'Idempotency-Key': key } });
  }
  documentContent(id: string): Observable<Blob> { return this.http.get(`${environment.apiUrl}/official-documents/${id}/content`, { responseType: 'blob' }); }
  revokeDocument(id: string, reason: string): Observable<GeneratedDocumentView> { return this.http.post<GeneratedDocumentView>(`${environment.apiUrl}/official-documents/${id}/revoke`, { reason }); }
  actionPermissions(): Observable<Record<string, boolean>> { return this.http.get<Record<string, boolean>>(`${this.settings}/permission-actions`); }
  documentDesign(): Observable<DocumentDesignView> { return this.http.get<DocumentDesignView>(`${this.settings}/document-design`); }
  publishDocumentTemplate(id: string, reason: string): Observable<DocumentTemplateVersionView> {
    return this.http.post<DocumentTemplateVersionView>(`${this.settings}/document-design/templates/${id}/publish`, { reason });
  }
  publishDocumentBranding(locale: string, reason: string, logo?: { contentType: string; base64: string } | null): Observable<DocumentBrandingVersionView> {
    return this.http.post<DocumentBrandingVersionView>(`${this.settings}/document-design/branding/publish`, {
      locale, reason, logoContentType: logo?.contentType ?? null, logoBase64: logo?.base64 ?? null,
    });
  }
}
