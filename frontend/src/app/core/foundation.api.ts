import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AcademicTermView {
  id: string; code: string; label: string; sequenceNo: number;
  startDate: string; endDate: string; gradeEntryOpensAt: string | null;
  gradeEntryClosesAt: string | null; bulletinPublishOpensAt: string | null;
  bulletinPublishClosesAt: string | null; version: number;
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
  calculationPolicy?: string; status?: string; version?: number;
}
export interface StandardStructureView {
  academicSessionId: string; periods: AcademicReportingPeriodView[]; warnings: string[]; applied: boolean;
}
export interface AcademicSessionView {
  id: string; code: string; label: string; startDate: string; endDate: string;
  status: 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED'; current: boolean; version: number;
  gradeEntryOpensAt: string | null; gradeEntryClosesAt: string | null;
  bulletinPublishOpensAt: string | null; bulletinPublishClosesAt: string | null;
  terms: AcademicTermView[];
}
export interface AcademicSessionUpsert {
  code: string; label: string; startDate: string; endDate: string; status?: string;
  current?: boolean; version?: number; gradeEntryOpensAt?: string | null;
  gradeEntryClosesAt?: string | null; bulletinPublishOpensAt?: string | null;
  bulletinPublishClosesAt?: string | null;
}
export interface AcademicTermUpsert {
  code: string; label: string; sequenceNo: number; startDate: string; endDate: string;
  version?: number; gradeEntryOpensAt?: string | null; gradeEntryClosesAt?: string | null;
  bulletinPublishOpensAt?: string | null; bulletinPublishClosesAt?: string | null;
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
  previewStandardStructure(sessionId: string): Observable<StandardStructureView> { return this.http.post<StandardStructureView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/standard/preview`, {}); }
  applyStandardStructure(sessionId: string, reason: string): Observable<StandardStructureView> {
    return this.http.post<StandardStructureView>(`${this.settings}/academic-sessions/${sessionId}/reporting-periods/standard/apply`, {}, { params: { reason } });
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
}
