import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SectionView { id: string; label: string; subsystem: string; level: string; classCount: number; }
export interface SectionUpsert { label: string; subsystem: string; level: string; }

export interface ClassView {
  id: string; name: string; sectionId: string; sectionLabel: string;
  subsystem: string; level: string; studentCount: number; teacherCount: number;
}
export interface ClassUpsert { name: string; sectionId: string; }

export interface TeacherOption {
  id: string; name: string; code: string;
  /** Section (cycle) de l'enseignant : maternelle | primary | secondary, null si non définie. */
  section: string | null;
  accountUsername?: string | null; accountRole?: string | null; accountActive?: boolean;
}

export interface SubjectView { id: string; code: string; subsystem: string | null; label: Record<string, string>; coef: number; }
export interface SubjectUpsert { code: string; subsystem: string | null; label: Record<string, string>; coef: number; }

// Per-class coefficients
export interface ClassCoefView { classId: string; className: string; subsystem: string; subjectId: string; subjectCode: string; coef: number; defaultCoef: number; }
export interface ClassCoefUpsert { classId: string; subjectId: string; coef: number; }
export interface CoefImportRow { subsystem: string; code: string; label?: string; klass: string; coef: number | null; }
export interface CoefImportError { row: number; label: string; message: string; }
export interface CoefImportResult { applied: number; subjectsCreated: number; skipped: number; errors: CoefImportError[]; }

export interface SubjectGroupView { id: string; code: string; label: Record<string, string>; displayOrder: number; showSubtotal: boolean; showRank: boolean; averagePolicy: string; version: number; }
export interface SubjectGroupUpsert { academicSessionId: string; code: string; label: Record<string, string>; displayOrder: number; showSubtotal?: boolean; showRank?: boolean; averagePolicy?: string; version?: number; }
export interface CurriculumTeacherView { id: string; employeeId: string; employeeName: string; employeeCode: string; role: string; source: string; active: boolean; version: number; accountUsername?: string | null; accountRole?: string | null; accountActive?: boolean; }
export interface CurriculumSubjectView {
  id: string; subjectId: string; subjectCode: string; subjectLabel: string; classId?: string; className?: string; defaultCoef?: number; groupId: string | null; groupCode: string | null;
  displayOrder: number; coefficient: number; maxScore: number; mandatory: boolean; passThreshold: number;
  showSubjectRank: boolean; remarkRequired: boolean; responsibleTeacher: CurriculumTeacherView | null; version: number;
  activeFrom?: string | null; activeTo?: string | null;
}
export interface CurriculumView { academicSessionId: string; sessionCode: string; sessionLabel: string; classId: string; className: string; groups: SubjectGroupView[]; subjects: CurriculumSubjectView[]; homeroomTeacher: CurriculumTeacherView | null; }
export interface CurriculumSubjectUpsert {
  academicSessionId: string; classId: string; subjectId: string; groupId?: string | null; displayOrder?: number;
  coefficient?: number; maxScore?: number; mandatory?: boolean; passThreshold?: number; showSubjectRank?: boolean;
  remarkRequired?: boolean; version?: number; activeFrom?: string | null; activeTo?: string | null;
}
export interface CurriculumTeacherUpsert {
  academicSessionId: string; classId: string; subjectId: string; employeeId: string; role: string; source?: string;
  effectiveFrom?: string | null; effectiveTo?: string | null; version?: number;
}
export interface HomeroomAssignmentUpsert { academicSessionId: string; classId: string; employeeId: string; effectiveFrom?: string | null; effectiveTo?: string | null; version?: number; }
export interface AssignmentImpactRequest { academicSessionId: string; classId: string; subjectId?: string | null; employeeId: string; role: 'HOMEROOM' | 'RESPONSIBLE'; effectiveFrom?: string | null; effectiveTo?: string | null; }
export interface AssignmentImpactSlotView { versionId: string; versionNo: number; versionStatus: string; slotId: string; subjectCode: string; dayIdx: number; slotIdx: number; publishedTeacherId: string | null; publishedTeacherName: string | null; teacherChanges: boolean; }
export interface AssignmentImpactView { academicSessionId: string; classId: string; subjectId: string | null; role: string; proposedTeacherId: string; effectiveFrom: string; effectiveTo: string | null; draftSlotCount: number; publishedSlotCount: number; publishedScheduleDrift: boolean; requiresNewDraftVersion: boolean; affectedPublishedSlots: AssignmentImpactSlotView[]; warnings: string[]; blockers: string[]; }
export interface CurriculumCopyPreviewRequest {
  sourceSessionId: string; targetSessionId: string; classIds?: string[]; allMatchingClasses?: boolean;
  includeGroups?: boolean; includeTeachers?: boolean; mergeMode?: string; selectedKeys?: string[]; edits?: CurriculumCopyEdit[];
}
export interface CurriculumCopyEdit { key: string; field: string; value: string | null; }
export interface CurriculumCopyRow {
  key: string; classId: string; className: string; subjectId: string | null; subjectCode: string; subjectLabel: string;
  status: string; source: Record<string, unknown>; proposed: Record<string, unknown>; existing: Record<string, unknown> | null;
  teacherStatus: string; teacherMessage: string | null; warnings: string[]; blockers: string[];
}
export interface CurriculumCopyPreview {
  sourceSessionId: string; targetSessionId: string; classCount: number; groups: SubjectGroupView[]; rows: CurriculumCopyRow[];
  warnings: string[]; blockers: string[]; fingerprint: string; createCount: number; updateCount: number; keepCount: number;
}
export interface CurriculumCopyApplyRequest extends CurriculumCopyPreviewRequest { reason: string; previewFingerprint: string; }

/** Academic Setup — the relational backbone (sections, classes, subjects). */
@Injectable({ providedIn: 'root' })
export class SetupApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/setup`;

  // Sections
  listSections(): Observable<SectionView[]> { return this.http.get<SectionView[]>(`${this.base}/sections`); }
  createSection(b: SectionUpsert): Observable<SectionView> { return this.http.post<SectionView>(`${this.base}/sections`, b); }
  updateSection(id: string, b: SectionUpsert): Observable<SectionView> { return this.http.put<SectionView>(`${this.base}/sections/${id}`, b); }
  deleteSection(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/sections/${id}`); }

  // Classes
  listClasses(): Observable<ClassView[]> { return this.http.get<ClassView[]>(`${this.base}/classes`); }
  createClass(b: ClassUpsert): Observable<ClassView> { return this.http.post<ClassView>(`${this.base}/classes`, b); }
  updateClass(id: string, b: ClassUpsert): Observable<ClassView> { return this.http.put<ClassView>(`${this.base}/classes/${id}`, b); }
  deleteClass(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/classes/${id}`); }

  // Class ↔ teachers (0..N teachers per class)
  /** `level` limite la liste aux enseignants de cette section (+ ceux sans section). */
  assignableTeachers(level?: string | null): Observable<TeacherOption[]> {
    const q = level ? `?level=${encodeURIComponent(level)}` : '';
    return this.http.get<TeacherOption[]>(`${this.base}/teachers${q}`);
  }
  classTeachers(classId: string): Observable<TeacherOption[]> { return this.http.get<TeacherOption[]>(`${this.base}/classes/${classId}/teachers`); }
  setClassTeachers(classId: string, employeeIds: string[]): Observable<TeacherOption[]> {
    return this.http.put<TeacherOption[]>(`${this.base}/classes/${classId}/teachers`, { employeeIds });
  }

  // Subjects
  listSubjects(): Observable<SubjectView[]> { return this.http.get<SubjectView[]>(`${this.base}/subjects`); }
  createSubject(b: SubjectUpsert): Observable<SubjectView> { return this.http.post<SubjectView>(`${this.base}/subjects`, b); }
  updateSubject(id: string, b: SubjectUpsert): Observable<SubjectView> { return this.http.put<SubjectView>(`${this.base}/subjects/${id}`, b); }
  deleteSubject(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/subjects/${id}`); }

  // Per-class coefficients
  listCoefficients(): Observable<ClassCoefView[]> { return this.http.get<ClassCoefView[]>(`${this.base}/subjects/coefficients`); }
  upsertCoefficient(b: ClassCoefUpsert): Observable<ClassCoefView> {
    return this.http.post<ClassCoefView>(`${this.base}/subjects/coefficients`, b);
  }
  deleteCoefficient(classId: string, subjectId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/subjects/coefficients?classId=${encodeURIComponent(classId)}&subjectId=${encodeURIComponent(subjectId)}`);
  }
  importCoefficients(rows: CoefImportRow[]): Observable<CoefImportResult> {
    return this.http.post<CoefImportResult>(`${this.base}/subjects/coefficients/import`, { rows });
  }

  curriculum(academicSessionId: string, classId: string): Observable<CurriculumView> {
    return this.http.get<CurriculumView>(`${this.base}/curriculum`, { params: { academicSessionId, classId } });
  }
  previewCurriculumCopy(body: CurriculumCopyPreviewRequest): Observable<CurriculumCopyPreview> {
    return this.http.post<CurriculumCopyPreview>(`${this.base}/curriculum/copy/preview`, body);
  }
  applyCurriculumCopy(body: CurriculumCopyApplyRequest, key: string): Observable<CurriculumCopyPreview> {
    return this.http.post<CurriculumCopyPreview>(`${this.base}/curriculum/copy/apply`, body, { headers: { 'Idempotency-Key': key } });
  }
  createCurriculumGroup(body: SubjectGroupUpsert): Observable<SubjectGroupView> { return this.http.post<SubjectGroupView>(`${this.base}/curriculum/groups`, body); }
  updateCurriculumGroup(id: string, body: SubjectGroupUpsert): Observable<SubjectGroupView> { return this.http.put<SubjectGroupView>(`${this.base}/curriculum/groups/${id}`, body); }
  deleteCurriculumGroup(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/curriculum/groups/${id}`); }
  upsertCurriculumSubject(body: CurriculumSubjectUpsert): Observable<CurriculumSubjectView> { return this.http.post<CurriculumSubjectView>(`${this.base}/curriculum/subjects`, body); }
  deleteCurriculumSubject(academicSessionId: string, classId: string, subjectId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/curriculum/subjects`, { params: { academicSessionId, classId, subjectId } });
  }
  upsertCurriculumTeacher(body: CurriculumTeacherUpsert): Observable<CurriculumTeacherView> { return this.http.post<CurriculumTeacherView>(`${this.base}/curriculum/teachers`, body); }
  upsertHomeroom(body: HomeroomAssignmentUpsert): Observable<CurriculumTeacherView> { return this.http.post<CurriculumTeacherView>(`${this.base}/curriculum/homeroom`, body); }
  assignmentImpactPreview(body: AssignmentImpactRequest): Observable<AssignmentImpactView> { return this.http.post<AssignmentImpactView>(`${this.base}/curriculum/assignments/impact-preview`, body); }
  deleteCurriculumTeacher(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/curriculum/teachers/${id}`); }
}
