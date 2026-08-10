import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GeneratedDocumentView } from '../../core/foundation.api';

export interface BulletinLine {
  subjectCode: string;
  subjectLabel: string;
  coef: number;
  mark: number;
  weighted: number;
  teacherRemark?: string;
  periodMarks?: Array<{ periodCode: string; mark: number }>;
  teacherName?: string | null;
  subjectGroupCode?: string | null;
  subjectGroupLabel?: string | null;
}

export interface BulletinView {
  id?: string;
  studentId: string;
  studentName: string;
  className: string;
  sequence: number;
  lines: BulletinLine[];
  average: number;
  rank: number;
  classSize: number;
  classAverage: number;
  validated: boolean;
  generalAppreciation: string | null;
  financiallyBlocked: boolean;
  reportingPeriodId?: string;
  reportingPeriodCode?: string;
  state?: string;
  complete?: boolean;
  blockers?: string[];
  snapshotHash?: string;
  version?: number;
  attendance?: { finalizedSessions: number; presentCount: number; absentCount: number; excusedCount: number; lateCount: number; lateMinutes: number; justifiedAbsenceHours: number; unjustifiedAbsenceHours: number; adjustedJustifiedHours: number; adjustedUnjustifiedHours: number; adjustedLateMinutes: number };
  conduct?: { workWarning: boolean; workBlame: boolean; conductWarning: boolean; conductBlame: boolean; honorRoll: boolean; encouragement: boolean; congratulations: boolean; exclusionDays: number; decisionCode: string | null; councilObservation: string | null; status: string };
  groupStats?: Array<{ code: string; label: string | null; average: number; total: number; coefficient: number; subjectCount: number }>;
}

export interface BulletinSnapshotView {
  id?: string; academicSessionId: string; reportingPeriodId: string;
  reportingPeriodCode: string; reportingPeriodLabel: string; studentId: string;
  studentName: string; matricule: string; educationalLevel?: string | null; subsystem?: string | null; className: string | null;
  lines: Array<{ subjectCode: string; subjectLabel: string; coefficient: number; mark: number; weighted: number; teacherRemark: string | null; appreciation: string; assessments: unknown[]; periodMarks?: Array<{ periodCode: string; mark: number }> | null; teacherName?: string | null; subjectGroupCode?: string | null; subjectGroupLabel?: string | null }>;
  average: number; rank: number | null; classSize: number; state: string; complete: boolean;
  blockers: string[]; snapshotHash: string; calculationPolicy: string; generalAppreciation: string | null; version: number;
  attendance: BulletinView['attendance']; conduct: BulletinView['conduct'];
  classStats?: { average: number; minimum: number; maximum: number; successCount: number; successRate: number; rankedCount: number } | null;
  groupStats?: Array<{ code: string; label: string | null; average: number; total: number; coefficient: number; subjectCount: number }> | null;
}

export interface PvRow {
  studentId: string;
  studentName: string;
  average: number;
  rank: number | null;
  state?: string;
  complete?: boolean;
  blockers?: string[];
}

export interface PvView {
  className: string;
  sequence: number;
  rows: PvRow[];
  classAverage: number;
  reportingPeriodId?: string;
  reportingPeriodCode?: string;
  reportingPeriodLabel?: string;
  totalStudents?: number;
  completeStudents?: number;
}

export interface GradeEntryAssessment {
  id: string; code: string; label: string; maxScore: number; weight: number;
  mandatory: boolean; displayOrder: number;
}
export interface AssessmentDefinition {
  id: string; academicSessionId: string; reportingPeriodId: string;
  code: string; label: string; assessmentType: string; maxScore: number;
  weight: number; mandatory: boolean; displayOrder: number; version: number;
  classId: string | null; subjectCode: string | null;
}
export interface AssessmentUpsert {
  reportingPeriodId: string; code: string; label: string; assessmentType?: string;
  maxScore: number; weight: number; mandatory: boolean; displayOrder: number;
  version?: number; classId?: string | null; subjectCode?: string | null;
}
export interface GradeEntrySubject {
  code: string; label: string; coefficient: number; teacherId: string | null; teacherName: string | null;
  status?: string; errorCode?: string; message?: string | null; remarkRequired?: boolean;
}
export interface GradeEntryCell {
  assessmentId: string; mark: number | null; valueStatus: 'SCORED' | 'MISSING' | 'ABSENT' | 'EXEMPT'; version: number;
}
export interface GradeEntryStudent {
  studentId: string; matricule: string; studentName: string; values: GradeEntryCell[];
  comment: string | null; workflowStatus: string;
}
export interface GradeEntryView {
  academicSessionId: string; reportingPeriodId: string; classId: string; className: string;
  subjectCode: string; subjectLabel: string; coefficient: number; teacherId: string | null; teacherName: string | null;
  packetStatus: 'DRAFT' | 'SUBMITTED' | 'RETURNED' | 'ACCEPTED' | 'LOCKED'; packetVersion: number;
  assessments: GradeEntryAssessment[]; students: GradeEntryStudent[]; totalStudents: number;
  completedStudents: number; blockers: string[]; availableSubjects: GradeEntrySubject[];
}
export interface GradeEntryCellUpsert { assessmentId: string; mark: number | null; valueStatus: string; version?: number; }
export interface GradeEntryStudentUpsert { studentId: string; values: GradeEntryCellUpsert[]; comment: string | null; }
export interface GradeEntrySaveRequest {
  reportingPeriodId: string; classId: string; subjectCode: string; students: GradeEntryStudentUpsert[]; packetVersion?: number;
}

export interface AttendanceAdjustment {
  id: string; justifiedAbsenceHours: number; unjustifiedAbsenceHours: number;
  lateMinutes: number; reason: string; evidenceReference: string | null;
  status: string; version: number;
}
export interface ReportCardInputRow {
  studentId: string; studentName: string; matricule: string;
  attendance: BulletinView['attendance'];
  attendanceAdjustment: AttendanceAdjustment | null;
  conduct: { workWarning: boolean; workBlame: boolean; conductWarning: boolean; conductBlame: boolean;
    honorRoll: boolean; encouragement: boolean; congratulations: boolean; exclusionDays: number;
    decisionCode: string | null; councilObservation: string | null; status: string; version: number } | null;
}
export interface ReportCardInputsView {
  academicSessionId: string; reportingPeriodId: string; reportingPeriodCode: string;
  reportingPeriodLabel: string; classId: string; className: string; rows: ReportCardInputRow[];
}
export interface ReportCardInputUpsert {
  reportingPeriodId: string; classId: string; studentId: string;
  justifiedAbsenceHours: number; unjustifiedAbsenceHours: number; lateMinutes: number;
  reason: string; evidenceReference?: string | null;
  workWarning: boolean; workBlame: boolean; conductWarning: boolean; conductBlame: boolean;
  honorRoll: boolean; encouragement: boolean; congratulations: boolean; exclusionDays: number;
  decisionCode?: string | null; councilObservation?: string | null;
  attendanceVersion?: number; conductVersion?: number;
}

export interface BulletinBatchJobView {
  id: string; academicSessionId: string; reportingPeriodId: string; classId: string; locale: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_ERRORS' | 'FAILED';
  totalItems: number; processedItems: number; publishedItems: number; blockedItems: number; errorItems: number;
  progressPercent: number; requestedAt: string; startedAt?: string | null; completedAt?: string | null;
  archiveAvailable: boolean; archiveSha256?: string | null; archiveSizeBytes?: number | null;
  lastError?: string | null; version: number;
}
export interface BulletinBatchItemView {
  id: string; studentId: string; studentName: string; status: 'QUEUED' | 'RUNNING' | 'PUBLISHED' | 'BLOCKED' | 'ERROR';
  attempts: number; fileName: string; sizeBytes: number; error: string;
}

export interface SecondaryCompetencyView {
  id: string; code: string; description: string; maxScore: number;
  displayOrder: number; active: boolean;
}
export interface SecondaryCompetencyMarkView {
  id: string; competencyId: string; reportingPeriodId: string; studentId: string;
  enrollmentId?: string | null; teacherId?: string | null; mark: number | null;
  valueStatus: 'SCORED' | 'ABSENT' | 'EXEMPT' | 'MISSING'; version: number;
}
export interface SecondaryCompetencyModelView {
  id: string; academicSessionId: string; reportingPeriodId: string; classId: string;
  subjectId: string; locale: string; name: string; version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED'; source: 'MANUAL' | 'IMPORT' | 'SEED';
  competencies: SecondaryCompetencyView[];
}
export interface SecondaryCompetencyModelRequest {
  academicSessionId: string; reportingPeriodId: string; classId: string;
  subjectId: string; locale: string; name: string; reason?: string;
  competencies: Array<{ code: string; description: string; maxScore?: number; displayOrder?: number }>;
}
export interface SecondaryCompetencyMarkRequest {
  modelId: string; competencyId: string; reportingPeriodId: string; studentId: string;
  enrollmentId?: string | null; teacherId?: string | null; mark?: number | null;
  valueStatus?: string; version?: number;
}
export interface SecondaryCompetencyImportRequest {
  modelId: string; reportingPeriodId: string;
  rows: Array<{ studentId: string; competencyCode: string; mark?: number | null; valueStatus?: string }>;
}

@Injectable({ providedIn: 'root' })
export class AcademicApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/academic`;

  bulletin(studentId: string, sequence: number): Observable<BulletinView> {
    return this.http.get<BulletinView>(
      `${this.base}/students/${encodeURIComponent(studentId)}/bulletin?sequence=${sequence}`,
    );
  }
  bulletinSnapshot(studentId: string, reportingPeriodId: string): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/students/${encodeURIComponent(studentId)}/bulletin-snapshots`, {}, { params: { reportingPeriodId } });
  }
  previewBulletinSnapshot(studentId: string, reportingPeriodId: string): Observable<BulletinSnapshotView> {
    return this.http.get<BulletinSnapshotView>(`${this.base}/students/${encodeURIComponent(studentId)}/bulletin-snapshots/preview`, { params: { reportingPeriodId } });
  }

  validateSnapshot(id: string): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/validate`, {});
  }

  publishSnapshot(id: string, reason: string, version?: number): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/publish`, { reason, version });
  }

  generateOfficialReportCard(id: string, locale: string, idempotencyKey: string): Observable<GeneratedDocumentView> {
    return this.http.post<GeneratedDocumentView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/document`, {}, {
      params: { locale }, headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  validate(studentId: string, sequence: number, appreciation: string): Observable<BulletinView> {
    return this.http.post<BulletinView>(
      `${this.base}/students/${encodeURIComponent(studentId)}/bulletin/validate`,
      { sequence, generalAppreciation: appreciation },
    );
  }

  pv(className: string, sequence: number): Observable<PvView> {
    return this.http.get<PvView>(
      `${this.base}/classes/${encodeURIComponent(className)}/pv?sequence=${sequence}`,
    );
  }

  sessionPv(classId: string, reportingPeriodId: string): Observable<PvView> {
    return this.http.get<PvView>(`${this.base}/classes/${encodeURIComponent(classId)}/pv-snapshot`, {
      params: { reportingPeriodId },
    });
  }

  gradeEntry(reportingPeriodId: string, classId: string, subjectCode?: string): Observable<GradeEntryView> {
    const params: Record<string, string> = { reportingPeriodId, classId };
    if (subjectCode) params['subjectCode'] = subjectCode;
    return this.http.get<GradeEntryView>(`${this.base}/grade-entry`, { params });
  }

  saveGradeEntry(body: GradeEntrySaveRequest): Observable<GradeEntryView> {
    return this.http.post<GradeEntryView>(`${this.base}/grade-entry/save`, body);
  }

  gradeEntryWorkflow(reportingPeriodId: string, classId: string, subjectCode: string, action: string, reason?: string, packetVersion?: number): Observable<GradeEntryView> {
    return this.http.post<GradeEntryView>(`${this.base}/grade-entry/workflow`, { reportingPeriodId, classId, subjectCode, action, reason, packetVersion });
  }

  assessments(reportingPeriodId: string, classId?: string, subjectCode?: string): Observable<AssessmentDefinition[]> {
    const params: Record<string, string> = { };
    if (classId) params['classId'] = classId;
    if (subjectCode) params['subjectCode'] = subjectCode;
    return this.http.get<AssessmentDefinition[]>(`${this.base}/reporting-periods/${encodeURIComponent(reportingPeriodId)}/assessments`, { params });
  }
  createAssessment(body: AssessmentUpsert): Observable<AssessmentDefinition> {
    return this.http.post<AssessmentDefinition>(`${this.base}/assessments`, body);
  }
  reportCardInputs(reportingPeriodId: string, classId: string): Observable<ReportCardInputsView> {
    return this.http.get<ReportCardInputsView>(`${this.base}/report-card-inputs`, { params: { reportingPeriodId, classId } });
  }
  saveReportCardInputs(body: ReportCardInputUpsert): Observable<ReportCardInputsView> {
    return this.http.put<ReportCardInputsView>(`${this.base}/report-card-inputs`, body);
  }
  submitReportCardInput(studentId: string, reportingPeriodId: string, classId: string): Observable<ReportCardInputsView> {
    return this.http.post<ReportCardInputsView>(`${this.base}/report-card-inputs/${encodeURIComponent(studentId)}/submit`, {}, { params: { reportingPeriodId, classId } });
  }
  reviewReportCardInput(studentId: string, body: { reportingPeriodId: string; classId: string; action: string; reason?: string; attendanceVersion?: number; conductVersion?: number }): Observable<ReportCardInputsView> {
    return this.http.post<ReportCardInputsView>(`${this.base}/report-card-inputs/${encodeURIComponent(studentId)}/review`, body);
  }
  bulletinBatch(classId: string, reportingPeriodId: string, locale: string): Observable<Blob> {
    return this.http.post(`${this.base}/classes/${encodeURIComponent(classId)}/bulletin-batch`, {}, { params: { reportingPeriodId, locale }, responseType: 'blob' });
  }
  bulletinBatchJobs(classId: string, reportingPeriodId: string): Observable<BulletinBatchJobView[]> {
    return this.http.get<BulletinBatchJobView[]>(`${this.base}/bulletin-batch-jobs`, { params: { classId, reportingPeriodId } });
  }
  createBulletinBatchJob(body: { classId: string; reportingPeriodId: string; locale: string }): Observable<BulletinBatchJobView> {
    return this.http.post<BulletinBatchJobView>(`${this.base}/bulletin-batch-jobs`, body);
  }
  bulletinBatchJob(id: string): Observable<BulletinBatchJobView> {
    return this.http.get<BulletinBatchJobView>(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}`);
  }
  bulletinBatchJobItems(id: string): Observable<BulletinBatchItemView[]> {
    return this.http.get<BulletinBatchItemView[]>(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/items`);
  }
  retryBulletinBatchJob(id: string, itemId?: string): Observable<BulletinBatchJobView> {
    return this.http.post<BulletinBatchJobView>(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/retry`, {}, { params: itemId ? { itemId } : {} });
  }
  downloadBulletinBatchJob(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/download`, { responseType: 'blob' });
  }

  secondaryCompetencyModels(params: { reportingPeriodId?: string; classId?: string; subjectId?: string; locale?: string } = {}): Observable<SecondaryCompetencyModelView[]> {
    return this.http.get<SecondaryCompetencyModelView[]>(`${this.base}/secondary-competencies`, { params: params as Record<string, string> });
  }
  secondaryCompetencyModel(id: string): Observable<SecondaryCompetencyModelView> {
    return this.http.get<SecondaryCompetencyModelView>(`${this.base}/secondary-competencies/${encodeURIComponent(id)}`);
  }
  createSecondaryCompetencyModel(body: SecondaryCompetencyModelRequest): Observable<SecondaryCompetencyModelView> {
    return this.http.post<SecondaryCompetencyModelView>(`${this.base}/secondary-competencies/models`, body);
  }
  copySecondaryCompetencyModel(id: string, reason: string): Observable<SecondaryCompetencyModelView> {
    return this.http.post<SecondaryCompetencyModelView>(`${this.base}/secondary-competencies/models/${encodeURIComponent(id)}/copy`, {}, { params: { reason } });
  }
  publishSecondaryCompetencyModel(id: string, reason: string): Observable<SecondaryCompetencyModelView> {
    return this.http.post<SecondaryCompetencyModelView>(`${this.base}/secondary-competencies/models/${encodeURIComponent(id)}/publish`, {}, { params: { reason } });
  }
  secondaryCompetencyMarks(modelId: string, reportingPeriodId: string, studentId?: string): Observable<SecondaryCompetencyMarkView[]> {
    const params: Record<string, string> = {};
    params['reportingPeriodId'] = reportingPeriodId;
    if (studentId) params['studentId'] = studentId;
    return this.http.get<SecondaryCompetencyMarkView[]>(`${this.base}/secondary-competencies/${encodeURIComponent(modelId)}/marks`, { params });
  }
  saveSecondaryCompetencyMark(body: SecondaryCompetencyMarkRequest): Observable<SecondaryCompetencyMarkView> {
    return this.http.put<SecondaryCompetencyMarkView>(`${this.base}/secondary-competencies/marks`, body);
  }
  importSecondaryCompetencyMarks(body: SecondaryCompetencyImportRequest): Observable<SecondaryCompetencyMarkView[]> {
    return this.http.post<SecondaryCompetencyMarkView[]>(`${this.base}/secondary-competencies/marks/import`, body);
  }
}
