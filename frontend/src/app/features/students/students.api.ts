import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Student } from '../../core/models';

export interface StudentUpsert {
  firstName: string;
  lastName: string;
  sex?: string;
  dob?: string | null;
  classId?: string | null;
  className?: string;
  subsystem?: string;
  level?: string;
  parentName?: string;
  parentPhone?: string;
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
  firstName: string;
  lastName: string;
  sex?: string;
  dob?: string | null;
  parentName?: string;
  parentPhone?: string;
}

export interface StudentImportRequest {
  classId: string;
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
