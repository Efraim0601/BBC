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
}

export interface SubjectView { id: string; code: string; subsystem: string | null; label: Record<string, string>; coef: number; }
export interface SubjectUpsert { code: string; subsystem: string | null; label: Record<string, string>; coef: number; }

// Per-class coefficients
export interface ClassCoefView { classId: string; className: string; subsystem: string; subjectId: string; subjectCode: string; coef: number; }
export interface CoefImportRow { subsystem: string; code: string; label?: string; klass: string; coef: number | null; }
export interface CoefImportError { row: number; label: string; message: string; }
export interface CoefImportResult { applied: number; subjectsCreated: number; skipped: number; errors: CoefImportError[]; }

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
  importCoefficients(rows: CoefImportRow[]): Observable<CoefImportResult> {
    return this.http.post<CoefImportResult>(`${this.base}/subjects/coefficients/import`, { rows });
  }
}
