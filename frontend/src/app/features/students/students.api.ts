import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Student } from '../../core/models';

export interface StudentUpsert {
  firstName: string;
  lastName: string;
  niu?: string | null;
  sex?: string;
  dob?: string | null;
  birthplace?: string | null;
  repeats?: boolean;
  classId?: string | null;
  className?: string;
  subsystem?: string;
  level?: string;
  parentName?: string;
  parentPhone?: string;
  fatherName?: string | null;
  fatherPhone?: string | null;
  fatherEmail?: string | null;
  motherName?: string | null;
  motherPhone?: string | null;
  motherEmail?: string | null;
  guardianName?: string | null;
  guardianPhone?: string | null;
  guardianEmail?: string | null;
  guardianRelation?: string | null;
}

export interface ParentAccountView {
  userId: string;
  displayName: string;
  username: string;
  active: boolean;
  childCount: number;
}

export interface ParentLinkRequest {
  displayName: string;
  username: string;
  password?: string;
}

export interface StudentImportRow {
  name?: string;
  firstName: string;
  lastName: string;
  niu?: string | null;
  sex?: string;
  dob?: string | null;
  birthplace?: string | null;
  repeats?: boolean;
  parentName?: string;
  parentPhone?: string;
}

/** A class to find-or-create on the fly during import (the "5e A" format). */
export interface NewClassSpec {
  name: string;
  subsystem: string;   // FR | EN
  level: string;       // maternelle | primary | secondary
}

export interface StudentImportRequest {
  classId?: string | null;
  newClass?: NewClassSpec | null;
  rows: StudentImportRow[];
}

export interface StudentImportError {
  row: number;
  name: string;
  message: string;
}

export interface StudentImportResult {
  created: number;
  failed: number;
  errors: StudentImportError[];
}

@Injectable({ providedIn: 'root' })
export class StudentApi {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/students`;

  list(className?: string): Observable<Student[]> {
    const q = className ? `?className=${encodeURIComponent(className)}` : '';
    return this.http.get<Student[]>(`${this.base}${q}`);
  }
  create(body: StudentUpsert): Observable<Student> {
    return this.http.post<Student>(this.base, body);
  }
  importStudents(body: StudentImportRequest): Observable<StudentImportResult> {
    return this.http.post<StudentImportResult>(`${this.base}/import`, body);
  }
  update(id: string, body: StudentUpsert): Observable<Student> {
    return this.http.put<Student>(`${this.base}/${id}`, body);
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  // Parent accounts (review issue #2)
  listParents(studentId: string): Observable<ParentAccountView[]> {
    return this.http.get<ParentAccountView[]>(`${this.base}/${studentId}/parents`);
  }
  linkParent(studentId: string, body: ParentLinkRequest): Observable<ParentAccountView> {
    return this.http.post<ParentAccountView>(`${this.base}/${studentId}/parents`, body);
  }
  unlinkParent(studentId: string, parentUserId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${studentId}/parents/${parentUserId}`);
  }
}
