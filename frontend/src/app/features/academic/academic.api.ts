import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GeneratedDocumentView } from '../../core/foundation.api';

export interface ConductRecommendation {
  workWarning: boolean; workBlame: boolean; conductWarning: boolean; conductBlame: boolean;
  honorRoll: boolean; encouragement: boolean; congratulations: boolean; exclusionDays: number;
  policyVersion: string; reason: string | null; version: number; calculatedAt: string | null;
}

export interface AttendanceMetricValues {
  expectedHours: number | null; finalizedHours: number | null; coveragePercent: number | null;
  totalAbsenceMinutes: number | null; totalAbsenceHours: number | null;
  justifiedAbsenceMinutes: number | null; justifiedAbsenceHours: number | null;
  unjustifiedAbsenceMinutes: number | null; unjustifiedAbsenceHours: number | null;
  lateMinutes: number | null; exclusionDays: number | null;
}
export interface AttendanceSessionEvidence {
  expectedSessionId: string | null; rollCallId: string | null; date: string | null;
  model: string | null; periodKey: string | null; subjectCode: string | null;
  status: string | null; cancelled: boolean; durationMinutes: number | null;
  durationHours: number | null; issue: string | null; repairTarget: string | null;
}
export interface AttendanceAdjustmentEvidence {
  id: string; justifiedAbsenceHours: number; unjustifiedAbsenceHours: number; lateMinutes: number;
  reason: string; evidenceReference: string | null; status: string; version: number;
  actorUserId: string | null; actorUsername: string | null; createdAt: string | null;
  reclassifiesAbsence: boolean; correctsAdjustmentId: string | null;
}
export interface AttendanceReadinessIssue {
  code: string; severity: string; studentId: string | null; date: string | null;
  expectedSessionId: string | null; rollCallId: string | null;
  messageFr: string; messageEn: string; repairTarget: string | null;
}
export interface AttendanceSummary {
  finalizedSessions: number; presentCount: number; absentCount: number; excusedCount: number;
  lateCount: number; lateMinutes: number; justifiedAbsenceHours: number | null;
  unjustifiedAbsenceHours: number | null; adjustedJustifiedHours: number;
  adjustedUnjustifiedHours: number; adjustedLateMinutes: number;
  expectedSessionCount: number; expectedHours: number | null; finalizedHours: number | null;
  coveragePercent: number | null; missingSessions: AttendanceSessionEvidence[];
  sourceRollCallIds: string[]; totalAbsenceMinutes: number | null; totalAbsenceHours: number | null;
  justifiedAbsenceMinutes: number | null; unjustifiedAbsenceMinutes: number | null;
  exclusionDays: number; approvedAdjustments: AttendanceAdjustmentEvidence[]; policyVersion: string;
  blockers: AttendanceReadinessIssue[]; warnings: AttendanceReadinessIssue[];
  rawValues: AttendanceMetricValues | null; displayValues: AttendanceMetricValues | null;
  annualEvidenceVersion: string | null; annualDraftRequired: boolean; sourceSnapshotIds: string[];
}
export interface AttendanceSourceBreakdown {
  expectedSessionId: string | null; rollCallId: string | null; studentId: string;
  date: string | null; model: string | null; periodKey: string | null; subjectCode: string | null;
  sessionStatus: string | null; markStatus: string | null; durationMinutes: number;
  absenceMinutes: number; lateMinutes: number; markSource: string | null; reason: string | null;
  note: string | null; cancelled: boolean; sessionVersion: number;
}
export interface AttendanceAggregation {
  academicSessionId: string; reportingPeriodId: string; classId: string | null; studentId: string;
  className: string | null; model: string | null; attendance: AttendanceSummary;
}

export interface BulletinLine {
  subjectCode: string;
  subjectLabel: string;
  coef: number;
  mark: number | null;
  weighted: number | null;
  teacherRemark?: string;
  periodMarks?: Array<{ periodCode: string; mark: number | null }>;
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
  reportingPeriodType?: string;
  product?: string;
  lines: BulletinLine[];
  average: number | null;
  rank: number | null;
  classSize: number;
  classAverage: number | null;
  validated: boolean;
  generalAppreciation: string | null;
  financiallyBlocked: boolean;
  reportingPeriodId?: string;
  reportingPeriodCode?: string;
  reportingPeriodLabel?: string;
  state?: string;
  complete?: boolean;
  blockers?: string[];
  snapshotHash?: string;
  version?: number;
  attendance?: AttendanceSummary;
  conduct?: { workWarning: boolean; workBlame: boolean; conductWarning: boolean; conductBlame: boolean; honorRoll: boolean; encouragement: boolean; congratulations: boolean; exclusionDays: number; decisionCode: string | null; councilObservation: string | null; status: string; recommendation?: ConductRecommendation | null; overrideBy?: string | null; overrideReason?: string | null; version?: number };
  groupStats?: Array<{ code: string; label: string | null; average: number; total: number; coefficient: number; subjectCount: number }>;
  workflowMeta?: BulletinWorkflowMeta;
  issues?: BulletinIssue[];
  capabilities?: BulletinCapabilities;
}

export interface BulletinIssue {
  code: string; severity: 'ERROR' | 'WARNING' | string; periodCode?: string | null;
  subjectCode?: string | null; messageFr: string; messageEn: string; repairTarget?: string | null;
}
export interface DependencyReadiness {
  periodId: string; code: string; label: string; periodType: string; weight: number;
  optional: boolean; readiness: string; expectedPacketCount: number; acceptedPacketCount: number;
  lockedPacketCount: number; submittedPacketCount: number; draftPacketCount: number;
  returnedPacketCount: number; missingPacketCount: number;
}
export interface BulletinCapabilities {
  canCreateDraft: boolean; canRefreshDraft: boolean; canValidate: boolean; canPublish: boolean;
  validationBlockers: string[];
}
export interface BulletinWorkflowMeta {
  inputReadiness: string; versionRelation: string; currentSourceHash?: string | null;
  persistedVersionId?: string | null; persistedVersionState?: string | null;
  persistedVersionNumber?: number | null; persistedSnapshotHash?: string | null;
  persistedAverage?: number | null; refreshRequired: boolean;
  dependencies: DependencyReadiness[]; capabilities: BulletinCapabilities;
}

export interface BulletinSnapshotView {
  id?: string; academicSessionId: string; reportingPeriodId: string;
  reportingPeriodCode: string; reportingPeriodLabel: string; studentId: string;
  studentName: string; matricule: string; educationalLevel?: string | null; subsystem?: string | null; className: string | null;
  lines: Array<{ subjectCode: string; subjectLabel: string; coefficient: number; mark: number | null; weighted: number | null; teacherRemark: string | null; appreciation: string; assessments: unknown[]; periodMarks?: Array<{ periodCode: string; mark: number | null }> | null; teacherName?: string | null; subjectGroupCode?: string | null; subjectGroupLabel?: string | null }>;
  average: number | null; rank: number | null; classSize: number; state: string; complete: boolean;
  blockers: string[]; snapshotHash: string; calculationPolicy: string; generalAppreciation: string | null; version: number;
  attendance: BulletinView['attendance']; conduct: BulletinView['conduct'];
  classStats?: { average: number; minimum: number; maximum: number; successCount: number; successRate: number; rankedCount: number } | null;
  groupStats?: Array<{ code: string; label: string | null; average: number; total: number; coefficient: number; subjectCount: number }> | null;
  reportingPeriodType?: string; product?: string; workflowMeta?: BulletinWorkflowMeta;
  issues?: BulletinIssue[];
  snapshot?: AuthoritativeSnapshotView | null;
}

export interface AuthoritativeSnapshotView {
  contractVersion: number; schoolId: string; academicSessionId: string; reportingPeriodId: string;
  reportingPeriodCode: string; reportingPeriodLabel: string; product: string;
  student: { id: string; name: string; firstName: string; lastName: string; matricule: string; dateOfBirth: string | null; placeOfBirth: string | null; gender: string | null; repeater: boolean };
  enrollment: { id: string; classId: string | null; classLabel: string | null; level: string | null; subsystem: string | null; classSize: number };
  permittedGuardian: { id: string; displayName: string; relationshipType: string } | null;
  staff: { classMaster: SnapshotTeacher | null; subjectTeachers: SnapshotTeacher[] };
  profilePhoto: { assetVersionId: string | null; contentType: string | null; sha256: string | null; width: number | null; height: number | null; fallbackDecision: string; capturedAt: string | null };
  school: { id: string; code: string | null; name: string | null; authority: string | null; address: string | null; city: string | null; country: string | null; phone: string | null; email: string | null; website: string | null; branding: unknown };
  curriculum: { versionId: string | null; versionNumber: number; state: string; contentHash: string | null; rows: unknown[] };
  result: { preciseAverage: number | null; displayAverage: number | null; overallRank: number | null; classSize: number; subjects: unknown[]; groups: unknown[]; classStats: unknown; blockers: string[]; issues: BulletinIssue[] };
  evidence: unknown; attendance: BulletinView['attendance']; conduct: BulletinView['conduct']; template: unknown;
  formulaVersion: string; calculationPolicy: string; generationActorId: string | null; generationTime: string | null;
  sourceVersions: Array<{ sourceType: string; sourceId: string | null; sourceVersion: number | null; sourceHash: string | null; label: string | null }>;
  canonicalSnapshotHash: string;
}
export interface SnapshotTeacher { id: string; code: string; name: string; role: string; subjectCode: string | null; assignmentVersion: number; }
export interface FormulaDrilldownView { snapshotId: string; snapshotVersion: number; snapshotHash: string; formulaVersion: string; calculationPolicy: string; product: string; steps: unknown[]; sourceVersions: AuthoritativeSnapshotView['sourceVersions']; }
export interface SnapshotDiffView { fromSnapshotId: string; toSnapshotId: string; fromHash: string; toHash: string; identical: boolean; changes: Array<{ path: string; fromValue: string | null; toValue: string | null }>; }
export interface BulletinTransitionView {
  id: string; bulletinVersionId: string; sourceVersionId: string | null; fromState: string | null; toState: string;
  eventType: string; actorUserId: string | null; occurredAt: string; reason: string | null;
  sourceVersions: string; optimisticVersion: number; calculationSnapshotHash: string | null;
  templateVersion: string | null; generatedDocumentId: string | null; auditEventId: string | null; affectedRows: string[];
}
export interface BulletinLifecycleStateView {
  bulletinVersionId: string; state: string; publicationProduct: string; publicationLocale: string | null;
  generatedDocumentId: string | null; supersedesId: string | null; correctsBulletinVersionId: string | null;
  version: number; allowedTransitions: string[]; windowState: string; governingTrimester: string | null;
  affectedMilestones: string[]; history: BulletinTransitionView[];
}

export interface PvRow {
  studentId: string;
  studentName: string;
  average: number | null;
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
export type AssessmentDefaultsMode = 'ONE_SEQUENCE' | 'ALL_SEQUENCES';
export interface AssessmentDefaultsRowInput {
  clientRowId: string; reportingPeriodId: string; subjectCode: string;
  code?: string; label?: string; maxScore?: number; weight?: number; mandatory?: boolean;
}
export interface AssessmentDefaultsRequest {
  academicSessionId: string; classId: string; mode: AssessmentDefaultsMode;
  reportingPeriodId?: string; rows?: AssessmentDefaultsRowInput[]; scopeFingerprint?: string;
}
export interface AssessmentDefaultsRow {
  clientRowId: string; reportingPeriodId: string; reportingPeriodCode: string; reportingPeriodLabel: string;
  curriculumSubjectId: string; subjectCode: string; subjectLabel: string; coefficient: number;
  maxScore: number; weight: number; mandatory: boolean; teacherId: string | null; teacherName: string | null;
  teacherStatus: string; proposedCode: string; proposedLabel: string; status: 'PROPOSED' | 'EXISTING' | 'INVALID';
  errors: string[]; existingAssessmentId: string | null; existingVersion: number;
}
export interface AssessmentDefaultsPeriod { reportingPeriodId: string; code: string; label: string; rows: AssessmentDefaultsRow[]; }
export interface AssessmentDefaultsPreview {
  academicSessionId: string; classId: string; className: string; subsystem: string; contentLanguage: string;
  mode: AssessmentDefaultsMode; scopeFingerprint: string; periods: AssessmentDefaultsPeriod[];
  totalRows: number; proposedRows: number; existingRows: number; excludedRows: number;
}
export interface AssessmentDefaultsApplyResponse {
  preview: AssessmentDefaultsPreview; generationBatchId: string;
  createdCount: number; existingCount: number; skippedCount: number;
}
export interface GradeEntrySubject {
  code: string; label: string; coefficient: number; teacherId: string | null; teacherName: string | null;
  status?: string; errorCode?: string; message?: string | null; remarkRequired?: boolean;
  assignmentReadiness?: { status: string; code: string; teacherId: string | null; teacherName: string | null; teacherCode?: string | null; assignmentId?: string | null; assignmentVersion?: number; source?: string | null; role?: string | null; messageFr?: string | null; messageEn?: string | null; repairable?: boolean };
}
export interface GradeEntryCell {
  assessmentId: string; mark: number | null; valueStatus: 'SCORED' | 'MISSING' | 'ABSENT' | 'EXEMPT'; version: number;
}
export interface GradeEntryStudent {
  studentId: string; matricule: string; studentName: string; values: GradeEntryCell[];
  comment: string | null; appreciationCode?: string | null; workflowStatus: string;
}
export interface GradeEntryView {
  academicSessionId: string; reportingPeriodId: string; classId: string; className: string;
  subjectCode: string; subjectLabel: string; coefficient: number; teacherId: string | null; teacherName: string | null;
  packetStatus: 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'RETURNED' | 'ACCEPTED' | 'LOCKED'; packetVersion: number;
  assessments: GradeEntryAssessment[]; students: GradeEntryStudent[]; totalStudents: number;
  completedStudents: number; blockers: string[]; availableSubjects: GradeEntrySubject[];
  completionBlockers?: Array<{ code: string; subjectCode: string; studentName?: string | null; messageFr: string; messageEn: string; repairTarget: string; severity: string }>;
  submissionBlockers?: Array<{ code: string; subjectCode: string; studentName?: string | null; messageFr: string; messageEn: string; repairTarget: string; severity: string }>;
  warnings?: Array<{ code: string; subjectCode: string; studentName?: string | null; messageFr: string; messageEn: string; repairTarget: string; severity: string }>;
  assignmentReadiness?: GradeEntrySubject['assignmentReadiness'];
  capabilities?: { canEditDraft: boolean; canSubmit: boolean; canReview: boolean; restrictedTeacher: boolean; explanation?: string | null };
  saveResults?: Array<{ studentId: string; assessmentId: string | null; outcome: 'SAVED' | 'UNCHANGED' | 'CONFLICT' | 'INVALID' | 'FORBIDDEN'; currentMark: number | null; currentValueStatus: string; currentVersion: number; fieldErrors: Record<string, string>; retryable: boolean }>;
}
export interface GradeEntryCellUpsert { assessmentId: string; mark: number | null; valueStatus: string; version?: number; }
export interface GradeEntryStudentUpsert { studentId: string; values: GradeEntryCellUpsert[]; comment: string | null; appreciationCode?: string | null; }
export interface GradeEntrySaveRequest {
  reportingPeriodId: string; classId: string; subjectCode: string; students: GradeEntryStudentUpsert[]; packetVersion?: number; requestId?: string;
}
export interface GradePacketQueueItem {
  packetId: string; reportingPeriodId: string; reportingPeriodCode: string; reportingPeriodLabel: string;
  classId: string; className: string; subjectCode: string; subjectLabel: string;
  packetStatus: 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'RETURNED' | 'ACCEPTED' | 'LOCKED';
  revisionNumber: number; totalStudents: number; completedStudents: number; completionState: string;
  windowState: string; windowOpensAt: string | null; windowClosesAt: string | null; returnedReason: string | null;
  teacherId: string | null; teacherName: string | null; packetVersion: number; actions: string[];
}
export interface GradePacketQueueView { teacherQueue: GradePacketQueueItem[]; reviewerQueue: GradePacketQueueItem[]; }
export interface GradePacketHistoryView {
  packetId: string; revisionNumber: number; supersedesPacketId: string | null; teacherId: string | null;
  responsibleAssignmentId: string | null; responsibleAssignmentVersion: number | null;
  transitions: Array<{ id: string; eventType: string; fromStatus: string | null; toStatus: string; reason: string | null; actorUserId: string | null; createdAt: string; affectedRows: string[] }>;
  comments: Array<{ id: string; commentId: string; comment: string | null; appreciationCode: string | null; workflowStatus: string; authorUserId: string | null; sourceVersion: number; changedBy: string | null; changedAt: string }>;
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
    decisionCode: string | null; councilObservation: string | null; status: string; version: number;
    recommendation?: ConductRecommendation | null; overrideBy?: string | null; overrideReason?: string | null } | null;
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
  overrideReason?: string | null; correctionReason?: string | null; correctionEvidenceReference?: string | null;
}

export type BulletinBatchResultCategory = 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'BLOCKED' | 'FAILED' | 'CANCELLED' | string;
export type BulletinBatchItemCategory = 'BUSINESS_BLOCKER' | 'TECHNICAL_ERROR' | 'SUCCESS' | 'RUNNING' | string;
export type BulletinBatchItemStatus = 'QUEUED' | 'RUNNING' | 'PUBLISHED' | 'BLOCKED' | 'ERROR';

export interface BulletinBatchReasonCount { code: string; count: number; }
export interface BulletinBatchRepairTarget { route: string; query: Record<string, string>; }
export interface BulletinBatchSnapshotEvidence {
  id: string; version: number; hash: string; publishedAt?: string | null; state?: string | null;
}
export interface BulletinBatchWindowView {
  state: 'UNRESTRICTED' | 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'INVALID' | string;
  launchAllowed: boolean; governingTrimesterCode: string; governingTrimesterLabel: string;
  affectedMilestones: string[]; timezone: string; serverTime: string;
  opensAt?: string | null; closesAt?: string | null; nextTransition?: string | null;
  repairTarget?: BulletinBatchRepairTarget | null;
}
export interface BulletinBatchPreviewRow {
  studentId: string; studentName: string; matricule: string; eligibility: 'READY' | 'BLOCKED' | string;
  code: string; category: BulletinBatchItemCategory; messageKey: string; messageArgs: Record<string, unknown>;
  currentState?: string | null; retryableNow: boolean; repairTarget?: BulletinBatchRepairTarget | null;
  snapshot?: BulletinBatchSnapshotEvidence | null;
  reportingPeriodId?: string | null; reportingPeriodCode?: string | null; reportingPeriodLabel?: string | null;
  product?: string | null; correctiveAction?: string | null; affectedRows?: string[];
}
export interface BulletinBatchPreviewView {
  policy: 'PUBLISHED_ONLY' | string; academicSessionId: string; academicSessionLabel: string;
  classId: string; className: string; reportingPeriodId: string; reportingPeriodCode: string;
  reportingPeriodLabel: string; totalStudents: number; readyStudents: number; blockedStudents: number;
  reasonCounts: BulletinBatchReasonCount[]; rows: BulletinBatchPreviewRow[]; scopeFingerprint: string;
  generatedAt: string; window: BulletinBatchWindowView;
  reportingPeriodIds?: string[]; products?: string[]; windows?: BulletinBatchWindowView[];
}
export interface BulletinBatchJobCreateRequest {
  classId: string; reportingPeriodId: string; locale: string; scopeFingerprint?: string;
  includeReadyStudentsWhenPartiallyBlocked?: boolean; reportingPeriodIds?: string[]; products?: string[];
}

export interface BulletinBatchJobView {
  id: string; academicSessionId: string; reportingPeriodId: string; classId: string; locale: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_ERRORS' | 'FAILED' | 'CANCELLED';
  totalItems: number; processedItems: number; publishedItems: number; blockedItems: number; errorItems: number;
  progressPercent: number; requestedAt: string; startedAt?: string | null; completedAt?: string | null;
  archiveAvailable: boolean; archiveSha256?: string | null; archiveSizeBytes?: number | null;
  lastError?: string | null; version: number; policy?: 'PUBLISHED_ONLY' | string;
  scopeFingerprint?: string | null; resultCategory?: BulletinBatchResultCategory; headlineCode?: string;
  headlineArgs?: Record<string, unknown>; reasonCounts?: BulletinBatchReasonCount[];
  studentArchiveAvailable?: boolean; diagnosticReportAvailable?: boolean; retryableErrorItems?: number;
  nowEligibleBlockedItems?: number; stillBlockedItems?: number; diagnosticSha256?: string | null;
  diagnosticSizeBytes?: number | null; window?: BulletinBatchWindowView | null;
  reportingPeriodIds?: string[]; products?: string[]; windows?: BulletinBatchWindowView[];
}
export interface BulletinBatchItemView {
  id: string; studentId: string; studentName: string; status: BulletinBatchItemStatus;
  attempts: number; fileName: string; sizeBytes: number; error: string; resultCode?: string | null;
  category?: BulletinBatchItemCategory | null; messageKey?: string | null; messageArgs?: Record<string, unknown>;
  currentState?: string | null; retryableNow?: boolean; repairTarget?: BulletinBatchRepairTarget | null;
  snapshot?: BulletinBatchSnapshotEvidence | null; correlationId?: string | null; technicalDetail?: string | null;
  reportingPeriodId?: string | null; reportingPeriodCode?: string | null; reportingPeriodLabel?: string | null;
  product?: string | null; correctiveAction?: string | null; affectedRows?: string[];
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
  private attendanceBase = `${environment.apiUrl}/attendance`;

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
  bulletinFormula(id: string): Observable<FormulaDrilldownView> {
    return this.http.get<FormulaDrilldownView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/formula`);
  }
  bulletinSourceVersionDiff(id: string, fromId: string): Observable<SnapshotDiffView> {
    return this.http.get<SnapshotDiffView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/source-version-diff`, { params: { fromId } });
  }

  validateSnapshot(id: string): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/validate`, {});
  }

  bulletinLifecycle(id: string): Observable<BulletinLifecycleStateView> {
    return this.http.get<BulletinLifecycleStateView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/lifecycle`);
  }

  transitionBulletin(id: string, action: string, reason: string, version: number): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/transition/${encodeURIComponent(action)}`, { reason, version });
  }

  startBulletinCorrection(id: string, reason: string, version: number): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/correction`, { reason, version });
  }

  refreshBulletinDraft(id: string, reason: string, version: number): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/refresh`, { reason, version });
  }

  publishSnapshot(id: string, reason: string, version?: number): Observable<BulletinSnapshotView> {
    return this.http.post<BulletinSnapshotView>(`${this.base}/bulletin-snapshots/${encodeURIComponent(id)}/publish`, { reason, version }, {
      // Keep the idempotency key short enough for the API gateway/header limit.
      // The bulletin id and optimistic version identify the publication attempt;
      // the free-form reason belongs in the JSON body, not in the header.
      headers: { 'Idempotency-Key': `bulletin-publish:${id}:${version ?? ''}` },
    });
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

  gradeEntryQueue(): Observable<GradePacketQueueView> {
    return this.http.get<GradePacketQueueView>(`${this.base}/grade-entry/queue`);
  }

  gradeEntryHistory(packetId: string): Observable<GradePacketHistoryView> {
    return this.http.get<GradePacketHistoryView>(`${this.base}/grade-entry/${encodeURIComponent(packetId)}/history`);
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
  previewAssessmentDefaults(body: AssessmentDefaultsRequest): Observable<AssessmentDefaultsPreview> {
    return this.http.post<AssessmentDefaultsPreview>(`${this.base}/assessment-defaults/preview`, body);
  }
  applyAssessmentDefaults(body: AssessmentDefaultsRequest, idempotencyKey: string): Observable<AssessmentDefaultsApplyResponse> {
    return this.http.post<AssessmentDefaultsApplyResponse>(`${this.base}/assessment-defaults/apply`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
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
  attendanceEvidence(reportingPeriodId: string, studentId: string): Observable<AttendanceAggregation> {
    return this.http.get<AttendanceAggregation>(`${this.attendanceBase}/evidence`, { params: { reportingPeriodId, studentId } });
  }
  attendanceSources(reportingPeriodId: string, studentId: string): Observable<AttendanceSourceBreakdown[]> {
    return this.http.get<AttendanceSourceBreakdown[]>(`${this.attendanceBase}/evidence/sources`, { params: { reportingPeriodId, studentId } });
  }
  bulletinBatch(classId: string, reportingPeriodId: string, locale: string): Observable<Blob> {
    return this.http.post(`${this.base}/classes/${encodeURIComponent(classId)}/bulletin-batch`, {}, { params: { reportingPeriodId, locale }, responseType: 'blob' });
  }
  bulletinBatchJobs(classId: string, reportingPeriodId: string): Observable<BulletinBatchJobView[]> {
    return this.http.get<BulletinBatchJobView[]>(`${this.base}/bulletin-batch-jobs`, { params: { classId, reportingPeriodId } });
  }
  previewBulletinBatch(body: { classId: string; reportingPeriodId?: string; locale: string; reportingPeriodIds?: string[]; products?: string[] }): Observable<BulletinBatchPreviewView> {
    return this.http.post<BulletinBatchPreviewView>(`${this.base}/bulletin-batch-jobs/preview`, body);
  }
  createBulletinBatchJob(body: BulletinBatchJobCreateRequest): Observable<BulletinBatchJobView> {
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
  recheckBlockedBatchItems(id: string, itemId?: string): Observable<BulletinBatchJobView> {
    return this.http.post<BulletinBatchJobView>(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/recheck-blocked`, {}, { params: itemId ? { itemId } : {} });
  }
  retryBatchErrors(id: string, itemId?: string): Observable<BulletinBatchJobView> {
    return this.http.post<BulletinBatchJobView>(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/retry-errors`, {}, { params: itemId ? { itemId } : {} });
  }
  downloadBulletinBatchJob(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/download`, { responseType: 'blob' });
  }
  downloadBulletinBatchDiagnostic(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/bulletin-batch-jobs/${encodeURIComponent(id)}/diagnostic`, { responseType: 'blob' });
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
